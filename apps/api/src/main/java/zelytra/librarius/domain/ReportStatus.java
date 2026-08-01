package zelytra.librarius.domain;

/**
 * Lifecycle of a {@link Report}. Every report is created {@link #OPEN}; nothing in this issue
 * moves it further.
 *
 * <p>The column exists for what consumes a report later — the automatic revocation (#195) and
 * a possible admin view — rather than for a screen this issue builds. {@link #DISMISSED} is the
 * one transition that milestone names (a way to wave a wrong report away); it is declared here
 * so the column has a meaning beyond a single constant, not written by any code yet.
 */
public enum ReportStatus {
    OPEN,
    DISMISSED
}
