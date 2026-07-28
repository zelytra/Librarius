import type { CSSProperties } from 'react';
import styles from './BookCover.module.css';

interface BookCoverProps {
  color: string;
  tag?: string;
  title?: string;
  /** Real cover URL; takes precedence over the "colored spine" rendering. */
  imageUrl?: string;
  width?: number | string;
  height?: number | string;
  radius?: number;
  onClick?: () => void;
  style?: CSSProperties;
}

/**
 * Book / manga cover for a catalogue result, whose colour is supplied by the
 * caller. Titles already in the library use `<Cover>`, which derives its colour
 * from the title itself.
 */
export function BookCover({
  color,
  tag,
  title,
  imageUrl,
  width = 104,
  height = 152,
  radius = 10,
  onClick,
  style,
}: BookCoverProps) {
  return (
    <div
      onClick={onClick}
      className={[styles.cover, onClick && styles.clickable, imageUrl && styles.withImage]
        .filter(Boolean)
        .join(' ')}
      style={{
        // Size, radius and background are set by the caller for each usage.
        width,
        height,
        borderRadius: radius,
        background: imageUrl ? `center / cover no-repeat url(${imageUrl})` : color,
        ...style,
      }}
    >
      {!imageUrl && (
        <>
          <span className={styles.tag}>{tag}</span>
          <span className={styles.title}>{title}</span>
        </>
      )}
    </div>
  );
}
