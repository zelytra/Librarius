package zelytra.librarius.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.web.ApiDtos.DashboardLayoutDto;
import zelytra.librarius.web.ApiDtos.DashboardSectionDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Home screen's per-user layout: which sections show, and in which order (#54).
 *
 * <p>Plain JDBC rather than a Panache entity, the same choice {@code CatalogCacheStore}
 * made for {@code catalog_cache}: mapping a {@code JSONB} column through Hibernate buys
 * nothing for a table nothing else joins against.
 *
 * <p>{@link #DEFAULT_ORDER} is what an account that has never touched the feature sees —
 * the same order the sections were hard-coded in on {@code HomePage.tsx} before this table
 * existed. Nothing is written until the first {@code save}: {@link #get} computes the
 * default in memory rather than persisting it, so the feature costs an account that never
 * uses it exactly one indexed lookup that finds nothing.
 */
@ApplicationScoped
public class DashboardLayoutService {

    /**
     * The Home sections, in the order a fresh account sees them. Where a new section goes
     * in this list is a product decision — {@code toRead} sits right after
     * {@code resumeReading} because the pile is what comes next after what is already open,
     * and {@code bookStack} right after {@code counters} because it is the "read" counter
     * drawn out rather than a subject of its own.
     * That placement only ever decides what a <em>fresh</em> account sees: an account that
     * already saved a layout gets the new section appended at the end, visible, whatever
     * its rank here; see {@link #normalize}.
     */
    static final List<String> DEFAULT_ORDER = List.of(
            "resumeReading", "toRead", "counters", "bookStack", "goal", "upcoming", "recentlyRead");

    private static final String SELECT = "SELECT sections FROM dashboard_layout WHERE user_id = ?";

    private static final String UPSERT = """
            INSERT INTO dashboard_layout (user_id, sections)
            VALUES (?, ?::jsonb)
            ON CONFLICT (user_id) DO UPDATE
            SET sections = excluded.sections
            """;

    private static final TypeReference<List<StoredSection>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper json;

    /** Wire shape of one stored section — a plain string code, see the class javadoc. */
    private record StoredSection(String code, boolean hidden) {
    }

    /** The caller's layout, complete: every known section, in the order they chose. */
    public DashboardLayoutDto get(String userId) {
        return new DashboardLayoutDto(normalize(readStored(userId)));
    }

    /** Replaces the caller's layout. The stored copy, like the returned one, is complete. */
    public DashboardLayoutDto save(String userId, DashboardLayoutDto input) {
        List<DashboardSectionDto> normalized = normalize(input.sections());
        write(userId, normalized);
        return new DashboardLayoutDto(normalized);
    }

    /**
     * Fills in whatever a stored layout is missing — every section, on a fresh account, or
     * just the ones added since it was last saved — and drops what it no longer recognises.
     *
     * <p>An unrecognised code is dropped rather than rejected: the alternative is a 400 the
     * day a section is renamed, for a value the caller never chose. A duplicate keeps its
     * first occurrence only, which cannot happen through this API's own {@code PUT} but
     * costs nothing to guard against in a JSONB column nothing stops from being edited by
     * hand.
     *
     * <p>Package-private, not private: {@code DashboardLayoutServiceTest} exercises it
     * directly, with no database involved.
     */
    static List<DashboardSectionDto> normalize(List<DashboardSectionDto> stored) {
        Set<String> seen = new LinkedHashSet<>();
        List<DashboardSectionDto> result = new ArrayList<>();
        if (stored != null) {
            for (DashboardSectionDto entry : stored) {
                if (entry != null && entry.code() != null && DEFAULT_ORDER.contains(entry.code())
                        && seen.add(entry.code())) {
                    result.add(entry);
                }
            }
        }
        for (String code : DEFAULT_ORDER) {
            if (!seen.contains(code)) {
                result.add(new DashboardSectionDto(code, false));
            }
        }
        return result;
    }

    private List<DashboardSectionDto> readStored(String userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? deserialize(rows.getString(1)) : null;
            }
        } catch (SQLException e) {
            Log.warnf("Dashboard layout unavailable for %s: %s", userId, e.getMessage());
            return null;
        }
    }

    private void write(String userId, List<DashboardSectionDto> sections) {
        String payload;
        try {
            payload = json.writeValueAsString(sections);
        } catch (JsonProcessingException e) {
            // The known sections are plain strings and booleans: this can only mean a
            // future section carries something that does not serialise.
            throw new IllegalStateException("Dashboard layout not serialisable", e);
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, userId);
            statement.setString(2, payload);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not save the dashboard layout for " + userId, e);
        }
    }

    private List<DashboardSectionDto> deserialize(String payload) {
        try {
            List<StoredSection> stored = json.readValue(payload, PAYLOAD_TYPE);
            return stored.stream().map(s -> new DashboardSectionDto(s.code(), s.hidden())).toList();
        } catch (JsonProcessingException e) {
            // A payload written by an older shape of the section list: treat it as
            // nothing stored so normalize() rebuilds the default rather than failing GET.
            Log.warnf("Discarding unreadable dashboard layout payload: %s", e.getMessage());
            return null;
        }
    }
}
