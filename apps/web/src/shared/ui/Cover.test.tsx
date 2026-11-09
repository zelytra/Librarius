import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { Cover } from './Cover';

/**
 * Only the loading sequence of a real cover is exercised here: the no-cover
 * rendering (the coloured block with the title printed on it) has no state to
 * get wrong, and is already covered indirectly by the screens that render it.
 */
describe('Cover', () => {
  test('shows no skeleton for the generated placeholder — nothing there ever loads', () => {
    const { container } = render(<Cover title="Berserk" />);

    expect(container.querySelector('[class*="skeleton"]')).not.toBeInTheDocument();
    expect(container.querySelector('img')).not.toBeInTheDocument();
  });

  test('shows the skeleton while a real cover is still loading', () => {
    const { container } = render(<Cover title="Berserk" imageUrl="https://covers.test/berserk.jpg" />);

    expect(container.querySelector('[class*="skeleton"]')).toBeInTheDocument();
    const img = screen.getByRole('presentation', { hidden: true });
    expect(img).not.toHaveClass(/mediaLoaded/);
  });

  test('fades the skeleton out once the image has loaded', () => {
    const { container } = render(<Cover title="Berserk" imageUrl="https://covers.test/berserk.jpg" />);
    const img = container.querySelector('img')!;

    fireEvent.load(img);

    expect(container.querySelector('[class*="skeleton"]')).not.toBeInTheDocument();
    expect(img).toHaveClass(/mediaLoaded/);
  });

  test('falls back to the titled placeholder when the cover fails to load', () => {
    const { container } = render(<Cover title="Berserk" imageUrl="https://covers.test/missing.jpg" />);
    const img = container.querySelector('img')!;

    fireEvent.error(img);

    // The broken image is dropped rather than left on top, and the placeholder prints the title
    // it prints for a title with no cover at all: a cover service answering 404 used to leave an
    // anonymous coloured tile nobody could tell apart from its neighbours.
    expect(container.querySelector('[class*="skeleton"]')).not.toBeInTheDocument();
    expect(container.querySelector('img')).not.toBeInTheDocument();
    expect(screen.getByText('Berserk')).toBeInTheDocument();
  });

  test('prints the title and the volume tag on a tile whose cover failed', () => {
    const { container } = render(
      <Cover variant="tile" title="One Piece Tome 3" tag="T.3" imageUrl="https://covers.test/missing.jpg" />
    );

    fireEvent.error(container.querySelector('img')!);

    expect(screen.getByText('One Piece Tome 3')).toBeInTheDocument();
    expect(screen.getByText('T.3')).toBeInTheDocument();
  });

  test('starts a fresh load when the image URL changes rather than keeping the old one loaded', () => {
    const { container, rerender } = render(
      <Cover title="Berserk" imageUrl="https://covers.test/berserk-v1.jpg" />,
    );
    fireEvent.load(container.querySelector('img')!);
    expect(container.querySelector('img')).toHaveClass(/mediaLoaded/);

    rerender(<Cover title="Berserk" imageUrl="https://covers.test/berserk-v2.jpg" />);

    expect(container.querySelector('[class*="skeleton"]')).toBeInTheDocument();
    expect(container.querySelector('img')).not.toHaveClass(/mediaLoaded/);
  });
});
