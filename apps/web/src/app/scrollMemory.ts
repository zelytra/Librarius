import { useEffect, useLayoutEffect, useRef, type RefObject } from 'react';
import { useLocation, useNavigationType } from 'react-router';

/** How long a restored position waits for the screen to grow tall enough again, in ms. */
const RESTORE_WINDOW_MS = 5000;

/**
 * Remembers where the shell's scrolling area was for every history entry (#600): coming back
 * to a screen — browser back, a back button — puts it where it was left, which in a library of
 * thousands of titles is the whole point; a new screen opens at its top.
 *
 * <p>The shell scrolls its own `<main>` rather than the window (see AppShell), so the router's
 * own scroll restoration cannot see it. Positions live for the session, keyed by the entry.
 */
export function useScrollMemory(area: RefObject<HTMLElement | null>): void {
  const location = useLocation();
  const navigationType = useNavigationType();
  const positions = scrollPositions;

  // The entry on screen, switched at commit time: the scroll event the switch itself causes —
  // the page shrinking under the old offset, the reset to the top — is dispatched before any
  // passive effect could swap a listener, and must not overwrite the entry just left.
  const current = useRef(location.key);

  // Every scroll is recorded against the entry on screen.
  useEffect(() => {
    const element = area.current;
    if (!element) return;
    const record = () => positions.set(current.current, element.scrollTop);
    element.addEventListener('scroll', record, { passive: true });
    return () => element.removeEventListener('scroll', record);
  }, [area, positions]);

  // A return goes back to the recorded place, waiting for the screen's data to make it tall
  // enough; anything else starts at the top.
  useLayoutEffect(() => {
    const element = area.current;
    current.current = location.key;
    if (!element) return;
    const saved = navigationType === 'POP' ? positions.get(location.key) : undefined;
    if (saved === undefined) {
      element.scrollTop = 0;
      return;
    }
    // The list comes back from the cache a page at a time and its covers settle: keep putting
    // the position back until it holds, the window runs out, or the reader scrolls themselves.
    const until = Date.now() + RESTORE_WINDOW_MS;
    let handle = 0;
    let stopped = false;
    const stop = () => {
      stopped = true;
    };
    const restore = () => {
      if (stopped) return;
      element.scrollTop = saved;
      if (element.scrollTop < saved - 1 && Date.now() < until) handle = requestAnimationFrame(restore);
    };
    element.addEventListener('wheel', stop, { passive: true });
    element.addEventListener('touchstart', stop, { passive: true });
    element.addEventListener('keydown', stop);
    restore();
    return () => {
      cancelAnimationFrame(handle);
      element.removeEventListener('wheel', stop);
      element.removeEventListener('touchstart', stop);
      element.removeEventListener('keydown', stop);
    };
  }, [area, location.key, navigationType, positions]);
}

const scrollPositions = new Map<string, number>();
