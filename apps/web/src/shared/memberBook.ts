import type { CatalogResult, MemberBookDto } from '../api/generated/librarius';

/**
 * Where a book seen on someone else's profile or in the feed leads (#513): the reader's own
 * Detail when they hold the work too, otherwise the catalog page — which shows the book from
 * what it is handed and offers to add it, the work screen itself being for its owners only.
 */
export function bookTarget(book: MemberBookDto): { to: string; state?: { result: CatalogResult } } {
  if (book.myItemId) return { to: `/detail/${book.myItemId}` };
  const result: CatalogResult = {
    kind: book.kind,
    title: book.title,
    authors: book.authors,
    year: book.year,
    coverUrl: book.coverUrl,
    synopsis: book.synopsis,
    isbn13: book.isbn13,
    publisher: book.publisher,
    seriesTitle: book.seriesTitle,
    volumeNumber: book.volumeNumber,
    provider: book.provider,
    providerRef: book.providerRef,
  };
  const provider = book.provider ?? 'librarius';
  const ref = book.providerRef ?? book.editionId ?? '';
  return { to: `/catalog/${encodeURIComponent(provider)}/${encodeURIComponent(ref)}`, state: { result } };
}
