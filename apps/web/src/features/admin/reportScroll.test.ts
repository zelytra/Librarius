import { describe, expect, test } from 'vitest';
// The stylesheet as written: jsdom lays nothing out (see `app/safeAreas.test.ts`).
import css from './AdminPage.module.css?raw';

/**
 * #564: on a phone a report with a long log could not be scrolled. The log scrolled on its
 * own inside the dialog, which also scrolls: a finger landing on it moved the log and never
 * the report. Below the tablet breakpoint the log takes its full height, and the dialog is
 * the one thing that scrolls.
 */
describe('a report on a phone', () => {
  test('lets the dialog scroll rather than the log inside it', () => {
    const phone = /@media \(max-width: 599px\) \{([\s\S]*?)\n\}/.exec(css)?.[1] ?? '';
    expect(phone).toMatch(/\.logs\s*\{[^}]*max-height:\s*none/);
    expect(phone).toMatch(/\.logs\s*\{[^}]*overflow-y:\s*visible/);
  });
});
