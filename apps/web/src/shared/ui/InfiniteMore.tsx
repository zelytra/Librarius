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
  // The latest state, read by an observer created once.
  const state = useRef({ hasMore, loading, onMore });
  state.current = { hasMore, loading, onMore };

  useEffect(() => {
    const node = sentinel.current;
    if (!node || !observes || !hasMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const { hasMore: more, loading: busy, onMore: load } = state.current;
        if (entries.some((e) => e.isIntersecting) && more && !busy) load();
      },
      // Start a screen ahead, so the next covers are there when the reader gets to them.
      { rootMargin: '0px 0px 800px 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [observes, hasMore]);

  // Once a page lands with the sentinel still in view, the observer does not fire again on
  // its own: ask for the next one while the end is still showing.
  const wasLoading = useRef(loading);
  useEffect(() => {
    const finished = wasLoading.current && !loading;
    wasLoading.current = loading;
    const node = sentinel.current;
    if (!finished || !node || !observes || !hasMore) return;
    if (node.getBoundingClientRect().top < window.innerHeight + 800) state.current.onMore();
  }, [loading, observes, hasMore]);

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
