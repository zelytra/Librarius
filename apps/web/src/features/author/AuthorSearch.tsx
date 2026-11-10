import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { colorFor } from '../../shared/ui/coverPalette';
import { useGetApiAuthors } from '../../api/generated/librarius';
import { initials } from './author';
import styles from './AuthorSearch.module.css';

/**
 * The authors a Discover search names, above the titles it found: `GET /api/authors?q=` on
 * the same words as the catalog search.
 *
 * It used to be a second search field of its own under the results, which readers took for
 * part of the page footer: someone looking for "Hugo" typed it in the main bar, as for
 * anything else, and was shown his books but never him. One bar now answers both, and the
 * authors only take room when there are some.
 */
export function AuthorMatches({ term }: { term: string }) {
  const { t } = useTranslation();
  const q = term.trim();
  const { data: results = [] } = useGetApiAuthors({ q }, { query: { enabled: q.length > 1 } });

  if (q.length <= 1 || results.length === 0) return null;
  return (
    <section className={styles.section} aria-label={t('author.search.matches')}>
      <h3 className={styles.title}>{t('author.search.matches')}</h3>
      <ul className={styles.results}>
        {results.slice(0, 6).map((a) => (
          <li key={a.id}>
            <Link to={`/authors/${a.id}`} className={styles.result}>
              <span
                className={styles.avatar}
                style={
                  a.photoUrl
                    ? { background: `center/cover no-repeat url(${a.photoUrl})` }
                    : { background: colorFor(a.name ?? '') }
                }
              >
                {!a.photoUrl && initials(a.name ?? '')}
              </span>
              <span className={styles.name}>{a.name}</span>
              {a.workCount != null && a.workCount > 0 && (
                <span className={styles.count}>{t('author.search.workCount', { count: a.workCount })}</span>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
