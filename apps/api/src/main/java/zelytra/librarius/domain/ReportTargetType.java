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
    SERIES
}
