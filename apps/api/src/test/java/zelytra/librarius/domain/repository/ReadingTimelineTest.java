package zelytra.librarius.domain.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.ReadingProgressRepository.Breakdown;
import zelytra.librarius.domain.repository.ReadingProgressRepository.LabelTotal;
import zelytra.librarius.domain.repository.ReadingProgressRepository.PeriodTotal;
import zelytra.librarius.domain.repository.ReadingProgressRepository.TimelineGranularity;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the reading timeline down on a dataset whose every figure is known in advance.
 *
 * <p>A chart that is subtly wrong looks exactly like a chart that is right, so the
 * expectations below are absolute rather than compared against a second implementation:
 * months holding nothing, editions carrying no page count, titles finished outside the
 * window, and a title still being read all have a decided answer.
 *
 * <p>The dataset sits in 2019 and 2020 on purpose — far from {@code now()}, so that the
 * assertions do not shift the day the suite is run.
 */
@QuarkusTest
class ReadingTimelineTest {

    /** Labels kept in a breakdown, mirroring {@code StatsService.TOP_LABELS}. */
    private static final int TOP_LABELS = 6;

    private static final LocalDate YEAR_FROM = LocalDate.of(2019, 1, 1);
    private static final LocalDate YEAR_TO = LocalDate.of(2019, 12, 31);

    @Inject
    ReadingProgressRepository progresses;

    @Inject
    EntityManager em;

    @Inject
    EntityManagerFactory emf;

    // ── Buckets ───────────────────────────────────────────────────────────────

    /**
     * Three months out of twelve hold something. The nine others are absent rather than
     * reported as zero: the answer follows the data, not the range asked for.
     */
    @Test
    void groupsFinishedTitlesByMonth() {
        String userId = seed();

        assertEquals(
                List.of(
                        new PeriodTotal(2019, 1, 3, 300),
                        new PeriodTotal(2019, 3, 1, 150),
                        new PeriodTotal(2019, 12, 4, 200)),
                progresses.timeline(userId, YEAR_FROM, YEAR_TO, TimelineGranularity.MONTH));
    }

    /** Same rows, one bucket per year: the two granularities must add up to the same. */
    @Test
    void groupsFinishedTitlesByYear() {
        String userId = seed();

        assertEquals(
                List.of(
                        new PeriodTotal(2019, null, 8, 650),
                        new PeriodTotal(2020, null, 1, 500)),
                progresses.timeline(userId, YEAR_FROM, LocalDate.of(2020, 12, 31),
                        TimelineGranularity.YEAR));
    }

    /** The window is inclusive on both ends — 1 January and 31 December are in. */
    @Test
    void countsBothEndsOfTheWindow() {
        String userId = seed();

        assertEquals(
                List.of(new PeriodTotal(2019, 1, 1, 100)),
                progresses.timeline(userId, LocalDate.of(2019, 1, 5), LocalDate.of(2019, 1, 5),
                        TimelineGranularity.MONTH));
        assertEquals(
                List.of(new PeriodTotal(2019, 12, 1, 50)),
                progresses.timeline(userId, LocalDate.of(2019, 12, 31), YEAR_TO,
                        TimelineGranularity.MONTH));
    }

    /** A window nobody read in is empty, not a row of zeros. */
    @Test
    void reportsNothingForAnEmptyWindow() {
        String userId = seed();

        assertEquals(List.of(), progresses.timeline(userId, LocalDate.of(2019, 6, 1),
                LocalDate.of(2019, 6, 30), TimelineGranularity.MONTH));
    }

    /** Another user's readings never reach the buckets. */
    @Test
    void bucketsAreScopedToTheUser() {
        String userId = seed();
        seed();

        assertEquals(
                List.of(new PeriodTotal(2019, null, 8, 650)),
                progresses.timeline(userId, YEAR_FROM, YEAR_TO, TimelineGranularity.YEAR));
    }

    // ── Derived figures ───────────────────────────────────────────────────────

    /**
     * Only the four titles carrying both dates count: 4, 10, 10 and 10 days, so 8.5. A
     * title marked read without ever having been marked as being read says nothing about
     * how long it took, and an average of zero would be a lie about it.
     */
    @Test
    void averagesTheDaysSpentOnTheTitlesThatCarryBothDates() {
        String userId = seed();

        OptionalDouble average = progresses.averageDaysToFinish(userId, YEAR_FROM, YEAR_TO);

        assertTrue(average.isPresent(), "four titles carry both dates");
        assertEquals(8.5, average.getAsDouble(), 0.0001);
    }

    @Test
    void reportsNoAverageWhenNoTitleCarriesBothDates() {
        String userId = seed();

        assertEquals(OptionalDouble.empty(),
                progresses.averageDaysToFinish(userId, LocalDate.of(2019, 12, 2), YEAR_TO));
    }

    // ── Breakdowns ────────────────────────────────────────────────────────────

    @Test
    void ranksTheAuthorsPublishersLanguagesAndRanksOfTheWindow() {
        String userId = seed();

        assertEquals(
                List.of(new LabelTotal("Makoto Yukimura", 4), new LabelTotal("Patrick Rothfuss", 3),
                        new LabelTotal("Frank Herbert", 1)),
                breakdown(userId, Breakdown.AUTHOR));
        assertEquals(
                List.of(new LabelTotal("Kana", 5), new LabelTotal("Bragelonne", 2),
                        new LabelTotal("Pocket", 1)),
                breakdown(userId, Breakdown.PUBLISHER));
        assertEquals(
                List.of(new LabelTotal("en", 4), new LabelTotal("fr", 3), new LabelTotal("ja", 1)),
                breakdown(userId, Breakdown.LANGUAGE));
        // Argent and Bronze both score 1: ties are broken alphabetically so the ranking does
        // not depend on the order the rows come back in.
        assertEquals(
                List.of(new LabelTotal("Or", 2), new LabelTotal("Argent", 1),
                        new LabelTotal("Bronze", 1)),
                breakdown(userId, Breakdown.RANK));
    }

    /** The 2020 reading is outside the window and must show up in none of the breakdowns. */
    @Test
    void breakdownsOnlyCoverTheWindow() {
        String userId = seed();

        assertEquals(List.of(new LabelTotal("Frank Herbert", 1)),
                progresses.breakdown(userId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31),
                        Breakdown.AUTHOR, TOP_LABELS));
    }

    private List<LabelTotal> breakdown(String userId, Breakdown dimension) {
        return progresses.breakdown(userId, YEAR_FROM, YEAR_TO, dimension, TOP_LABELS);
    }

    // ── Cost ──────────────────────────────────────────────────────────────────

    /**
     * The point of aggregating in SQL: a timeline over a bigger history costs the same six
     * queries — one per bucket set, one per breakdown, one for the average.
     */
    @Test
    void queryCountDoesNotGrowWithTheHistory() {
        long small = timelineQueryCount(seedRepeated(1));
        long large = timelineQueryCount(seedRepeated(10));

        assertEquals(small, large, "a ten times longer history must cost the same");
        assertTrue(large <= 6, "one query per bucket set, per breakdown and for the average, got "
                + large);
    }

    private long timelineQueryCount(String userId) {
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getQueryExecutionCount();

        progresses.timeline(userId, YEAR_FROM, YEAR_TO, TimelineGranularity.MONTH);
        progresses.averageDaysToFinish(userId, YEAR_FROM, YEAR_TO);
        for (Breakdown dimension : Breakdown.values()) {
            progresses.breakdown(userId, YEAR_FROM, YEAR_TO, dimension, TOP_LABELS);
        }

        return statistics.getQueryExecutionCount() - before;
    }

    // ── Dataset ───────────────────────────────────────────────────────────────

    /**
     * One title of the fixture.
     *
     * @param finishedAt   the day it was finished, {@code null} for a title still being read
     * @param startedAt    the day it was started, {@code null} when it was never recorded
     * @param volumeNumber set when the title is a volume of a run, which the VOLUMES goal
     *                     unit counts and the BOOKS one does not distinguish
     * @param rankCode     code of a built-in rank category, {@code null} when unranked
     */
    private record Read(LocalDate finishedAt, LocalDate startedAt, Integer pageCount,
            Integer volumeNumber, String authors, String publisher, String language,
            String rankCode) {
    }

    private static LocalDate day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }

    /**
     * Ten titles: three finished in January 2019 (one of them on an edition with no page
     * count), one in March, four in December, one in February 2020 — outside a 2019 window —
     * and one still being read, which carries no finishing date at all.
     */
    private static List<Read> dataset() {
        return List.of(
                new Read(day(2019, 1, 5), day(2019, 1, 1), 100, null,
                        "Patrick Rothfuss", "Bragelonne", "fr", "or"),
                new Read(day(2019, 1, 20), day(2019, 1, 10), 200, 1,
                        "Patrick Rothfuss", "Bragelonne", "fr", "or"),
                new Read(day(2019, 1, 31), null, null, 2,
                        "Patrick Rothfuss", "Kana", "en", null),
                new Read(day(2019, 3, 15), day(2019, 3, 5), 150, null,
                        "Frank Herbert", "Pocket", "fr", "argent"),
                new Read(day(2019, 12, 1), day(2019, 11, 21), 50, 3,
                        "Makoto Yukimura", "Kana", "en", null),
                new Read(day(2019, 12, 8), null, 50, 4,
                        "Makoto Yukimura", "Kana", "en", "bronze"),
                new Read(day(2019, 12, 15), null, 50, 5,
                        "Makoto Yukimura", "Kana", "en", null),
                new Read(day(2019, 12, 31), null, 50, 6,
                        "Makoto Yukimura", "Kana", "ja", null),
                new Read(day(2020, 2, 10), null, 500, null,
                        "Frank Herbert", "Pocket", "fr", null),
                new Read(null, day(2019, 5, 1), 999, null,
                        "Patrick Rothfuss", "Bragelonne", "fr", null));
    }

    private String seed() {
        return seedRepeated(1);
    }

    /**
     * Inserts the dataset {@code times} over for a brand new user and returns their
     * identifier. Repeating it multiplies every figure without changing their shape, which
     * is what the cost test needs.
     */
    private String seedRepeated(int times) {
        String userId = "timeline-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            // library_item.user_id is a foreign key onto app_user.
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Timeline fixture";
            em.persist(user);

            for (int round = 0; round < times; round++) {
                for (Read read : dataset()) {
                    persist(userId, read);
                }
            }
        });
        return userId;
    }

    private void persist(String userId, Read read) {
        Work work = new Work();
        work.kind = read.volumeNumber() == null ? Kind.BOOK : Kind.MANGA;
        work.title = "Timeline " + UUID.randomUUID();
        work.authorsText = read.authors();
        work.volumeNumber = read.volumeNumber();
        em.persist(work);

        Edition edition = new Edition();
        edition.work = work;
        edition.pageCount = read.pageCount();
        edition.publisher = read.publisher();
        edition.language = read.language();
        em.persist(edition);

        LibraryItem item = new LibraryItem();
        item.userId = userId;
        item.edition = edition;
        item.status = read.finishedAt() == null ? LibraryStatus.READING : LibraryStatus.READ;
        if (read.rankCode() != null) {
            item.rankCategory = em.createQuery(
                            "select rc from RankCategory rc where rc.code = :code and rc.userId is null",
                            RankCategory.class)
                    .setParameter("code", read.rankCode())
                    .getSingleResult();
        }
        em.persist(item);

        ReadingProgress progress = new ReadingProgress();
        progress.libraryItem = item;
        progress.startedAt = read.startedAt();
        progress.finishedAt = read.finishedAt();
        em.persist(progress);
    }
}
