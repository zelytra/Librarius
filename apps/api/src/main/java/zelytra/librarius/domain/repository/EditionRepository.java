package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Edition;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EditionRepository implements PanacheRepositoryBase<Edition, UUID> {

    /**
     * Every edition of a work, in the order they entered the catalog.
     *
     * <p>Ordered on the creation date rather than on the release date: most editions carry
     * no release date, and falling back to the identifier would shuffle the list on every
     * call — a section whose rows move between two renders is unusable to pick from.
     */
    public List<Edition> listByWork(UUID workId) {
        return list("work.id = ?1 order by createdAt asc, id asc", workId);
    }

    /**
     * An edition of the work that describes nothing beyond the work itself — no ISBN,
     * publisher, language, length, format, date or provider record: what an import or a bare
     * manual entry records, and what the next one of the same title can share — provided the
     * user does not hold it already, in the library or the wishlist, where an edition is unique
     * per user.
     */
    public java.util.Optional<Edition> findBareFreeFor(UUID workId, String userId) {
        return getEntityManager().createQuery("""
                        select e from Edition e
                        where e.work.id = :work and e.isbn13 is null and e.publisher is null
                          and e.language is null and e.pageCount is null and e.format is null
                          and e.releaseDate is null and e.provider is null
                          and not exists (select 1 from LibraryItem li where li.edition = e and li.userId = :user)
                          and not exists (select 1 from WishlistItem wi where wi.edition = e and wi.userId = :user)
                        order by e.createdAt asc, e.id asc""", Edition.class)
                .setParameter("work", workId)
                .setParameter("user", userId)
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    /**
     * Editions of a series whose publication date is still ahead.
     *
     * <p>The only source of <em>French</em> release dates the application holds today: no
     * free API covers French publishers, but an edition entered by hand or imported with a
     * future date says exactly when a volume comes out, on a market its language names.
     * {@code UpcomingReleaseRefresher} turns those into announcements.
     */
    public List<Edition> announcedFrom(LocalDate from) {
        return getEntityManager()
                .createQuery("""
                        select e from Edition e
                          join fetch e.work w
                          join fetch w.series s
                        where e.releaseDate >= :from
                          and w.series is not null
                        order by e.releaseDate asc, e.id asc
                        """, Edition.class)
                .setParameter("from", from)
                .getResultList();
    }

    /**
     * Imported or hand-entered editions the catalog may still complete — missing a cover, an
     * ISBN or a length — that were never looked up, or not since {@code retryBefore}. Never
     * tried first, then the longest-waiting: an edition the catalog does not know goes to the
     * back instead of holding the head of the queue on every run. Only editions with no
     * provider reference: a catalog edition already came with whatever its provider knew.
     */
    public List<Edition> needingEnrichment(java.time.OffsetDateTime retryBefore, int limit) {
        return getEntityManager()
                .createQuery("""
                        select e from Edition e
                          join fetch e.work w
                        where e.provider is null
                          and (e.pageCount is null or e.coverUrl is null or e.isbn13 is null)
                          and (e.enrichAttemptedAt is null or e.enrichAttemptedAt < :retryBefore)
                        order by e.enrichAttemptedAt asc nulls first, e.createdAt desc, e.id asc
                        """, Edition.class)
                .setParameter("retryBefore", retryBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * The same editions, restricted to those in one user's library or wishlist — what an
     * import just brought in (#425), looked up straight away rather than behind everybody's.
     */
    public List<Edition> needingEnrichmentFor(String userId, java.time.OffsetDateTime retryBefore,
            int limit) {
        return getEntityManager()
                .createQuery("""
                        select e from Edition e
                          join fetch e.work w
                        where e.provider is null
                          and (e.pageCount is null or e.coverUrl is null or e.isbn13 is null)
                          and (e.enrichAttemptedAt is null or e.enrichAttemptedAt < :retryBefore)
                          and (exists (select 1 from LibraryItem li where li.edition = e and li.userId = :userId)
                               or exists (select 1 from WishlistItem wi where wi.edition = e and wi.userId = :userId))
                        order by e.enrichAttemptedAt asc nulls first, e.createdAt desc, e.id asc
                        """, Edition.class)
                .setParameter("retryBefore", retryBefore)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList();
    }
}
