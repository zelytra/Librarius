import type { ReactNode } from 'react';
import { Icon } from '../ui/Icon';
import type { Selection } from './useSelection';
import styles from './Selection.module.css';

/**
 * A tile that can be picked while a screen is selecting (#457): a tap ticks it instead of
 * opening the book, and a check mark says it is in.
 */
export function Selectable({
  id,
  label,
  selection,
  children,
}: {
  id: string | undefined;
  label: string;
  selection: Selection;
  children: ReactNode;
}) {
  if (!selection.active || !id) return <>{children}</>;
  const on = selection.selected.has(id);
  return (
    <div className={`${styles.selectable} ${on ? styles.on : ''}`}>
      {children}
      <button
        type="button"
        role="checkbox"
        aria-checked={on}
        aria-label={label}
        className={styles.overlay}
        onClick={() => selection.toggle(id)}
      >
        <span className={styles.tick}>{on && <Icon name="check_circle" size={22} fill color="var(--accent-deep)" />}</span>
      </button>
    </div>
  );
}
