package zelytra.librarius.scan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import zelytra.librarius.author.AuthorNormalizer;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;
import zelytra.librarius.web.ApiDtos.ScanResultDto;
import zelytra.librarius.web.ApiDtos.WishlistItemDto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a scanned barcode is, for the reader holding the book: theirs already, on their
 * wishlist, or not yet either — with the catalog entry that adds it in one tap.
 *
 * <p>The ISBN alone is not enough to recognise a book the reader owns. A library imported from
 * Booknode or Babelio carries no ISBN at all — those sites do not publish one — so a reader
 * scanning a book they have read would be told it is new to them. When the ISBN finds nothing,
 * the catalog says what the book is, and the library is searched for that: the same title by
 * the same author, or the same volume of the same series.
 */
@ApplicationScoped
public class ScanService {

    /** How the scanned book was recognised in the reader's own lists. */
    public enum Match {
        /** An edition of theirs carries this very ISBN. */
        ISBN,
        /** Same title, compatible author: very likely the book, perhaps another edition. */
        TITLE,
        /** Same series, same volume number. */
        SERIES_VOLUME,
        /** Not in the library nor on the wishlist. */
        NONE
    }

    @Inject
    LibraryItemRepository library;

    @Inject
    WishlistItemRepository wishlist;

    @Inject
    CatalogService catalog;

    /**
     * Looks the ISBN up in the reader's lists first — the answer a bookshop needs within two
     * seconds, and one that needs nothing beyond our own database — and only then in the
     * catalogs. The catalog call runs outside any transaction: it can take seconds, and a
     * connection held across it is one the rest of the API waits for.
     *
     * @param beforeCatalog run just before the catalogs are asked, to charge the caller's quota
     *                      only for the scans that reach them
     */
    public ScanResultDto lookUp(String userId, String isbn13, Runnable beforeCatalog) {
        ScanResultDto byIsbn = QuarkusTransaction.requiringNew().call(() -> {
            Optional<LibraryItem> owned = first(library.listForExport(userId), it -> it.edition, isbn13);
            Optional<WishlistItem> wished = first(wishlist.listForExport(userId), it -> it.edition, isbn13);
            return owned.isPresent() || wished.isPresent()
                    ? result(isbn13, Match.ISBN, owned.orElse(null), wished.orElse(null), null)
                    : null;
        });
        if (byIsbn != null) {
            return byIsbn;
        }

        beforeCatalog.run();
        CatalogResult found = catalog.search(Set.of(), new CatalogQuery(null, null, null, null, null, isbn13), 5)
                .stream().findFirst().orElse(null);
        if (found == null) {
            return result(isbn13, Match.NONE, null, null, null);
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            List<LibraryItem> owned = library.listForExport(userId);
            List<WishlistItem> wished = wishlist.listForExport(userId);
            for (Match how : List.of(Match.TITLE, Match.SERIES_VOLUME)) {
                Optional<LibraryItem> ownedLike = owned.stream()
                        .filter(it -> same(how, it.edition.work, found)).findFirst();
                Optional<WishlistItem> wishedLike = wished.stream()
                        .filter(it -> same(how, it.edition.work, found)).findFirst();
                if (ownedLike.isPresent() || wishedLike.isPresent()) {
                    return result(isbn13, how, ownedLike.orElse(null), wishedLike.orElse(null), found);
                }
            }
            return result(isbn13, Match.NONE, null, null, found);
        });
    }

    private static ScanResultDto result(String isbn13, Match match, LibraryItem owned, WishlistItem wished,
            CatalogResult found) {
        return new ScanResultDto(isbn13, match.name(),
                owned != null ? LibraryItemDto.of(owned) : null,
                wished != null ? WishlistItemDto.of(wished) : null,
                found);
    }

    private static <T> Optional<T> first(List<T> items, Function<T, Edition> edition, String isbn13) {
        return items.stream().filter(it -> isbn13.equals(edition.apply(it).isbn13)).findFirst();
    }

    /** Whether a work of the reader's is the catalog's book, judged the way {@code how} says. */
    static boolean same(Match how, Work work, CatalogResult found) {
        return switch (how) {
            case TITLE -> {
                String mine = AuthorNormalizer.key(work.title);
                yield mine != null && mine.equals(AuthorNormalizer.key(found.title()))
                        && authorsCompatible(work.authorsText, found.authors());
            }
            case SERIES_VOLUME -> {
                String series = AuthorNormalizer.key(work.seriesTitle);
                yield series != null && work.volumeNumber != null
                        && series.equals(AuthorNormalizer.key(found.seriesTitle()))
                        && work.volumeNumber.equals(found.volumeNumber());
            }
            default -> false;
        };
    }

    /**
     * Two author credits that could be the same person: either one missing, or a name of three
     * letters or more in common. Loose on purpose — "Hugo, Victor" and "Victor Hugo" must agree —
     * since it only ever confirms a title that already matched word for word.
     */
    static boolean authorsCompatible(String a, String b) {
        Set<String> mine = words(a);
        Set<String> theirs = words(b);
        if (mine.isEmpty() || theirs.isEmpty()) {
            return true;
        }
        return mine.stream().anyMatch(theirs::contains);
    }

    private static Set<String> words(String credit) {
        String key = AuthorNormalizer.key(credit);
        if (key == null) {
            return Set.of();
        }
        return Arrays.stream(key.split("-")).filter(w -> w.length() >= 3).collect(Collectors.toSet());
    }
}
