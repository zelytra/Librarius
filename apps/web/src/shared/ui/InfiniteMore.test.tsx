import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { InfiniteMore } from './InfiniteMore';

/** An IntersectionObserver the test drives: `show()` brings the sentinel into view. */
function stubObserver(inView = { current: false }) {
  let callback: IntersectionObserverCallback = () => {};
  const roots: (Element | Document | null | undefined)[] = [];
  let created = 0;
  class Observer {
    constructor(cb: IntersectionObserverCallback, options?: IntersectionObserverInit) {
      callback = cb;
      roots.push(options?.root);
      created++;
    }
    // Like a real observer, the first notification reports where the target stands now.
    observe() {
      callback([{ isIntersecting: inView.current } as IntersectionObserverEntry], {} as IntersectionObserver);
    }
    disconnect() {}
  }
  vi.stubGlobal('IntersectionObserver', Observer);
  return {
    roots,
    created: () => created,
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

  /**
   * #481: after a fast fling the end of the list is still in view when a page lands. No new
   * crossing happens, so an observer kept across loads never spoke again and the list stalled;
   * a fresh one after each page reports where the end stands, and the next page follows.
   */
  test('asks for the next page when one lands with the end of the list still in view', () => {
    const inView = { current: true };
    const observer = stubObserver(inView);
    const onMore = vi.fn();
    const { rerender } = render(<InfiniteMore hasMore loading onMore={onMore} label="Voir plus" />);
    expect(onMore).not.toHaveBeenCalled();

    rerender(<InfiniteMore hasMore loading={false} onMore={onMore} label="Voir plus" />);
    expect(onMore).toHaveBeenCalledTimes(1);

    rerender(<InfiniteMore hasMore loading onMore={onMore} label="Voir plus" />);
    rerender(<InfiniteMore hasMore loading={false} onMore={onMore} label="Voir plus" />);
    expect(onMore).toHaveBeenCalledTimes(2);
    expect(observer.created()).toBeGreaterThanOrEqual(2);
  });
});
