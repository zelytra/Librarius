package zelytra.librarius.report;

import zelytra.librarius.domain.ReportTargetType;

import java.util.UUID;

/**
 * Raised when a report names a target that no catalog object carries. The resource turns it
 * into a 400: reporting an unknown {@code target_id} must fail loudly, not succeed silently
 * (#192 acceptance criteria).
 */
public class UnknownReportTargetException extends RuntimeException {

    public UnknownReportTargetException(ReportTargetType targetType, UUID targetId) {
        super("No " + targetType + " carries the identifier " + targetId);
    }
}
