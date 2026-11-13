package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import zelytra.librarius.library.PageCountEstimator;
import zelytra.librarius.pricing.PriceRefresher;

/**
 * Brings an import's data up to date as soon as it lands (#425): the covers, ISBNs, page
 * counts and publishers the catalog knows, then the prices of the editions that now carry an
 * ISBN, then the estimates of the lengths still unknown.
 *
 * <p>The scheduled enricher and price refresher work through everybody's backlog a small batch
 * at a time, so a 2 000-title import used to take hours to fill in, and its prices waited for
 * the enricher to find the ISBNs first. Here the importing reader's own editions go first, in
 * consecutive batches, on the import worker that has just finished — off the request path,
 * and still through the catalog's own rate limiter. A cap keeps one huge import from holding a
 * worker for ever; whatever is left goes back to the schedule.
 */
@ApplicationScoped
public class ImportFollowUp {

    /** Batches of each pass at most: 20 × 60 editions to enrich, 20 × 40 to price. */
    static final int MAX_ROUNDS = 20;

    @Inject
    ImportEnricher enricher;

    @Inject
    PriceRefresher prices;

    @Inject
    PageCountEstimator pages;

    @Inject
    zelytra.librarius.series.SeriesVolumeRefresher seriesVolumes;

    /** Off in the test profile, which calls {@link #refresh} directly. */
    @ConfigProperty(name = "librarius.imports.follow-up.enabled", defaultValue = "true")
    boolean enabled;

    /** Called once an import has been persisted; never throws. */
    public void afterImport(String userId) {
        if (!enabled) {
            return;
        }
        try {
            refresh(userId);
        } catch (RuntimeException e) {
            Log.warnf("Import follow-up failed for %s: %s", userId, e.getMessage());
        }
    }

    /** Enriches, prices and estimates the user's editions now; returns the editions looked at. */
    public int refresh(String userId) {
        int looked = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int batch = enricher.enrichBatchFor(userId);
            looked += batch;
            if (batch == 0) {
                break;
            }
        }
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int batch = prices.refreshBatchFor(userId);
            looked += batch;
            if (batch == 0) {
                break;
            }
        }
        pages.estimateNow();
        // The reader's series learn how many volumes they run to, so their missing tomes show (#429).
        seriesVolumes.refreshFor(userId);
        return looked;
    }
}
