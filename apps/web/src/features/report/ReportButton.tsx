import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { Loading } from '../../shared/ui/states';
import {
  ReportReason,
  usePostApiReports,
  type ReportTargetType,
} from '../../api/generated/librarius';
import styles from './ReportButton.module.css';

/** The reasons the picklist offers, in the order it shows them. */
const REASONS: ReportReason[] = [
  ReportReason.WRONG_COVER,
  ReportReason.WRONG_INFO,
  ReportReason.DUPLICATE,
  ReportReason.OTHER,
];

/**
 * "Signaler une erreur" on a shared catalog object — a work, an edition or a series (#192).
 *
 * <p>Opens a small dialog with a short reason picklist and an optional comment, and posts a
 * report. It is write-only: nothing reads a report back, so the only feedback is a
 * confirmation that it was sent. The reporter is the authenticated caller, added server-side —
 * the component never sends an identity.
 */
export function ReportButton({
  targetType,
  targetId,
}: {
  targetType: ReportTargetType;
  targetId: string;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button type="button" className={styles.trigger} onClick={() => setOpen(true)}>
        <Icon name="flag" size={16} color="var(--faint)" />
        {t('report.trigger')}
      </button>
      {open && (
        <ReportDialog targetType={targetType} targetId={targetId} onClose={() => setOpen(false)} />
      )}
    </>
  );
}

/**
 * The dialog itself, mounted only while open so every opening starts from a blank draft. Its
 * shape mirrors the end-of-reading sheet: a scrim that closes on a click beside it, a panel
 * that stops the click, and Escape as the way out.
 */
function ReportDialog({
  targetType,
  targetId,
  onClose,
}: {
  targetType: ReportTargetType;
  targetId: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);

  const [reason, setReason] = useState<ReportReason | undefined>(undefined);
  const [comment, setComment] = useState('');
  const [sent, setSent] = useState(false);
  const [failed, setFailed] = useState(false);

  const { mutate, isPending } = usePostApiReports({
    mutation: {
      onSuccess: () => setSent(true),
      onError: () => setFailed(true),
    },
  });

  // Escape is the way out of any dialog, and a report half-written has nothing to lose.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    panel.current?.focus();
  }, []);

  function submit() {
    if (!reason) return;
    setFailed(false);
    mutate({ data: { targetType, targetId, reason, comment: comment.trim() || undefined } });
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
        <button
          type="button"
          onClick={onClose}
          aria-label={t('report.close')}
          className={styles.close}
        >
          <Icon name="close" size={20} color="var(--ink-soft)" />
        </button>

        {sent ? (
          <div className={styles.sent}>
            <Icon name="check_circle" size={40} fill color="var(--accent)" />
            <h3 id={headingId} className={styles.title}>
              {t('report.sent.title')}
            </h3>
            <p className={styles.subtitle}>{t('report.sent.body')}</p>
            <Button variant="secondary" size="block" onClick={onClose}>
              {t('report.sent.close')}
            </Button>
          </div>
        ) : (
          <>
            <h3 id={headingId} className={styles.title}>
              {t('report.title')}
            </h3>
            <p className={styles.subtitle}>{t('report.subtitle')}</p>

            <div className={styles.reasons} role="group" aria-label={t('report.reasonLegend')}>
              {REASONS.map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={reason === value}
                  onClick={() => setReason(value)}
                  className={`${styles.reason} ${reason === value ? styles.reasonOn : ''}`}
                >
                  {t(`report.reasons.${value}`)}
                </button>
              ))}
            </div>

            <textarea
              value={comment}
              rows={3}
              placeholder={t('report.commentPlaceholder')}
              aria-label={t('report.commentLabel')}
              className={styles.comment}
              onChange={(event) => setComment(event.target.value)}
            />

            {failed && <p className={styles.error}>{t('report.error')}</p>}

            <div className={styles.actions}>
              <Button
                variant="primary"
                size="block"
                disabled={!reason || isPending}
                onClick={submit}
              >
                {t('report.submit')}
                <Loading size="compact" pending={isPending} />
              </Button>
              <Button variant="ghost" size="block" onClick={onClose}>
                {t('report.cancel')}
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
