package zelytra.librarius.imports;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import zelytra.librarius.imports.ImportService.ImportResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deferred imports: a scrape walks dozens of paginated pages and then inserts thousands of
 * rows — far more than an HTTP request can hold open before the ingress cuts it, which is the
 * {@code 500} a large Booknode library used to hit on its first try. The request now hands
 * back a job identifier, the import runs on a small pool of its own, and the client polls it
 * to completion.
 *
 * <p>Jobs live in memory and die with the pod, like the deferred exports next door: a job is
 * only the <em>progress</em> of work whose result — the library itself — lands in the
 * database as it goes, so losing one to a restart costs a re-run, not data. They expire on
 * their own after {@link #TTL} so a finished or abandoned import does not sit around.
 */
@ApplicationScoped
public class ImportJobs {

    private static final Logger LOG = Logger.getLogger(ImportJobs.class);

    /** Long enough to outlast the slowest scrape, short enough to keep nothing around. */
    static final Duration TTL = Duration.ofMinutes(30);

    public enum Status {
        RUNNING, DONE, FAILED
    }

    /**
     * One deferred import. Mutable, and read from two threads — the pool writes the outcome,
     * the request thread reads it — so the fields are {@code volatile}: each is written once
     * (or monotonically, for the counts) and there is nothing to make atomic beyond the
     * visibility of that write.
     */
    public static final class Job {

        public final UUID id = UUID.randomUUID();
        public final String userId;
        public final String source;
        public final Instant createdAt = Instant.now();

        volatile Status status = Status.RUNNING;
        volatile int total;
        volatile int imported;
        volatile int skipped;
        volatile String error;

        Job(String userId, String source) {
            this.userId = userId;
            this.source = source;
        }

        public Status status() {
            return status;
        }

        public int total() {
            return total;
        }

        public int imported() {
            return imported;
        }

        public int skipped() {
            return skipped;
        }

        public String error() {
            return error;
        }

        boolean expired() {
            return createdAt.plus(TTL).isBefore(Instant.now());
        }
    }

    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    /**
     * Two threads, daemon: an import spends most of its time waiting on Booknode, and a burst
     * of them must not starve the request pool. Anything past two waits its turn.
     */
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "librarius-import");
        thread.setDaemon(true);
        return thread;
    });

    @Inject
    ImportService imports;

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }

    /**
     * Schedules an import and returns the job to poll. A scrape passes its {@code handle} and a
     * null {@code csv}; a file passes its {@code csv} and a null handle, with {@code source}
     * "csv".
     */
    public Job submit(String userId, String source, String handle, String csv) {
        sweep();
        Job job = new Job(userId, source);
        jobs.put(job.id, job);
        workers.execute(() -> run(job, handle, csv));
        return job;
    }

    /**
     * A job, only for the user who asked for it: an identifier belonging to someone else is
     * empty, which the resource turns into a 404 — the same answer as one that never existed.
     */
    public Optional<Job> find(String userId, UUID id) {
        sweep();
        return Optional.ofNullable(jobs.get(id)).filter(job -> job.userId.equals(userId));
    }

    private void run(Job job, String handle, String csv) {
        try {
            List<ImportedBook> books = csv != null
                    ? ImportService.parseCsv(csv)
                    : imports.fetchBooks(job.source, handle);
            job.total = books.size();
            ImportResult result = imports.persist(job.userId, job.source, books);
            job.imported = result.imported();
            job.skipped = result.skipped();
            job.status = Status.DONE;
        } catch (ImportException e) {
            // A bad handle or an unreachable profile carries a message meant for the reader.
            job.error = e.getMessage();
            job.status = Status.FAILED;
        } catch (RuntimeException e) {
            // Anything else is ours, not the reader's: the resource shows a generic message,
            // and the detail stays in the log rather than in the response.
            job.status = Status.FAILED;
            LOG.error("Deferred import failed", e);
        }
    }

    /** Forgets what has aged out. Cheap, and runs on the calls that already touch the map. */
    private void sweep() {
        jobs.values().removeIf(Job::expired);
    }
}
