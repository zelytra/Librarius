import { describe, expect, test } from 'vitest';
import css from './global.css?raw';

describe('global.css', () => {
  /** The platform's spin arrows clashed with the app's fields: every number input drops them. */
  test('hides the spin arrows of number fields in every engine', () => {
    expect(css).toMatch(/input\[type='number'\]\s*\{[^}]*appearance:\s*textfield/);
    expect(css).toMatch(/::-webkit-inner-spin-button[\s\S]*-webkit-appearance:\s*none/);
  });
});
