import type { CSSProperties } from 'react';

interface IconProps {
  name: string;
  size?: number;
  fill?: boolean;
  color?: string;
  style?: CSSProperties;
}

/** Icône Material Symbols Rounded. */
export function Icon({ name, size = 24, fill = false, color, style }: IconProps) {
  return (
    <span
      className="material-symbol"
      data-fill={fill ? '1' : '0'}
      style={{ fontSize: size, color, ...style }}
      aria-hidden="true"
    >
      {name}
    </span>
  );
}
