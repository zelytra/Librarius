package zelytra.librarius.export;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import zelytra.librarius.export.ExportService.ExportFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deferred exports: the ones too large to serialise inside the request that asked for them.
 *
 * <p>A 400-volume library serialises in milliseconds, so the common case stays synchronous
 * and the user gets their file from a single call. Past
 * {@code librarius.export.async-threshold} titles the request hands back a job identifier
 * instead, the document is built on a small pool of its own, and the client comes back for
 * it — which keeps a very large account from holding an HTTP worker, and from timing out
 * behind the ingress.
 *
 * <p>Jobs live in memory and die with the pod. That is deliberate: an export is a copy of
 * data that is still in the database, so losing one costs a second click, where persisting
 * it would mean storing a full copy of somebody's library outside the tables that hold it —
 * exactly what the account deletion below has to be able to promise it erased. They also
 * expire on their own after {@link #TTL}, so a downloaded — or abandoned — export does not
 * sit in memory for the life of the process.
 */
@ApplicationScoped
public class ExportJobs {

    private static final Logger LOG = Logger.getLogger(ExportJobs.class);

    /** Long enough for a slow client to come back, short enough to keep nothing around. */
    static final Duration TTL = Duration.ofMinutes(15);

    public enum Status {
        PENDING, READY, FAILED
    }

    /**
     * One deferred export.
     *
     * <p>Mutable, and read from two threads: the pool writes the outcome, the request thread
     * reads it. The fields are {@code volatile} rather than guarded by a lock — a job is
     * written once and read many times, and there is nothing to make atomic beyond the
     * visibility of that single write.
     */
    public static final class Job {

        public final UUID id = UUID.randomUUID();
        public final String userId;
        public final ExportFormat format;
        public final int rows;
        public final Instant createdAt = Instant.now();

        volatile Status status = Status.PENDING;
        volatile ExportFile file;

        Job(String userId, ExportFormat format, int rows) {
            this.userId = userId;
            this.format = format;
            this.rows = rows;
        }

        public Status status() {
            return status;
        }

        public ExportFile file() {
            return file;
        }

        boolean expired() {
            return createdAt.plus(TTL).isBefore(Instant.now());
        }
    }

    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    /**
     * Two threads, daemon: an export is I/O-light and CPU-cheap, and a burst of them must
     * not be able to starve the request pool. Anything past two waits its turn.
     */
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "librarius-export");
        thread.setDaemon(true);
        return thread;
    });

    @Inject
    ExportService exports;

    /**
     * Above how many titles an export stops being served inside the request. Configurable
     * because the right value depends on the machine, not on the code.
     */
    @ConfigProperty(name = "librarius.export.async-threshold", defaultValue = "2000")
    int asyncThreshold;

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }

    /** Whether an account of this size is served straight away. */
    public boolean fitsInRequest(int rows) {
        return rows <= asyncThreshold;
    }

    /** Schedules an export and returns the job to poll. */
    public Job submit(String userId, ExportFormat format, int rows) {
        sweep();
        Job job = new Job(userId, format, rows);
        jobs.put(job.id, job);
        workers.execute(() -> run(job));
        return job;
    }

    /**
     * A job, only for the user who asked for it.
     *
     * <p>The scoping is here rather than at the resource so that no caller can forget it:
     * an identifier belonging to somebody else is empty, which the resource turns into a
     * 404 — the same answer as an identifier that never existed.
     */
    public Optional<Job> find(String userId, UUID id) {
        sweep();
        return Optional.ofNullable(jobs.get(id))
                .filter(job -> job.userId.equals(userId));
    }

    private void run(Job job) {
        try {
            job.file = exports.build(job.userId, job.format);
            job.status = Status.READY;
        } catch (RuntimeException e) {
            job.status = Status.FAILED;
            // No identifier and no content: a failed export must not turn into a log line
            // holding somebody's library.
            LOG.error("Deferred export failed", e);
        }
    }

    /** Forgets what has aged out. Cheap, and runs on the calls that already touch the map. */
    private void sweep() {
        jobs.values().removeIf(Job::expired);
    }
}
