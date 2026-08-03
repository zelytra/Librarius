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
        List<AuthorWorkDto> bibliography = new ArrayList<>(works.stream()
                .map(w -> AuthorWorkDto.of(w, covers.get(w.id), ownedItems.get(w.id)))
                .toList());

        // Add the works a provider credits to the author but the local catalog does not hold
        // yet: they carry no workId, which the page renders as a title nobody here owns. Behind
        // a flag, and off the transaction path, so the suite never reaches out to a provider and
        // no database connection is held across the outbound call.
        if (enrichBibliography) {
            Set<String> known = works.stream().map(w -> normalizeTitle(w.title))
                    .collect(Collectors.toCollection(java.util.HashSet::new));
            Set<Kind> kinds = works.stream().map(w -> w.kind)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (CatalogResult r : catalog.worksOfAuthor(kinds, author.name, MAX_PROVIDER_WORKS)) {
                if (r.title() != null && known.add(normalizeTitle(r.title()))) {
                    bibliography.add(new AuthorWorkDto(null, r.kind(), r.title(), r.authors(),
                            r.seriesTitle(), r.volumeNumber(), r.year(), r.coverUrl(), null, null));
                }
            }
        }
        return Optional.of(AuthorDetailDto.of(author, follows.isFollowing(userId, authorId),
                bibliography));
    }

    private static String normalizeTitle(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).trim();
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
