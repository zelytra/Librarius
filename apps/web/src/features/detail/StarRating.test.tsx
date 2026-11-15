import { fireEvent, screen } from '@testing-library/react';
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

  /** Each star 40 px wide from x = 0: a click at `x` lands on its left or right half. */
  function clickAt(star: HTMLElement, x: number) {
    star.getBoundingClientRect = () => ({ left: 0, width: 40, top: 0, height: 40, right: 40, bottom: 40, x: 0, y: 0, toJSON: () => ({}) });
    fireEvent.click(star, { clientX: x, detail: 1 });
  }

  /** #484: half stars. */
  test('shows a half star for a rating that ends on one', () => {
    renderWithProviders(<StarRating value={3.5} onRate={() => {}} />);

    const stars = screen.getAllByRole('button');
    const classes = stars.map((star) => star.querySelector('svg')?.getAttribute('class') ?? '');
    expect(classes.filter((c) => c.includes('filled'))).toHaveLength(3);
    expect(classes[3]).toContain('half');
    expect(classes[4]).toContain('empty');
    expect(stars[3]).toHaveAttribute('aria-pressed', 'mixed');
    expect(screen.getByRole('group', { name: 'Ma note : 3,5 sur 5' })).toBeInTheDocument();
  });

  test('gives half a star from the left half of a star, a whole one from its right half', () => {
    const onRate = vi.fn();
    renderWithProviders(<StarRating value={2} onRate={onRate} />);

    const stars = screen.getAllByRole('button');
    clickAt(stars[4], 8);
    expect(onRate).toHaveBeenLastCalledWith(4.5);
    clickAt(stars[4], 32);
    expect(onRate).toHaveBeenLastCalledWith(5);
  });

  test('clears a half-star rating from the half in force', () => {
    const onRate = vi.fn();
    renderWithProviders(<StarRating value={4.5} onRate={onRate} />);

    clickAt(screen.getAllByRole('button')[4], 8);
    expect(onRate).toHaveBeenCalledWith(undefined);
  });

  test('a click from the keyboard gives the whole star', async () => {
    const onRate = vi.fn();
    renderWithProviders(<StarRating value={1} onRate={onRate} />);

    screen.getAllByRole('button')[3].focus();
    await userEvent.keyboard('{Enter}');
    expect(onRate).toHaveBeenCalledWith(4);
  });

  test('the arrow keys move the rating by half a star, within the five', async () => {
    const onRate = vi.fn();
    const { rerender } = renderWithProviders(<StarRating value={4} onRate={onRate} />);

    screen.getAllByRole('button')[0].focus();
    await userEvent.keyboard('{ArrowLeft}');
    expect(onRate).toHaveBeenLastCalledWith(3.5);
    await userEvent.keyboard('{ArrowRight}');
    expect(onRate).toHaveBeenLastCalledWith(4.5);

    onRate.mockClear();
    rerender(<StarRating value={5} onRate={onRate} />);
    screen.getAllByRole('button')[0].focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(onRate).not.toHaveBeenCalled();
  });
});
