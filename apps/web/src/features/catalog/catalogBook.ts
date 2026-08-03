import { type CatalogResult, type ManualBookDto } from '../../api/generated/librarius';
import { knownKind } from '../discover/medium';

/**
 * The URL a catalog result opens at — distinct per record, so browser history and a shared
 * link behave. The data itself rides in navigation state; the path only has to be stable and
 * unique. Lives here, next to {@link toBook}, so the search list can link to a fiche without
 * pulling the fiche's own (lazily loaded) module into its bundle.
 */
export function catalogPath(result: CatalogResult): string {
  const provider = encodeURIComponent(result.provider ?? 'catalog');
  const ref = encodeURIComponent(result.providerRef ?? result.title ?? '—');
  return `/catalog/${provider}/${ref}`;
}

/**
 * A catalog result as the API takes it when the title is added to a library. `provider` and
 * `providerRef` say which record it came from: without them the server cannot tell a title
 * picked from the catalog from one typed by hand, and no provider can be asked about it again
 * later. The kind is the result's own — a mixed feed carries no screen-wide default to fall
 * back on.
 *
 * <p>Shared by the search list and the catalog detail page, so a title added from either
 * lands in the collection the same way.
 */
export function toBook(r: CatalogResult): ManualBookDto {
  return {
    kind: knownKind(r.kind),
    title: r.title ?? '—',
    authors: r.authors,
    seriesTitle: r.seriesTitle,
    volumeNumber: r.volumeNumber,
    coverUrl: r.coverUrl,
    synopsis: r.synopsis,
    isbn13: r.isbn13,
    publisher: r.publisher,
    language: r.language,
    pageCount: r.pageCount,
    originalYear: r.year,
    releaseDate: r.releaseDate,
    provider: r.provider,
    providerRef: r.providerRef,
  };
}
