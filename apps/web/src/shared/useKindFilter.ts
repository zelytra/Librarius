import { useSearchParams } from 'react-router';
import type { Kind } from '../api/generated/librarius';
import { KINDS } from './kinds';

/** The kind a list is narrowed to, kept in the URL's `kind`; undefined for every kind. */
export function useKindFilter(): [Kind | undefined, (kind: Kind | undefined) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('kind');
  const kind = KINDS.some((k) => k.id === raw) ? (raw as Kind) : undefined;
  const setKind = (next: Kind | undefined) =>
    setParams(
      (prev) => {
        const out = new URLSearchParams(prev);
        if (next) out.set('kind', next);
        else out.delete('kind');
        return out;
      },
      { replace: true },
    );
  return [kind, setKind];
}

