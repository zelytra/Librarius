import { useEffect, useState } from 'react';
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
  // Loading state of the real cover, tracked through the `<img>` itself so the
  // skeleton can fade out on `onLoad` and give up on `onError` — a background-image
  // has neither event, which is why a real `<img>` sits on top of the coloured block
  // instead. Reset whenever the URL changes: a title swapped for another mid-session
  // (an edit, a re-match against the catalog) starts a fresh load, not a stale one.
  const [loaded, setLoaded] = useState(false);
  const [errored, setErrored] = useState(false);
  useEffect(() => {
    setLoaded(false);
    setErrored(false);
  }, [imageUrl]);

  // The coloured block stays underneath even when a cover image is expected: it is
  // both the no-cover rendering and the backdrop the skeleton and the fading-in
  // image sit on.
  const background = colorFor(title);
  const withImage = imageUrl ? styles.withImage : '';
  const showSkeleton = Boolean(imageUrl) && !loaded && !errored;

  const media = imageUrl && (
    <>
      <img
        src={imageUrl}
        // Decorative: the title is already printed on the placeholder underneath and read
        // from the surrounding caption, so there is nothing here for i18n to own — an empty
        // alt is the correct value, not a stand-in for one.
        // eslint-disable-next-line no-restricted-syntax -- empty alt, not user-facing text
        alt=""
        className={`${styles.media} ${loaded ? styles.mediaLoaded : ''}`}
        onLoad={() => setLoaded(true)}
        onError={() => setErrored(true)}
      />
      {showSkeleton && <div className={styles.skeleton} aria-hidden="true" />}
    </>
  );

  if (variant === 'hero') {
    return (
      <div
        className={`${styles.art} ${styles.heroArt} ${withImage}`}
        style={{ background }}
        onClick={onClick}
      >
        {!imageUrl && <div className={styles.heroTitle}>{title}</div>}
        {media}
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
          {media}
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
        {media}
        {children}
      </div>
      <div className={styles.caption}>{caption}</div>
    </div>
  );
}
