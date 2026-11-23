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

    @Inject
    zelytra.librarius.domain.repository.LibraryItemRepository items;

    @Inject
    zelytra.librarius.domain.repository.SeriesReviewRepository seriesReviews;

    @Inject
    zelytra.librarius.domain.repository.AppUserRepository users;

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
            // A review can be reported only once it is shared: a private one is read by nobody.
            case REVIEW -> items.find("id = ?1 and reviewShared = true", targetId).count() > 0;
            case SERIES_REVIEW -> seriesReviews.find("id = ?1 and shared = true", targetId).count() > 0;
        };
    }

    /** The reports, newest first, with what each targets and who filed it (#621). */
    @Transactional
    public zelytra.librarius.web.ApiDtos.AdminReportPageDto list(zelytra.librarius.domain.ReportStatus status,
            zelytra.librarius.web.PageRequest window) {
        var query = status == null
                ? reports.find("order by createdAt desc, id")
                : reports.find("status = ?1 order by createdAt desc, id", status);
        var items = query.page(window.page(), window.size()).list().stream().map(this::toAdminDto).toList();
        return new zelytra.librarius.web.ApiDtos.AdminReportPageDto(items, window.page(), window.size(), query.count());
    }

    /** Reports nobody has upheld or dismissed yet (#635). */
    public long countOpen() {
        return reports.count("status", zelytra.librarius.domain.ReportStatus.OPEN);
    }

    /** False for an unknown report. */
    @Transactional
    public boolean setStatus(UUID id, zelytra.librarius.domain.ReportStatus status) {
        Report report = reports.findById(id);
        if (report == null) {
            return false;
        }
        report.status = status;
        return true;
    }

    private zelytra.librarius.web.ApiDtos.AdminReportDto toAdminDto(Report r) {
        String title = null;
        String detail = null;
        UUID seriesId = null;
        switch (r.targetType) {
            case WORK -> {
                var work = works.findById(r.targetId);
                if (work != null) {
                    title = work.title;
                    detail = work.authorsText;
                }
            }
            case EDITION -> {
                var edition = editions.findById(r.targetId);
                if (edition != null) {
                    title = edition.work.title;
                    detail = edition.publisher;
                }
            }
            case SERIES -> {
                var s = series.findById(r.targetId);
                if (s != null) {
                    title = s.title;
                    seriesId = s.id;
                }
            }
            case REVIEW -> {
                var item = items.findById(r.targetId);
                if (item != null) {
                    title = item.edition.work.title;
                    detail = item.review;
                }
            }
            case SERIES_REVIEW -> {
                var review = seriesReviews.findById(r.targetId);
                var reviewed = review == null ? null : series.findById(review.seriesId);
                if (reviewed != null) {
                    title = reviewed.title;
                    detail = review.review;
                    seriesId = reviewed.id;
                }
            }
        }
        var reporter = users.findById(r.reporterId);
        return new zelytra.librarius.web.ApiDtos.AdminReportDto(r.id, r.targetType.name(), r.targetId, title, detail,
                seriesId, r.reason.name(), r.comment, r.status.name(),
                reporter == null ? null : zelytra.librarius.web.ApiDtos.MemberSummaryDto.of(reporter), r.createdAt);
    }

    /** A blank comment is stored as nothing, the same rule the review text follows. */
    private static String blankToNull(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
