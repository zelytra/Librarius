import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react';
import styles from './primitives.module.css';

/** Joins the class names that are actually set. */
function cx(...names: (string | false | undefined)[]): string {
  return names.filter(Boolean).join(' ');
}

/** Padded page container — every screen sits in one. */
export function Screen({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cx(styles.screen, className)}>{children}</div>;
}

/**
 * Screen heading. The screen supplies its own bottom margin through
 * `className`, since it differs from one to the next.
 */
export function ScreenTitle({ children, className }: { children: ReactNode; className?: string }) {
  return <h2 className={cx(styles.screenTitle, className)}>{children}</h2>;
}

/** Rounded surface card. */
export function Card({
  children,
  className,
  style,
  onClick,
}: {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={cx(styles.card, onClick && styles.clickable, className)}
      style={style}
    >
      {children}
    </div>
  );
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

/**
 * `lg` and `block` are the two stacked calls to action on Detail, `compact`
 * the button glued to the import field.
 */
type ButtonSize = 'default' | 'lg' | 'block' | 'compact';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: styles.primary,
  secondary: styles.secondary,
  ghost: styles.ghost,
};

const BUTTON_SIZES: Record<ButtonSize, string> = {
  default: '',
  lg: styles.sizeLg,
  block: styles.sizeBlock,
  compact: styles.sizeCompact,
};

/** Primary / secondary / ghost button. */
export function Button({
  variant = 'primary',
  size = 'default',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={cx(styles.button, BUTTON_VARIANTS[variant], BUTTON_SIZES[size], className)}
      {...rest}
    >
      {children}
    </button>
  );
}

/** Pill-shaped badge / tag. */
export function Chip({
  children,
  selected = false,
  onClick,
  dotColor,
}: {
  children: ReactNode;
  selected?: boolean;
  onClick?: () => void;
  dotColor?: string;
}) {
  return (
    <button
      onClick={onClick}
      className={cx(styles.chip, onClick && styles.chipClickable, selected && styles.chipSelected)}
    >
      {/* The dot carries the rank colour, which only the caller knows. */}
      {dotColor && <span className={styles.chipDot} style={{ background: dotColor }} />}
      {children}
    </button>
  );
}

/** Segmented control (2+ mutually exclusive options). */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { id: T; label: string }[];
  value: T;
  onChange: (id: T) => void;
}) {
  return (
    <div className={styles.segmented}>
      {options.map((opt) => (
        <button
          key={opt.id}
          // Explicit, because a button inside a form submits it by default: picking an
          // option is a choice, never the validation of the form around it.
          type="button"
          onClick={() => onChange(opt.id)}
          className={cx(styles.segment, opt.id === value && styles.segmentOn)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

/** Section header with an optional action on the right. */
export function SectionHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className={styles.sectionHeader}>
      <h3 className={styles.sectionTitle}>{title}</h3>
      {action && <span className={styles.sectionAction}>{action}</span>}
    </div>
  );
}

type StatusTone = 'muted' | 'faint' | 'error';

const STATUS_TONES: Record<StatusTone, string> = {
  muted: styles.statusMuted,
  faint: styles.statusFaint,
  error: styles.statusError,
};

/** One-line status paragraph: loading, error, or an invitation to act. */
export function StatusText({
  tone = 'muted',
  children,
}: {
  tone?: StatusTone;
  children: ReactNode;
}) {
  return <p className={cx(styles.status, STATUS_TONES[tone])}>{children}</p>;
}
