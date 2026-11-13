package zelytra.librarius.library;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Kind;

/**
 * Fills the holes in the page counts (#414).
 *
 * <p>An edition no catalog knew the length of gets {@code page_count_estimate}: the average of
 * the known volumes of its series when there is one, else a figure per kind. The real
 * {@code page_count} always wins where both exist, so a length the enricher finds later
 * replaces the guess without anything to undo.
 *
 * <p>New editions get the per-kind figure at insertion ({@code Edition#estimateMissingLength});
 * this pass refines them from their series as volumes of known length arrive. Two set-based
 * statements, whatever the size of the catalog.
 */
@ApplicationScoped
public class PageCountEstimator {

    @Inject
    EntityManager em;

    /**
     * Typical length of a title of that kind, in pages.
     *
     * <p>Rounded medians of what the catalogs report: a trade novel around 320 pages, a
     * tankōbon 192, a franco-belgian album 56, a graphic novel 160. An audiobook is counted
     * like the book it reads.
     */
    public static int defaultFor(Kind kind) {
        if (kind == null) {
            return 320;
        }
        return switch (kind) {
            case MANGA -> 192;
            case COMIC -> 56;
            case GRAPHIC_NOVEL -> 160;
            case BOOK, AUDIOBOOK -> 320;
        };
    }

    @Scheduled(every = "{librarius.pages.estimate.every}",
            delayed = "{librarius.pages.estimate.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledEstimate() {
        int updated = estimateNow();
        if (updated > 0) {
            Log.infof("Page estimates: %d editions refined", updated);
        }
    }

    /** Runs both passes now; returns how many editions changed. */
    @Transactional
    public int estimateNow() {
        int defaults = em.createNativeQuery("""
                update edition e
                set page_count_estimate = case w.kind
                                              when 'MANGA' then 192
                                              when 'COMIC' then 56
                                              when 'GRAPHIC_NOVEL' then 160
                                              else 320
                                          end
                from work w
                where e.work_id = w.id
                  and e.page_count is null
                  and e.page_count_estimate is null
                """).executeUpdate();
        int fromSeries = em.createNativeQuery("""
                with series_average as (
                    select w.series_id, round(avg(e.page_count))::int as pages
                    from edition e
                      join work w on w.id = e.work_id
                    where w.series_id is not null
                      and e.page_count is not null
                      and e.page_count > 0
                    group by w.series_id
                )
                update edition e
                set page_count_estimate = sa.pages
                from work w
                  join series_average sa on sa.series_id = w.series_id
                where e.work_id = w.id
                  and e.page_count is null
                  and e.page_count_estimate is distinct from sa.pages
                """).executeUpdate();
        return defaults + fromSeries;
    }
}
