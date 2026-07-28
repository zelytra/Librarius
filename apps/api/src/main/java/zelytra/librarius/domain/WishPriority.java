package zelytra.librarius.domain;

/**
 * Priority of a wish, from the most urgent to the least.
 *
 * <p>The urgency is carried by an explicit {@link #rank} rather than left to the stored
 * name or to {@link #ordinal()}: the wishlist is ordered by it, and ordering on the name
 * yields {@code PRIORITY, SOMEDAY, SOON} — alphabetically right and the exact opposite of
 * what the user means. A new priority therefore only has to be declared here, with the
 * rank it deserves.
 */
public enum WishPriority {

    /** Next purchase — what the user intends to buy now. */
    PRIORITY(0),

    /** Wanted in the near future. */
    SOON(1),

    /** Kept in mind, with no date attached. */
    SOMEDAY(2);

    /** Urgency of the wish: the lower the rank, the sooner the user wants the book. */
    public final int rank;

    WishPriority(int rank) {
        this.rank = rank;
    }
}
