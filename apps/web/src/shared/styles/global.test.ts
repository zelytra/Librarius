import { describe, expect, test } from 'vitest';
import css from './global.css?raw';
import viewport from '../../../index.html?raw';

describe('global.css', () => {
  /** The platform's spin arrows clashed with the app's fields: every number input drops them. */
  test('hides the spin arrows of number fields in every engine', () => {
    expect(css).toMatch(/input\[type='number'\]\s*\{[^}]*appearance:\s*textfield/);
    expect(css).toMatch(/::-webkit-inner-spin-button[\s\S]*-webkit-appearance:\s*none/);
  });

  /**
   * #553: iOS zooms onto any field whose text is under 16px when it takes the focus, and
   * leaves the page zoomed. On a touch screen every text field is at least 16px — the zoom
   * itself stays allowed (no `user-scalable=no`, WCAG 1.4.4).
   */
  test('keeps every text field at 16px or more on a touch screen', () => {
    const touch = /@media \(pointer: coarse\) \{([\s\S]*?)\n\}/.exec(css)?.[1] ?? '';
    expect(touch).toMatch(/input[^{]*,\s*select,\s*textarea\s*\{[^}]*font-size:\s*max\(16px, 1em\)\s*!important/);
    expect(touch).toMatch(/:not\(\[type='checkbox'\]\)/);
  });

  test('never disables the zoom itself', () => {
    expect(viewport).toMatch(/name="viewport"/);
    expect(viewport).not.toMatch(/user-scalable|maximum-scale/);
  });
});
