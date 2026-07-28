import type { CSSProperties, ReactNode } from 'react';
import { colorFor } from './coverPalette';
import styles from './Cover.module.css';

/** Aspect ratios the mockup uses, per shape. */
const SHELF_RATIO = 1.46;
const TILE_RATIO = 0.68;
const SHELF_WIDTH = 104;

/**
 * `shelf` — carousel cover on Home, `tile` — Collection grid cell,
 * `hero` — large cover on Detail.
 */
export type CoverVariant = 'shelf' | 'tile' | 'hero';

interface CoverProps {
  /** Printed on a generated cover, and used as the colour seed. */
  title: string;
  /** Real cover; takes precedence over the generated coloured block. */
  imageUrl?: string | null;
  /** Small uppercase label at the top of a generated tile. */
  tag?: string;
  /** Line under the cover — the authors. */
  caption?: ReactNode;
  variant?: CoverVariant;
  /** Fixed width. A `tile` without one fills its grid cell. */
  width?: number;
  onClick?: () => void;
  /** Overlays drawn on the cover itself (rank badge, remove button). */
  children?: ReactNode;
}

/**
 * Book / manga cover, in the three shapes the app draws. Without an image it
 * renders a coloured block derived from the title, with the title — and, on a
 * tile, its kind — printed on it.
 */
export function Cover({
  title,
  imageUrl,
  tag,
  caption,
  variant = 'shelf',
  width,
  onClick,
  children,
}: CoverProps) {
  // Background and dimensions are the only genuinely dynamic parts: the colour
  // comes from the title, the size from the caller.
  const background = imageUrl ? `center/cover no-repeat url(${imageUrl})` : colorFor(title);
  const withImage = imageUrl ? styles.withImage : '';

  if (variant === 'hero') {
    return (
      <div
        className={`${styles.art} ${styles.heroArt} ${withImage}`}
        style={{ background }}
        onClick={onClick}
      >
        {!imageUrl && <div className={styles.heroTitle}>{title}</div>}
      </div>
    );
  }

  if (variant === 'tile') {
    const artStyle: CSSProperties = width
      ? { background, width, height: width / TILE_RATIO }
      : { background };
    return (
      <div className={styles.tileRoot} style={{ width }}>
        <div
          className={`${styles.art} ${styles.tileArt} ${width ? '' : styles.tileArtFluid} ${withImage}`}
          style={artStyle}
          onClick={onClick}
        >
          {!imageUrl && (
            <>
              <span className={styles.tileTag}>{tag}</span>
              <div className={styles.tileTitle}>{title}</div>
            </>
          )}
          {children}
        </div>
        <div className={styles.caption}>{caption}</div>
      </div>
    );
  }

  const shelfWidth = width ?? SHELF_WIDTH;
  return (
    <div className={styles.shelfRoot} style={{ width: shelfWidth }} onClick={onClick}>
      <div
        className={`${styles.art} ${styles.shelfArt} ${withImage}`}
        style={{ background, width: shelfWidth, height: shelfWidth * SHELF_RATIO }}
      >
        {!imageUrl && <div className={styles.shelfTitle}>{title}</div>}
        {children}
      </div>
      <div className={styles.caption}>{caption}</div>
    </div>
  );
}
