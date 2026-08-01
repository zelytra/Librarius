import { Kind } from '../../api/generated/librarius';

/**
 * The whole taxonomy Discover lets a caller search, filter or enter by hand — the medium
 * filter's options, `ManualAddForm`'s own picker, and the badge a mixed result feed shows
 * next to every hit all read this one list and the label map below it. A second, private
 * copy of the same set lives in `CollectionPage.ALL_KINDS`: that one narrows to the kinds a
 * user actually owns something of, a rule this feature has no use for, so nothing here is
 * shared beyond the taxonomy itself. Growing it again only means adding a value here and to
 * `KIND_LABEL_KEY`; nothing else in this feature names a kind.
 */
export const ALL_KINDS: Kind[] = [Kind.BOOK, Kind.MANGA, Kind.COMIC, Kind.GRAPHIC_NOVEL, Kind.AUDIOBOOK];

/** i18n key of the label for each kind. */
export const KIND_LABEL_KEY: Record<Kind, string> = {
  BOOK: 'discover.kinds.book',
  MANGA: 'discover.kinds.manga',
  COMIC: 'discover.kinds.comic',
  GRAPHIC_NOVEL: 'discover.kinds.graphicNovel',
  AUDIOBOOK: 'discover.kinds.audiobook',
};

/**
 * Narrows a free-form kind coming from the API to a known one, `BOOK` when it is missing or
 * unrecognised. `CatalogResult.kind` is typed as an optional string and is not expected to
 * be empty in practice, but a mixed feed carries no screen-wide default left to fall back on
 * instead — this is the only fallback a result's own kind now has.
 */
export function knownKind(kind: string | undefined): Kind {
  return (ALL_KINDS as string[]).includes(kind ?? '') ? (kind as Kind) : Kind.BOOK;
}
