import type { KeyboardEvent, MouseEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { activeLanguage } from '../../i18n/languages';
import styles from './StarRating.module.css';

/** The rating is out of five, like everywhere the app shows one. */
const STARS = [1, 2, 3, 4, 5];

type Fill = 'filled' | 'half' | 'empty';

/** How much of star `n` a rating fills. */
const fillOf = (n: number, value: number): Fill => (n <= value ? 'filled' : n - 0.5 === value ? 'half' : 'empty');

/**
 * A rating out of five by half stars (#484), and the way back from one given by mistake:
 * clicking the value already in force clears the rating instead of setting it again.
 *
 * <p>The left half of a star gives half of it, the right half the whole star; a click from the
 * keyboard gives the whole star, and the arrow keys move the rating by half a star.
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
  const number = (n: number) => n.toLocaleString(activeLanguage());
  const rate = (next: number) => onRate(next === value ? undefined : next);

  const onClick = (n: number) => (event: MouseEvent<HTMLButtonElement>) => {
    // `detail` is 0 for a click the keyboard made: there is no pointer to place.
    const box = event.currentTarget.getBoundingClientRect();
    const leftHalf = event.detail > 0 && event.clientX < box.left + box.width / 2;
    rate(leftHalf ? n - 0.5 : n);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    const step = event.key === 'ArrowRight' ? 0.5 : event.key === 'ArrowLeft' ? -0.5 : 0;
    if (!step) return;
    event.preventDefault();
    const next = Math.min(5, Math.max(0.5, value + step));
    if (next !== value) onRate(next);
  };

  return (
    <div
      className={styles.stars}
      role="group"
      aria-label={value ? t('detail.review.current', { rating: number(value) }) : t('detail.review.none')}
    >
      {STARS.map((n) => {
        const fill = fillOf(n, value);
        return (
          <button
            key={n}
            type="button"
            onClick={onClick(n)}
            onKeyDown={onKeyDown}
            aria-label={n === value ? t('detail.review.clear') : t('detail.review.star', { rating: number(n) })}
            aria-pressed={fill === 'half' ? 'mixed' : fill === 'filled'}
            className={styles.star}
          >
            <StarGlyph size={size} fill={fill} />
          </button>
        );
      })}
    </div>
  );
}

/**
 * A rating shown, not given (#484): the same drawn stars, halves included, read out as one
 * figure — another reader's or a friend's.
 */
export function Stars({ value, size = 14, className }: { value: number; size?: number; className?: string }) {
  const { t } = useTranslation();
  return (
    <span
      role="img"
      className={`${styles.shown} ${className ?? ''}`}
      aria-label={t('reviews.stars', { rating: value.toLocaleString(activeLanguage()) })}
    >
      {STARS.map((n) => <StarGlyph key={n} size={size} fill={fillOf(n, value)} />)}
    </span>
  );
}

const STAR_PATH = 'M12 2.8l2.83 5.73 6.33.92-4.58 4.46 1.08 6.3L12 17.24l-5.66 2.97 1.08-6.3-4.58-4.46 6.33-.92z';

/**
 * One star, drawn rather than taken from the icon font: the self-hosted Material Symbols
 * subset carries no FILL axis, so `<Icon fill>` drew the same outline for a given star and an
 * empty one. A half star is the empty one with its left half filled over it.
 */
function StarGlyph({ size, fill }: { size: number; fill: Fill }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" className={styles[fill]}>
      <path d={STAR_PATH} strokeWidth="1.6" strokeLinejoin="round" />
      {fill === 'half' && (
        <path d={STAR_PATH} strokeWidth="1.6" strokeLinejoin="round" className={styles.halfFill} />
      )}
    </svg>
  );
}
