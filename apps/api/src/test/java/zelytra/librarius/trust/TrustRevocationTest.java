package zelytra.librarius.trust;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Report;
import zelytra.librarius.domain.ReportReason;
import zelytra.librarius.domain.ReportStatus;
import zelytra.librarius.domain.ReportTargetType;
import zelytra.librarius.domain.Work;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The automatic trust revocation, end to end against the database (#195).
 *
 * <p>The evaluation grants and revokes through one verdict, so this drives the real thing: a
 * qualifying account is promoted, then upheld reports against its contributions cross the
 * configured bar ({@code max-upheld-reports=2} in the test profile) and the next run takes the
 * flag back — {@code trusted} to {@code false}, {@code trusted_at} cleared. What is worth locking
 * down here rather than in {@link TrustEvaluatorTest} is everything the pure criteria cannot show:
 * that only {@code UPHELD} reports weigh (an {@code OPEN} one does not), that the revocation lands
 * on the reported account and nobody else's, that a re-run is idempotent, and that revocation is a
 * state and not a ban — dismiss the reports and the account earns the flag back.
 */
@QuarkusTest
class TrustRevocationTest {

    @Inject
    TrustEvaluator evaluator;

    @Inject
    EntityManager em;

    /**
     * Upheld reports against a trusted account's contributions, once they cross the bar, revoke
     * it — and only it. Fewer than the bar, or unreviewed reports, leave the flag alone.
     */
    @Test
    void upheldReportsCrossingTheBarRevokeTrustWithoutTouchingAnother() {
        String reporter = seedUser(0, 0);
        // Qualifies on tenure and reads, and starts clean.
        String culprit = seedUser(2, 2);
        // Equally qualifying, never reported: the control the revocation must not touch.
        String bystander = seedUser(2, 2);

        evaluator.evaluateAll();
        assertTrue(trusted(culprit), "the qualifying account is granted trust");
        assertTrue(trusted(bystander), "so is the equally qualifying control");

        // One upheld report is below the bar of two: still trusted.
        upheldReport(reporter, culprit);
        evaluator.evaluateAll();
        assertTrue(trusted(culprit), "a single upheld report is under the bar");

        // An OPEN report never counts, however many: it is unreviewed, not confirmed.
        openReport(reporter, culprit);
        openReport(reporter, culprit);
        evaluator.evaluateAll();
        assertTrue(trusted(culprit), "open reports do not weigh — only upheld ones do");

        // A second upheld report reaches the bar of two: revoked.
        upheldReport(reporter, culprit);
        int changed = evaluator.evaluateAll();

        assertTrue(changed >= 1, "reaching the bar changes the account's standing");
        assertFalse(trusted(culprit), "the reported account loses the flag");
        assertNull(trustedAt(culprit), "and its earned-at day is cleared");
        assertTrue(trusted(bystander), "the never-reported control keeps its trust");
        assertNotNull(trustedAt(bystander));
    }

    /** A revoked account holds steady across re-runs: revocation does not keep flipping. */
    @Test
    void aRevokedAccountStaysRevokedOnReRun() {
        String reporter = seedUser(0, 0);
        String culprit = seedUser(2, 2);

        evaluator.evaluateAll();
        assertTrue(trusted(culprit));

        upheldReport(reporter, culprit);
        upheldReport(reporter, culprit);
        evaluator.evaluateAll();
        assertFalse(trusted(culprit), "revoked once the bar is reached");

        int changedAgain = evaluator.evaluateAll();
        assertEquals(0, changedAgain, "a re-run changes nobody's standing");
        assertFalse(trusted(culprit), "still revoked");
    }

    /**
     * Revocation is a state, not a ban: dismiss the reports that cost the account its flag and the
     * very same evaluator grants it back — no separate path to re-earn trust.
     */
    @Test
    void aRevokedAccountRegainsTrustOnceTheReportsAreDismissed() {
        String reporter = seedUser(0, 0);
        String culprit = seedUser(2, 2);

        evaluator.evaluateAll();
        upheldReport(reporter, culprit);
        upheldReport(reporter, culprit);
        evaluator.evaluateAll();
        assertFalse(trusted(culprit), "revoked while the upheld reports stand");

        QuarkusTransaction.requiringNew().run(() ->
                em.createQuery("update Report set status = :dismissed where contributorId = :id")
                        .setParameter("dismissed", ReportStatus.DISMISSED)
                        .setParameter("id", culprit)
                        .executeUpdate());

        int changed = evaluator.evaluateAll();
        assertTrue(changed >= 1, "clearing the signal changes the account's standing back");
        assertTrue(trusted(culprit), "the account earns the flag back through the same gate");
        assertNotNull(trustedAt(culprit), "and carries the day it re-earned it");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * A fresh account with {@code readCount} titles marked {@code READ}, optionally backdated so
     * its tenure clears the test threshold.
     *
     * @param readCount titles to mark {@code READ}
     * @param ageMonths months to backdate {@code created_at} by; 0 leaves it at now
     */
    private String seedUser(int readCount, int ageMonths) {
        String userId = "revoke-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Revocation fixture";
            em.persist(user);

            for (int i = 0; i < readCount; i++) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Revoke " + UUID.randomUUID();
                em.persist(work);

                Edition edition = new Edition();
                edition.work = work;
                em.persist(edition);

                LibraryItem item = new LibraryItem();
                item.userId = userId;
                item.edition = edition;
                item.status = LibraryStatus.READ;
                em.persist(item);
            }
        });

        if (ageMonths > 0) {
            // created_at carries a database default and is not insertable through JPA, so the only
            // way to give a fixture a past is a native update after it is provisioned.
            OffsetDateTime backdated = OffsetDateTime.now().minusMonths(ageMonths).minusDays(1);
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("update app_user set created_at = ?1 where id = ?2")
                            .setParameter(1, backdated)
                            .setParameter(2, userId)
                            .executeUpdate());
        }
        return userId;
    }

    /** Files an upheld report by {@code reporterId} against {@code contributorId}'s contribution. */
    private void upheldReport(String reporterId, String contributorId) {
        report(reporterId, contributorId, ReportStatus.UPHELD);
    }

    /** Files an unreviewed (open) report — the kind the revocation must ignore. */
    private void openReport(String reporterId, String contributorId) {
        report(reporterId, contributorId, ReportStatus.OPEN);
    }

    private void report(String reporterId, String contributorId, ReportStatus status) {
        QuarkusTransaction.requiringNew().run(() -> {
            Report report = new Report();
            report.reporterId = reporterId;
            report.contributorId = contributorId;
            // target_id carries no foreign key and the revocation never resolves it, so a bare
            // identifier stands in for the contributed object the report would point at.
            report.targetType = ReportTargetType.WORK;
            report.targetId = UUID.randomUUID();
            report.reason = ReportReason.WRONG_INFO;
            report.status = status;
            em.persist(report);
        });
    }

    private boolean trusted(String userId) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(AppUser.class, userId).trusted);
    }

    private OffsetDateTime trustedAt(String userId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> em.find(AppUser.class, userId).trustedAt);
    }
}
