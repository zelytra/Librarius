package zelytra.librarius.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Second level of the catalog cache: the {@code catalog_cache} table.
 *
 * <p>Plain JDBC rather than a Panache entity. The two statements that matter here — an
 * upsert and an advisory lock — have no JPA equivalent, and mapping the row would drag a
 * {@code JSONB} column through Hibernate for a table no other code ever joins against.
 *
 * <p>The payload is the serialised {@link CatalogResult} list, exactly what the provider
 * returned before aggregation.
 *
 * <h2>Why a cold fetch is rationed</h2>
 *
 * <p>The advisory lock has to be held for the whole outbound call, otherwise it guarantees
 * nothing — and a PostgreSQL lock cannot outlive the transaction that took it, nor a
 * transaction the connection that carries it. So a cold fetch <em>necessarily</em> holds one
 * pooled connection while a third party is on the line. What can be bounded is how many of
 * them do it at once, and that is what {@code librarius.catalog.cache.fetch.concurrency}
 * does: cold fetches can never claim more than that many connections, whatever the request
 * rate, so the rest of the API always finds the pool it needs. Beyond that ceiling a fetch
 * gives up on the lock rather than queue for a connection — see
 * {@link #fetch(String, String, String, Duration, Supplier)}.
 */
@ApplicationScoped
public class CatalogCacheStore {

    private static final String SELECT_FRESH = """
            SELECT payload FROM catalog_cache
            WHERE provider = ? AND query_hash = ? AND expires_at > now()
            """;

    private static final String UPSERT = """
            INSERT INTO catalog_cache (provider, query_hash, payload, fetched_at, expires_at)
            VALUES (?, ?, ?::jsonb, now(), now() + make_interval(secs => ?))
            ON CONFLICT (provider, query_hash) DO UPDATE
            SET payload = excluded.payload,
                fetched_at = excluded.fetched_at,
                expires_at = excluded.expires_at
            """;

    private static final String PURGE = "DELETE FROM catalog_cache WHERE expires_at <= now()";

    private static final String LOCK = "SELECT pg_advisory_xact_lock(?, ?)";

    /**
     * Namespace of the advisory locks taken here. Advisory locks share one space for the
     * whole database, so an arbitrary but distinctive first key keeps them from colliding
     * with any other use.
     */
    private static final int LOCK_NAMESPACE = 0x4C49_4243;

    private static final TypeReference<List<CatalogResult>> PAYLOAD_TYPE =
            new TypeReference<>() {
            };

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    /**
     * How many cold fetches may hold a connection at the same time. The ceiling the pool
     * sees: against the fifty connections {@code quarkus.datasource.jdbc.max-size} declares,
     * four leaves forty-six for everything else the API does.
     */
    @ConfigProperty(name = "librarius.catalog.cache.fetch.concurrency", defaultValue = "4")
    int fetchConcurrency;

    /**
     * How long a cold fetch waits for one of those permits before going out to the provider
     * on its own.
     */
    @ConfigProperty(name = "librarius.catalog.cache.fetch.queue-timeout", defaultValue = "2S")
    Duration fetchQueueTimeout;

    private Semaphore permits;

    @PostConstruct
    void sizeThePermits() {
        permits = new Semaphore(Math.max(1, fetchConcurrency));
    }

    /**
     * Returns the stored payload for {@code key}, falling back to {@code loader} and storing
     * what it produces.
     *
     * <p>The read is a single indexed lookup on its own connection, borrowed and given back
     * at once: a hit — the common case — touches no transaction at all.
     *
     * <p>An empty result is never stored: the providers turn a failure into an empty list,
     * and persisting that would turn a provider blip into hours of empty searches.
     */
    public Lookup loadOrFetch(String provider, String key, Duration ttl,
            Supplier<List<CatalogResult>> loader) {
        String hash = hash(key);
        try (Connection connection = dataSource.getConnection()) {
            List<CatalogResult> stored = select(connection, provider, hash);
            if (stored != null) {
                return new Lookup(stored, true);
            }
        } catch (SQLException e) {
            // The persistent level is an optimisation: a database hiccup must degrade the
            // response time, not the response.
            Log.warnf("Catalog cache unavailable for %s/%s: %s", provider, key, e.getMessage());
            return new Lookup(loader.get(), false);
        }
        return fetch(provider, key, hash, ttl, loader);
    }

    /**
     * Cold path: nothing stored, so somebody has to call the provider.
     *
     * <p>The permit is taken <em>before</em> any connection is, which is the whole point —
     * a fetch that has to wait waits on a worker thread, not on a connection the rest of the
     * API needs. Once a permit is held the fetch runs under the advisory lock, so concurrent
     * pods missing the same cold key still produce one outbound call and not one each.
     *
     * <p>When no permit comes free in time the fetch goes out unlocked. That trades the
     * cross-pod deduplication of one key for pool safety, and only under a burst wide enough
     * to fill the permits: a duplicate outbound call costs one provider request, whereas a
     * connection held while queueing costs every other request in the pod. The
     * {@code librarius_catalog_cache_unlocked_fetches_total} counter says how often that
     * happens, and is the signal to raise the permit count.
     */
    private Lookup fetch(String provider, String key, String hash, Duration ttl,
            Supplier<List<CatalogResult>> loader) {
        if (!acquire()) {
            meters.counter("librarius.catalog.cache.unlocked.fetches").increment();
            List<CatalogResult> fetched = loader.get();
            store(provider, hash, fetched, ttl);
            return new Lookup(fetched, false);
        }
        try {
            // A transaction only because pg_advisory_xact_lock needs one; requiringNew()
            // rather than @Transactional because the caller is inside this same bean, where
            // the interceptor would never run.
            return QuarkusTransaction.requiringNew()
                    .call(() -> lockedFetch(provider, key, hash, ttl, loader));
        } finally {
            permits.release();
        }
    }

    /**
     * Takes the advisory lock on the key, re-reads, and calls the provider only if the row is
     * still missing — whoever held the lock may have filled it while we waited.
     */
    private Lookup lockedFetch(String provider, String key, String hash, Duration ttl,
            Supplier<List<CatalogResult>> loader) {
        try (Connection connection = dataSource.getConnection()) {
            lock(connection, provider, hash);

            List<CatalogResult> stored = select(connection, provider, hash);
            if (stored != null) {
                return new Lookup(stored, true);
            }

            List<CatalogResult> fetched = loader.get();
            if (!fetched.isEmpty()) {
                // A write that fails must not send us back to the provider: the answer is
                // already in hand, only its persistence is lost.
                try {
                    upsert(connection, provider, hash, fetched, ttl);
                } catch (SQLException e) {
                    Log.warnf("Catalog cache write failed for %s/%s: %s", provider, key,
                            e.getMessage());
                }
            }
            return new Lookup(fetched, false);
        } catch (SQLException e) {
            Log.warnf("Catalog cache unavailable for %s/%s: %s", provider, key, e.getMessage());
            return new Lookup(loader.get(), false);
        }
    }

    /** Waits for a fetch permit, reporting whether one was granted. */
    private boolean acquire() {
        try {
            return permits.tryAcquire(fetchQueueTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Best-effort write for a fetch that ran without the lock. */
    private void store(String provider, String hash, List<CatalogResult> value, Duration ttl) {
        if (value.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            upsert(connection, provider, hash, value, ttl);
        } catch (SQLException e) {
            Log.warnf("Catalog cache write failed for %s: %s", provider, e.getMessage());
        }
    }

    /** Deletes the entries whose time-to-live has run out. Returns how many were removed. */
    @Transactional
    public int purgeExpired() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(PURGE)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            Log.warnf("Catalog cache purge failed: %s", e.getMessage());
            return 0;
        }
    }

    private List<CatalogResult> select(Connection connection, String provider, String hash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FRESH)) {
            statement.setString(1, provider);
            statement.setString(2, hash);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return deserialize(rows.getString(1));
            }
        }
    }

    private void upsert(Connection connection, String provider, String hash,
            List<CatalogResult> value, Duration ttl) throws SQLException {
        String payload;
        try {
            payload = json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Log.warnf("Catalog cache payload not serialisable for %s: %s", provider,
                    e.getMessage());
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, provider);
            statement.setString(2, hash);
            statement.setString(3, payload);
            statement.setDouble(4, ttl.toMillis() / 1000d);
            statement.executeUpdate();
        }
    }

    private void lock(Connection connection, String provider, String hash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK)) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, (provider + hash).hashCode());
            statement.execute();
        }
    }

    private List<CatalogResult> deserialize(String payload) {
        try {
            return json.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            // A stored payload written by an older shape of CatalogResult: treat it as a
            // miss so the next fetch overwrites it.
            Log.warnf("Discarding unreadable catalog cache payload: %s", e.getMessage());
            return null;
        }
    }

    /**
     * SHA-256 of the canonical request, hex encoded. Only used to key a cache row, so the
     * choice of digest is about collision resistance and a fixed width, not about secrecy.
     */
    static String hash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /** Outcome of a lookup: the value, and whether the table already held it. */
    public record Lookup(List<CatalogResult> value, boolean hit) {
    }
}
