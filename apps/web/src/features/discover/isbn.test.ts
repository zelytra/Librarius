import { describe, expect, test } from 'vitest';
import { detectIsbn } from './isbn';

describe('detectIsbn', () => {
  test('recognises an ISBN13 however it was copied', () => {
    // The same number as printed on a back cover, pasted from a shop, and typed by hand.
    expect(detectIsbn('9780441013593')).toBe('9780441013593');
    expect(detectIsbn('978-0-441-01359-3')).toBe('9780441013593');
    expect(detectIsbn('  978 0 441 01359 3 ')).toBe('9780441013593');
    expect(detectIsbn('ISBN: 978-0-441-01359-3')).toBe('9780441013593');
  });

  test('recognises an ISBN10, which is all an older edition carries', () => {
    expect(detectIsbn('0-441-01359-7')).toBe('0441013597');
    // The check digit of an ISBN10 can be an X.
    expect(detectIsbn('080442957X')).toBe('080442957X');
  });

  test('leaves an ordinary search alone', () => {
    expect(detectIsbn('fourth wing')).toBeNull();
    expect(detectIsbn('1984')).toBeNull();
    expect(detectIsbn('')).toBeNull();
  });

  test('refuses a number that is not an ISBN', () => {
    // Right length, wrong check digit: searching it on the ISBN field would find
    // nothing, where the keywords might still have.
    expect(detectIsbn('9780441013594')).toBeNull();
    // Thirteen digits outside the Bookland prefixes — a barcode or an order number.
    expect(detectIsbn('1234567890123')).toBeNull();
  });
});
