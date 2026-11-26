import type { CategoryDto } from '../../api/generated/librarius';
import { Icon } from '../../shared/ui/Icon';
import styles from './CategoryChoice.module.css';

/** Opacity suffix of the selected category's background. */
const TINT = '22';

type Choice =
  /** One category at most — a rank: choosing the one in force clears it, as the stars clear a rating. */
  | { selectedId?: string; onSelect: (categoryId: string | undefined) => void; selectedIds?: never; onToggle?: never }
  /** Any number of them — the reader's own categories (#646): each button files or unfiles. */
  | { selectedIds: string[]; onToggle: (categoryId: string) => void; selectedId?: never; onSelect?: never };

/**
 * Where a title is filed, picked from the categories given, in the order given.
 *
 * <p>A title carries one rank — a built-in shelf, Platine to Abandon — and any number of the
 * reader's own categories (#646): the same row of buttons serves both, as a single choice or
 * as toggles, so "Or + Polar" reads as two pressed buttons in two rows.
 */
export function CategoryChoice({ categories, label, ...choice }: { categories: CategoryDto[]; label?: string } & Choice) {
  return (
    <div className={styles.row} role="group" aria-label={label}>
      {categories.map((c) => {
        const on = choice.selectedIds ? choice.selectedIds.includes(c.id ?? '') : c.id === choice.selectedId;
        const press = () => {
          if (choice.onToggle) choice.onToggle(c.id ?? '');
          else choice.onSelect(on ? undefined : c.id);
        };
        return (
          <button
            key={c.id}
            type="button"
            onClick={press}
            aria-pressed={on}
            className={styles.button}
            // The selected state is painted in the category's own colour, which the
            // stylesheet cannot know: it is stored per category, and a custom one gets a
            // colour of the server's choosing.
            style={on && c.color ? { borderColor: c.color, background: `${c.color}${TINT}` } : undefined}
          >
            {/* Selected, a check stands in for the dot, so the state never rests on a colour
                alone — an imported category may have none (#648). */}
            {on ? (
              <Icon name="check_circle" size={16} fill color={c.color ?? 'var(--accent-deep)'} />
            ) : (
              <span className={styles.dot} style={{ background: c.color ?? 'var(--chip)' }} />
            )}
            {c.label}
          </button>
        );
      })}
    </div>
  );
}
