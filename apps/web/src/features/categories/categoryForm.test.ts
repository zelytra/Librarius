import { describe, expect, test } from 'vitest';
// The stylesheet as written: jsdom lays nothing out (see `app/safeAreas.test.ts`).
import css from './CategoriesPage.module.css?raw';

function rule(selector: string): string {
  return new RegExp(`\\${selector}\\s*\\{([^}]*)\\}`).exec(css)?.[1] ?? '';
}

/**
 * #611: the name field of the create form was squeezed to a few pixels. The input, the button
 * and a palette of 44 px swatches shared one row that could not wrap, and the input was the
 * one part allowed to shrink. The forms now wrap, and the palette takes a row of its own.
 */
describe('the category forms', () => {
  test.each(['.createForm', '.editForm'])('%s wraps onto several rows', (selector) => {
    expect(rule(selector)).toMatch(/flex-wrap:\s*wrap/);
  });

  test('give the palette a full row of its own', () => {
    expect(rule('.palette')).toMatch(/flex-basis:\s*100%/);
  });

  test('keep the name field readable', () => {
    expect(rule('.input')).toMatch(/min-width:\s*(1[2-9]\d|[2-9]\d\d)px/);
  });
});
