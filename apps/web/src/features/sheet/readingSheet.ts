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
 * Paper size (ISO 216, in millimetres) and margins per format. The size is written in
 * millimetres rather than by name: Chromium knows `A4` and `A5` but not `A6`, and silently
 * prints an unknown name on Letter.
 */
const PAGE: Record<SheetFormat, { width: number; height: number; margin: number; rings: number }> = {
  A4: { width: 210, height: 297, margin: 12, rings: 22 },
  A5: { width: 148, height: 210, margin: 10, rings: 20 },
  A6: { width: 105, height: 148, margin: 6, rings: 14 },
};

/**
 * The size the sheet is drawn at (#548): exactly the paper's, so the preview has the paper's
 * proportions and the printed sheet fills its page instead of floating in the middle of it.
 */
export function sheetSize(format: SheetFormat): { width: string; height: string } {
  return { width: `${PAGE[format].width}mm`, height: `${PAGE[format].height}mm` };
}

/**
 * The @page rule that tells the printer the paper size picked. The page margin is zero on
 * purpose (#521): a browser prints its own header and footer — date, title, URL, page
 * number — in that margin, so the sheet carries its margins itself ({@link printMargin}).
 */
export function pageRule(format: SheetFormat): string {
  const { width, height } = PAGE[format];
  return `@page { size: ${width}mm ${height}mm; margin: 0; }`;
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

/**
 * How the sheet is drawn (#549, #550): a handwritten page with hand-drawn rules — the
 * default —, the plain typeset one it used to be, or a school notebook's ruled page.
 */
export type SheetTheme = 'handwritten' | 'classic' | 'notebook';

export const SHEET_THEMES: SheetTheme[] = ['handwritten', 'classic', 'notebook'];

export const SHEET_THEME_STORAGE_KEY = 'librarius.sheetTheme';

/** The theme picked in Settings, on this device; handwritten until one is picked. */
export function readSheetTheme(): SheetTheme {
  try {
    const stored = localStorage.getItem(SHEET_THEME_STORAGE_KEY);
    return SHEET_THEMES.includes(stored as SheetTheme) ? (stored as SheetTheme) : 'handwritten';
  } catch {
    return 'handwritten';
  }
}

export function storeSheetTheme(theme: SheetTheme): void {
  try {
    localStorage.setItem(SHEET_THEME_STORAGE_KEY, theme);
  } catch {
    /* storage unavailable: the pick lasts as long as the page */
  }
}

const idList = (raw: string | null) => [...new Set((raw ?? '').split(',').filter(Boolean))];

/** The titles and series a multi-sheet print was asked for (#551), from `?items=…&series=…`. */
export function sheetIdsFromParams(params: URLSearchParams): { items: string[]; series: string[] } {
  return { items: idList(params.get('items')), series: idList(params.get('series')) };
}
