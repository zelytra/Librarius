package zelytra.librarius.catalog;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two-level catalog cache against a real PostgreSQL (Dev Services).
 *
 * <p>The case that matters is the one the in-memory cache never handled: a pod restart. It
 * is reproduced by emptying the Caffeine cache, which is exactly what losing the process
 * does, and then checking that the provider is not called a second time.
 */
@QuarkusTest
class CatalogCacheTest {

    private static final String PROVIDER = "test-provider";

    @Inject
    CatalogCache cache;

    @Inject
    CatalogCacheStore store;

    @Inject
    CacheManager cacheManager;

    @Inject
    MeterRegistry meters;

    @Inject
    AgroalDataSource dataSource;

    private static CatalogResult result(String title) {
        return new CatalogResult("BOOK", title, "Rebecca Yarros", 2023, "https://cover",
                "synopsis", "9781234567890", "Piatkus", "fr", LocalDate.of(2024, 5, 2),
                PROVIDER, "ref-1");
    }

    /** A provider that counts its calls, so a cache hit is provable and not merely likely. */
    private record CountingProvider(AtomicInteger calls, List<CatalogResult> answer)
            implements Supplier<List<CatalogResult>> {

        static CountingProvider returning(String title) {
            return new CountingProvider(new AtomicInteger(), List.of(result(title)));
        }

        @Override
        public List<CatalogResult> get() {
            calls.incrementAndGet();
            return answer;
        }
    }

    /** Everything a pod restart takes away: the in-memory level, and only that. */
    private void restart() {
        cacheManager.getCache("catalog-search").orElseThrow().invalidateAll()
                .await().indefinitely();
        cacheManager.getCache("catalog-upcoming").orElseThrow().invalidateAll()
                .await().indefinitely();
    }

    private double lookups(String level, String outcome) {
        Counter counter = meters.find("librarius.catalog.cache.lookups")
                .tags("level", level, "result", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private int storedRows(String key) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM catalog_cache WHERE provider = ? AND query_hash = ?")) {
            statement.setString(1, PROVIDER);
            statement.setString(2, CatalogCacheStore.hash(key));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    @Test
    void survivesARestartAndDoesNotCallTheProviderAgain() {
        CountingProvider provider = CountingProvider.returning("Fourth Wing");
        String key = "search|BOOK|fourth wing|20";

        List<CatalogResult> beforeRestart =
                cache.get(CatalogCache.Scope.SEARCH, PROVIDER, key, provider);
        assertEquals(1, provider.calls().get(), "the first call must reach the provider");

        restart();

        List<CatalogResult> afterRestart =
                cache.get(CatalogCache.Scope.SEARCH, PROVIDER, key, provider);
        assertEquals(1, provider.calls().get(),
                "after a restart the answer must come from PostgreSQL, not the provider");
        // Round-trips through JSONB unchanged, release date included.
        assertEquals(beforeRestart, afterRestart);
    }

    @Test
    void countsHitsAndMissesForEachLevel() {
        CountingProvider provider = CountingProvider.returning("Babel");
        String key = "search|BOOK|babel|20";

        double memoryMiss = lookups("memory", "miss");
        double memoryHit = lookups("memory", "hit");
        double databaseMiss = lookups("database", "miss");
        double databaseHit = lookups("database", "hit");

        // Cold: both levels miss.
        cache.get(CatalogCache.Scope.SEARCH, PROVIDER, key, provider);
        assertEquals(memoryMiss + 1, lookups("memory", "miss"));
        assertEquals(databaseMiss + 1, lookups("database", "miss"));

        // Warm: the in-memory level answers and the table is not even queried.
        cache.get(CatalogCache.Scope.SEARCH, PROVIDER, key, provider);
        assertEquals(memoryHit + 1, lookups("memory", "hit"));
        assertEquals(databaseMiss + 1, lookups("database", "miss"));
        assertEquals(databaseHit, lookups("database", "hit"));

        // After a restart the second level is the one earning its keep.
        restart();
        cache.get(CatalogCache.Scope.SEARCH, PROVIDER, key, provider);
        assertEquals(databaseHit + 1, lookups("database", "hit"));
        assertEquals(1, provider.calls().get());
    }

    @Test
    void exposesTheHitRateOnThePrometheusEndpoint() {
        cache.get(CatalogCache.Scope.SEARCH, PROVIDER, "search|BOOK|prometheus|20",
                CountingProvider.returning("Piranesi"));

        given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("librarius_catalog_cache_lookups_total"));
    }

    @Test
    void doesNotServeAnEntryPastItsTimeToLive() {
        CountingProvider provider = CountingProvider.returning("Dune");
        String key = "search|BOOK|dune|20";

        // A negative time-to-live writes a row that is already expired — the state an entry
        // reaches on its own after six hours, without making the test wait for them.
        store.loadOrFetch(PROVIDER, key, Duration.ofSeconds(-1), provider);
        assertEquals(1, provider.calls().get());

        CatalogCacheStore.Lookup second =
                store.loadOrFetch(PROVIDER, key, Duration.ofSeconds(-1), provider);
        assertFalse(second.hit(), "an expired row must not be served");
        assertEquals(2, provider.calls().get());
    }

    @Test
    void purgeDropsExpiredEntriesAndKeepsLiveOnes() throws SQLException {
        String expired = "search|BOOK|purge-expired|20";
        String live = "search|BOOK|purge-live|20";
        store.loadOrFetch(PROVIDER, expired, Duration.ofSeconds(-1),
                CountingProvider.returning("Stale"));
        store.loadOrFetch(PROVIDER, live, Duration.ofHours(6),
                CountingProvider.returning("Fresh"));
        assertEquals(1, storedRows(expired));

        assertTrue(store.purgeExpired() >= 1);

        assertEquals(0, storedRows(expired));
        assertEquals(1, storedRows(live));
    }

    @Test
    void doesNotPersistAnEmptyResult() throws SQLException {
        // The providers turn a failure into an empty list. Storing it would keep the search
        // empty for the whole time-to-live, long after the provider recovered.
        String key = "search|BOOK|provider-down|20";

        CatalogCacheStore.Lookup lookup =
                store.loadOrFetch(PROVIDER, key, Duration.ofHours(6), List::of);

        assertTrue(lookup.value().isEmpty());
        assertEquals(0, storedRows(key));
    }

    @Test
    void rewritesAnEntryThatIsFetchedAgain() {
        // Two writes on the same key must upsert rather than collide on the primary key,
        // which is what happens when two pods miss at the same moment.
        String key = "search|BOOK|upsert|20";
        store.loadOrFetch(PROVIDER, key, Duration.ofSeconds(-1),
                CountingProvider.returning("First"));

        CatalogCacheStore.Lookup rewritten = store.loadOrFetch(PROVIDER, key,
                Duration.ofHours(6), CountingProvider.returning("Second"));

        assertEquals("Second", rewritten.value().get(0).title());
        assertTrue(store.loadOrFetch(PROVIDER, key, Duration.ofHours(6), List::of).hit());
    }
}
