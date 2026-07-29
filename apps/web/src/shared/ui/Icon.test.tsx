import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { Icon } from './Icon';
import { ICON_CODEPOINTS } from './iconSubset';

describe('Icon', () => {
  test('renders the code point for a name in the self-hosted subset', () => {
    render(<Icon name="delete" />);
    const icon = screen.getByText(String.fromCodePoint(ICON_CODEPOINTS.delete));
    expect(icon).toHaveClass('material-symbol');
    expect(icon).toHaveAttribute('aria-hidden', 'true');
    expect(icon).toHaveAttribute('data-fill', '0');
  });

  test('applies size, color and the fill flag', () => {
    render(<Icon name="star" size={30} fill color="#d9b94e" />);
    const icon = screen.getByText(String.fromCodePoint(ICON_CODEPOINTS.star));
    expect(icon).toHaveAttribute('data-fill', '1');
    expect(icon.style.fontSize).toBe('30px');
    expect(icon.style.color).toBe('rgb(217, 185, 78)');
  });

  test('every code point falls in the Private Use Area Material Symbols uses', () => {
    // Catches a mistyped hex literal in iconSubset.ts: a value outside this range is
    // not a Material Symbols glyph at all, so the subset (and the app) would render
    // whatever the surrounding text happens to contain at that code point instead.
    for (const [name, codepoint] of Object.entries(ICON_CODEPOINTS)) {
      expect(codepoint, name).toBeGreaterThanOrEqual(0xe000);
      expect(codepoint, name).toBeLessThanOrEqual(0xf8ff);
    }
  });

  test('a name missing from the subset throws instead of rendering the wrong glyph', () => {
    expect(() => render(<Icon name="not_a_real_icon" />)).toThrow(/not_a_real_icon/);
  });
});
