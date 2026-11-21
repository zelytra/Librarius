import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { CATEGORY_PALETTE } from './categoryPalette';
import styles from './CategoriesPage.module.css';

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
