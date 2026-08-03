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
     * Imported or hand-entered editions still without a page count — a scrape brings none, and a
     * manual entry may omit it. Joined to their work so a page-count lookup can read the title and
     * author, most recently added first so a fresh import fills before an old backlog, and capped
     * so one enrichment run cannot spend the provider quota. Only editions with no provider
     * reference: a catalog edition either came with its length or the provider has none to give.
     */
    public List<Edition> needingPageCount(int limit) {
        return getEntityManager()
                .createQuery("""
                        select e from Edition e
                          join fetch e.work w
                        where e.pageCount is null
                          and e.provider is null
                        order by e.createdAt desc, e.id asc
                        """, Edition.class)
                .setMaxResults(limit)
                .getResultList();
    }
}
