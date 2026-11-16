/** Days between the start and the end of a reading, both counted; null when either is missing. */
export function readingDays(startedAt?: string | null, finishedAt?: string | null): number | null {
  if (!startedAt || !finishedAt) return null;
  const start = Date.parse(startedAt);
  const end = Date.parse(finishedAt);
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return null;
  return Math.round((end - start) / 86_400_000) + 1;
}

/** The paper sizes offered: a full page, a half page (#432), or a card to slip into the book (#427). */
export type SheetFormat = 'A4' | 'A5' | 'A6';

export const SHEET_FORMATS: SheetFormat[] = ['A4', 'A5', 'A6'];

/**
 * Paper size and margins per format. The size is written in millimetres rather than by name:
 * Chromium knows `A4` and `A5` but not `A6`, and silently prints an unknown name on Letter.
 */
const PAGE: Record<SheetFormat, { size: string; margin: number; rings: number }> = {
  A4: { size: '210mm 297mm', margin: 12, rings: 22 },
  A5: { size: '148mm 210mm', margin: 10, rings: 20 },
  A6: { size: '105mm 148mm', margin: 6, rings: 14 },
};

/**
 * The @page rule that tells the printer the paper size picked. The page margin is zero on
 * purpose (#521): a browser prints its own header and footer — date, title, URL, page
 * number — in that margin, so the sheet carries its margins itself ({@link printMargin}).
 */
export function pageRule(format: SheetFormat): string {
  return `@page { size: ${PAGE[format].size}; margin: 0; }`;
}

/**
 * The white space the printed sheet keeps around its text. For a ring binder (#432) the inner
 * margin widens so the punched holes — 8 mm from the edge on the usual punches, 5 to 6 mm
 * across — fall outside the text.
 */
export function printMargin(format: SheetFormat, rings = false): string {
  const { margin, rings: inner } = PAGE[format];
  return `${margin}mm ${margin}mm ${margin}mm ${rings ? inner : margin}mm`;
}
