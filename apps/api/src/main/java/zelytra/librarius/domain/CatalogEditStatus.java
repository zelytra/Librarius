package zelytra.librarius.domain;

/** Where a {@link CatalogEdit} stands (#618). */
public enum CatalogEditStatus {
    /** Waiting for an admin. */
    PENDING,
    /** Written to the catalog — by an admin, or at once when a store page confirmed it (#620). */
    APPLIED,
    /** Refused by an admin; nothing was written. */
    REJECTED,
    /** Applied, then undone by an admin: the values it replaced are back. */
    REVERTED
}
