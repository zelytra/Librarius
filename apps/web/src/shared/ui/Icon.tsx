import type { CSSProperties } from 'react';
import { ICON_CODEPOINTS } from './iconSubset';

interface IconProps {
  name: string;
  size?: number;
  fill?: boolean;
  color?: string;
  style?: CSSProperties;
}

/**
 * Material Symbols Rounded icon, drawn from the self-hosted subset in
 * `shared/styles/fonts/material-symbols-subset.woff2` (see `iconSubset.ts`) rather than
 * the 407 kB full font — issue #161.
 *
 * `name` still takes the icon's usual Material Symbols name (`"arrow_back"`); this just
 * looks up its Private Use Area code point rather than relying on the font's ligature
 * table, and renders that character instead of the name itself. Every call site is
 * unchanged.
 *
 * A name missing from `iconSubset.ts` renders nothing rather than the wrong glyph — and,
 * under Vitest, throws instead: `pnpm web:test` renders every screen at least once, so
 * this is what catches an icon added without updating that table and regenerating the
 * font.
 */
export function Icon({ name, size = 24, fill = false, color, style }: IconProps) {
  const codepoint = ICON_CODEPOINTS[name];
  if (codepoint === undefined) {
    if (import.meta.env.MODE === 'test') {
      throw new Error(
        `Icon "${name}" is missing from the self-hosted Material Symbols subset. ` +
          `Add it to shared/ui/iconSubset.ts, then run ` +
          `"pnpm --filter @librarius/web generate:icon-font" and commit the regenerated font.`
      );
    }
    return null;
  }
  return (
    <span
      className="material-symbol"
      data-fill={fill ? '1' : '0'}
      style={{ fontSize: size, color, ...style }}
      aria-hidden="true"
    >
      {String.fromCodePoint(codepoint)}
    </span>
  );
}
