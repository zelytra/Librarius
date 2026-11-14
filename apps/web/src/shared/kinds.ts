import { Kind } from '../api/generated/librarius';

/** The kinds offered, in the order of the library's switch. */
export const KINDS: { id: Kind; labelKey: string }[] = [
  { id: Kind.BOOK, labelKey: 'collection.kinds.book' },
  { id: Kind.MANGA, labelKey: 'collection.kinds.manga' },
  { id: Kind.COMIC, labelKey: 'collection.kinds.comic' },
];

