import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

/** Pause in the typing before a search is sent. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * A free-text search kept in the URL's `q` (#424, #430): what is typed is the input, what is
 * searched is `search`, which follows the input after a short pause and survives a reload or
 * a trip to a detail screen and back.
 */
export function useUrlSearch(param = 'q') {
  const [params, setParams] = useSearchParams();
  const search = params.get(param) ?? '';
  const [input, setInput] = useState(search);
  useEffect(() => {
    const handle = setTimeout(() => {
      const q = input.trim();
      if (q === search) return;
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (q) next.set(param, q);
          else next.delete(param);
          return next;
        },
        { replace: true },
      );
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input, search, setParams, param]);
  return { input, setInput, search };
}
