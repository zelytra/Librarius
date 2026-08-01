import type { ReactNode } from 'react';
import { Chip } from '../../shared/ui/primitives';
import styles from './fields.module.css';

/**
 * Labelled fields shared by the two forms this screen carries — the advanced search
 * criteria and the manual entry. Wrapping the control in its `<label>` is what gives every
 * field an accessible name without a matching `id` to keep in step.
 */

interface FieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'number' | 'url';
  /** Reserved for the fields whose value is a number typed on a phone. */
  inputMode?: 'numeric' | 'text';
  required?: boolean;
}

export function Field({ label, value, onChange, type = 'text', inputMode, required }: FieldProps) {
  return (
    <label className={styles.field}>
      <span className={styles.label}>{label}</span>
      <input
        type={type}
        inputMode={inputMode}
        required={required}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={styles.control}
      />
    </label>
  );
}

interface SelectFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}

export function SelectField({ label, value, onChange, options }: SelectFieldProps) {
  return (
    <label className={styles.field}>
      <span className={styles.label}>{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={styles.control}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

interface MultiSelectFieldProps<T extends string> {
  label: string;
  values: T[];
  onChange: (values: T[]) => void;
  options: { value: T; label: string }[];
}

/**
 * A field the caller can narrow to zero, one or several values at once — the medium filter
 * is currently the only user. Wrapped in the same `.field` shape `Field` and `SelectField`
 * use, so it reads as one more labelled criterion in the grid, but the control itself is a
 * row of toggleable chips rather than a native element: a `<select multiple>` renders as a
 * scrollable listbox, worse than a row the eye takes in at once for five short, independent
 * options none of which excludes another.
 */
export function MultiSelectField<T extends string>({
  label,
  values,
  onChange,
  options,
}: MultiSelectFieldProps<T>) {
  function toggle(value: T) {
    onChange(values.includes(value) ? values.filter((v) => v !== value) : [...values, value]);
  }
  return (
    <div className={`${styles.field} ${styles.wide}`}>
      <span className={styles.label}>{label}</span>
      <div className={styles.chipRow}>
        {options.map((o) => (
          <Chip key={o.value} selected={values.includes(o.value)} onClick={() => toggle(o.value)}>
            {o.label}
          </Chip>
        ))}
      </div>
    </div>
  );
}

/** Two-column grid the fields sit in; a single column below the breakpoint. */
export function FieldGrid({ children }: { children: ReactNode }) {
  return <div className={styles.grid}>{children}</div>;
}
