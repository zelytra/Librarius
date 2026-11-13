import { useEffect, useSyncExternalStore } from 'react';
import { Icon } from './Icon';
import {
  TOAST_DURATION,
  currentToasts,
  dismissToast,
  subscribeToasts,
  type ToastMessage,
} from './toast';
import styles from './Toaster.module.css';

function ToastItem({ toast }: { toast: ToastMessage }) {
  useEffect(() => {
    const timer = window.setTimeout(() => dismissToast(toast.id), TOAST_DURATION[toast.tone]);
    return () => window.clearTimeout(timer);
  }, [toast.id, toast.tone]);

  return (
    <div
      className={`${styles.toast} ${styles[toast.tone]}`}
      role={toast.tone === 'error' ? 'alert' : 'status'}
      onClick={() => dismissToast(toast.id)}
    >
      <span className={styles.icon}>
        <Icon name={toast.icon} size={20} color="currentColor" />
      </span>
      <span className={styles.message}>{toast.message}</span>
    </div>
  );
}

/**
 * Where the outcome messages of {@code toast.ts} appear (#413): above the bottom navigation
 * on a phone, at the bottom right past it. Mounted once, by the app shell.
 */
export function Toaster() {
  const toasts = useSyncExternalStore(subscribeToasts, currentToasts, currentToasts);
  return (
    <div className={styles.region} aria-live="polite">
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </div>
  );
}
