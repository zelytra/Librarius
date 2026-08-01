package zelytra.librarius.author;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.AuthorFollowRepository;
import zelytra.librarius.domain.repository.AuthorRepository;
import zelytra.librarius.web.ApiDtos.AuthorDetailDto;
import zelytra.librarius.web.ApiDtos.AuthorSummaryDto;
import zelytra.librarius.web.ApiDtos.AuthorWorkDto;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 * the {@code followed} flag is user-scoped, always through {@code CurrentUser.id()}.
 */
@ApplicationScoped
public class AuthorService {

    /** Ceiling on the number of authors the name search returns. */
    private static final int MAX_SEARCH_RESULTS = 20;

    @Inject
    AuthorRepository authors;

    @Inject
    AuthorFollowRepository follows;

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
        Map<UUID, String> covers = authors.coverByWork(works.stream().map(w -> w.id).toList());
        List<AuthorWorkDto> bibliography = works.stream()
                .map(w -> AuthorWorkDto.of(w, covers.get(w.id)))
                .toList();
        return Optional.of(AuthorDetailDto.of(author, follows.isFollowing(userId, authorId),
                bibliography));
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
