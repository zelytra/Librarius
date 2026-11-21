import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import styles from './CategoriesPage.module.css';

/**
 * The colours a category of one's own may wear (#599): deep enough for the white medal icon
 * drawn on them to stay legible (4.5:1 at least against white, measured), named for a screen reader.
 */
export const CATEGORY_PALETTE: readonly { color: string; nameKey: string }[] = [
  { color: '#5f6c57', nameKey: 'categories.colors.sage' },
  { color: '#3b7a57', nameKey: 'categories.colors.green' },
  { color: '#2f6f8f', nameKey: 'categories.colors.teal' },
  { color: '#5b5ea6', nameKey: 'categories.colors.indigo' },
  { color: '#8a4f9e', nameKey: 'categories.colors.plum' },
  { color: '#b04a5a', nameKey: 'categories.colors.raspberry' },
  { color: '#a5672a', nameKey: 'categories.colors.amber' },
  { color: '#6b6b6b', nameKey: 'categories.colors.grey' },
];

/** One swatch per colour, a radio group: one is always picked. */
export function ColorPicker({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (color: string) => void;
  label: string;
}) {
  const { t } = useTranslation();
  return (
    <div role="radiogroup" aria-label={label} className={styles.palette}>
      {CATEGORY_PALETTE.map(({ color, nameKey }) => {
        const picked = color.toLowerCase() === value.toLowerCase();
        return (
          <button
            key={color}
            type="button"
            role="radio"
            aria-checked={picked}
            aria-label={t(nameKey)}
            title={t(nameKey)}
            className={styles.swatch}
            // The colour is the swatch's whole point: it cannot come from a stylesheet.
            style={{ background: color }}
            onClick={() => onChange(color)}
          >
            {picked && <Icon name="check_circle" size={18} color="#fff" />}
          </button>
        );
      })}
    </div>
  );
}
