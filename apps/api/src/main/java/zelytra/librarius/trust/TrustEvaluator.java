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
import zelytra.librarius.domain.repository.ReportRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The one place that decides whether an account is trusted (#180, #195).
 *
 * <p>Trust is a private, server-computed signal — "this account's catalog contributions can be
 * trusted". It is never something a user sets on themselves and never something another user
 * grants: only the application decides, from the account's own activity. So the decision lives
 * off the request path entirely, in one scheduled job, and no endpoint anywhere accepts
 * {@code trusted} as input.
 *
 * <p><strong>Why the criteria sit in a single method.</strong> What counts as trustworthy is an
 * open product question (#180) — a blend of tenure, of how much the account has actually read,
 * and of a record clear of upheld reports — and it is meant to be retuned. Keeping the whole of
 * it in {@link #qualifies} behind thresholds read from configuration means that retuning it
 * moves a number in {@code application.properties}, never touches an endpoint, and needs no
 * schema change.
 *
 * <p><strong>Grant and revocation are the same decision.</strong> A run recomputes
 * {@link #qualifies} for every account: one that now clears the bar and was not trusted is
 * promoted; one that no longer clears it and was trusted is revoked ({@code trusted = false},
 * {@code trusted_at} cleared). The two are not separate criteria, only the two directions a
 * single verdict can move (#195). Revocation is a state, not a ban — an account that recovers,
 * because the upheld reports against it aged out of the window or were dismissed, earns the flag
 * back on a later run through the very same gate.
 *
 * <p><strong>What can move the verdict downward.</strong> Tenure and the read count only grow, so
 * on their own the verdict is monotonic. The one signal that can fall away is the count of
 * <em>upheld</em> reports against the account's contributions (#192, #195): crossing
 * {@link #maxUpheldReports} over the trailing {@link #reportWindowDays} takes the flag back. Only
 * {@code UPHELD} reports count — a moderator-confirmed one, never an unreviewed {@code OPEN}
 * report — so filing reports is not a way to strip a stranger of their trust. Nothing writes
 * {@code UPHELD} yet: that needs a moderation surface a maintainer must set up (see
 * {@code V19__report_contributor.sql}), so in production the count stays zero and this only ever
 * grants, exactly as #180 shipped it, until that surface and the attribution of a report to a
 * contributor (#198) both exist.
 */
@ApplicationScoped
public class TrustEvaluator {

    @ConfigProperty(name = "librarius.trust.min-account-months", defaultValue = "3")
    long minAccountMonths;

    @ConfigProperty(name = "librarius.trust.min-read-titles", defaultValue = "10")
    long minReadTitles;

    @ConfigProperty(name = "librarius.trust.max-upheld-reports", defaultValue = "3")
    long maxUpheldReports;

    @ConfigProperty(name = "librarius.trust.report-window-days", defaultValue = "180")
    long reportWindowDays;

    @Inject
    AppUserRepository users;

    @Inject
    LibraryItemRepository libraryItems;

    @Inject
    ReportRepository reports;

    public TrustEvaluator() {
    }

    /** Test seam: build the evaluator with explicit thresholds, bypassing configuration. */
    TrustEvaluator(long minAccountMonths, long minReadTitles, long maxUpheldReports,
            long reportWindowDays) {
        this.minAccountMonths = minAccountMonths;
        this.minReadTitles = minReadTitles;
        this.maxUpheldReports = maxUpheldReports;
        this.reportWindowDays = reportWindowDays;
    }

    /**
     * Recomputes trust for every account.
     *
     * <p>Daily by default, and switched off by setting the interval to {@code off} — which is
     * what the test profile does, so the suite drives {@link #evaluateAll} itself rather than
     * waiting on a timer. {@code SKIP} means a run overrunning its interval is not doubled up.
     */
    @Scheduled(every = "{librarius.trust.evaluate.every}",
            delayed = "{librarius.trust.evaluate.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledEvaluation() {
        int changed = evaluateAll();
        Log.infof("Trust evaluation: %d account(s) changed standing", changed);
    }

    /**
     * Recomputes the verdict for every account, promoting those that now qualify and revoking
     * those that no longer do, and returns how many accounts changed standing.
     *
     * <p>One transaction: the reads and the writes are all on the database, no outbound call sits
     * between them, and the changes of a run either all commit or none do. An account whose
     * standing is unchanged is left exactly as it was, timestamp included — a re-run is
     * idempotent, and neither re-stamps a still-trusted account nor keeps flipping a stable one.
     */
    @Transactional
    public int evaluateAll() {
        OffsetDateTime now = OffsetDateTime.now();
        int changed = 0;
        for (AppUser user : users.listAll()) {
            boolean qualifies = qualifies(signalsFor(user, now), now);
            if (qualifies && !user.trusted) {
                user.trusted = true;
                user.trustedAt = now;
                changed++;
            } else if (!qualifies && user.trusted) {
                user.trusted = false;
                user.trustedAt = null;
                changed++;
            }
        }
        return changed;
    }

    /** Gathers the activity signals of one account as of {@code now}. */
    private TrustSignals signalsFor(AppUser user, OffsetDateTime now) {
        long readTitles = libraryItems.countByStatus(user.id, LibraryStatus.READ);
        OffsetDateTime windowStart = now.minusDays(reportWindowDays);
        long upheldReports = reports.countUpheldContributionsSince(user.id, windowStart);
        return new TrustSignals(user.createdAt, readTitles, upheldReports);
    }

    /**
     * The criteria, and the only place they live: enough tenure, enough finished reading, and
     * fewer than {@link #maxUpheldReports} upheld reports against the account's contributions,
     * all three required. Retuned by moving the thresholds above rather than by editing this
     * shape, and read in both directions — an account that stops clearing the last criterion is
     * revoked by the same verdict that would have granted it.
     *
     * @param asOf the instant tenure is measured against — passed in rather than read here so
     *             the decision is a pure function of its inputs and testable without a clock
     */
    boolean qualifies(TrustSignals signals, OffsetDateTime asOf) {
        long months = ChronoUnit.MONTHS.between(signals.createdAt(), asOf);
        return months >= minAccountMonths
                && signals.readTitles() >= minReadTitles
                && signals.upheldReports() < maxUpheldReports;
    }

    /**
     * One account's activity, as the criteria read it.
     *
     * @param createdAt     when the account was provisioned — its tenure
     * @param readTitles    titles the account marked {@code READ} — the volume of its activity
     * @param upheldReports upheld reports against its contributions over the trailing window; zero
     *                      in production until a moderation surface (#195) upholds a report and
     *                      attribution (#198) ties it to a contributor
     */
    public record TrustSignals(OffsetDateTime createdAt, long readTitles, long upheldReports) {
    }
}
