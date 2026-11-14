import { useCallback, useState } from 'react';

/** How a series is named in a selection, next to the titles' own ids (#467). */
export const SERIES_KEY = 'series:';

/** A set of titles picked on a screen to act on together (#457). */
export interface Selection {
  active: boolean;
  selected: ReadonlySet<string>;
  start: () => void;
  stop: () => void;
  toggle: (id: string) => void;
}

export function useSelection(): Selection {
  const [active, setActive] = useState(false);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const start = useCallback(() => setActive(true), []);
  const stop = useCallback(() => {
    setActive(false);
    setSelected(new Set());
  }, []);
  const toggle = useCallback((id: string) => {
    setSelected((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);
  return { active, selected, start, stop, toggle };
}
