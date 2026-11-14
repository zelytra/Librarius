import { useTranslation } from 'react-i18next';
import { useGetApiAuthorsIdExternal, useGetApiWorksIdExternal } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import styles from './ExternalOpinions.module.css';

/**
 * What other communities think of a title (#453) — Open Library's readers, AniList's score
 * for a manga — each credited and linked to its source, next to the members' own reviews.
 * Hidden when no source could be matched to the title.
 */
export function ExternalRatings({ workId }: { workId: string | undefined }) {
  const { t } = useTranslation();
  const { data: ratings = [] } = useGetApiWorksIdExternal(workId ?? '', { query: { enabled: Boolean(workId) } });
  if (ratings.length === 0) return null;
  const locale = activeLanguage();
  const number = (n: number) => n.toLocaleString(locale);

  return (
    <section className={styles.section} aria-label={t('external.title')}>
      <h4 className={styles.title}>{t('external.title')}</h4>
      <ul className={styles.list}>
        {ratings.map((rating) => {
          const facts: string[] = [];
          if (rating.average != null) {
            facts.push(t('external.average', {
              average: rating.average.toLocaleString(locale, { maximumFractionDigits: 1 }),
            }));
          }
          if (rating.count != null) facts.push(t('external.count', { count: rating.count, n: number(rating.count) }));
          if (rating.readers != null) facts.push(t('external.readers', { count: rating.readers, n: number(rating.readers) }));
          if (rating.wanted != null) facts.push(t('external.wanted', { count: rating.wanted, n: number(rating.wanted) }));
          return (
            <li key={rating.source} className={styles.item}>
              {rating.url ? (
                <a className={styles.source} href={rating.url} target="_blank" rel="noopener noreferrer">
                  {rating.source}
                </a>
              ) : (
                <span className={styles.source}>{rating.source}</span>
              )}
              <span className={styles.facts}>{facts.join(' · ')}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/** The opening of the author's encyclopedia article (#453), with its source and licence. */
export function ExternalAuthor({ authorId }: { authorId: string }) {
  const { t } = useTranslation();
  const { data = [] } = useGetApiAuthorsIdExternal(authorId);
  const article = data[0];
  if (!article?.extract) return null;
  return (
    <section className={styles.section} aria-label={t('external.about')}>
      <p className={styles.extract}>{article.extract}</p>
      <p className={styles.credit}>
        {article.url ? (
          <a href={article.url} target="_blank" rel="noopener noreferrer">
            {t('external.credit', { source: article.source, licence: article.licence })}
          </a>
        ) : (
          t('external.credit', { source: article.source, licence: article.licence })
        )}
      </p>
    </section>
  );
}
