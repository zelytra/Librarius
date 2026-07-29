import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import { Button } from './primitives';
import styles from './states.module.css';

/**
 * The three non-nominal states every screen can end up in. They live here so a failing
 * call never renders as an empty screen, and so the wording and the layout stay the same
 * wherever the failure happens.
 */

/**
 * How long a wait has to last before it is worth announcing. Under this, the indicator
 * would appear and disappear inside the blink it was meant to explain, which reads as a
 * glitch rather than as feedback — most calls to this API answer well below it.
 */
export const LOADING_DELAY_MS = 400;

/**
 * How long the indicator stays once it has appeared. Without a floor, an answer landing
 * just past the delay above would paint it for a single frame, which is the same flicker
 * seen from the other end.
 */
export const LOADING_MIN_VISIBLE_MS = 400;

/**
 * Whether the wait has lasted long enough to be shown, and has not been on screen long
 * enough to be taken away again. Both thresholds are about flicker, not about the wait
 * itself: a fast round trip is shown as nothing at all.
 */
function useIndicatorVisible(pending: boolean): boolean {
  const [visible, setVisible] = useState(false);
  const shownAt = useRef(0);

  useEffect(() => {
    // Waiting and already shown, or idle and already hidden: nothing to schedule.
    if (pending === visible) return;

    if (pending) {
      const timer = setTimeout(() => {
        shownAt.current = Date.now();
        setVisible(true);
      }, LOADING_DELAY_MS);
      return () => clearTimeout(timer);
    }

    const left = LOADING_MIN_VISIBLE_MS - (Date.now() - shownAt.current);
    if (left <= 0) {
      setVisible(false);
      return;
    }
    const timer = setTimeout(() => setVisible(false), left);
    return () => clearTimeout(timer);
  }, [pending, visible]);

  return visible;
}

/** `large` fills a screen that has nothing to show yet; `compact` sits inside a control. */
type LoadingSize = 'large' | 'compact';

interface LoadingProps {
  size?: LoadingSize;
  /** Overrides the generic wording. The large format prints it, the compact one hides it. */
  label?: string;
  /**
   * Whether the wait is still running. A mounted `Loading` is a wait by definition, so a
   * screen that swaps it for its content on arrival passes nothing; an action passes its
   * mutation's `isPending` and leaves the component mounted, which is what lets the
   * minimum visible duration outlive the end of the wait.
   */
  pending?: boolean;
}

/**
 * Data on its way, in the one shape the whole app waits in.
 *
 * <p>The animated mark is a placeholder for the animated logo planned for later. It is
 * drawn here and nowhere else, so that swap is this component plus one CSS rule rather
 * than every call site.
 */
export function Loading({ size = 'large', label, pending = true }: LoadingProps) {
  const { t } = useTranslation();
  const visible = useIndicatorVisible(pending);
  if (!visible) return null;

  const text = label ?? t('common.loading');
  if (size === 'compact') {
    return (
      <span className={styles.loadingCompact} role="status">
        <span className={`${styles.mark} ${styles.markCompact}`} />
        <span className={styles.srOnly}>{text}</span>
      </span>
    );
  }
  return (
    <div className={styles.loadingLarge} role="status">
      <span className={`${styles.mark} ${styles.markLarge}`} />
      <p className={styles.loadingLabel}>{text}</p>
    </div>
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
