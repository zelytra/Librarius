package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Empties one reader's library and keeps their account (#382).
 *
 * <p>Everything a statistic is drawn from goes: the titles (their readings cascade from
 * {@code library_item}), the wishes, the goals, the reader's own categories, the series and
 * authors they follow, their series reviews. What stays is who they are on the service —
 * the account, the profile, the readers they follow or block, the reports they filed and
 * their Home layout. The shared catalog is never touched: no row of it references a user.
 *
 * <p>One transaction, in this order: a partial reset would leave statistics that describe
 * neither the old library nor an empty one.
 */
@ApplicationScoped
public class LibraryReset {

    /** The tables emptied for the reader, in deletion order. Each carries a {@code user_id}. */
    static final List<String> TABLES = List.of(
            "library_item", "wishlist_item", "reading_goal", "rank_category",
            "series_follow", "series_review", "author_follow");

    @Inject
    EntityManager em;

    /** @return the rows removed, per table */
    @Transactional
    public Map<String, Integer> reset(String userId) {
        Map<String, Integer> removed = new LinkedHashMap<>();
        for (String table : TABLES) {
            // The table names are the constants above, never input: only the id is bound.
            removed.put(table, em.createNativeQuery("delete from " + table + " where user_id = ?1")
                    .setParameter(1, userId)
                    .executeUpdate());
        }
        em.clear();
        return removed;
    }
}
