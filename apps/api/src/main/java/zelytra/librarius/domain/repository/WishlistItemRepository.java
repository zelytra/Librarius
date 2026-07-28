package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;

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
         * Most urgent first, then most recently added — the historical ordering, kept as
         * the default.
         */
        PRIORITY("wi.priority asc, wi.createdAt desc, wi.id desc"),
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
