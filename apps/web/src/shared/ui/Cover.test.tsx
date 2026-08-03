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

  test('gives up on the skeleton if the cover fails to load, without printing the title over it', () => {
    const { container } = render(<Cover title="Berserk" imageUrl="https://covers.test/missing.jpg" />);
    const img = container.querySelector('img')!;

    fireEvent.error(img);

    expect(container.querySelector('[class*="skeleton"]')).not.toBeInTheDocument();
    expect(screen.queryByText('Berserk')).not.toBeInTheDocument();
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
