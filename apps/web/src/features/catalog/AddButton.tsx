import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { StatusPicker } from '../../shared/ui/StatusPicker';
import { DEFAULT_ADD_TARGET, type AddTarget } from './addTargets';
import styles from './AddButton.module.css';

/**
 * The one way to add a title (#399): a single "Ajouter" button, then a sheet asking where it
 * goes — its reading status and whether the reader has it (#411), owned and on the to-read
 * pile by default. Every screen that adds a title from the catalog uses it, so the choice
 * reads the same everywhere.
 */
export function AddButton({
  onAdd,
  compact = false,
}: {
  /** Files the title; a rejection is reported by the caller and keeps the sheet open. */
  onAdd: (target: AddTarget) => Promise<void>;
  /** The small form a search result uses; the default spans its block. */
  compact?: boolean;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      {compact ? (
        <button type="button" className={styles.compact} onClick={() => setOpen(true)}>
          <Icon name="add" size={16} color="var(--on-accent)" />
          {t('add.open')}
        </button>
      ) : (
        <Button size="block" onClick={() => setOpen(true)}>
          <Icon name="add" size={18} color="var(--on-accent)" />
          {t('add.open')}
        </Button>
      )}
      {open && <AddSheet onAdd={onAdd} onClose={() => setOpen(false)} />}
    </>
  );
}

function AddSheet({ onAdd, onClose }: { onAdd: (target: AddTarget) => Promise<void>; onClose: () => void }) {
  const { t } = useTranslation();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);
  // Owned and on the to-read pile: a title one adds is most often one just bought.
  const [target, setTarget] = useState<AddTarget>(DEFAULT_ADD_TARGET);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    panel.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function confirm() {
    setPending(true);
    try {
      await onAdd(target);
      onClose();
    } catch {
      setPending(false);
    }
  }

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
        <h3 id={headingId} className={styles.title}>
          {t('add.title')}
        </h3>
        <div className={styles.choices}>
          <StatusPicker
            status={target.status}
            ownership={target.ownership}
            onStatus={(status) => setTarget((current) => ({ ...current, status }))}
            onOwnership={(ownership) => setTarget((current) => ({ ...current, ownership }))}
            disabled={pending}
          />
        </div>
        <div className={styles.actions}>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            {t('add.cancel')}
          </Button>
          <Button onClick={() => void confirm()} disabled={pending}>
            {t(pending ? 'common.working' : 'add.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
