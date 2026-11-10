/**
 * Where a title found in the catalog can go, picked at the moment it is added: read already,
 * onto the pile of books owned but not read yet, or onto the wishlist. Adding used to file
 * every title as owned-and-unread, and a book read years ago then needed a second trip to
 * its page to say so.
 */
export type AddTarget = 'READ' | 'OWNED' | 'WISHLIST';

export const ADD_TARGETS: { id: AddTarget; labelKey: string; addedKey: string; icon: string }[] = [
  { id: 'READ', labelKey: 'add.targets.READ', addedKey: 'add.added.READ', icon: 'check_circle' },
  { id: 'OWNED', labelKey: 'add.targets.OWNED', addedKey: 'add.added.OWNED', icon: 'library_books' },
  { id: 'WISHLIST', labelKey: 'add.targets.WISHLIST', addedKey: 'add.added.WISHLIST', icon: 'favorite' },
];

export function addedKeyOf(target: AddTarget): string {
  return ADD_TARGETS.find((t) => t.id === target)!.addedKey;
}
