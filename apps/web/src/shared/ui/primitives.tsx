import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react';

/** Carte de surface arrondie. */
export function Card({
  children,
  style,
  onClick,
}: {
  children: ReactNode;
  style?: CSSProperties;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 18,
        boxShadow: 'var(--shadow)',
        cursor: onClick ? 'pointer' : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
}

/** Bouton principal / secondaire / fantôme. */
export function Button({ variant = 'primary', style, children, ...rest }: ButtonProps) {
  const variants: Record<ButtonVariant, CSSProperties> = {
    primary: {
      background: 'var(--accent)',
      color: '#fff',
      border: 'none',
      boxShadow: '0 8px 18px -8px rgba(122,143,115,0.7)',
    },
    secondary: {
      background: 'var(--surface)',
      color: 'var(--ink-soft)',
      border: '1.5px solid var(--line)',
    },
    ghost: { background: 'transparent', color: 'var(--ink-soft)', border: 'none' },
  };
  return (
    <button
      style={{
        borderRadius: 15,
        padding: '14px 18px',
        fontSize: 15,
        fontWeight: 600,
        cursor: 'pointer',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        ...variants[variant],
        ...style,
      }}
      {...rest}
    >
      {children}
    </button>
  );
}

/** Pastille / étiquette ronde. */
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
      style={{
        flex: '0 0 auto',
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '8px 14px',
        borderRadius: 30,
        cursor: onClick ? 'pointer' : 'default',
        fontSize: 13,
        fontWeight: 600,
        fontFamily: 'inherit',
        border: selected ? '1.5px solid var(--accent)' : '1.5px solid var(--line)',
        background: selected ? 'var(--accent-soft)' : 'var(--surface)',
        color: selected ? 'var(--accent-deep)' : 'var(--ink-soft)',
      }}
    >
      {dotColor && (
        <span
          style={{ width: 9, height: 9, borderRadius: '50%', background: dotColor, flex: '0 0 auto' }}
        />
      )}
      {children}
    </button>
  );
}

/** Sélecteur segmenté (2+ options exclusives). */
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
    <div style={{ display: 'flex', background: 'var(--chip)', borderRadius: 14, padding: 4 }}>
      {options.map((opt) => {
        const on = opt.id === value;
        return (
          <button
            key={opt.id}
            onClick={() => onChange(opt.id)}
            style={{
              flex: 1,
              border: 'none',
              cursor: 'pointer',
              padding: '10px 0',
              borderRadius: 11,
              fontSize: 13.5,
              fontWeight: 600,
              fontFamily: 'inherit',
              background: on ? 'var(--surface)' : 'transparent',
              color: on ? 'var(--ink)' : 'var(--muted)',
              boxShadow: on ? '0 3px 8px -4px rgba(74,64,52,0.35)' : 'none',
            }}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}

/** En-tête de section avec action optionnelle à droite. */
export function SectionHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        justifyContent: 'space-between',
        marginBottom: 12,
      }}
    >
      <h3 style={{ fontSize: 18 }}>{title}</h3>
      {action && <span style={{ fontSize: 12.5, color: 'var(--muted)', fontWeight: 500 }}>{action}</span>}
    </div>
  );
}
