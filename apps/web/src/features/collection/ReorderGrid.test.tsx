import { afterEach, describe, expect, test, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { useState } from 'react';
import { ReorderGrid } from './ReorderGrid';
import '../../i18n';

const shown = () => screen.getAllByTestId('entry').map((el) => el.textContent);

/** The grid under a parent that, like the shelf, shows the order it is handed back. */
function Shelf({ initial, onCommit }: { initial: string[]; onCommit: (keys: string[]) => void }) {
  const [keys, setKeys] = useState(initial);
  return (
    <ReorderGrid
      keys={keys}
      render={(key) => <span data-testid="entry">{key}</span>}
      onCommit={(next) => {
        onCommit(next);
        setKeys(next);
      }}
    />
  );
}

function grid(keys: string[], onCommit = vi.fn()) {
  const view = render(<Shelf initial={keys} onCommit={onCommit} />);
  return { ...view, onCommit };
}

const cellOf = (key: string) => screen.getByText(key).closest<HTMLElement>('[data-reorder-key]')!;
const handleOf = (key: string) => within(cellOf(key)).getByRole('button', { name: /glisser/i });

/** The pointer is over `key`'s cell, wherever the event lands (jsdom does no layout). */
const pointerOver = (key: string) => {
  document.elementFromPoint = () => cellOf(key);
};

afterEach(() => {
  delete (document as { elementFromPoint?: unknown }).elementFromPoint;
});

describe('ReorderGrid', () => {
  test('shows the entries in the order it is given', () => {
    grid(['a', 'b', 'c']);
    expect(shown()).toEqual(['a', 'b', 'c']);
  });

  test('follows a new sort or filter rather than keeping the order it had (#486)', () => {
    const entry = (k: string) => <span data-testid="entry">{k}</span>;
    const { rerender } = render(<ReorderGrid keys={['a', 'b', 'c']} render={entry} onCommit={vi.fn()} />);
    rerender(<ReorderGrid keys={['c', 'a', 'b']} render={entry} onCommit={vi.fn()} />);
    expect(shown()).toEqual(['c', 'a', 'b']);
    rerender(<ReorderGrid keys={['b', 'd']} render={entry} onCommit={vi.fn()} />);
    expect(shown()).toEqual(['b', 'd']);
  });

  test('moves an entry one place with its buttons', () => {
    const { onCommit } = grid(['a', 'b', 'c']);
    fireEvent.click(within(cellOf('b')).getByRole('button', { name: /avant/i }));
    expect(onCommit).toHaveBeenCalledWith(['b', 'a', 'c']);
    fireEvent.click(within(cellOf('b')).getByRole('button', { name: /après/i }));
    expect(onCommit).toHaveBeenLastCalledWith(['a', 'b', 'c']);
  });

  test('drags an entry over another and hands the new order back on release', () => {
    const { onCommit } = grid(['a', 'b', 'c']);
    fireEvent.pointerDown(handleOf('a'), { pointerId: 1 });
    pointerOver('c');
    fireEvent.pointerMove(window, { pointerId: 1 });
    expect(shown()).toEqual(['b', 'c', 'a']);
    expect(onCommit).not.toHaveBeenCalled();
    fireEvent.pointerUp(window, { pointerId: 1 });
    expect(onCommit).toHaveBeenCalledWith(['b', 'c', 'a']);
  });

  test('a release anywhere on the page ends the drag, not only over the handle (#486)', () => {
    const { onCommit } = grid(['a', 'b', 'c']);
    fireEvent.pointerDown(handleOf('a'), { pointerId: 1 });
    pointerOver('b');
    fireEvent.pointerMove(window, { pointerId: 1 });
    fireEvent.pointerUp(document.body, { pointerId: 1 });
    expect(onCommit).toHaveBeenCalledTimes(1);
    expect(cellOf('a').className).not.toMatch(/dragging/);
    // Once released, moving the pointer moves nothing any more.
    pointerOver('c');
    fireEvent.pointerMove(window, { pointerId: 1 });
    expect(shown()).toEqual(['b', 'a', 'c']);
  });

  test('a cancelled gesture ends the drag and puts the shelf back as it was', () => {
    const { onCommit } = grid(['a', 'b']);
    fireEvent.pointerDown(handleOf('a'), { pointerId: 1 });
    pointerOver('b');
    fireEvent.pointerMove(window, { pointerId: 1 });
    fireEvent.pointerCancel(window, { pointerId: 1 });
    expect(cellOf('a').className).not.toMatch(/dragging/);
    expect(shown()).toEqual(['a', 'b']);
    expect(onCommit).not.toHaveBeenCalled();
  });

  test('a release with nothing moved saves nothing', () => {
    const { onCommit } = grid(['a', 'b']);
    fireEvent.pointerDown(handleOf('a'), { pointerId: 1 });
    fireEvent.pointerUp(window, { pointerId: 1 });
    expect(onCommit).not.toHaveBeenCalled();
  });
});
