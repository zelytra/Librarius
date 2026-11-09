import { useTranslation } from 'react-i18next';
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
          <StarGlyph size={size} filled={n <= value} />
        </button>
      ))}
    </div>
  );
}

/**
 * One star, drawn rather than taken from the icon font: the self-hosted Material Symbols
 * subset carries no FILL axis, so `<Icon fill>` drew the same outline for a given star and an
 * empty one, and a four-star rating read as five hollow stars of two near-identical shades.
 */
function StarGlyph({ size, filled }: { size: number; filled: boolean }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      className={filled ? styles.filled : styles.empty}
    >
      <path
        d="M12 2.8l2.83 5.73 6.33.92-4.58 4.46 1.08 6.3L12 17.24l-5.66 2.97 1.08-6.3-4.58-4.46 6.33-.92z"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
    </svg>
  );
}
