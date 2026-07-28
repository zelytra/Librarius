package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.ReadingProgress;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@ApplicationScoped
public class ReadingProgressRepository implements PanacheRepositoryBase<ReadingProgress, UUID> {

    public Optional<ReadingProgress> findByItem(UUID libraryItemId) {
        return find("libraryItem.id", libraryItemId).firstResultOptional();
    }

    // ── Reading timeline ──────────────────────────────────────────────────────
    // Everything below is grouped by the database. The endpoint answers with a
    // handful of buckets whatever the size of the collection behind them: folding
    // a reading history in Java is exactly what moving the statistics to SQL got
    // rid of, and a timeline walks more rows than the counters ever did.

    /** Size of a timeline bucket, exposed as the {@code granularity} query parameter. */
    public enum TimelineGranularity {
        MONTH,
        YEAR;

        /**
         * Resolves the value sent by the client, {@code MONTH} when it is absent.
         *
         * @return the matching granularity, or empty when the value is not one of ours
         */
        public static Optional<TimelineGranularity> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.of(MONTH);
            }
            return Arrays.stream(values())
                    .filter(g -> g.name().equalsIgnoreCase(value.trim()))
                    .findFirst();
        }
    }

    /**
     * What the user finished during one bucket of the timeline.
     *
     * @param year  calendar year of the bucket
     * @param month month of the bucket, {@code null} at year granularity
     * @param books titles finished during the bucket
     * @param pages their pages, ignoring the editions carrying no page count
     */
    public record PeriodTotal(int year, Integer month, long books, long pages) {
    }

    /** A label of some dimension and the number of finished titles carrying it. */
    public record LabelTotal(String label, long count) {
    }

    /** Dimensions the finished titles can be broken down by, beyond the genres. */
    public enum Breakdown {
        AUTHOR("w.authors", ""),
        PUBLISHER("e.publisher", ""),
        LANGUAGE("e.language", ""),
        /** The rank category the title is filed under; an inner join drops the unranked. */
        RANK("rc.label", " join li.rankCategory rc");

        private final String column;
        private final String extraJoin;

        Breakdown(String column, String extraJoin) {
            this.column = column;
            this.extraJoin = extraJoin;
        }

        public static Optional<Breakdown> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Arrays.stream(values())
                    .filter(b -> b.name().equalsIgnoreCase(value.trim()))
                    .findFirst();
        }
    }

    /**
     * Titles finished inside the window, bucketed by month or by year.
     *
     * <p>Only the buckets the user actually read something in come back: a period with no
     * reading is an absent row, not a zero. Padding a range out to its every month is a
     * rendering concern, and doing it here would make the answer grow with the range asked
     * for rather than with the data behind it.
     *
     * @param from first day counted, inclusive
     * @param to   last day counted, inclusive
     */
    public List<PeriodTotal> timeline(String userId, LocalDate from, LocalDate to,
            TimelineGranularity granularity) {
        String bucket = granularity == TimelineGranularity.MONTH
                ? "year(rp.finishedAt), month(rp.finishedAt)"
                : "year(rp.finishedAt)";

        List<Object[]> rows = getEntityManager()
                .createQuery("""
                        select %s, count(rp), sum(li.edition.pageCount)
                        from ReadingProgress rp
                          join rp.libraryItem li
                        where li.userId = :userId
                          and rp.finishedAt between :from and :to
                        group by %s
                        order by %s
                        """.formatted(bucket, bucket, bucket), Object[].class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        boolean monthly = granularity == TimelineGranularity.MONTH;
        return rows.stream()
                .map(row -> new PeriodTotal(
                        ((Number) row[0]).intValue(),
                        monthly ? ((Number) row[1]).intValue() : null,
                        ((Number) row[monthly ? 2 : 1]).longValue(),
                        asLong(row[monthly ? 3 : 2])))
                .toList();
    }

    /**
     * How long the user took, on average, to finish a title in the window.
     *
     * <p>Only the titles carrying both dates count: a title marked read without ever having
     * been marked as being read says nothing about how long it took. Empty when none of
     * them does, which an average of zero would not be the same as.
     *
     * <p>Native, unlike everything else here: subtracting two dates has no portable form in
     * HQL, and the arithmetic belongs in the database rather than in a fold over the rows.
     */
    public OptionalDouble averageDaysToFinish(String userId, LocalDate from, LocalDate to) {
        Object average = getEntityManager()
                .createNativeQuery("""
                        select avg(rp.finished_at - rp.started_at)
                        from reading_progress rp
                          join library_item li on li.id = rp.library_item_id
                        where li.user_id = :userId
                          and rp.started_at is not null
                          and rp.finished_at between :from and :to
                          and rp.finished_at >= rp.started_at
                        """)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return average == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(((Number) average).doubleValue());
    }

    /**
     * The {@code limit} most represented labels of one dimension among the titles finished
     * in the window, most frequent first. Ties are broken alphabetically so the ranking
     * stays stable from one call to the next, exactly like the genre breakdown.
     */
    public List<LabelTotal> breakdown(String userId, LocalDate from, LocalDate to,
            Breakdown dimension, int limit) {
        String column = dimension.column;
        return getEntityManager()
                .createQuery("""
                        select %s, count(rp)
                        from ReadingProgress rp
                          join rp.libraryItem li
                          join li.edition e
                          join e.work w%s
                        where li.userId = :userId
                          and rp.finishedAt between :from and :to
                          and %s is not null
                          and length(trim(%s)) > 0
                        group by %s
                        order by count(rp) desc, %s asc
                        """.formatted(column, dimension.extraJoin, column, column, column, column),
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new LabelTotal((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /**
     * Where the user stands against a yearly goal, counted in the goal's own unit.
     *
     * <p>{@code BOOKS} counts every title finished during the window, {@code VOLUMES} only
     * those carrying a volume number — a run being followed rather than a standalone read —
     * and {@code PAGES} adds their pages up.
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
        return asLong(total);
    }

    /** {@code SUM} over no row, or over editions with no page count, comes back null. */
    private static long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
