import { useCallback, useState } from 'react';
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
  // instead.
  //
  // The state remembers *which* URL loaded or failed rather than being reset by an effect
  // (#398): an image already in the cache — every cover when the reader comes back to a
  // screen — can fire `load` before a mount effect runs, and resetting after it left the
  // skeleton up forever, no second `load` ever coming. Keyed on the URL, a title swapped
  // for another mid-session still starts a fresh load.
  const [loadedUrl, setLoadedUrl] = useState<string | null>(null);
  const [erroredUrl, setErroredUrl] = useState<string | null>(null);
  const loaded = Boolean(imageUrl) && loadedUrl === imageUrl;
  const errored = Boolean(imageUrl) && erroredUrl === imageUrl;
  // An image complete before React attached `onLoad` (straight from the memory cache) is
  // caught when the element mounts.
  const imgRef = useCallback(
    (img: HTMLImageElement | null) => {
      if (img && imageUrl && img.complete && img.naturalWidth > 0) setLoadedUrl(imageUrl);
    },
    [imageUrl],
  );

  // The coloured block stays underneath even when a cover image is expected: it is
  // both the no-cover rendering and the backdrop the skeleton and the fading-in
  // image sit on.
  const background = colorFor(title);
  // The titled placeholder stands until a real cover has actually loaded: a cover that fails
  // (Open Library answers 404 for an ISBN it has no image of) or that never arrives (a slow
  // or offline network) is no cover. Before, the title was only drawn when there was no URL
  // at all, so either case left an anonymous coloured tile. The image is transparent until
  // it decodes and sits above the title, so a successful load simply covers it.
  const placeholder = !imageUrl || !loaded;
  const withImage = placeholder ? '' : styles.withImage;
  const showSkeleton = Boolean(imageUrl) && !loaded && !errored;

  const media = imageUrl && !errored && (
    <>
      <img
        src={imageUrl}
        // Decorative: the title is already printed on the placeholder underneath and read
        // from the surrounding caption, so there is nothing here for i18n to own — an empty
        // alt is the correct value, not a stand-in for one.
        // eslint-disable-next-line no-restricted-syntax -- empty alt, not user-facing text
        alt=""
        ref={imgRef}
        className={`${styles.media} ${loaded ? styles.mediaLoaded : ''}`}
        onLoad={() => setLoadedUrl(imageUrl)}
        onError={() => setErroredUrl(imageUrl)}
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
        {placeholder && <div className={styles.heroTitle}>{title}</div>}
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
          {placeholder && (
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
        {placeholder && <div className={styles.shelfTitle}>{title}</div>}
        {media}
        {children}
      </div>
      <div className={styles.caption}>{caption}</div>
    </div>
  );
}
