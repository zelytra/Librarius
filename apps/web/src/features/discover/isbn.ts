/**
 * Recognises an ISBN typed or pasted into the plain search field.
 *
 * Someone holding a book copies the number off the back cover, and searching those digits
 * as keywords finds nothing: the catalogs index an ISBN on its own field. Detecting it here
 * is what turns a paste into a hit, without asking the user to say which kind of search they
 * meant.
 *
 * The check digit is verified rather than trusted. Thirteen digits alone would also match a
 * barcode, an order number or a phone number, and searching one of those on the ISBN field
 * would answer nothing at all where a keyword search might still have found something.
 */

/** Separators the number is printed with, plus the prefix people paste along with it. */
function normalise(input: string): string {
  return input.trim().replace(/^isbn[-\s:]*(?:1[03][-\s:]*)?/i, '').replace(/[\s-]/g, '').toUpperCase();
}

function isValidIsbn13(digits: string): boolean {
  // Bookland prefixes: the only two the EAN-13 range assigns to books.
  if (!/^97[89]\d{10}$/.test(digits)) return false;
  const sum = [...digits].reduce((acc, c, i) => acc + Number(c) * (i % 2 === 0 ? 1 : 3), 0);
  return sum % 10 === 0;
}

function isValidIsbn10(value: string): boolean {
  if (!/^\d{9}[\dX]$/.test(value)) return false;
  const sum = [...value].reduce(
    (acc, c, i) => acc + (c === 'X' ? 10 : Number(c)) * (10 - i),
    0,
  );
  return sum % 11 === 0;
}

/**
 * The ISBN contained in `input`, stripped of its separators, or `null` when the text is an
 * ordinary search. Both lengths are accepted: the providers index them side by side, and an
 * older book is often only printed with its ISBN-10.
 */
export function detectIsbn(input: string): string | null {
  const candidate = normalise(input);
  if (isValidIsbn13(candidate) || isValidIsbn10(candidate)) return candidate;
  return null;
}

/**
 * The ISBN-13 of an ISBN `detectIsbn` accepted: an ISBN-10 gains its 978 prefix and a new
 * check digit. A book is scanned as one and typed as the other; both must be the same book.
 */
export function toIsbn13(isbn: string): string {
  if (isbn.length === 13) return isbn;
  const body = '978' + isbn.slice(0, 9);
  const sum = [...body].reduce((acc, c, i) => acc + Number(c) * (i % 2 === 0 ? 1 : 3), 0);
  return body + String((10 - (sum % 10)) % 10);
}
