package zelytra.librarius.domain;

/**
 * What a {@link Report} points at: one of the three shared catalog objects a member can flag
 * an error in.
 *
 * <p>The value decides which table {@code report.target_id} is resolved against — there is no
 * foreign key spanning the three, so a report of an unknown target is refused by the service
 * before it is stored. The set is deliberately closed to the shared catalog: a
 * {@code library_item} is one user's private ownership row, not something another reader could
 * be wrong about.
 */
public enum ReportTargetType {
    WORK,
    EDITION,
    SERIES,
    /**
     * A shared title review (#208) — {@code target_id} is its {@code library_item}. An opinion,
     * not a catalog fact: never weighed by the trust evaluation.
     */
    REVIEW,
    /** A shared series review (#208) — {@code target_id} is its {@code series_review}. */
    SERIES_REVIEW
}
