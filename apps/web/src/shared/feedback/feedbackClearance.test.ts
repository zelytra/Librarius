import { describe, expect, test } from 'vitest';
// The stylesheets as written: jsdom lays nothing out (see `app/safeAreas.test.ts`).
import feedback from './FeedbackButton.module.css?raw';
import selection from '../selection/Selection.module.css?raw';
import bar from '../selection/SelectionBar.tsx?raw';

/**
 * #593: on a phone the report button sat on top of the selection bar's actions. While a
 * selection bar is on screen, the button rises above it.
 */
describe('the report button and the selection bar', () => {
  test('marks the selection bar so the page can tell it is open', () => {
    expect(bar).toMatch(/className=\{styles\.bar\}[^>]*data-selection-bar/);
  });

  test('lifts the report button above an open selection bar', () => {
    const barBottom = /\.bar\s*\{[^}]*bottom:\s*calc\((\d+)px/.exec(selection)?.[1];
    expect(barBottom).toBe('76');
    expect(feedback).toMatch(
      /:global\(body:has\(\[data-selection-bar\]\)\) \.trigger\s*\{[^}]*bottom:\s*calc\(148px \+ env\(safe-area-inset-bottom\)\)/,
    );
  });
});
