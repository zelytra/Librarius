import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import { Loading } from '../../shared/ui/states';
import { useGetApiAuthors } from '../../api/generated/librarius';
import { initials } from './author';
import styles from './AuthorSearch.module.css';

/** Delay before a keystroke turns into a request — same debounce as the Collection search. */
const DEBOUNCE_MS = 300;

/**
 * A minimal author discovery entry point: `GET /api/authors?q=` local to Discover, as the
 * issue asks for — this does not redesign navigation or the bottom nav, it is one field and
 * a result list leading into the Author page.
 */
export function AuthorSearch() {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const [term, setTerm] = useState('');

  useEffect(() => {
    const handle = setTimeout(() => setTerm(input.trim()), DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input]);

  const { data: results = [], isFetching: loading } = useGetApiAuthors(
    { q: term },
    { query: { enabled: term.length > 0 } },
  );

  return (
    <section className={styles.section}>
      <h3 className={styles.title}>{t('author.search.title')}</h3>
      <div className={styles.searchBar}>
        <Icon name="search" size={19} color="var(--faint)" />
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={t('author.search.placeholder')}
          aria-label={t('author.search.placeholder')}
          className={styles.searchInput}
        />
      </div>

      {loading && <Loading size="compact" />}

      {term.length > 0 && !loading && results.length === 0 && (
        <p className={styles.empty}>{t('author.search.noResults')}</p>
      )}

      {results.length > 0 && (
        <ul className={styles.results}>
          {results.map((a) => (
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
                  <span className={styles.count}>
                    {t('author.search.workCount', { count: a.workCount })}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
