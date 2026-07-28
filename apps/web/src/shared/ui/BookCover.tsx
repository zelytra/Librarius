import type { CSSProperties } from 'react';

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
 * Book / manga cover. When no image is available, renders a stylized colored "spine"
 * (tag on top, title at the bottom) faithful to the mockup.
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
      style={{
        width,
        height,
        borderRadius: radius,
        background: imageUrl ? `center / cover no-repeat url(${imageUrl})` : color,
        borderLeft: '3px solid rgba(0,0,0,0.07)',
        boxShadow: '0 8px 18px -8px rgba(74,64,52,0.4)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: imageUrl ? 0 : '12px 11px',
        cursor: onClick ? 'pointer' : undefined,
        overflow: 'hidden',
        flex: '0 0 auto',
        ...style,
      }}
    >
      {!imageUrl && (
        <>
          <span
            style={{
              fontSize: 9,
              fontWeight: 700,
              letterSpacing: '0.08em',
              color: 'rgba(58,52,44,0.5)',
            }}
          >
            {tag}
          </span>
          <span
            style={{
              fontFamily: "'Newsreader', serif",
              fontSize: 14,
              fontWeight: 600,
              lineHeight: 1.1,
              color: '#352f28',
            }}
          >
            {title}
          </span>
        </>
      )}
    </div>
  );
}
