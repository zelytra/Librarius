package zelytra.librarius.stats;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import zelytra.librarius.web.ApiDtos.KindValueDto;
import zelytra.librarius.web.ApiDtos.ValueDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the library and the wishlist are worth (#421), in two grouped queries.
 *
 * <p>The library counts the titles the reader physically has ({@code ownership = OWNED}) at
 * the price they gave the title themselves (#431), else the publisher's price of a new copy: a borrowed book or an ebook is not on the shelves, and
 * the price of a printed copy would overstate either. The wishlist counts each wish at that
 * market price, falling back on the price the reader estimated.
 *
 * <p>Every price the BnF gives is in euros, so the figure is in euros; a title with no known
 * price contributes nothing, and the counters say how many those are.
 */
@ApplicationScoped
public class ValueService {

    @Inject
    EntityManager em;

    public ValueDto value(String userId) {
        Map<String, BigDecimal[]> library = new TreeMap<>();
        Map<String, long[]> libraryCounts = new TreeMap<>();
        long owned = 0;
        long ownedPriced = 0;
        BigDecimal libraryTotal = BigDecimal.ZERO;
        @SuppressWarnings("unchecked")
        List<Object[]> libraryRows = em.createNativeQuery("""
                select w.kind, count(*), count(coalesce(li.price, e.list_price)),
                       coalesce(sum(coalesce(li.price, e.list_price)), 0)
                from library_item li
                  join edition e on e.id = li.edition_id
                  join work w on w.id = e.work_id
                where li.user_id = :userId
                  and li.ownership = 'OWNED'
                group by w.kind
                """).setParameter("userId", userId).getResultList();
        for (Object[] row : libraryRows) {
            String kind = (String) row[0];
            long priced = ((Number) row[2]).longValue();
            BigDecimal sum = toDecimal(row[3]);
            owned += ((Number) row[1]).longValue();
            ownedPriced += priced;
            libraryTotal = libraryTotal.add(sum);
            library.put(kind, new BigDecimal[] {sum});
            libraryCounts.put(kind, new long[] {priced});
        }

        Map<String, BigDecimal[]> wishlist = new TreeMap<>();
        Map<String, long[]> wishlistCounts = new TreeMap<>();
        long wishes = 0;
        long wishesPriced = 0;
        BigDecimal wishlistTotal = BigDecimal.ZERO;
        @SuppressWarnings("unchecked")
        List<Object[]> wishRows = em.createNativeQuery("""
                select w.kind, count(*), count(coalesce(e.list_price, wi.estimated_price)),
                       coalesce(sum(coalesce(e.list_price, wi.estimated_price)), 0)
                from wishlist_item wi
                  join edition e on e.id = wi.edition_id
                  join work w on w.id = e.work_id
                where wi.user_id = :userId
                group by w.kind
                """).setParameter("userId", userId).getResultList();
        for (Object[] row : wishRows) {
            String kind = (String) row[0];
            long priced = ((Number) row[2]).longValue();
            BigDecimal sum = toDecimal(row[3]);
            wishes += ((Number) row[1]).longValue();
            wishesPriced += priced;
            wishlistTotal = wishlistTotal.add(sum);
            wishlist.put(kind, new BigDecimal[] {sum});
            wishlistCounts.put(kind, new long[] {priced});
        }

        TreeMap<String, Boolean> kinds = new TreeMap<>();
        library.keySet().forEach(k -> kinds.put(k, true));
        wishlist.keySet().forEach(k -> kinds.put(k, true));
        List<KindValueDto> byKind = new ArrayList<>();
        for (String kind : kinds.keySet()) {
            byKind.add(new KindValueDto(kind,
                    library.getOrDefault(kind, new BigDecimal[] {BigDecimal.ZERO})[0],
                    libraryCounts.getOrDefault(kind, new long[] {0})[0],
                    wishlist.getOrDefault(kind, new BigDecimal[] {BigDecimal.ZERO})[0],
                    wishlistCounts.getOrDefault(kind, new long[] {0})[0]));
        }
        return new ValueDto("EUR", libraryTotal, owned, ownedPriced, wishlistTotal, wishes,
                wishesPriced, byKind);
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return decimal.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
