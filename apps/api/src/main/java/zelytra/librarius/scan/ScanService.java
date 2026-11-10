package zelytra.librarius.scan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import zelytra.librarius.author.AuthorNormalizer;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;
import zelytra.librarius.web.ApiDtos.ScanResultDto;
import zelytra.librarius.web.ApiDtos.WishlistItemDto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
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
     * The fields matching needs, one per title of the reader's — not the entities: loading a
     * 2400-title library as entities, each with its reading progress, took three seconds a scan.
     */
    record Candidate(UUID id, String isbn13, String title, String authors, String seriesTitle, Integer volume) {
    }

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
        List<Candidate> owned = QuarkusTransaction.requiringNew().call(() -> candidates("LibraryItem", userId));
        List<Candidate> wished = QuarkusTransaction.requiringNew().call(() -> candidates("WishlistItem", userId));

        Optional<Candidate> ownedByIsbn = owned.stream().filter(c -> isbn13.equals(c.isbn13())).findFirst();
        Optional<Candidate> wishedByIsbn = wished.stream().filter(c -> isbn13.equals(c.isbn13())).findFirst();
        if (ownedByIsbn.isPresent() || wishedByIsbn.isPresent()) {
            return result(userId, isbn13, Match.ISBN, ownedByIsbn, wishedByIsbn, null);
        }

        beforeCatalog.run();
        CatalogResult found = catalog.search(Set.of(), new CatalogQuery(null, null, null, null, null, isbn13), 5)
                .stream().findFirst().orElse(null);
        if (found == null) {
            return result(userId, isbn13, Match.NONE, Optional.empty(), Optional.empty(), null);
        }
        // Strictest first: the same title word for word, then the same volume of the same
        // series, and only then a title worded differently with the same words and numbers.
        List<BiPredicate<Candidate, CatalogResult>> passes = List.of(
                (c, r) -> sameTitle(c, r), (c, r) -> sameVolume(c, r), (c, r) -> closeTitle(c, r));
        List<Match> labels = List.of(Match.TITLE, Match.SERIES_VOLUME, Match.TITLE);
        for (int i = 0; i < passes.size(); i++) {
            BiPredicate<Candidate, CatalogResult> pass = passes.get(i);
            Optional<Candidate> ownedLike = owned.stream().filter(c -> pass.test(c, found)).findFirst();
            Optional<Candidate> wishedLike = wished.stream().filter(c -> pass.test(c, found)).findFirst();
            if (ownedLike.isPresent() || wishedLike.isPresent()) {
                return result(userId, isbn13, labels.get(i), ownedLike, wishedLike, found);
            }
        }
        return result(userId, isbn13, Match.NONE, Optional.empty(), Optional.empty(), found);
    }

    private List<Candidate> candidates(String entity, String userId) {
        List<Object[]> rows = library.getEntityManager().createQuery("""
                        select i.id, e.isbn13, w.title, w.authorsText, w.seriesTitle, w.volumeNumber
                        from %s i join i.edition e join e.work w
                        where i.userId = :userId
                        """.formatted(entity), Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        return rows.stream().map(r -> new Candidate((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                (String) r[4], (Integer) r[5])).toList();
    }

    /** The answer, with the one matched title of each list loaded in full for the screen. */
    private ScanResultDto result(String userId, String isbn13, Match match, Optional<Candidate> owned,
            Optional<Candidate> wished, CatalogResult found) {
        return QuarkusTransaction.requiringNew().call(() -> new ScanResultDto(isbn13, match.name(),
                owned.flatMap(c -> library.findOwned(userId, c.id())).map(LibraryItemDto::of).orElse(null),
                wished.flatMap(c -> wishlist.findOwned(userId, c.id())).map(WishlistItemDto::of).orElse(null),
                found));
    }

    /** The same title word for word, by a compatible author. */
    static boolean sameTitle(Candidate mine, CatalogResult found) {
        String title = AuthorNormalizer.key(mine.title());
        return title != null && title.equals(AuthorNormalizer.key(found.title()))
                && authorsCompatible(mine.authors(), found.authors());
    }

    /** The same volume number of the same series. */
    static boolean sameVolume(Candidate mine, CatalogResult found) {
        String series = AuthorNormalizer.key(mine.seriesTitle());
        return series != null && mine.volume() != null
                && series.equals(AuthorNormalizer.key(found.seriesTitle()))
                && mine.volume().equals(found.volumeNumber());
    }

    /**
     * A title worded differently — "Les Misérables, Tome 1 : Fantine" and "Fantine : Collection
     * Les Misérables. Tome I" — by a compatible author: most of the words in common, and exactly
     * the same numbers, Roman ones read as such, so that volume 3 of a run never stands for
     * volume 4 however alike their titles are.
     */
    static boolean closeTitle(Candidate mine, CatalogResult found) {
        if (!authorsCompatible(mine.authors(), found.authors())) {
            return false;
        }
        Set<String> a = titleWords(mine.title());
        Set<String> b = titleWords(found.title());
        if (a.size() < 2 || b.size() < 2 || !numbers(mine.title()).equals(numbers(found.title()))) {
            return false;
        }
        long shared = a.stream().filter(b::contains).count();
        return shared / (double) (a.size() + b.size() - shared) >= 0.6;
    }

    private static final Pattern ROMAN = Pattern.compile("^(x{0,3})(ix|iv|v?i{0,3})$");

    private static Set<String> titleWords(String title) {
        return tokens(title).stream().filter(w -> w.length() >= 3 && number(w) == null).collect(Collectors.toSet());
    }

    private static Set<Integer> numbers(String title) {
        return tokens(title).stream().map(ScanService::number).filter(n -> n != null).collect(Collectors.toSet());
    }

    private static List<String> tokens(String title) {
        String key = AuthorNormalizer.key(title);
        return key == null ? List.of() : Arrays.asList(key.split("-"));
    }

    private static Integer number(String token) {
        if (token.matches("\\d{1,4}")) {
            return Integer.valueOf(token);
        }
        if (token.isEmpty() || !ROMAN.matcher(token).matches()) {
            return null;
        }
        int value = 0;
        int previous = 0;
        for (int i = token.length() - 1; i >= 0; i--) {
            int digit = switch (token.charAt(i)) {
                case 'i' -> 1;
                case 'v' -> 5;
                default -> 10;
            };
            value += digit < previous ? -digit : digit;
            previous = Math.max(previous, digit);
        }
        return value;
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
