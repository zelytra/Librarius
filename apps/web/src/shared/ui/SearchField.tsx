import { Icon } from './Icon';
import styles from './SearchField.module.css';

/** The search field of a list screen: a magnifier, an input, nothing else. */
export function SearchField({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  /** Placeholder and accessible name. */
  label: string;
}) {
  return (
    <div className={styles.field}>
      <Icon name="search" size={21} color="var(--faint)" />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={label}
        aria-label={label}
        className={styles.input}
      />
    </div>
  );
}
