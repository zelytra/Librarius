package zelytra.librarius.domain;

import java.time.LocalDate;

/**
 * How much of an announced release date is actually known.
 *
 * <p>A publisher announces "mars 2027" far more often than "12 mars 2027", and the date is
 * stored on the first day of the window it opens. Without this, the stored anchor would be
 * indistinguishable from a real day and every screen would print a precision the data never
 * had.
 */
public enum DatePrecision {

    DAY,
    MONTH,
    QUARTER,
    YEAR;

    /**
     * Last day still covered by the announcement.
     *
     * <p>What tells "already out" from "still ahead": a volume announced for March 2027 is
     * ahead for the whole of March, not until the 1st.
     *
     * @param anchor first day of the window, as stored
     */
    public LocalDate windowEnd(LocalDate anchor) {
        return switch (this) {
            case DAY -> anchor;
            case MONTH -> anchor.withDayOfMonth(1).plusMonths(1).minusDays(1);
            case QUARTER -> anchor.withDayOfMonth(1).plusMonths(3).minusDays(1);
            case YEAR -> anchor.withDayOfYear(1).plusYears(1).minusDays(1);
        };
    }
}
