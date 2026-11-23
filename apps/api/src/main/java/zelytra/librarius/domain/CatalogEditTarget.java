package zelytra.librarius.domain;

/**
 * What a {@link CatalogEdit} changes (#618). An {@code EDITION} proposal also carries the
 * fields of the work behind it — a reader edits "the book", one screen, whatever table each
 * field lives in.
 */
public enum CatalogEditTarget {
    EDITION,
    SERIES
}
