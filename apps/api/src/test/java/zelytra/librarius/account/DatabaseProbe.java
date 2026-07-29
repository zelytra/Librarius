package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the database directly, so that the deletion test can assert on rows rather than on
 * what the API is willing to show.
 *
 * <p>An API-level check would only prove that the endpoints stopped returning things, which
 * is also what a forgotten {@code where user_id = …} looks like. The point of erasure is that
 * the rows are gone.
 */
@ApplicationScoped
public class DatabaseProbe {

    @Inject
    EntityManager em;

    /**
     * Rows still attached to a user, per table.
     *
     * <p>The tables are discovered from {@code information_schema} rather than listed here,
     * so that a user-scoped table added later without an {@code ON DELETE CASCADE} fails this
     * test the day it appears instead of quietly surviving every account deletion.
     *
     * @return one entry per table holding a {@code user_id}, plus {@code app_user} itself and
     *         {@code reading_progress}, which hangs off {@code library_item} and carries no
     *         {@code user_id} of its own
     */
    @Transactional
    public Map<String, Long> personalRows(String userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("app_user", count("select count(*) from app_user where id = ?1", userId));

        @SuppressWarnings("unchecked")
        List<String> tables = em.createNativeQuery("""
                select table_name from information_schema.columns
                 where table_schema = 'public' and column_name = 'user_id'
                 order by table_name
                """).getResultList();
        for (String table : tables) {
            counts.put(table, count("select count(*) from " + table + " where user_id = ?1",
                    userId));
        }

        counts.put("reading_progress", count("""
                select count(*) from reading_progress p
                  join library_item li on li.id = p.library_item_id
                 where li.user_id = ?1
                """, userId));
        return counts;
    }

    /** Whether a shared catalog row is still there — it must be, an account is not a book. */
    @Transactional
    public boolean catalogRowExists(String table, UUID id) {
        return count("select count(*) from " + table + " where id = ?1", id) > 0;
    }

    private long count(String sql, Object parameter) {
        Object result = em.createNativeQuery(sql).setParameter(1, parameter).getSingleResult();
        return ((Number) result).longValue();
    }
}
