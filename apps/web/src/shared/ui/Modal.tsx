import { useEffect, useId, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';
import styles from './Modal.module.css';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  /** Shown under the title. */
  description?: ReactNode;
}

/**
 * A dialog over the screen it was opened from (#416): full screen on a phone, a centred panel
 * past the tablet width. Escape, the close button and a click on the scrim all close it; the
 * focus moves into it on opening and the page behind it stops scrolling while it is open.
 */
export function Modal({ title, onClose, children, description }: ModalProps) {
  const { t } = useTranslation();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    panel.current?.focus();
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  return (
    <div className={styles.scrim} onClick={onClose}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        tabIndex={-1}
        className={styles.panel}
        onClick={(event) => event.stopPropagation()}
      >
        <div className={styles.head}>
          <h2 id={headingId} className={styles.title}>
            {title}
          </h2>
          <button type="button" onClick={onClose} aria-label={t('common.close')} className={styles.close}>
            <Icon name="close" size={20} color="var(--ink-soft)" />
          </button>
        </div>
        {description && <div className={styles.description}>{description}</div>}
        <div className={styles.body}>{children}</div>
      </div>
    </div>
  );
}
