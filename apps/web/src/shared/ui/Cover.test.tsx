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

  /**
   * #398: coming back to a screen, the covers are in the cache and complete before any
   * `load` handler runs. They must show at once, not sit under a skeleton forever.
   */
  test('shows a cover already in the cache without waiting for a load event', () => {
    const complete = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'complete');
    const width = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'naturalWidth');
    Object.defineProperty(HTMLImageElement.prototype, 'complete', { configurable: true, get: () => true });
    Object.defineProperty(HTMLImageElement.prototype, 'naturalWidth', { configurable: true, get: () => 120 });
    try {
      const { container } = render(<Cover title="Berserk" imageUrl="https://covers.test/cached.jpg" />);

      expect(container.querySelector('[class*="skeleton"]')).not.toBeInTheDocument();
      expect(container.querySelector('img')).toHaveClass(/mediaLoaded/);
    } finally {
      if (complete) Object.defineProperty(HTMLImageElement.prototype, 'complete', complete);
      if (width) Object.defineProperty(HTMLImageElement.prototype, 'naturalWidth', width);
    }
  });

  /** And a load event that lands before the component settles is never undone. */
  test('keeps a cover loaded once its load event has fired', () => {
    const { container, rerender } = render(<Cover title="Berserk" imageUrl="https://covers.test/a.jpg" />);
    fireEvent.load(container.querySelector('img')!);
    rerender(<Cover title="Berserk" imageUrl="https://covers.test/a.jpg" />);

    expect(container.querySelector('img')).toHaveClass(/mediaLoaded/);
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

  test('keeps the title readable until the cover has loaded, then lets the cover take over', () => {
    const { container } = render(
      <Cover variant="tile" title="Dune" imageUrl="https://covers.test/slow.jpg" />,
    );

    // A cover still on its way (a slow or offline network) must not leave an anonymous tile.
    expect(screen.getByText('Dune')).toBeInTheDocument();

    fireEvent.load(container.querySelector('img')!);

    expect(screen.queryByText('Dune')).not.toBeInTheDocument();
  });
});
