package zelytra.librarius.domain;

/**
 * How firm an announced release date is.
 *
 * <p>Distinct from {@link DatePrecision}, which says how <em>precise</em> the date is: a
 * publisher can confirm a month ({@code CONFIRMED} / {@code MONTH}) and a provider can be
 * precise to the day about a date nobody has committed to ({@code ESTIMATED} / {@code DAY}).
 * The interface says so, rather than presenting both as facts.
 */
public enum ReleaseConfidence {

    /** Announced by the publisher, or read off an edition already catalogued. */
    CONFIRMED,
    /** Deduced — a provider's projection, or a publication rhythm. */
    ESTIMATED
}
