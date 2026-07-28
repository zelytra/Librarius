import type { ReactNode } from 'react';
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

/** Two-column grid the fields sit in; a single column below the breakpoint. */
export function FieldGrid({ children }: { children: ReactNode }) {
  return <div className={styles.grid}>{children}</div>;
}
