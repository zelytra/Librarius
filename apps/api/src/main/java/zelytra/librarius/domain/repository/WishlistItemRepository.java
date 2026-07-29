package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WishlistItemRepository implements PanacheRepositoryBase<WishlistItem, UUID> {

    public boolean deleteOwned(String userId, UUID id) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }

    /**
     * The whole wishlist of one user, edition and work fetched along the way, ordered by
     * title — same contract, and same reason, as
     * {@link LibraryItemRepository#listForExport(String)}.
     */
    public List<WishlistItem> listForExport(String userId) {
        return getEntityManager()
                .createQuery("""
                        select wi from WishlistItem wi
                          join fetch wi.edition e
                          join fetch e.work w
                        where wi.userId = :userId
                        order by lower(w.title) asc, w.volumeNumber asc nulls first,
                                 coalesce(e.isbn13, '') asc, lower(coalesce(e.publisher, '')) asc,
                                 wi.id asc
                        """, WishlistItem.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * Scoping-safe lookup: returns the wish only when it belongs to the caller.
     *
     * <p>Callers turn an empty result into a 404 rather than a 403 — confirming that a
     * wish exists but belongs to someone else is already a leak.
     */
    public Optional<WishlistItem> findOwned(String userId, UUID id) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    // ── Paged browsing ────────────────────────────────────────────────────────
    // Same contract as the collection: the database filters, sorts and slices.

    /**
     * Everything that narrows the wishlist down, all fields optional but the user.
     *
     * <p>The collection filters {@code status} and {@code rank} have no counterpart here —
     * a wish carries neither — so the wishlist exposes {@code priority} instead.
     *
     * @param userId   owner of the wishes, the only mandatory criterion
     * @param kind     restrict to books or to mangas
     * @param priority restrict to a single acquisition priority
     * @param search   free text matched against the title, the authors and the series
     */
    public record WishlistFilter(String userId, Kind kind, WishPriority priority, String search) {
    }

    /** Orderings offered on the wishlist, exposed as the {@code sort} query parameter. */
    public enum WishlistSort {
        /**
         * Most urgent first, then most recently added — the default.
         *
         * <p>The urgency comes from {@link #urgencyRank()}, not from the column: the
         * priority is stored as its name, so {@code order by wi.priority} sorted
         * {@code PRIORITY, SOMEDAY, SOON} and showed the wishes the user had no date for
         * ahead of the ones they meant to buy next.
         */
        PRIORITY(urgencyRank() + " asc, wi.createdAt desc, wi.id desc"),
        ADDED("wi.createdAt desc, wi.id desc"),
        TITLE("lower(w.title) asc, wi.id asc"),
        AUTHOR("lower(coalesce(w.authors, '')) asc, lower(w.title) asc, wi.id asc"),
        /** Cheapest first; wishes carrying no estimate are pushed to the end. */
        PRICE("coalesce(wi.estimatedPrice, 1000000) asc, lower(w.title) asc, wi.id asc");

        private final String clause;

        WishlistSort(String clause) {
            this.clause = clause;
        }

        /**
         * Maps each priority to {@link WishPriority#rank} inside the query, so the wishlist
         * is ordered by what the user meant rather than by how the value is spelled.
         *
         * <p>Generated from the enum instead of being spelled out once here and once in the
         * enum: a fourth priority is then a single declaration, and the two can never
         * disagree on which wish is the more urgent.
         */
        private static String urgencyRank() {
            StringBuilder expression = new StringBuilder("case wi.priority");
            for (WishPriority priority : WishPriority.values()) {
                expression.append(" when ").append(WishPriority.class.getName()).append('.')
                        .append(priority.name()).append(" then ").append(priority.rank);
            }
            return expression.append(" end").toString();
        }

        /**
         * Resolves the lower-case value sent by the client. Every ordering ends on the
         * identifier so that ties never swap places from one page to the next.
         *
         * @return the matching ordering, or empty when the value is not one of ours
         */
        public static Optional<WishlistSort> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.of(PRIORITY);
            }
            return Arrays.stream(values())
                    .filter(s -> s.name().equalsIgnoreCase(value.trim()))
                    .findFirst();
        }
    }

    /** Number of wishes matching the filter, for the {@code total} of the envelope. */
    public long countMatching(WishlistFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(filter, params);
        TypedQuery<Long> query = getEntityManager().createQuery(
                "select count(wi) from WishlistItem wi join wi.edition e join e.work w where "
                        + where,
                Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    /** One page of the filtered wishlist, edition and work fetched in the same query. */
    public List<WishlistItem> listMatching(WishlistFilter filter, WishlistSort sort, int offset,
            int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(filter, params);
        TypedQuery<WishlistItem> query = getEntityManager().createQuery(
                "select wi from WishlistItem wi join fetch wi.edition e join fetch e.work w"
                        + " where " + where + " order by " + sort.clause,
                WishlistItem.class);
        params.forEach(query::setParameter);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    /**
     * What one priority's wishes would cost.
     *
     * @param priority    the group
     * @param count       wishes in it, whether or not they carry an estimate
     * @param pricedCount those of them that do
     * @param total       sum of those estimates, never null
     */
    public record PriorityBudget(WishPriority priority, long count, long pricedCount,
            BigDecimal total) {
    }

    /**
     * Budget of the wishes matching the filter, grouped by priority, most urgent first.
     *
     * <p>Aggregated by the database rather than by summing the page: a page holds at most
     * `size` rows, so adding up what is on screen would answer a different question from
     * the one the user is asking. Priorities nobody wishes for are absent rather than
     * reported as zero — the client shows what exists.
     */
    public List<PriorityBudget> budgetByPriority(WishlistFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(filter, params);
        TypedQuery<Object[]> query = getEntityManager().createQuery(
                "select wi.priority, count(wi), count(wi.estimatedPrice),"
                        + " coalesce(sum(wi.estimatedPrice), 0)"
                        + " from WishlistItem wi join wi.edition e join e.work w"
                        + " where " + where
                        + " group by wi.priority",
                Object[].class);
        params.forEach(query::setParameter);

        // Ordering happens here rather than in SQL: `order by wi.priority` would sort the
        // stored names, which is the very bug #114 is about.
        return query.getResultList().stream()
                .map(row -> new PriorityBudget(
                        (WishPriority) row[0],
                        (Long) row[1],
                        (Long) row[2],
                        (BigDecimal) row[3]))
                .sorted(java.util.Comparator.comparingInt(b -> b.priority().rank))
                .toList();
    }

    /**
     * Builds the shared {@code where} of the count and of the page, collecting the bound
     * parameters on the way so the two queries always agree on the criteria.
     */
    private static String whereClause(WishlistFilter filter, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("wi.userId = :userId");
        params.put("userId", filter.userId());

        if (filter.kind() != null) {
            clauses.add("w.kind = :kind");
            params.put("kind", filter.kind());
        }
        if (filter.priority() != null) {
            clauses.add("wi.priority = :priority");
            params.put("priority", filter.priority());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            clauses.add("""
                    (lower(w.title) like :search escape '!'
                     or lower(coalesce(w.authors, '')) like :search escape '!'
                     or lower(coalesce(w.seriesTitle, '')) like :search escape '!')""");
            params.put("search", LibraryItemRepository.likePattern(filter.search()));
        }
        return String.join(" and ", clauses);
    }
}
