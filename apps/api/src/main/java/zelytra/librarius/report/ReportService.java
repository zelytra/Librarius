package zelytra.librarius.report;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Report;
import zelytra.librarius.domain.ReportReason;
import zelytra.librarius.domain.ReportTargetType;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.ReportRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.WorkRepository;

import java.util.UUID;

/**
 * Records a member's report against a shared catalog object (#192).
 *
 * <p>One job: validate that the flagged object exists, then persist the report. Nothing reads
 * a report back — the revocation consumer (#195) and any admin view are out of scope here.
 *
 * <p><strong>Why the target is checked in code.</strong> {@code report.target_id} points at
 * one of {@code work}, {@code edition} or {@code series} depending on {@code target_type}, so
 * no single foreign key can guard it. The check below is what a real FK would have given for
 * free, and it is what makes an unknown target a 400 (via {@link UnknownReportTargetException})
 * rather than a report filed against nothing.
 */
@ApplicationScoped
public class ReportService {

    @Inject
    ReportRepository reports;

    @Inject
    WorkRepository works;

    @Inject
    EditionRepository editions;

    @Inject
    SeriesRepository series;

    /**
     * Files a report on behalf of {@code reporterId}.
     *
     * @throws UnknownReportTargetException when no catalog object of {@code targetType} carries
     *                                      {@code targetId} — surfaced as a 400
     */
    @Transactional
    public Report create(String reporterId, ReportTargetType targetType, UUID targetId,
            ReportReason reason, String comment) {
        if (!targetExists(targetType, targetId)) {
            throw new UnknownReportTargetException(targetType, targetId);
        }
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.comment = blankToNull(comment);
        reports.persist(report);
        return report;
    }

    private boolean targetExists(ReportTargetType targetType, UUID targetId) {
        return switch (targetType) {
            case WORK -> works.findById(targetId) != null;
            case EDITION -> editions.findById(targetId) != null;
            case SERIES -> series.findById(targetId) != null;
        };
    }

    /** A blank comment is stored as nothing, the same rule the review text follows. */
    private static String blankToNull(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
