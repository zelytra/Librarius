package zelytra.librarius.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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

    /**
     * Returns the stored payload for {@code key}, falling back to {@code loader} and storing
     * what it produces.
     *
     * <p>Runs in a single transaction, {@code loader} included. That is deliberate: on a
     * miss the transaction takes a PostgreSQL advisory lock on the key before calling the
     * provider, so when several pods miss the same cold key at the same moment only one of
     * them goes out to the provider — the others block on the lock and find the row already
     * written when they wake up. The first level collapses concurrency inside a pod, so the
     * number of waiters is bounded by the replica count rather than by the request rate, and
     * the outbound call is bounded by the REST client timeouts.
     *
     * <p>An empty result is never stored: the providers turn a failure into an empty list,
     * and persisting that would turn a provider blip into hours of empty searches.
     */
    @Transactional
    public Lookup loadOrFetch(String provider, String key, Duration ttl,
            Supplier<List<CatalogResult>> loader) {
        String hash = hash(key);
        try (Connection connection = dataSource.getConnection()) {
            List<CatalogResult> stored = select(connection, provider, hash);
            if (stored != null) {
                return new Lookup(stored, true);
            }

            lock(connection, provider, hash);

            // Re-read: whoever held the lock may have filled the row while we waited.
            stored = select(connection, provider, hash);
            if (stored != null) {
                return new Lookup(stored, true);
            }

            List<CatalogResult> fetched = loader.get();
            if (!fetched.isEmpty()) {
                upsert(connection, provider, hash, fetched, ttl);
            }
            return new Lookup(fetched, false);
        } catch (SQLException e) {
            // The persistent level is an optimisation: a database hiccup must degrade the
            // response time, not the response.
            Log.warnf("Catalog cache unavailable for %s/%s: %s", provider, key, e.getMessage());
            return new Lookup(loader.get(), false);
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
