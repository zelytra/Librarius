package zelytra.librarius.trust;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The one place that decides whether an account is trusted (#180).
 *
 * <p>Trust is a private, server-computed signal — "this account's catalog contributions can be
 * trusted". It is never something a user sets on themselves and never something another user
 * grants: only the application decides, from the account's own activity. So the decision lives
 * off the request path entirely, in one scheduled job, and no endpoint anywhere accepts
 * {@code trusted} as input.
 *
 * <p><strong>Why the criteria sit in a single method.</strong> What counts as trustworthy is an
 * open product question (#180) — a blend of tenure, of how much the account has actually read,
 * and of a record clean of upheld reports — and it is meant to be retuned. Keeping the whole of
 * it in {@link #qualifies} behind thresholds read from configuration means that retuning it
 * moves a number in {@code application.properties}, never touches an endpoint, and needs no
 * schema change.
 *
 * <p><strong>The evaluation only ever grants.</strong> An account that already earned the flag
 * is left alone: this issue ships the earning of trust, and taking it back on an upheld report
 * is #195. Because tenure and the read count only ever grow, a promotion here is stable on its
 * own; revocation is a genuinely separate mechanism, not the absence of a criterion.
 */
@ApplicationScoped
public class TrustEvaluator {

    @ConfigProperty(name = "librarius.trust.min-account-months", defaultValue = "3")
    long minAccountMonths;

    @ConfigProperty(name = "librarius.trust.min-read-titles", defaultValue = "10")
    long minReadTitles;

    @Inject
    AppUserRepository users;

    @Inject
    LibraryItemRepository libraryItems;

    public TrustEvaluator() {
    }

    /** Test seam: build the evaluator with explicit thresholds, bypassing configuration. */
    TrustEvaluator(long minAccountMonths, long minReadTitles) {
        this.minAccountMonths = minAccountMonths;
        this.minReadTitles = minReadTitles;
    }

    /**
     * Recomputes trust for every account that has not earned it yet.
     *
     * <p>Daily by default, and switched off by setting the interval to {@code off} — which is
     * what the test profile does, so the suite drives {@link #evaluateAll} itself rather than
     * waiting on a timer. {@code SKIP} means a run overrunning its interval is not doubled up.
     */
    @Scheduled(every = "{librarius.trust.evaluate.every}",
            delayed = "{librarius.trust.evaluate.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledEvaluation() {
        int promoted = evaluateAll();
        Log.infof("Trust evaluation: %d account(s) promoted", promoted);
    }

    /**
     * Evaluates every account not yet trusted and promotes those that now qualify, returning
     * how many were promoted. Already-trusted accounts are untouched: this only ever grants.
     *
     * <p>One transaction: the reads and the writes are all on the database, no outbound call
     * sits between them, and the promotions of a run either all commit or none do.
     */
    @Transactional
    public int evaluateAll() {
        OffsetDateTime now = OffsetDateTime.now();
        int promoted = 0;
        for (AppUser user : users.listUntrusted()) {
            if (qualifies(signalsFor(user), now)) {
                user.trusted = true;
                user.trustedAt = now;
                promoted++;
            }
        }
        return promoted;
    }

    /** Gathers the activity signals of one account. */
    private TrustSignals signalsFor(AppUser user) {
        long readTitles = libraryItems.countByStatus(user.id, LibraryStatus.READ);
        // No report exists yet: the gate is here so the criterion stays expressible and tested,
        // and #195 is what will feed it a non-zero count and revoke on the back of one.
        long upheldReports = 0;
        return new TrustSignals(user.createdAt, readTitles, upheldReports);
    }

    /**
     * The criteria, and the only place they live: enough tenure, enough finished reading, and a
     * record clean of upheld reports, all three required. Retuned by moving the thresholds
     * above rather than by editing this shape.
     *
     * @param asOf the instant tenure is measured against — passed in rather than read here so
     *             the decision is a pure function of its inputs and testable without a clock
     */
    boolean qualifies(TrustSignals signals, OffsetDateTime asOf) {
        long months = ChronoUnit.MONTHS.between(signals.createdAt(), asOf);
        return months >= minAccountMonths
                && signals.readTitles() >= minReadTitles
                && signals.upheldReports() == 0;
    }

    /**
     * One account's activity, as the criteria read it.
     *
     * @param createdAt     when the account was provisioned — its tenure
     * @param readTitles    titles the account marked {@code READ} — the volume of its activity
     * @param upheldReports reports upheld against its contributions; always 0 today, until #195
     */
    public record TrustSignals(OffsetDateTime createdAt, long readTitles, long upheldReports) {
    }
}
