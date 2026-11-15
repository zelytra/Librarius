import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { InfiniteMore } from './InfiniteMore';

/** An IntersectionObserver the test drives: `show()` brings the sentinel into view. */
function stubObserver() {
  let callback: IntersectionObserverCallback = () => {};
  const roots: (Element | Document | null | undefined)[] = [];
  class Observer {
    constructor(cb: IntersectionObserverCallback, options?: IntersectionObserverInit) {
      callback = cb;
      roots.push(options?.root);
    }
    observe() {}
    disconnect() {}
  }
  vi.stubGlobal('IntersectionObserver', Observer);
  return {
    roots,
    show: () => callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver),
  };
}

describe('InfiniteMore', () => {
  afterEach(() => vi.unstubAllGlobals());

  test('loads the next page as the end of the list comes into view, with no button', () => {
    const observer = stubObserver();
    const onMore = vi.fn();
    render(<InfiniteMore hasMore loading={false} onMore={onMore} label="Voir plus" />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    observer.show();
    expect(onMore).toHaveBeenCalledTimes(1);
  });

  test('does not ask twice while a page is loading, nor past the last one', () => {
    const observer = stubObserver();
    const onMore = vi.fn();
    const { rerender } = render(<InfiniteMore hasMore loading onMore={onMore} label="Voir plus" />);
    observer.show();
    expect(onMore).not.toHaveBeenCalled();

    rerender(<InfiniteMore hasMore={false} loading={false} onMore={onMore} label="Voir plus" />);
    expect(screen.queryByTestId('infinite-more')).not.toBeInTheDocument();
  });

  test('keeps a button where the browser cannot observe the end of the list', async () => {
    const onMore = vi.fn();
    render(<InfiniteMore hasMore loading={false} onMore={onMore} label="Voir plus" />);

    await userEvent.click(screen.getByRole('button', { name: 'Voir plus' }));
    expect(onMore).toHaveBeenCalledTimes(1);
  });

  /**
   * #473: the app scrolls inside its <main>, not the window. Observed against the viewport,
   * the look-ahead margin was ignored and a page only loaded once the reader hit the bottom.
   */
  test('watches the end of the list against the element that scrolls', () => {
    const observer = stubObserver();
    render(
      <main style={{ overflowY: 'auto' }} data-testid="scroller">
        <InfiniteMore hasMore loading={false} onMore={vi.fn()} label="Voir plus" />
      </main>,
    );
    expect(observer.roots.at(-1)).toBe(screen.getByTestId('scroller'));
  });
});
