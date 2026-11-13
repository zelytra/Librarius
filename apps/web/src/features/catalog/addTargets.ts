import type { Ownership, ReadingStatus } from '../../shared/statuses';
import { addsToWishlistOnly } from '../../shared/statuses';

/**
 * Where a title found in the catalog goes, picked when it is added (#411): its reading status
 * and whether the reader has it. The default is the most common case — a book just bought,
 * owned and on the to-read pile.
 */
export interface AddTarget {
  status: ReadingStatus;
  ownership: Ownership;
}

export const DEFAULT_ADD_TARGET: AddTarget = { status: 'TO_READ', ownership: 'OWNED' };

/** The message a screen shows once the title is filed. */
export function addedKeyOf(target: AddTarget): string {
  if (addsToWishlistOnly(target.status, target.ownership)) return 'add.added.WISHLIST';
  if (target.status === 'READ') return 'add.added.READ';
  return target.status === 'TO_READ' ? 'add.added.TO_READ' : 'add.added.LIBRARY';
}
