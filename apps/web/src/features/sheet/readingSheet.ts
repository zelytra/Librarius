/** Days between the start and the end of a reading, both counted; null when either is missing. */
export function readingDays(startedAt?: string | null, finishedAt?: string | null): number | null {
  if (!startedAt || !finishedAt) return null;
  const start = Date.parse(startedAt);
  const end = Date.parse(finishedAt);
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return null;
  return Math.round((end - start) / 86_400_000) + 1;
}

/** The two paper sizes offered (#427): a full page, or a card to slip into the book. */
export type SheetFormat = 'A4' | 'A6';

/**
 * Paper size and printer margins per format. The size is written in millimetres rather than
 * by name: Chromium knows `A4` but not `A6`, and silently prints an unknown name on Letter.
 */
const PAGE: Record<SheetFormat, { size: string; margin: string }> = {
  A4: { size: '210mm 297mm', margin: '12mm' },
  A6: { size: '105mm 148mm', margin: '6mm' },
};

/** The @page rule that tells the printer the paper size picked. */
export function pageRule(format: SheetFormat): string {
  return `@page { size: ${PAGE[format].size}; margin: ${PAGE[format].margin}; }`;
}
