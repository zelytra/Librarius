import type { CategoryDto } from '../../api/generated/librarius';
import styles from './CategoryChoice.module.css';

/** Opacity suffix of the selected category's background. */
const TINT = '22';

/**
 * Where a title is filed, picked from every category the caller has.
 *
 * <p>The row used to be narrowed to the three metals, which left a category created on the
 * Categories screen assignable from nowhere — and the `abandon` shelf, seeded for the
 * end-of-reading sheet, unreachable altogether. It now draws what `GET /api/categories`
 * returns, in the order it returns it: the four built-ins, then the caller's own.
 *
 * <p>Choosing the category already in force clears it, the way the stars clear a rating: a
 * title carries at most one rank, so there has to be a way back to none.
 */
export function CategoryChoice({
  categories,
  selectedId,
  onSelect,
}: {
  categories: CategoryDto[];
  selectedId?: string;
  onSelect: (categoryId: string | undefined) => void;
}) {
  return (
    <div className={styles.row}>
      {categories.map((c) => {
        const on = c.id === selectedId;
        return (
          <button
            key={c.id}
            type="button"
            onClick={() => onSelect(on ? undefined : c.id)}
            aria-pressed={on}
            className={styles.button}
            // The selected state is painted in the category's own colour, which the
            // stylesheet cannot know: it is stored per category, and a custom one gets a
            // colour of the server's choosing.
            style={on && c.color ? { borderColor: c.color, background: `${c.color}${TINT}` } : undefined}
          >
            {/* Same reason, and the fallback covers a payload that predates the colour. */}
            <span className={styles.dot} style={{ background: c.color ?? 'var(--chip)' }} />
            {c.label}
          </button>
        );
      })}
    </div>
  );
}
