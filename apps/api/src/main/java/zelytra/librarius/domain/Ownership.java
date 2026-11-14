package zelytra.librarius.domain;

/**
 * Whether the reader has the book, and in what form (#411): the second status family, next
 * to the reading status of {@link LibraryStatus}.
 *
 * <p>Stored as its name in {@code library_item.ownership} (V27), a {@code VARCHAR(16)} with
 * no database enum behind it, like the reading status.
 */
public enum Ownership {
    /** A physical copy on the reader's own shelves. The only form the library value counts. */
    OWNED,
    /** Had in hand — a library, a friend — but not on the shelves. */
    BORROWED,
    /** Read on an e-reader or another digital format. */
    EBOOK,
    /** Listened to as an audiobook (#455). */
    AUDIOBOOK,
    /**
     * Not had, and wanted: the edition also sits on the wishlist, which keeps its priority
     * and price. Reached on a title already in the library, e.g. read from a borrowed copy.
     */
    WISHLIST,
    /** Not had, in any form. */
    NOT_OWNED
}
