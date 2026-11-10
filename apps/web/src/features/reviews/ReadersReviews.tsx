import { useTranslation } from 'react-i18next';
import type { SharedReviewsDto } from '../../api/generated/librarius';
import { ReportButton } from '../report/ReportButton';
import { activeLanguage } from '../../i18n/languages';
import { colorFor } from '../../shared/ui/coverPalette';
import { initials } from '../author/author';
import styles from './ReadersReviews.module.css';

/**
 * What other readers shared about a book or a series (#207): their average, and their reviews
 * newest first. Only the readers whose activity the caller can see are here — the API has
 * gated them (#206) — so an empty list is an invitation, never an error.
 */
export function ReadersReviews({
  data,
  loading,
  kind = 'REVIEW',
}: {
  data: SharedReviewsDto | undefined;
  loading?: boolean;
  /** What a report targets: a book review, or a series review (#208). */
  kind?: 'REVIEW' | 'SERIES_REVIEW';
}) {
  const { t } = useTranslation();
  if (loading) return null;
  const reviews = data?.reviews ?? [];
  const locale = activeLanguage();

  return (
    <section className={styles.section} aria-label={t('reviews.title')}>
      <h3 className={styles.title}>
        {t('reviews.title')}
        {data?.average != null && (
          <span className={styles.average}>
            {t('reviews.average', {
              average: data.average.toLocaleString(locale, { maximumFractionDigits: 1 }),
              count: data.count ?? reviews.length,
            })}
          </span>
        )}
      </h3>
      {reviews.length === 0 ? (
        <p className={styles.empty}>{t('reviews.empty')}</p>
      ) : (
        <ul className={styles.list}>
          {reviews.map((review) => {
            const name = review.authorName ?? '—';
            return (
              <li key={review.id} className={styles.item}>
                <span className={styles.avatar} style={{ background: colorFor(name) }} aria-hidden="true">
                  {initials(name)}
                </span>
                <div className={styles.body}>
                  <div className={styles.head}>
                    <span className={styles.name}>{name}</span>
                    {review.rating != null && (
                      <span className={styles.stars} aria-label={t('reviews.stars', { rating: review.rating })}>
                        {'★'.repeat(review.rating)}
                        <span className={styles.starsOff}>{'★'.repeat(5 - review.rating)}</span>
                      </span>
                    )}
                  </div>
                  {review.review && <p className={styles.text}>{review.review}</p>}
                  {review.id && <ReportButton targetType={kind} targetId={review.id} />}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
