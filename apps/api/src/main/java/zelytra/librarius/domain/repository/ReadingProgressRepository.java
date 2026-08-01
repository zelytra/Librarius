package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@ApplicationScoped
public class ReadingProgressRepository implements PanacheRepositoryBase<ReadingProgress, UUID> {

    /**
     * What tells a finished title from an abandoned one in every aggregation below.
     *
     * <p>Giving a title up stamps {@code finished_at} as well — the day tracking stopped is
     * worth keeping, and it is the date the reader would look for — so that column on its
     * own no longer means "read to the end". Without this clause a book put down at page 40
     * would advance the annual goal, fill a timeline bucket and enter the pace average, and
     * the figures would be wrong in the one direction nobody checks: upwards.
     *
     * <p>It excludes the abandoned rather than requiring {@code READ}, which is the same
     * thing for anything written through {@link zelytra.librarius.library.ReadingProgressService}
     * and not for a row restored from an archive, where the status and the dates come from
     * the file. Excluding changes the answer for the new status only.
     *
     * <p>The alias is {@code li} in the HQL and in the one native statement alike, so a
     * single fragment serves both and the four queries cannot drift apart.
     */
    private static final String NOT_ABANDONED = "and li.status <> :abandoned";

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
        AUTHOR("w.authorsText", ""),
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
                          %s
                        group by %s
                        order by %s
                        """.formatted(bucket, NOT_ABANDONED, bucket, bucket), Object[].class)
                .setParameter("userId", userId)
                .setParameter("abandoned", LibraryStatus.ABANDONED)
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
                          %s
                        """.formatted(NOT_ABANDONED))
                .setParameter("userId", userId)
                // A native statement compares against the stored name, not against the enum.
                .setParameter("abandoned", LibraryStatus.ABANDONED.name())
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
                          %s
                          and %s is not null
                          and length(trim(%s)) > 0
                        group by %s
                        order by count(rp) desc, %s asc
                        """.formatted(column, dimension.extraJoin, NOT_ABANDONED,
                                column, column, column, column),
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("abandoned", LibraryStatus.ABANDONED)
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
     * <p>{@code BOOKS} and {@code VOLUMES} both count the titles finished during the
     * window: a volume of a manga is a {@code work} of its own in this model, so the two
     * units differ in wording and not in what they measure. Counting only the titles
     * carrying a volume number would make a "50 volumes" goal quietly ignore every novel
     * read towards it. {@code PAGES} adds the page counts up, editions declaring none
     * contributing nothing.
     */
    public long progressTowards(String userId, LocalDate from, LocalDate to, GoalUnit unit) {
        // The unit picks the aggregate and nothing else; it is an enum, never caller input.
        String aggregate = unit == GoalUnit.PAGES ? "sum(li.edition.pageCount)" : "count(rp)";

        Object total = getEntityManager()
                .createQuery("""
                        select %s
                        from ReadingProgress rp
                          join rp.libraryItem li
                        where li.userId = :userId
                          and rp.finishedAt between :from and :to
                          %s
                        """.formatted(aggregate, NOT_ABANDONED), Object.class)
                .setParameter("userId", userId)
                .setParameter("abandoned", LibraryStatus.ABANDONED)
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
