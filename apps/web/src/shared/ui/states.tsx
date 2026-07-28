import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import { Button } from './primitives';
import styles from './states.module.css';

/**
 * The three non-nominal states every screen can end up in. They live here so a failing
 * call never renders as an empty screen, and so the wording and the layout stay the same
 * wherever the failure happens.
 */

/** Data on its way. */
export function Loading({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <p className={styles.loading} role="status">
      {label ?? t('common.loading')}
    </p>
  );
}

interface ErrorStateProps {
  /** Overrides the generic heading when the screen can say something more precise. */
  title?: string;
  message?: string;
  /** Omitted when nothing can be retried — a failed mutation, for instance. */
  onRetry?: () => void;
  retryLabel?: string;
}

/** A call failed: say so, and offer the action that fixes it. */
export function ErrorState({ title, message, onRetry, retryLabel }: ErrorStateProps) {
  const { t } = useTranslation();
  return (
    <div className={`${styles.state} ${styles.error}`} role="alert">
      <Icon name="cloud_off" size={40} />
      <p className={styles.title}>{title ?? t('errors.title')}</p>
      <p className={styles.message}>{message ?? t('errors.message')}</p>
      {onRetry && (
        <div className={styles.action}>
          <Button variant="secondary" onClick={onRetry}>
            {retryLabel ?? t('common.retry')}
          </Button>
        </div>
      )}
    </div>
  );
}

interface EmptyStateProps {
  /** Material Symbols name, picked to echo what is missing. */
  icon: string;
  iconSize?: number;
  title: string;
  description?: string;
  /** The way out of the empty screen — usually a button towards Discover. */
  action?: ReactNode;
  /** The screen supplies its own padding; it differs from one to the next. */
  className?: string;
}

/** Nothing to show, and what the user can do about it. */
export function EmptyState({
  icon,
  iconSize = 42,
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div className={`${styles.state} ${className ?? ''}`}>
      <Icon name={icon} size={iconSize} />
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.message}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}
