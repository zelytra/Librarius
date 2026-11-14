import { Link } from 'react-router';
import { useGetApiAuthors } from '../../api/generated/librarius';
import { splitAuthorNames } from './author';
import styles from './AuthorNames.module.css';

/**
 * One name out of a credit line, resolved to the shared catalog through `GET /api/authors?q=`.
 *
 * <p>`BookView`/`AuthorWorkDto.authors` is the raw credit line, not a list of identifiers —
 * the API keeps `work_author` for its own matching, not for the client to walk. Searching
 * each name is the same local table `AuthorService.search` reads, so a name that resolves
 * here is the very row the Author page opens; a name with no match — a spelling the shared
 * catalog folds differently, or a work recorded before #182 backfilled it — stays plain text
 * rather than a link that would 404.
 */
function AuthorNameLink({ name, searchFallback }: { name: string; searchFallback?: boolean }) {
  const { data: matches = [] } = useGetApiAuthors({ q: name });
  const needle = name.trim().toLowerCase();
  const match = matches.find((a) => (a.name ?? '').trim().toLowerCase() === needle);
  if (!match?.id) {
    // From a catalog result (#443), a name the shared catalog does not know yet still leads
    // somewhere: a search on that author's books.
    if (!searchFallback) return <>{name}</>;
    return (
      <Link
        to={`/discover?author=${encodeURIComponent(name)}`}
        className={styles.link}
        onClick={(e) => e.stopPropagation()}
      >
        {name}
      </Link>
    );
  }
  // A credit line often sits in the caption of a clickable cover, whose own `onClick` opens
  // the title. Stopping the click here — not preventing it — lets the link reach the author
  // page without the cover underneath also firing and winning the navigation. Where there is
  // no such parent (the Author page's own header), stopping a click that bubbles nowhere is
  // harmless.
  return (
    <Link
      to={`/authors/${match.id}`}
      className={styles.link}
      onClick={(e) => e.stopPropagation()}
    >
      {name}
    </Link>
  );
}

/**
 * A credit line, each name linked to its author page when it resolves to a known one — and,
 * with `searchFallback`, to a search on that author when it does not (#443). `limit` keeps the
 * first names of a long anthology credit.
 */
export function AuthorNames({ text, searchFallback, limit }: { text?: string; searchFallback?: boolean; limit?: number }) {
  const names = splitAuthorNames(text).slice(0, limit ?? Infinity);
  if (names.length === 0) return null;
  return (
    <>
      {names.map((name, index) => (
        <span key={`${name}-${index}`}>
          {index > 0 && ', '}
          <AuthorNameLink name={name} searchFallback={searchFallback} />
        </span>
      ))}
    </>
  );
}
