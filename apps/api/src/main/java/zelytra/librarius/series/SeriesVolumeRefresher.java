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

    /** Batches at most when one user's series are resolved straight away. */
    private static final int MAX_ROUNDS = 10;

    /** Off in the test profile, which calls the refresher directly. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "librarius.series.volume-refresh.on-add",
            defaultValue = "true")
    boolean async;

    @Inject
    jakarta.transaction.TransactionSynchronizationRegistry transactions;

    /** One daemon thread: a lookup a reader triggered must not hold a request thread. */
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "librarius-series-volumes");
                thread.setDaemon(true);
                return thread;
            });

    @jakarta.annotation.PreDestroy
    void stop() {
        worker.shutdownNow();
    }

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
        return resolve(QuarkusTransaction.requiringNew().call(() -> series.needingVolumeCount(BATCH)));
    }

    /**
     * Resolves the series one user holds a volume of, straight away (#429): after an import, the
     * reader's runs show their missing volumes without waiting for the schedule.
     *
     * @return how many series received a total
     */
    public int refreshFor(String userId) {
        int filled = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Series> batch = QuarkusTransaction.requiringNew()
                    .call(() -> series.needingVolumeCount(userId, BATCH));
            if (batch.isEmpty()) {
                break;
            }
            filled += resolve(batch);
        }
        return filled;
    }

    /**
     * Resolves one series once the transaction that created it has committed (#429) — a title
     * added from the search opens a run whose other volumes the grid can list the same day.
     * Runs off the request thread; a failure only leaves it to the schedule.
     */
    public void refreshAfterCommit(UUID seriesId) {
        if (seriesId == null || !async) {
            return;
        }
        Runnable task = () -> worker.execute(() -> {
            try {
                resolve(QuarkusTransaction.requiringNew().call(() -> {
                    Series found = series.findById(seriesId);
                    return found != null && found.totalVolumes == null && found.volumesCheckedAt == null
                            ? List.of(found) : List.<Series>of();
                }));
            } catch (RuntimeException e) {
                Log.warnf("Series volume lookup failed for %s: %s", seriesId, e.getMessage());
            }
        });
        try {
            transactions.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
                @Override
                public void beforeCompletion() {
                    // Nothing to do before the commit.
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        task.run();
                    }
                }
            });
        } catch (IllegalStateException noTransaction) {
            task.run();
        }
    }

    /** Asks the providers for each series, stamps it, and writes the total when one is known. */
    private int resolve(List<Series> batch) {
        int filled = 0;
        for (Series s : batch) {
            OptionalInt volumes;
            try {
                volumes = catalog.seriesVolumes(s.kind, s.title);
            } catch (RuntimeException e) {
                Log.warnf("Series volume lookup failed for %s: %s", s.id, e.getMessage());
                volumes = OptionalInt.empty();
            }
            OptionalInt found = volumes;
            filled += QuarkusTransaction.requiringNew().call(() -> {
                Series row = series.findById(s.id);
                if (row == null) {
                    return 0;
                }
                row.volumesCheckedAt = java.time.OffsetDateTime.now();
                return found.isPresent() ? setTotal(s.id, found.getAsInt()) : 0;
            });
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
