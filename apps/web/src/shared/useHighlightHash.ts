import { useEffect } from 'react';
import { useLocation } from 'react-router';

/** The class `global.css` animates; exported for the tests. */
export const HIGHLIGHT_CLASS = 'highlight-target';
const HIGHLIGHT_MS = 2400;

/**
 * A link into another screen names the part it is about in its hash — `/settings/reading#goal`
 * — and the screen brings that part into view and lights it up for a moment (#560), so the
 * reader sees at once where the thing they clicked lives. Smooth only when motion is welcome.
 */
export function useHighlightHash(): void {
  const { hash } = useLocation();
  useEffect(() => {
    if (!hash) return;
    const target = document.getElementById(decodeURIComponent(hash.slice(1)));
    if (!target) return;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    target.scrollIntoView?.({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
    target.classList.add(HIGHLIGHT_CLASS);
    const timer = window.setTimeout(() => target.classList.remove(HIGHLIGHT_CLASS), HIGHLIGHT_MS);
    return () => {
      window.clearTimeout(timer);
      target.classList.remove(HIGHLIGHT_CLASS);
    };
  }, [hash]);
}
