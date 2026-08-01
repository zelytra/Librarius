package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Work;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AuthorRepository implements PanacheRepositoryBase<Author, UUID> {

    /**
     * Authors of the shared catalog whose name contains the term, ordered by name. This is
     * the local {@code GET /api/authors?q=} search — the whole catalog, the same answer to
     * every caller — and not the external provider search of {@code /api/catalog/search}.
     *
     * <p>The term's own {@code %} and {@code _} are escaped, so typing them searches for the
     * characters rather than for the wildcards, the same rule the collection search follows.
     *
     * @param term  raw search term; a blank one matches nothing rather than the whole table
     * @param limit ceiling on the number of authors returned
     */
    public List<Author> search(String term, int limit) {
        if (term == null || term.isBlank() || limit <= 0) {
            return List.of();
        }
        return find("lower(name) like ?1 escape '!' order by lower(name) asc, id asc",
                likePattern(term))
                .range(0, limit - 1)
                .list();
    }

    /**
     * The bibliography of an author: every work of the shared catalog crediting them, in one
     * query, series grouped and volumes in order. Shared catalog data — it says what the
     * author wrote, never who owns it.
     */
    public List<Work> bibliography(UUID authorId) {
        return getEntityManager()
                .createQuery("""
                        select w from Work w
                          join w.authors a
                        where a.id = :authorId
                        order by lower(coalesce(w.seriesTitle, w.title)) asc,
                                 w.volumeNumber asc nulls first,
                                 lower(w.title) asc, w.id asc
                        """, Work.class)
                .setParameter("authorId", authorId)
                .getResultList();
    }

    /**
     * How many works of the shared catalog each of these authors is credited on, keyed by
     * author identifier. One grouped query so a page of search results costs a single
     * round-trip; an author crediting nothing is absent rather than reported as zero.
     */
    public Map<UUID, Long> workCounts(Collection<UUID> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        getEntityManager()
                .createQuery("""
                        select a.id, count(w.id) from Work w
                          join w.authors a
                        where a.id in :ids
                        group by a.id
                        """, Object[].class)
                .setParameter("ids", authorIds)
                .getResultList()
                .forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).longValue()));
        return counts;
    }

    /**
     * A cover for each of these works, keyed by work identifier: that of the earliest edition
     * carrying one, works without any cover being absent. Covers live on editions, not on
     * works, so the bibliography borrows a representative one.
     */
    public Map<UUID, String> coverByWork(Collection<UUID> workIds) {
        if (workIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> covers = new HashMap<>();
        getEntityManager()
                .createQuery("""
                        select e.work.id, e.coverUrl from Edition e
                        where e.work.id in :ids and e.coverUrl is not null
                        order by e.createdAt asc, e.id asc
                        """, Object[].class)
                .setParameter("ids", workIds)
                .getResultList()
                .forEach(row -> covers.putIfAbsent((UUID) row[0], (String) row[1]));
        return covers;
    }

    /**
     * Turns a search term into a {@code like} pattern with the user's own wildcards escaped,
     * the same fold and escape the collection search uses.
     */
    private static String likePattern(String term) {
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * The authors carrying one of these folded keys, keyed by it. Unknown keys are absent.
     *
     * <p>One query for the whole credit line rather than one per name: a work names a handful
     * of authors, and recording it must not cost a round-trip for each of them.
     *
     * @param keys keys as {@code AuthorNormalizer.key} produced them
     */
    public Map<String, Author> byKeys(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Author> found = new LinkedHashMap<>();
        for (Author author : list("nameKey in ?1", keys)) {
            found.put(author.nameKey, author);
        }
        return found;
    }
}
