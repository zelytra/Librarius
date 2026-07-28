package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.ReadingProgress;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReadingProgressRepository implements PanacheRepositoryBase<ReadingProgress, UUID> {

    public Optional<ReadingProgress> findByItem(UUID libraryItemId) {
        return find("libraryItem.id", libraryItemId).firstResultOptional();
    }

    /**
     * Where the user stands against a yearly goal, counted in the goal's own unit.
     *
     * <p>{@code BOOKS} counts every title finished during the window, {@code VOLUMES} only
     * those carrying a volume number — a run being followed rather than a standalone read —
     * and {@code PAGES} adds their pages up.
     *
     * <p>Aggregated by the database, like the rest of the statistics: the endpoint is hit on
     * every Home and Stats render, and its cost must not follow the number of titles.
     *
     * @param from first day counted, inclusive
     * @param to   last day counted, inclusive
     */
    public long progressTowards(String userId, LocalDate from, LocalDate to, GoalUnit unit) {
        String selection = unit == GoalUnit.PAGES ? "sum(li.edition.pageCount)" : "count(rp)";
        String extraCriteria = unit == GoalUnit.VOLUMES
                ? " and li.edition.work.volumeNumber is not null"
                : "";

        Object total = getEntityManager()
                .createQuery("""
                        select %s
                        from ReadingProgress rp
                          join rp.libraryItem li
                        where li.userId = :userId
                          and rp.finishedAt between :from and :to%s
                        """.formatted(selection, extraCriteria), Object.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        // SUM over no row, or over editions with no page count, comes back null.
        return total == null ? 0L : ((Number) total).longValue();
    }
}
