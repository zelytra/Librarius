package zelytra.librarius.author;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.AuthorFollowRepository;
import zelytra.librarius.domain.repository.AuthorRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.web.ApiDtos.AuthorDetailDto;
import zelytra.librarius.web.ApiDtos.AuthorSummaryDto;
import zelytra.librarius.web.ApiDtos.AuthorWorkDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The authors of the shared catalog, read through one user's follows.
 *
 * <p>Two jobs. It resolves the free-text credit line of an entry into the shared
 * {@code author} rows — the runtime counterpart of the backfill in
 * {@code V13__author_entities.sql}, same split and same fold, so a work recorded today and a
 * work migrated from the free-text column land on the same rows. And it reads authors back
 * for {@code /api/authors}: the name search, an author's bibliography, and follow/unfollow.
 *
 * <p><strong>Scoping.</strong> Unlike {@code SeriesService}, this is a catalog browser: an
 * author is visible to anyone authenticated, on the whole shared catalog, whether or not the
 * caller owns anything of theirs. An unknown identifier is a 404; a known one never is. Only
 * the {@code followed} flag and the {@code libraryItemId} carried on each work of the
 * bibliography are user-scoped, both always read through {@code CurrentUser.id()}.
 */
@ApplicationScoped
public class AuthorService {

    /** Ceiling on the number of authors the name search returns. */
    private static final int MAX_SEARCH_RESULTS = 20;

    @Inject
    AuthorRepository authors;

    @Inject
    AuthorFollowRepository follows;

    @Inject
    LibraryItemRepository items;

    @Inject
    CatalogService catalog;

    /** Whether the author page's bibliography is enriched from the providers (off in tests). */
    @ConfigProperty(name = "librarius.author.enrich-bibliography", defaultValue = "true")
    boolean enrichBibliography;

    /** Provider works pulled onto an author page: enough for a full bibliography, bounded. */
    private static final int MAX_PROVIDER_WORKS = 40;

    /**
     * Resolves a free-text credit line, creating the authors nobody has credited yet.
     *
     * <p>Duplicates within the line collapse: "Asimov, asimov" credits one person, which is
     * what the {@code work_author} primary key would enforce anyway.
     *
     * @param creditLine the raw value, e.g. {@code "Isaac Asimov, Robert Silverberg"}; may be
     *                   null or blank
     * @return the authors to credit on the work, empty when the line names none
     */
    public Set<Author> resolve(String creditLine) {
        // Folded key -> the spelling it was first seen under.
        Map<String, String> names = new LinkedHashMap<>();
        for (String part : AuthorNormalizer.parts(creditLine)) {
            String key = AuthorNormalizer.key(part);
            if (key != null) {
                names.putIfAbsent(key, AuthorNormalizer.name(part));
            }
        }
        if (names.isEmpty()) {
            return Set.of();
        }

        Map<String, Author> known = authors.byKeys(names.keySet());
        Set<Author> resolved = new LinkedHashSet<>();
        names.forEach((key, name) -> resolved.add(known.computeIfAbsent(key, missing -> {
            Author created = new Author();
            created.name = name;
            created.nameKey = missing;
            authors.persist(created);
            return created;
        })));
        return resolved;
    }

    /**
     * Authors of the shared catalog whose name matches the term, with the caller's own
     * {@code followed} flag on each. A blank term matches nothing rather than the whole table.
     */
    public List<AuthorSummaryDto> search(String userId, String term) {
        List<Author> found = authors.search(term, MAX_SEARCH_RESULTS);
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(a -> a.id).toList();
        Map<UUID, Long> counts = authors.workCounts(ids);
        Set<UUID> followed = follows.followedAuthorIds(userId);
        return found.stream()
                .map(a -> AuthorSummaryDto.of(a, counts.getOrDefault(a.id, 0L),
                        followed.contains(a.id)))
                .toList();
    }

    /**
     * An author and their bibliography, with the caller's {@code followed} flag. Empty only
     * when the identifier is unknown — the whole catalog is browsable, ownership regardless.
     */
    public Optional<AuthorDetailDto> detail(String userId, UUID authorId) {
        Author author = authors.findById(authorId);
        if (author == null) {
            return Optional.empty();
        }
        List<Work> works = authors.bibliography(authorId);
        List<UUID> workIds = works.stream().map(w -> w.id).toList();
        Map<UUID, String> covers = authors.coverByWork(workIds);
        Map<UUID, UUID> ownedItems = items.itemIdsByWork(userId, workIds);
        // The shared catalog can hold one title twice — an import founded a work before another
        // entry filed the same volume under its series. One line per title: the reader's own
        // copy first, then one with a cover, the catalog order breaking the remaining ties.
        Map<String, AuthorWorkDto> byTitle = new LinkedHashMap<>();
        for (Work w : works) {
            AuthorWorkDto dto = AuthorWorkDto.of(w, covers.get(w.id), ownedItems.get(w.id));
            byTitle.merge(bibliographyKey(dto.title(), dto.volumeNumber()), dto,
                    (kept, other) -> rank(other) > rank(kept) ? other : kept);
        }
        List<AuthorWorkDto> bibliography = new ArrayList<>(byTitle.values());
        works = works.stream().filter(w -> byTitle.values().stream()
                .anyMatch(dto -> w.id.equals(dto.workId()))).toList();

        // Add the works a provider credits to the author but the local catalog does not hold
        // yet: they carry no workId, which the page renders as a title nobody here owns. Behind
        // a flag, and off the transaction path, so the suite never reaches out to a provider and
        // no database connection is held across the outbound call.
        if (enrichBibliography) {
            Set<String> known = new java.util.HashSet<>(byTitle.keySet());
            Set<Kind> kinds = works.stream().map(w -> w.kind)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (CatalogResult r : catalog.worksOfAuthor(kinds, author.name, MAX_PROVIDER_WORKS)) {
                // A provider's author search matches words, not people: the BnF answered "Frank
                // Herbert" with a thesis by F. T. H. Fletcher and a book on Van Gogh. Only a
                // work that actually credits this author joins the bibliography.
                if (r.title() != null && credits(r.authors(), author.name)
                        && known.add(bibliographyKey(r.title(), r.volumeNumber()))) {
                    bibliography.add(new AuthorWorkDto(null, r.kind(), r.title(), r.authors(),
                            r.seriesTitle(), r.volumeNumber(), r.year(), r.coverUrl(), null, null));
                }
            }
        }
        return Optional.of(AuthorDetailDto.of(author, follows.isFollowing(userId, authorId),
                bibliography));
    }

    /**
     * What makes two bibliography lines one title: the title folded like an author key (case,
     * accents, punctuation), without an edition statement in brackets ("(French Edition)",
     * "(Éd. collector)") and without its subtitle, plus the volume number when there is one —
     * so "La Zone Du Dehors (French Edition)" and "La zone du dehors" are one line, while two
     * volumes of a run stay two. Package-private for the test.
     */
    static String bibliographyKey(String title, Integer volume) {
        if (title == null) {
            return "";
        }
        String main = EDITION_NOTE.matcher(title).replaceAll(" ");
        int subtitle = main.indexOf(" : ");
        if (subtitle > 0) {
            main = main.substring(0, subtitle);
        }
        // A volume keys on its series and number, however its title words it: "One Piece Tome
        // 1" recorded with no number and "One Piece Tome 1" filed as volume 1 are one book.
        zelytra.librarius.catalog.VolumeParser.Parsed parsed =
                zelytra.librarius.catalog.VolumeParser.parse(main);
        Integer number = volume != null ? volume : parsed.volumeNumber();
        if (number != null && parsed.seriesTitle() != null) {
            main = parsed.seriesTitle();
        }
        String key = AuthorNormalizer.key(main);
        return (key == null ? "" : key) + (number != null ? "#" + number : "");
    }

    /** A bracketed edition statement: "(French Edition)", "(Éd. collector)", "(Nouvelle édition)". */
    private static final java.util.regex.Pattern EDITION_NOTE = java.util.regex.Pattern.compile(
            "\\((?=[^)]*(?iu:edition|édition|(?<!\\p{L})[ée]d\\.))[^)]*\\)");

    /** Which of two copies of one title a bibliography keeps: owned, then with a cover. */
    private static int rank(AuthorWorkDto dto) {
        return (dto.libraryItemId() != null ? 2 : 0) + (dto.coverUrl() != null ? 1 : 0);
    }

    /**
     * Whether a credit line names this author: one of its names carries every word of the
     * author's, each word folded and allowed to differ by a romanisation suffix ("Eiichiro" /
     * "Eiichirou") or to be an initial ("J. R. R. Tolkien" / "John Ronald Reuel Tolkien").
     * Package-private for the test.
     */
    static boolean credits(String creditLine, String authorName) {
        List<String> wanted = words(authorName);
        if (wanted.isEmpty() || creditLine == null) {
            return false;
        }
        for (String part : AuthorNormalizer.parts(creditLine)) {
            List<String> have = words(part);
            if (have.isEmpty()) {
                continue;
            }
            // The family name closes both names — headings arrive in reading order — and is
            // spelled out on both: an initial may stand for a given name, never for the whole
            // person. Otherwise "F. T. H. Fletcher", "Frank Thomas Herbert Fletcher" and the
            // German writer Herbert Frank, all live BnF answers, passed for Frank Herbert.
            String family = wanted.get(wanted.size() - 1);
            String last = have.get(have.size() - 1);
            boolean familyNamed = last.length() > 1 && sameNameWord(family, last);
            if (familyNamed && wanted.stream()
                    .allMatch(w -> have.stream().anyMatch(h -> sameNameWord(w, h)))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(String name) {
        String key = AuthorNormalizer.key(name);
        if (key == null) {
            return List.of();
        }
        return java.util.Arrays.stream(key.split("-")).filter(w -> !w.isEmpty()).toList();
    }

    private static boolean sameNameWord(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.length() == 1 || b.length() == 1) {
            return a.charAt(0) == b.charAt(0);
        }
        return a.length() >= 3 && b.length() >= 3 && (a.startsWith(b) || b.startsWith(a));
    }

    /**
     * Starts following an author. Idempotent.
     *
     * @return false only when no author carries this identifier, which the resource turns
     *         into a 404
     */
    @Transactional
    public boolean follow(String userId, UUID authorId) {
        if (authors.findById(authorId) == null) {
            return false;
        }
        follows.follow(userId, authorId);
        return true;
    }

    /**
     * Stops following an author. Idempotent.
     *
     * @return false only when no author carries this identifier
     */
    @Transactional
    public boolean unfollow(String userId, UUID authorId) {
        if (authors.findById(authorId) == null) {
            return false;
        }
        follows.unfollow(userId, authorId);
        return true;
    }
}
