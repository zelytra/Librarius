import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { StarRating } from './StarRating';

/** The stars a rating shows: the given ones filled, the rest hollow. */
describe('StarRating', () => {
  test('fills exactly the stars up to the rating', () => {
    renderWithProviders(<StarRating value={4} onRate={() => {}} />);

    const stars = screen.getAllByRole('button');
    expect(stars).toHaveLength(5);
    const filled = stars.map((star) => star.querySelector('svg')?.getAttribute('class') ?? '');
    // The icon font cannot fill a glyph, which is why four given stars used to look like
    // five empty ones: the drawn star carries the state in its class.
    expect(filled.filter((c) => c.includes('filled'))).toHaveLength(4);
    expect(filled[4]).toContain('empty');
    expect(stars[3]).toHaveAttribute('aria-pressed', 'true');
    expect(stars[4]).toHaveAttribute('aria-pressed', 'false');
  });

  test('clears the rating from the star already in force', async () => {
    const onRate = vi.fn();
    renderWithProviders(<StarRating value={3} onRate={onRate} />);

    const stars = screen.getAllByRole('button');
    await userEvent.click(stars[2]);
    expect(onRate).toHaveBeenCalledWith(undefined);
    await userEvent.click(stars[4]);
    expect(onRate).toHaveBeenCalledWith(5);
  });
});
