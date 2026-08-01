package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Report;
import zelytra.librarius.domain.ReportStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistence of {@link Report}. Nothing reads a report back for a caller — no endpoint does
 * (#192) — but the automatic trust revocation (#195) reads the rows in aggregate, off the
 * request path, through {@link #countUpheldContributionsSince}.
 */
@ApplicationScoped
public class ReportRepository implements PanacheRepositoryBase<Report, UUID> {

    /**
     * How many reports upheld against {@code contributorId}'s contributions were filed since
     * {@code since} — the negative signal the trust revocation reads (#195).
     *
     * <p>Only {@link ReportStatus#UPHELD} counts: a moderator-confirmed report, not an unreviewed
     * {@code OPEN} one, so filing reports is never a way to strip a stranger of their trust. The
     * {@code since} bound makes it a rolling window — an old, since-outlived strike stops weighing
     * on a fresh evaluation.
     */
    public long countUpheldContributionsSince(String contributorId, OffsetDateTime since) {
        return count("contributorId = ?1 and status = ?2 and createdAt >= ?3",
                contributorId, ReportStatus.UPHELD, since);
    }
}
