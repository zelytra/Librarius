package zelytra.librarius.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Reading status of a title in the library (#411), one of the two status families: where the
 * reader stands in the book. Whether they have the book at all is {@link Ownership}.
 *
 * <p>Stored as its own name in {@code library_item.status}, a bare {@code VARCHAR(16)}:
 * there is no database enum and no {@code CHECK} constraint behind it, so a value added
 * here is storable without a schema change.
 */
public enum LibraryStatus {
    /**
     * On the to-read pile, never opened. Stored as {@code OWNED} until V27, which renamed it
     * once ownership became a family of its own; {@link #fromString} still reads the old
     * name, so a query string or an export written before keeps working.
     */
    @JsonAlias("OWNED")
    TO_READ,
    /** Being read. */
    READING,
    /** Read to the end. */
    READ,
    /**
     * Given up on partway through.
     *
     * <p>Not a variety of {@code READ}: the reading position is whatever the reader
     * actually reached, and the title counts towards no annual goal and no timeline
     * bucket. It carries a {@code finished_at} all the same — the day tracking stopped —
     * which is why every aggregation counted from that column filters this status out
     * rather than trusting the date alone.
     */
    ABANDONED;

    /**
     * The status a query parameter names, case-insensitively, the pre-V27 {@code OWNED}
     * included. JAX-RS prefers this to {@code valueOf} for a {@code @QueryParam}.
     *
     * @throws IllegalArgumentException on a name that is no status
     */
    public static LibraryStatus fromString(String value) {
        String name = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return "OWNED".equals(name) ? TO_READ : valueOf(name);
    }
}
