package zelytra.librarius.series;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.repository.SeriesRepository;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Fills {@code series.total_volumes} off the request path.
 *
 * <p>The series grid runs to the furthest volume anyone knows about — the announced total, the
 * last volume in the catalog, or the last one the user owns (see {@code SeriesService.Run}). Only
 * the first of those tells a reader who owns tome 3 of a twenty-volume run that tomes 4–20 exist,
 * and nothing filled it, so the grid stopped at whatever the collection reached. This resolves
 * each series against its provider — AniList reports a manga's {@code volumes} — once, and writes
 * the total, so the missing tomes appear.
 *
 * <p>Nothing here is user-scoped: {@link Series} is shared catalog data. The provider is called
 * outside any transaction, so no database connection is held across the outbound HTTP call.
 */
@ApplicationScoped
public class SeriesVolumeRefresher {

    /** Series resolved per run: a batch, so one run cannot spend the whole provider quota. */
    private static final int BATCH = 50;

    @Inject
    CatalogService catalog;

    @Inject
    SeriesRepository series;

    /**
     * Daily by default, off in the test profile (interval {@code off}). {@code SKIP} keeps a run
     * that overran its interval from being doubled up.
     */
    @Scheduled(every = "{librarius.series.volume-refresh.every}",
            delayed = "{librarius.series.volume-refresh.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefresh() {
        int filled = refreshNow();
        Log.infof("Series volumes: %d filled", filled);
    }

    /** Resolves a batch of series still missing a total, and returns how many were filled. */
    public int refreshNow() {
        List<Series> batch = QuarkusTransaction.requiringNew()
                .call(() -> series.needingVolumeCount(BATCH));
        int filled = 0;
        for (Series s : batch) {
            OptionalInt volumes = catalog.seriesVolumes(s.kind, s.title);
            if (volumes.isPresent()) {
                filled += QuarkusTransaction.requiringNew()
                        .call(() -> setTotal(s.id, volumes.getAsInt()));
            }
        }
        return filled;
    }

    /**
     * Writes the total onto a series that still lacks one, and reports whether it did — a series
     * a concurrent run has filled in the meantime is left alone. Must run inside a transaction.
     */
    int setTotal(UUID seriesId, int volumes) {
        Series found = series.findById(seriesId);
        if (found == null || found.totalVolumes != null) {
            return 0;
        }
        found.totalVolumes = volumes;
        return 1;
    }
}
