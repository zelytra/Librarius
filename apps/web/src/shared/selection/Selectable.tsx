import type { ReactNode } from 'react';
import { Icon } from '../ui/Icon';
import type { Selection } from './useSelection';
import styles from './Selection.module.css';

/**
 * A tile that can be picked while a screen is selecting (#457, #468): a tap anywhere on it
 * ticks it instead of opening it. The frame and the check mark sit on the cover itself —
 * the same 0.68 ratio and radius as every tile — so they hug the book rather than the cell
 * and its caption.
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
      <span className={styles.frame} aria-hidden="true">
        <span className={styles.tick}>
          <Icon name={on ? 'check_circle' : 'radio_button_unchecked'} size={22} fill={on}
            color={on ? 'var(--accent-deep)' : 'var(--muted)'} />
        </span>
      </span>
      <button
        type="button"
        role="checkbox"
        aria-checked={on}
        aria-label={label}
        className={styles.hit}
        onClick={() => selection.toggle(id)}
      />
    </div>
  );
}
