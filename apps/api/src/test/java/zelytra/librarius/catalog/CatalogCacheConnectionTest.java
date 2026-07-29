package zelytra.librarius.catalog;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the connection pool against a burst of cold catalog searches.
 *
 * <p>A cold miss takes an advisory lock and only then calls Open Library or AniList, so that
 * pods missing the same key produce a single outbound call. The lock cannot outlive its
 * transaction, nor the transaction its connection: the fetch therefore holds a pooled
 * connection while a third party is on the line, for as long as the third party takes. With
 * nothing rationing them, as many cold searches as the pool has connections — twenty by
 * default — empty it, and every other request in the pod then fails to get one.
 *
 * <p>The profile below shrinks the pool to four so the same arithmetic runs in seconds
 * instead of needing twenty threads, and gives cold fetches two permits out of it. The point
 * of the first test is that the two remaining connections stay reachable.
 */
@QuarkusTest
@TestProfile(CatalogCacheConnectionTest.SmallPoolProfile.class)
class CatalogCacheConnectionTest {

    private static final String PROVIDER = "test-provider";

    /** Four connections, two of which cold fetches may ever hold. */
    public static class SmallPoolProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.jdbc.max-size", "4",
                    // A borrow that finds nothing free gives up rather than hanging the test.
                    "quarkus.datasource.jdbc.acquisition-timeout", "1S",
                    "librarius.catalog.cache.fetch.concurrency", "2",
                    "librarius.catalog.cache.fetch.queue-timeout", "1S",
                    // Nothing here goes through an endpoint, so the Keycloak Dev Service would
                    // only add a container — and a minute of startup — to a test about the
                    // connection pool.
                    "quarkus.keycloak.devservices.enabled", "false",
                    "quarkus.oidc.tenant-enabled", "false");
        }
    }

    @Inject
    CatalogCacheStore store;

    @Inject
    AgroalDataSource dataSource;

    private static List<CatalogResult> answer(String title) {
        return List.of(new CatalogResult("BOOK", title, "Rebecca Yarros", 2023, "https://cover",
                "synopsis", "9781234567890", "Piatkus", "fr", LocalDate.of(2024, 5, 2),
                PROVIDER, "ref-1"));
    }

    /** A provider that announces it has been reached, then takes its time. */
    private record SlowProvider(AtomicInteger calls, CountDownLatch reached, Duration duration,
            String title) implements Supplier<List<CatalogResult>> {

        @Override
        public List<CatalogResult> get() {
            calls.incrementAndGet();
            reached.countDown();
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return answer(title);
        }
    }

    /** Borrows a connection and runs a trivial query on it, as any other endpoint would. */
    private void queryOnAFreshConnection() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT 1");
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
        }
    }

    @Test
    void aBurstOfColdSearchesLeavesConnectionsForTheRestOfTheApi() throws Exception {
        String run = UUID.randomUUID().toString();
        // As many cold searches as the pool has connections: the exact burst that empties it.
        int burst = 4;
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch reached = new CountDownLatch(burst);
        ExecutorService pool = Executors.newFixedThreadPool(burst);
        try {
            for (int i = 0; i < burst; i++) {
                String key = "search|BOOK|" + run + '-' + i + "|20";
                pool.submit(() -> store.loadOrFetch(PROVIDER, key, Duration.ofHours(6),
                        new SlowProvider(calls, reached, Duration.ofSeconds(3), "Fourth Wing")));
            }

            assertTrue(reached.await(15, TimeUnit.SECONDS),
                    "every cold search should have reached its provider");

            // All four are inside a provider call. Holding a connection each, they would have
            // taken the whole pool and this borrow would time out.
            queryOnAFreshConnection();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentColdMissesOnTheSameKeyStillProduceASingleOutboundCall() throws Exception {
        // Two callers going through the table directly, which is what two pods missing the
        // same cold key look like: the in-memory level cannot collapse them, only the
        // advisory lock can.
        String key = "search|BOOK|" + UUID.randomUUID() + "|20";
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch reached = new CountDownLatch(1);
        Supplier<List<CatalogResult>> provider =
                new SlowProvider(calls, reached, Duration.ofMillis(800), "Babel");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<CatalogCacheStore.Lookup>> lookups = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                lookups.add(pool.submit(
                        () -> store.loadOrFetch(PROVIDER, key, Duration.ofHours(6), provider)));
            }

            List<CatalogCacheStore.Lookup> answers = new ArrayList<>();
            for (Future<CatalogCacheStore.Lookup> lookup : lookups) {
                answers.add(lookup.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1, calls.get(), "the second caller must wait for the first, not refetch");
            assertEquals(answer("Babel"), answers.get(0).value());
            assertEquals(answer("Babel"), answers.get(1).value());
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

}
