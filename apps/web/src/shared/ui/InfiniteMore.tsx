import { useEffect, useRef, type ReactNode } from 'react';
import { Button } from './primitives';
import { Loading } from './states';

/**
 * The end of a paged list (#450): the next page loads by itself as the end comes into view,
 * well before the reader reaches it, instead of behind a button to press again and again.
 *
 * <p>A browser without `IntersectionObserver` (and the test DOM) keeps the button, so the list
 * is never stuck at its first page.
 */
/** The nearest ancestor that scrolls, or null for the viewport. The app scrolls in its <main>. */
function scrollParent(node: HTMLElement): HTMLElement | null {
  for (let el = node.parentElement; el; el = el.parentElement) {
    if (/(auto|scroll)/.test(getComputedStyle(el).overflowY)) return el;
  }
  return null;
}

export function InfiniteMore({
  hasMore,
  loading,
  onMore,
  label,
  className,
}: {
  hasMore: boolean;
  loading: boolean;
  onMore: () => void;
  /** The fallback button's text, also read out while a page loads. */
  label: ReactNode;
  className?: string;
}) {
  const sentinel = useRef<HTMLDivElement>(null);
  const observes = typeof IntersectionObserver !== 'undefined';
  // The latest callback, read by the observer without re-creating it when only it changes.
  const more = useRef(onMore);
  more.current = onMore;

  // One observer per page (#481): none while a page loads, a fresh one once it has landed.
  // A new observer's first notification says where the end of the list stands right now, so
  // a page that lands with the end still in view — after a fast fling — asks for the next one;
  // an observer kept across loads only spoke on a crossing, and after a fling none came.
  useEffect(() => {
    const node = sentinel.current;
    if (!node || !observes || !hasMore || loading) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          // One request per observer: the next observer takes over once the page lands.
          observer.disconnect();
          more.current();
        }
      },
      // Start a screen ahead, so the next covers are there when the reader gets to them. The
      // margin only extends the root, so the root is the element that actually scrolls (#473).
      { root: scrollParent(node), rootMargin: '0px 0px 800px 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [observes, hasMore, loading]);

  if (!hasMore) return null;
  return (
    <div ref={sentinel} className={className} data-testid="infinite-more">
      {observes ? (
        <Loading size="compact" pending={loading} />
      ) : (
        <Button variant="secondary" disabled={loading} onClick={onMore}>
          {label}
        </Button>
      )}
    </div>
  );
}
