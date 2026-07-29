package zelytra.librarius.domain;

/**
 * State of an owned title.
 *
 * <p>Stored as its own name in {@code library_item.status}, a bare {@code VARCHAR(16)}:
 * there is no database enum and no {@code CHECK} constraint behind it, so a value added
 * here is storable without a schema change.
 */
public enum LibraryStatus {
    /** Owned, never opened. */
    OWNED,
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
    ABANDONED
}
