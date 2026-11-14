package zelytra.librarius.domain;

/**
 * The format of the reader's copy (#447), picked like the reading status and the ownership.
 * Stored as its name in {@code library_item.format} (V32), {@code NULL} when unsaid.
 */
public enum BookFormat {
    /** Poche. */
    POCKET,
    /** Broché. */
    PAPERBACK,
    /** Relié. */
    HARDCOVER,
    /** Grand format. */
    LARGE,
    /** Collector, édition de luxe. */
    DELUXE,
    /** Coffret, intégrale. */
    BOX_SET
}
