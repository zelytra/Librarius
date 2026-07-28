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
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository.GenreTotal;
import zelytra.librarius.domain.repository.LibraryItemRepository.StatusTotals;
import zelytra.librarius.genre.GenreService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the move of the statistics from Java to SQL.
 *
 * <p>{@code StatsResource} used to load the whole collection and fold it in memory. The
 * aggregations now live in {@link LibraryItemRepository}, so every test below compares the
 * SQL result against {@link #inMemoryStats} — a faithful copy of the replaced loop — over a
 * dataset that exercises the awkward cases: editions with no page count, series titles
 * differing only by case, blank series and blank genres, and more genres than the
 * breakdown shows.
 *
 * <p>The genre breakdown is the one aggregation that no longer matches that loop: it groups
 * on the normalised genres rather than on the free-text value, so it is asserted on absolute
 * values here and covered in full by {@link GenreBackfillTest} and
 * {@code zelytra.librarius.web.GenreApiTest}.
 */
@QuarkusTest
class StatsAggregationTest {

    /** Genres shown in the breakdown, mirroring {@code StatsResource.TOP_GENRES}. */
    private static final int TOP_GENRES = 6;

    @Inject
    LibraryItemRepository library;

    @Inject
    GenreService genres;

    @Inject
    EntityManager em;

    @Inject
    EntityManagerFactory emf;

    // ── Non-regression ────────────────────────────────────────────────────────

    @Test
    void sqlAggregationMatchesTheReplacedInMemoryFold() {
        String userId = seed(representativeDataset());

        InMemoryStats expected = inMemoryStats(userId);
        StatusTotals totals = library.statusTotals(userId);

        assertEquals(expected.read(), totals.read(), "read count");
        assertEquals(expected.reading(), totals.reading(), "reading count");
        assertEquals(expected.toRead(), totals.toRead(), "to-read count");
        assertEquals(expected.pagesRead(), totals.pagesRead(), "pages read");
        assertEquals(expected.seriesCount(), library.countDistinctSeries(userId), "series count");
    }

    /**
     * The same comparison on an empty collection: the grouped query returns no row at all,
     * which must read as zeros rather than blow up.
     */
    @Test
    void emptyLibraryAggregatesToZero() {
        String userId = seed(List.of());

        StatusTotals totals = library.statusTotals(userId);

        assertEquals(new StatusTotals(0, 0, 0, 0), totals);
        assertEquals(0, library.countDistinctSeries(userId));
        assertEquals(List.of(), library.topGenres(userId, TOP_GENRES));
    }

    /**
     * Absolute values, so the test still fails if the reference fold were itself broken.
     *
     * <p>"Polar" comes back as {@code policier}: the dataset spells the genres the way a
     * provider would, and the breakdown answers with the canonical code and label.
     */
    @Test
    void breakdownKeepsTheSixMostFrequentGenresMostFrequentFirst() {
        String userId = seed(representativeDataset());

        assertEquals(
                List.of(
                        new GenreTotal("fantasy", "Fantasy", 7),
                        new GenreTotal("science-fiction", "Science-fiction", 6),
                        new GenreTotal("policier", "Policier", 5),
                        new GenreTotal("romance", "Romance", 4),
                        new GenreTotal("historique", "Historique", 3),
                        new GenreTotal("horreur", "Horreur", 2)),
                library.topGenres(userId, TOP_GENRES));
    }

    /** Series are matched case-insensitively; blank and missing titles count for nothing. */
    @Test
    void seriesAreCountedCaseInsensitively() {
        String userId = seed(representativeDataset());

        assertEquals(3, library.countDistinctSeries(userId));
    }

    /** Another user's titles never leak into the counters. */
    @Test
    void aggregationsAreScopedToTheUser() {
        String userId = seed(representativeDataset());
        seed(representativeDataset());

        assertEquals(inMemoryStats(userId).read(), library.statusTotals(userId).read());
        assertEquals(3, library.countDistinctSeries(userId));
    }

    // ── Cost ──────────────────────────────────────────────────────────────────

    /**
     * The point of the change: the number of queries no longer follows the number of
     * titles. A ten-times bigger collection must cost exactly the same three queries.
     */
    @Test
    void queryCountDoesNotGrowWithTheCollection() {
        String small = seed(representativeDataset().subList(0, 3));
        String large = seed(representativeDataset());

        assertEquals(3, aggregationQueryCount(small), "queries on a 3-title collection");
        assertEquals(3, aggregationQueryCount(large), "queries on a 32-title collection");
    }

    private long aggregationQueryCount(String userId) {
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getQueryExecutionCount();

        library.statusTotals(userId);
        library.countDistinctSeries(userId);
        library.topGenres(userId, TOP_GENRES);

        return statistics.getQueryExecutionCount() - before;
    }

    // ── Reference implementation ──────────────────────────────────────────────

    private record InMemoryStats(long read, long reading, long toRead, long pagesRead,
            long seriesCount) {
    }

    /**
     * The fold {@code StatsResource} used to run, kept here as the reference the SQL
     * aggregations are compared against. The genre breakdown is left out: it deliberately no
     * longer agrees with the old fold, which counted "Fantasy, Aventure" as a genre of its
     * own.
     */
    private InMemoryStats inMemoryStats(String userId) {
        List<LibraryItem> items = library.listByUser(userId);

        long read = 0;
        long reading = 0;
        long toRead = 0;
        long pagesRead = 0;
        Set<String> series = new HashSet<>();

        for (LibraryItem item : items) {
            switch (item.status) {
                case READ -> {
                    read++;
                    Integer pages = item.edition.pageCount;
                    if (pages != null) {
                        pagesRead += pages;
                    }
                }
                case READING -> reading++;
                case OWNED -> toRead++;
            }
            String seriesTitle = item.edition.work.seriesTitle;
            if (seriesTitle != null && !seriesTitle.isBlank()) {
                series.add(seriesTitle.toLowerCase());
            }
        }

        return new InMemoryStats(read, reading, toRead, pagesRead, series.size());
    }

    // ── Dataset ───────────────────────────────────────────────────────────────

    /** One title to seed: status, edition page count, series title and genres of the work. */
    private record Title(LibraryStatus status, Integer pageCount, String seriesTitle, String genres) {
    }

    /**
     * Thirty-two titles covering the cases the aggregation has to survive.
     *
     * <p>The seven genres carry deliberately distinct frequencies (7, 6, 5, 4, 3, 2, 1): the
     * ranking is then fully determined and the comparison does not hinge on how ties are
     * broken, which the replaced fold settled by insertion order.
     */
    private static List<Title> representativeDataset() {
        Map<String, Integer> spread = new LinkedHashMap<>();
        spread.put("Fantasy", 7);
        spread.put("Science-fiction", 6);
        spread.put("Polar", 5);
        spread.put("Romance", 4);
        spread.put("Historique", 3);
        spread.put("Horreur", 2);
        spread.put("Poésie", 1);

        // Case variants of the same two series, plus a missing and a blank title: three
        // distinct series once lower-cased.
        String[] seriesCycle = {
                "Vinland Saga", "vinland saga", "VINLAND SAGA",
                "Berserk", "berserk",
                null, "   ", "L'Ancien" };
        LibraryStatus[] statusCycle = {
                LibraryStatus.READ, LibraryStatus.READING, LibraryStatus.OWNED, LibraryStatus.READ };

        List<Title> titles = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Integer> genre : spread.entrySet()) {
            for (int i = 0; i < genre.getValue(); i++, index++) {
                // Every third edition declares no page count: SUM must skip it the way the
                // null check used to.
                Integer pageCount = index % 3 == 0 ? null : 100 + index;
                titles.add(new Title(statusCycle[index % statusCycle.length], pageCount,
                        seriesCycle[index % seriesCycle.length], genre.getKey()));
            }
        }

        // Titles carrying no usable genre: absent from the breakdown, present in the counters.
        titles.add(new Title(LibraryStatus.READ, 320, "Berserk", null));
        titles.add(new Title(LibraryStatus.OWNED, null, null, "   "));
        titles.add(new Title(LibraryStatus.READING, 210, null, ""));
        titles.add(new Title(LibraryStatus.READ, 0, "vinland saga", null));

        return List.copyOf(titles);
    }

    /** Inserts the titles for a brand new user and returns their identifier. */
    private String seed(List<Title> titles) {
        String userId = "stats-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            // library_item.user_id is a foreign key onto app_user.
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Stats fixture";
            em.persist(user);

            for (Title title : titles) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Stats " + UUID.randomUUID();
                work.seriesTitle = title.seriesTitle();
                // Same path as a real entry: the raw wording is kept, and the normalised
                // genres are what the breakdown groups on.
                work.genresText = title.genres();
                work.genres = genres.resolve(title.genres());
                em.persist(work);

                Edition edition = new Edition();
                edition.work = work;
                edition.pageCount = title.pageCount();
                em.persist(edition);

                LibraryItem item = new LibraryItem();
                item.userId = userId;
                item.edition = edition;
                item.status = title.status();
                em.persist(item);
            }
        });

        assertTrue(library.listByUser(userId).size() == titles.size(), "dataset seeded");
        return userId;
    }
}
