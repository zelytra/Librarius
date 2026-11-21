import { describe, expect, test } from 'vitest';
import { cropSquare } from './avatarImage';

/** #571: the square of the picture that becomes the avatar, from the zoom and the drag. */
describe('cropSquare', () => {
  test('takes the centre square at zoom 1', () => {
    expect(cropSquare(4000, 3000, { zoom: 1, x: 0, y: 0 })).toEqual({ sx: 500, sy: 0, side: 3000 });
  });

  test('narrows the square as the zoom grows, still centred', () => {
    expect(cropSquare(4000, 3000, { zoom: 2, x: 0, y: 0 })).toEqual({ sx: 1250, sy: 750, side: 1500 });
  });

  test('moves to the edges at the full drag, never past them', () => {
    expect(cropSquare(4000, 3000, { zoom: 2, x: 1, y: -1 })).toEqual({ sx: 2500, sy: 0, side: 1500 });
    expect(cropSquare(4000, 3000, { zoom: 2, x: 5, y: 5 })).toEqual({ sx: 2500, sy: 1500, side: 1500 });
  });

  test('keeps a zoom under 1 at 1', () => {
    expect(cropSquare(1000, 1000, { zoom: 0.5, x: 0, y: 0 })).toEqual({ sx: 0, sy: 0, side: 1000 });
  });
});
