import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import styles from './StarRating.module.css';

/** The rating is out of five, like everywhere the app shows one. */
const STARS = [1, 2, 3, 4, 5];

/**
 * A rating out of five, and the way back from one given by mistake: clicking the star
 * already in force clears the rating instead of setting it again.
 *
 * <p>The value is not held here, because its two callers hold it differently — the review
 * section saves every click, the end-of-reading sheet keeps a draft until it is confirmed.
 * Both therefore show the reader the same five stars, with the same labels, rather than two
 * near-identical rows drifting apart.
 */
export function StarRating({
  value = 0,
  size = 30,
  onRate,
}: {
  value?: number;
  size?: number;
  onRate: (rating: number | undefined) => void;
}) {
  const { t } = useTranslation();

  return (
    <div className={styles.stars}>
      {STARS.map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onRate(n === value ? undefined : n)}
          aria-label={n === value ? t('detail.review.clear') : t('detail.review.star', { rating: n })}
          aria-pressed={n <= value}
          className={styles.star}
        >
          <Icon
            name="star"
            size={size}
            fill={n <= value}
            color={n <= value ? 'var(--gold)' : 'var(--line)'}
          />
        </button>
      ))}
    </div>
  );
}
