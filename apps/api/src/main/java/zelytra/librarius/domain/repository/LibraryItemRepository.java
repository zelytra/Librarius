package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LibraryItemRepository implements PanacheRepositoryBase<LibraryItem, UUID> {

    public List<LibraryItem> listByUser(String userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public List<LibraryItem> listByUserAndStatus(String userId, LibraryStatus status) {
        return list("userId = ?1 and status = ?2 order by createdAt desc", userId, status);
    }

    /** Scoping-safe lookup: returns the item only if it belongs to the user. */
    public Optional<LibraryItem> findOwned(String userId, UUID id) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public boolean deleteOwned(String userId, UUID id) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }

    // ── Statistics ────────────────────────────────────────────────────────────
    // Aggregated in SQL rather than by walking the collection in memory: the cost
    // must not grow with the number of titles, /api/stats is hit on every render.

    /**
     * Per-status item counters, plus the pages of the read ones.
     *
     * @param read      items marked {@code READ}
     * @param reading   items marked {@code READING}
     * @param toRead    items marked {@code OWNED}, i.e. owned but not started
     * @param pagesRead total pages of the read items, ignoring editions with no page count
     */
    public record StatusTotals(long read, long reading, long toRead, long pagesRead) {
    }

    /** A genre label and the number of the user's items carrying it. */
    public record GenreTotal(String genre, long count) {
    }

    /**
     * Counts the user's items per status and sums the pages of the read ones, in a single
     * grouped query. Editions carrying no page count contribute nothing, {@code SUM}
     * ignoring NULL.
     */
    public StatusTotals statusTotals(String userId) {
        List<Object[]> rows = getEntityManager()
                .createQuery("""
                        select li.status, count(li), sum(li.edition.pageCount)
                        from LibraryItem li
                        where li.userId = :userId
                        group by li.status
                        """, Object[].class)
                .setParameter("userId", userId)
                .getResultList();

        long read = 0;
        long reading = 0;
        long toRead = 0;
        long pagesRead = 0;
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            switch ((LibraryStatus) row[0]) {
                case READ -> {
                    read = count;
                    pagesRead = row[2] == null ? 0L : ((Number) row[2]).longValue();
                }
                case READING -> reading = count;
                case OWNED -> toRead = count;
            }
        }
        return new StatusTotals(read, reading, toRead, pagesRead);
    }

    /**
     * Number of distinct series in the user's collection.
     *
     * <p>A series is still identified by its lower-cased title: there is no {@code series}
     * table yet, so {@code work.series_title} is the only key available. Titles that carry
     * none — standalone works — are left out.
     */
    public long countDistinctSeries(String userId) {
        return getEntityManager()
                .createQuery("""
                        select count(distinct lower(li.edition.work.seriesTitle))
                        from LibraryItem li
                        where li.userId = :userId
                          and li.edition.work.seriesTitle is not null
                          and length(trim(li.edition.work.seriesTitle)) > 0
                        """, Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    /**
     * The {@code limit} most represented genres in the user's collection, most frequent
     * first. Equal counts are broken alphabetically so the ranking stays stable from one
     * call to the next.
     */
    public List<GenreTotal> topGenres(String userId, int limit) {
        return getEntityManager()
                .createQuery("""
                        select li.edition.work.genres, count(li)
                        from LibraryItem li
                        where li.userId = :userId
                          and li.edition.work.genres is not null
                          and length(trim(li.edition.work.genres)) > 0
                        group by li.edition.work.genres
                        order by count(li) desc, li.edition.work.genres asc
                        """, Object[].class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new GenreTotal((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }
}
