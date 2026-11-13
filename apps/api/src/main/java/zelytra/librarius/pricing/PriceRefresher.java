package zelytra.librarius.pricing;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import zelytra.librarius.catalog.provider.BnfProvider;
import zelytra.librarius.catalog.provider.BnfProvider.ListPrice;
import zelytra.librarius.domain.Edition;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up the publisher's price of the editions people own or want (#421).
 *
 * <p>The price comes from the BnF record (UNIMARC 010$d). In France that price is fixed by law
 * for every shop, so it is the market price of a new copy rather than an estimate. Only the
 * editions sitting in somebody's library or wishlist are looked up — the catalog cache holds
 * far more, and nobody would read their price — in small batches, so one run cannot spend the
 * BnF's patience. Every edition looked up is stamped, priced or not, and retried only after
 * {@link #RETRY_AFTER}.
 *
 * <p>Same shape as the import enricher: the targets are read in one short transaction, the BnF
 * is called with no transaction open, and each answer is written in its own.
 */
@ApplicationScoped
public class PriceRefresher {

    /** Editions looked up per run. */
    private static final int BATCH = 40;

    /** How long an edition waits before its price is looked up again. */
    static final Duration RETRY_AFTER = Duration.ofDays(60);

    @Inject
    EntityManager em;

    @Inject
    BnfProvider bnf;

    /** One edition to price, carried out of the read transaction. */
    record Target(UUID id, String isbn13) {
    }

    @Scheduled(every = "{librarius.prices.refresh.every}",
            delayed = "{librarius.prices.refresh.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefresh() {
        int priced = refreshNow();
        if (priced > 0) {
            Log.infof("Prices: %d editions priced from the BnF", priced);
        }
    }

    /** Runs one batch now; returns how many editions received a price. */
    public int refreshNow() {
        return price(QuarkusTransaction.requiringNew().call(() -> targets(null)));
    }

    /**
     * One batch of one user's editions (#425), run as soon as their import lands.
     *
     * @return how many editions the batch looked up — zero once none is left
     */
    public int refreshBatchFor(String userId) {
        List<Target> targets = QuarkusTransaction.requiringNew().call(() -> targets(userId));
        price(targets);
        return targets.size();
    }

    private int price(List<Target> targets) {
        int priced = 0;
        for (Target target : targets) {
            Optional<ListPrice> price = lookup(target.isbn13());
            QuarkusTransaction.requiringNew().run(() -> record(target.id(), price.orElse(null)));
            if (price.isPresent()) {
                priced++;
            }
        }
        return priced;
    }

    /** The BnF answer, or empty when it fails: a missing price is no reason to stop the batch. */
    Optional<ListPrice> lookup(String isbn13) {
        try {
            return bnf.priceByIsbn(isbn13);
        } catch (RuntimeException e) {
            Log.warnf("Price lookup failed for %s: %s", isbn13, e.getMessage());
            return Optional.empty();
        }
    }

    /** Editions to price: everybody's, or only those of {@code userId} when given. */
    private List<Target> targets(String userId) {
        String owner = userId == null ? "" : " and li.user_id = :userId";
        String wisher = userId == null ? "" : " and wi.user_id = :userId";
        jakarta.persistence.Query query = em.createNativeQuery("""
                select e.id, e.isbn13
                from edition e
                where e.isbn13 is not null
                  and (e.price_checked_at is null or e.price_checked_at < :before)
                  and (exists (select 1 from library_item li where li.edition_id = e.id%s)
                       or exists (select 1 from wishlist_item wi where wi.edition_id = e.id%s))
                order by e.price_checked_at nulls first, e.created_at desc
                """.formatted(owner, wisher))
                .setParameter("before", OffsetDateTime.now().minus(RETRY_AFTER))
                .setMaxResults(BATCH);
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new Target((UUID) row[0], (String) row[1]))
                .toList();
    }

    private void record(UUID editionId, ListPrice price) {
        Edition edition = em.find(Edition.class, editionId);
        if (edition == null) {
            return;
        }
        edition.priceCheckedAt = OffsetDateTime.now();
        if (price != null) {
            edition.listPrice = price.amount();
            edition.listPriceCurrency = price.currency();
        }
    }
}
