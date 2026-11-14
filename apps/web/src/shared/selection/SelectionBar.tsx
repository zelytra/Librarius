import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiLibraryQueryKey,
  getGetApiSeriesQueryKey,
  useGetApiCategories,
  usePostApiLibraryBulk,
  type BulkUpdateDto,
} from '../../api/generated/librarius';
import { Button } from '../ui/primitives';
import { Modal } from '../ui/Modal';
import { StatusPicker } from '../ui/StatusPicker';
import { toast } from '../ui/toast';
import { STATS_QUERIES } from '../statsQueries';
import { CategoryChoice } from '../../features/detail/CategoryChoice';
import { StarRating } from '../../features/detail/StarRating';
import type { Selection } from './useSelection';
import styles from './Selection.module.css';

/**
 * The bar of a screen that is selecting (#457): how many titles are picked, and the sheet of
 * what can be done to all of them at once — reading status, ownership, format, category,
 * rating, or removal — each applied as soon as it is chosen, in one request.
 */
export function SelectionBar({ selection }: { selection: Selection }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const { data: categories = [] } = useGetApiCategories({ query: { enabled: open } });
  const count = selection.selected.size;

  const { mutate, isPending } = usePostApiLibraryBulk({
    mutation: {
      onSuccess: (result) => {
        toast.success(t('selection.applied', { count: result.updated ?? 0 }), 'check_circle');
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiSeriesQueryKey() });
        void queryClient.invalidateQueries(STATS_QUERIES);
        setOpen(false);
        setConfirmRemove(false);
        selection.stop();
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });
  const apply = (change: Omit<BulkUpdateDto, 'ids'>) => mutate({ data: { ids: [...selection.selected], ...change } });

  if (!selection.active) return null;
  return (
    <>
      <div className={styles.bar} role="region" aria-label={t('selection.title')}>
        <span className={styles.count}>{t('selection.count', { count })}</span>
        <Button variant="secondary" size="compact" onClick={selection.stop}>
          {t('selection.cancel')}
        </Button>
        <Button variant="primary" size="compact" disabled={count === 0} onClick={() => setOpen(true)}>
          {t('selection.edit')}
        </Button>
      </div>
      {open && (
        <Modal title={t('selection.sheetTitle', { count })} onClose={() => setOpen(false)}>
          <div className={styles.sheet}>
            <StatusPicker
              status={undefined}
              ownership={undefined}
              onStatus={(status) => apply({ status })}
              onOwnership={(ownership) => apply({ ownership })}
              format={null}
              onFormat={(format) => format && apply({ format })}
              disabled={isPending}
            />
            <div>
              <h3 className={styles.sheetTitle}>{t('selection.category')}</h3>
              <CategoryChoice
                categories={categories}
                onSelect={(categoryId) => apply(categoryId ? { categoryId } : { clearCategory: true })}
              />
              <button type="button" className={styles.link} onClick={() => apply({ clearCategory: true })}>
                {t('selection.clearCategory')}
              </button>
            </div>
            <div>
              <h3 className={styles.sheetTitle}>{t('selection.rating')}</h3>
              <StarRating onRate={(rating) => apply(rating ? { rating } : { clearRating: true })} />
            </div>
            <Button
              variant="secondary"
              disabled={isPending}
              onClick={() => (confirmRemove ? apply({ remove: true }) : setConfirmRemove(true))}
            >
              {t(confirmRemove ? 'selection.removeConfirm' : 'selection.remove', { count })}
            </Button>
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
