import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiLibraryQueryKey,
  getGetApiSeriesQueryKey,
  useGetApiCategories,
  usePostApiLibraryBulk,
} from '../../api/generated/librarius';
import { Button } from '../ui/primitives';
import { Modal } from '../ui/Modal';
import { StatusPicker } from '../ui/StatusPicker';
import { toast } from '../ui/toast';
import { STATS_QUERIES } from '../statsQueries';
import { CategoryChoice } from '../../features/detail/CategoryChoice';
import { StarRating } from '../../features/detail/StarRating';
import type { Selection } from './useSelection';
import { bulkRequest, type Draft } from './bulkRequest';
import styles from './Selection.module.css';

const changesIn = (draft: Draft) => Object.values(draft).filter((v) => v !== undefined).length;

/**
 * The bar of a screen that is selecting (#457): how many titles or series are picked, and the
 * sheet of what to do to all of them. Several changes are picked first — status, ownership,
 * format, category, rating — then applied together in one request (#467); removal stays a
 * separate, confirmed action.
 */
export function SelectionBar({ selection }: { selection: Selection }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Draft>({});
  const [confirmRemove, setConfirmRemove] = useState(false);
  const { data: categories = [] } = useGetApiCategories({ query: { enabled: open } });
  const count = selection.selected.size;
  const pending = changesIn(draft);

  const close = () => {
    setOpen(false);
    setDraft({});
    setConfirmRemove(false);
  };

  const { mutate, isPending } = usePostApiLibraryBulk({
    mutation: {
      onSuccess: (result) => {
        toast.success(t('selection.applied', { count: result.updated ?? 0 }), 'check_circle');
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiSeriesQueryKey() });
        void queryClient.invalidateQueries(STATS_QUERIES);
        close();
        selection.stop();
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });

  const set = (change: Partial<Draft>) => setDraft((d) => ({ ...d, ...change }));

  if (!selection.active) return null;
  return (
    <>
      {/* Hidden while the sheet is open: it would sit over the sheet's own footer. */}
      {!open && (
      <div className={styles.bar} role="region" aria-label={t('selection.title')}>
        <span className={styles.count}>{t('selection.count', { count })}</span>
        <Button variant="secondary" size="compact" onClick={selection.stop}>
          {t('selection.cancel')}
        </Button>
        <Button variant="primary" size="compact" disabled={count === 0} onClick={() => setOpen(true)}>
          {t('selection.edit')}
        </Button>
      </div>
      )}
      {open && (
        <Modal title={t('selection.sheetTitle', { count })} onClose={close} description={t('selection.sheetHint')}>
          <div className={styles.sheet}>
            <StatusPicker
              status={draft.status}
              ownership={draft.ownership}
              onStatus={(status) => set({ status: draft.status === status ? undefined : status })}
              onOwnership={(ownership) => set({ ownership: draft.ownership === ownership ? undefined : ownership })}
              format={draft.format ?? null}
              onFormat={(format) => set({ format: format ?? undefined })}
              disabled={isPending}
            />
            <div>
              <h3 className={styles.sheetTitle}>{t('selection.category')}</h3>
              <CategoryChoice
                categories={categories}
                selectedId={draft.category !== 'clear' ? draft.category : undefined}
                onSelect={(categoryId) => set({ category: categoryId })}
              />
              <button
                type="button"
                aria-pressed={draft.category === 'clear'}
                className={`${styles.link} ${draft.category === 'clear' ? styles.linkOn : ''}`}
                onClick={() => set({ category: draft.category === 'clear' ? undefined : 'clear' })}
              >
                {t('selection.clearCategory')}
              </button>
            </div>
            <div>
              <h3 className={styles.sheetTitle}>{t('selection.rating')}</h3>
              <StarRating
                value={typeof draft.rating === 'number' ? draft.rating : 0}
                onRate={(rating) => set({ rating })}
              />
              <button
                type="button"
                aria-pressed={draft.rating === 'clear'}
                className={`${styles.link} ${draft.rating === 'clear' ? styles.linkOn : ''}`}
                onClick={() => set({ rating: draft.rating === 'clear' ? undefined : 'clear' })}
              >
                {t('selection.clearRating')}
              </button>
            </div>
            <div className={styles.danger}>
              <Button
                variant="secondary"
                disabled={isPending}
                onClick={() =>
                  confirmRemove
                    ? mutate({ data: { ...bulkRequest(selection.selected, {}), remove: true } })
                    : setConfirmRemove(true)
                }
              >
                {t(confirmRemove ? 'selection.removeConfirm' : 'selection.remove', { count })}
              </Button>
            </div>
            <div className={styles.footer}>
              <span className={styles.pending}>{t('selection.pending', { count: pending })}</span>
              <Button
                variant="primary"
                disabled={isPending || pending === 0}
                onClick={() => mutate({ data: bulkRequest(selection.selected, draft) })}
              >
                {t('selection.apply')}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}

/** The button that starts selecting on a screen. */
export function SelectButton({ selection }: { selection: Selection }) {
  const { t } = useTranslation();
  if (selection.active) return null;
  return (
    <Button variant="secondary" size="compact" onClick={selection.start}>
      {t('selection.start')}
    </Button>
  );
}
