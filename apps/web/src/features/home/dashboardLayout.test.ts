import { describe, expect, test } from 'vitest';
import {
  DEFAULT_SECTION_ORDER,
  defaultLayout,
  moveSection,
  toggleHidden,
  type DashboardSectionPref,
} from './dashboardLayout';

describe('defaultLayout', () => {
  test('lists every section in the default order, none hidden', () => {
    const layout = defaultLayout();

    expect(layout.map((s) => s.code)).toEqual([...DEFAULT_SECTION_ORDER]);
    expect(layout.every((s) => !s.hidden)).toBe(true);
  });
});

describe('moveSection', () => {
  const sections: DashboardSectionPref[] = [
    { code: 'a', hidden: false },
    { code: 'b', hidden: false },
    { code: 'c', hidden: false },
  ];

  test('swaps an entry with the one below it', () => {
    expect(moveSection(sections, 0, 1).map((s) => s.code)).toEqual(['b', 'a', 'c']);
  });

  test('swaps an entry with the one above it', () => {
    expect(moveSection(sections, 2, -1).map((s) => s.code)).toEqual(['a', 'c', 'b']);
  });

  test('does nothing when the first entry is asked to move up', () => {
    expect(moveSection(sections, 0, -1)).toBe(sections);
  });

  test('does nothing when the last entry is asked to move down', () => {
    expect(moveSection(sections, 2, 1)).toBe(sections);
  });

  test('never mutates the array it was given', () => {
    const copy = sections.map((s) => ({ ...s }));
    moveSection(sections, 0, 1);
    expect(sections).toEqual(copy);
  });
});

describe('toggleHidden', () => {
  test('flips only the targeted entry', () => {
    const sections: DashboardSectionPref[] = [
      { code: 'a', hidden: false },
      { code: 'b', hidden: false },
    ];

    const once = toggleHidden(sections, 1);
    expect(once.map((s) => s.hidden)).toEqual([false, true]);

    const twice = toggleHidden(once, 1);
    expect(twice.map((s) => s.hidden)).toEqual([false, false]);
  });
});
