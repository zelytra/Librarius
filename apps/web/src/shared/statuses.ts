/**
 * The two status families of a title (#411), as every screen offers them.
 *
 * <p>The reading status says where the reader stands in the book; the ownership whether
 * they have it, and in what form. One list each, in the order the pickers show them, so the
 * Detail screen, the add sheet and the scan read the same everywhere.
 */

export type ReadingStatus = 'TO_READ' | 'READING' | 'READ' | 'ABANDONED';
export type Ownership = 'OWNED' | 'BORROWED' | 'EBOOK' | 'AUDIOBOOK' | 'WISHLIST' | 'NOT_OWNED';

export const READING_STATUSES: { id: ReadingStatus; icon: string }[] = [
  { id: 'TO_READ', icon: 'library_books' },
  { id: 'READING', icon: 'auto_stories' },
  { id: 'READ', icon: 'check_circle' },
  { id: 'ABANDONED', icon: 'block' },
];

export const OWNERSHIPS: { id: Ownership; icon: string }[] = [
  { id: 'OWNED', icon: 'collections_bookmark' },
  { id: 'BORROWED', icon: 'handshake' },
  { id: 'EBOOK', icon: 'tablet_android' },
  { id: 'AUDIOBOOK', icon: 'headphones' },
  { id: 'WISHLIST', icon: 'favorite' },
  { id: 'NOT_OWNED', icon: 'visibility_off' },
];

/** The format of the reader's copy (#447), optional. */
export type BookFormat = 'POCKET' | 'PAPERBACK' | 'HARDCOVER' | 'LARGE' | 'DELUXE' | 'BOX_SET';

export const FORMATS: { id: BookFormat; icon: string }[] = [
  { id: 'POCKET', icon: 'book_2' },
  { id: 'PAPERBACK', icon: 'menu_book' },
  { id: 'HARDCOVER', icon: 'auto_stories' },
  { id: 'LARGE', icon: 'library_books' },
  { id: 'DELUXE', icon: 'workspace_premium' },
  { id: 'BOX_SET', icon: 'collections_bookmark' },
];

/** The kind a reader files a title under (#454). */
export type BookKind = 'BOOK' | 'COMIC' | 'MANGA';

export const KINDS: { id: BookKind; icon: string }[] = [
  { id: 'BOOK', icon: 'menu_book' },
  { id: 'COMIC', icon: 'collections_bookmark' },
  { id: 'MANGA', icon: 'auto_stories' },
];

export const kindLabelKey = (kind: string | undefined) => `statuses.kind.${kind}`;

export const formatLabelKey = (format: string | undefined) => `statuses.format.${format}`;

/** i18n key of a reading status's label. */
export const statusLabelKey = (status: string | undefined) => `statuses.reading.${status ?? 'TO_READ'}`;

/** i18n key of an ownership's label. */
export const ownershipLabelKey = (ownership: string | undefined) => `statuses.ownership.${ownership ?? 'OWNED'}`;

/**
 * Where a new title goes: a plain wish when it is wanted and not read in any way — the
 * wishlist with its priority and price, as before — and the library otherwise, with both
 * statuses (#411).
 */
export function addsToWishlistOnly(status: ReadingStatus, ownership: Ownership): boolean {
  return ownership === 'WISHLIST' && status === 'TO_READ';
}
