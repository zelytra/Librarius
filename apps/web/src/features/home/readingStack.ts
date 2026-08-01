import { activeLanguage } from '../../i18n/languages';

/**
 * The arithmetic behind the Home screen's stack of books read (#181).
 *
 * <p>Kept out of the component, and pure — the same reasoning as {@code shared/goal.ts} and
 * {@code dashboardLayout.ts}: the two interesting decisions are "how tall a drawing" and
 * "how much paper is that", and both are far easier to pin down without a render involved.
 */

/**
 * Thickness of one **sheet** of book paper, in millimetres.
 *
 * <p>A ream of the 80 g/m² offset stock trade novels are printed on — 500 sheets — measures
 * roughly 50 mm, which puts a sheet at a tenth of a millimetre. It is a round figure for a
 * range, not a measurement: heavy art paper runs two to three times thicker and the thin
 * stock manga is printed on runs under it, and no edition in the catalog records the paper
 * it used. That is why every screen showing the result labels it as an estimate.
 */
export const SHEET_THICKNESS_MM = 0.1;

/** A page is one side of a sheet, so a sheet carries two of them. */
export const PAGE_THICKNESS_MM = SHEET_THICKNESS_MM / 2;

/**
 * Below this many books the stack is drawn one spine per book: a newcomer with three titles
 * read should see three, not a compressed approximation of three.
 */
const DRAWN_ONE_FOR_ONE_UP_TO = 8;

/**
 * The tallest stack ever drawn. Past it the section would own the whole screen, and the
 * card is one block among six on a dashboard.
 */
export const MAX_SPINES = 14;

/**
 * How many spines to draw for a library of {@code booksRead} titles.
 *
 * <p>Exact up to {@link DRAWN_ONE_FOR_ONE_UP_TO}, then one more spine per doubling. A
 * linear drawing is unusable at both ends of the persona range in {@code PRODUCT.md} § 2 —
 * Sarah's handful of books is a smear one spine tall, Léa's 400 volumes an illustration
 * taller than the phone — while a scale that is logarithmic all the way through under-draws
 * the small library, which is the one whose owner most needs the encouragement. Hence the
 * two regimes: the small shelf is drawn as it is, and growth past it costs a doubling.
 *
 * <p>It saturates: 512 books and 5000 both draw {@link MAX_SPINES}. That is deliberate and
 * it is why the card states the real figures in words beside the drawing — the illustration
 * carries the order of magnitude, the text carries the number.
 */
export function stackSpines(booksRead: number): number {
  if (booksRead <= 0) return 0;
  if (booksRead <= DRAWN_ONE_FOR_ONE_UP_TO) return Math.floor(booksRead);
  const doublings = Math.log2(booksRead / DRAWN_ONE_FOR_ONE_UP_TO);
  return Math.min(MAX_SPINES, DRAWN_ONE_FOR_ONE_UP_TO + Math.ceil(doublings));
}

/** The unit a height is expressed in, spelled the way {@code Intl} names it. */
type HeightUnit = 'centimeter' | 'meter';

export interface PaperHeight {
  value: number;
  unit: HeightUnit;
}

/**
 * The paper the pages read add up to, in the unit that reads best.
 *
 * <p>Centimetres below a metre, metres above: a first novel is 2 cm of paper, and rounding
 * that to "0.0 m" turns the one figure the section exists for into nothing. The switch is on
 * the height itself rather than on a page count, so it holds whatever the books were.
 */
export function paperHeight(pagesRead: number): PaperHeight {
  const millimetres = Math.max(0, pagesRead) * PAGE_THICKNESS_MM;
  return millimetres >= 1000
    ? { value: millimetres / 1000, unit: 'meter' }
    : { value: millimetres / 10, unit: 'centimeter' };
}

/**
 * That height as it is shown — « 2,1 m », "2.1 m" — the unit coming from {@code Intl} rather
 * than from a translated string: the symbol and the space before it are a locale's business,
 * and both languages already read their number formats off {@link activeLanguage}.
 */
export function formatPaperHeight(pagesRead: number): string {
  const { value, unit } = paperHeight(pagesRead);
  return value.toLocaleString(activeLanguage(), {
    style: 'unit',
    unit,
    unitDisplay: 'short',
    maximumFractionDigits: 1,
  });
}
