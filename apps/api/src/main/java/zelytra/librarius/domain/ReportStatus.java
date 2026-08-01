package zelytra.librarius.domain;

/**
 * Lifecycle of a {@link Report}. Every report is created {@link #OPEN}; a moderation review
 * either waves it away as {@link #DISMISSED} or confirms it as {@link #UPHELD}.
 *
 * <p>The automatic trust revocation (#195) reads only {@link #UPHELD}: a confirmed report is the
 * one that counts against the account whose contribution it flags, so an unreviewed {@code OPEN}
 * report cannot on its own cost anyone their trust, and a filed report is never a weapon.
 *
 * <p><strong>Nothing writes {@link #UPHELD} yet.</strong> Moving a report there is a moderation
 * action behind an admin surface a maintainer must set up (a Keycloak-gated role), deliberately
 * not built here — see the migration {@code V19__report_contributor.sql}. Until it exists the
 * revocation signal is always zero and the evaluator only ever grants, as before.
 */
public enum ReportStatus {

    /** Filed, not yet reviewed. The value every report is created with. */
    OPEN,

    /** Reviewed and waved away: the report was wrong, and it counts against nobody. */
    DISMISSED,

    /** Reviewed and confirmed: the report counts against the account that contributed the object. */
    UPHELD
}
