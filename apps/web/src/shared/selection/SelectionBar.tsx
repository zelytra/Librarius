import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiLibraryQueryKey,
  getGetApiSeriesQueryKey,
  useGetApiCategories,
  usePostApiLibraryBulk,
} from '../../api/generated/librarius';
import { Button, Chip } from '../ui/primitives';
import { Modal } from '../ui/Modal';
import { StatusPicker } from '../ui/StatusPicker';
import { toast } from '../ui/toast';
import { STATS_QUERIES } from '../statsQueries';
import { CategoryChoice } from '../../features/detail/CategoryChoice';
import { StarRating } from '../../features/detail/StarRating';
import { ALL_KINDS, KIND_LABEL_KEY } from '../../features/discover/medium';
import { Icon } from '../ui/Icon';
import { SERIES_KEY, type Selection } from './useSelection';
import { bulkRequest, type Draft } from './bulkRequest';
import styles from './Selection.module.css';

const changesIn = (draft: Draft) => Object.values(draft).filter((v) => v !== undefined).length;

/** The print screen of a selection's reading sheets (#551): titles and series, apart. */
function sheetsUrl(selected: ReadonlySet<string>): string {
  const ids = [...selected];
  const params = new URLSearchParams();
  const items = ids.filter((id) => !id.startsWith(SERIES_KEY));
  const series = ids.filter((id) => id.startsWith(SERIES_KEY)).map((id) => id.slice(SERIES_KEY.length));
  if (items.length) params.set('items', items.join(','));
  if (series.length) params.set('series', series.join(','));
  return `/sheets?${params.toString()}`;
}

/**
 * The bar of a screen that is selecting (#457): how many titles or series are picked, and the
 * sheet of what to do to all of them. Several changes are picked first — status, ownership,
 * format, kind, category, rating — then applied together in one request (#467); removal stays a
 * separate, confirmed action.
 */
export function SelectionBar({ selection }: { selection: Selection }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
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
      {/* Room under the last row, so the floating bar never hides the last titles (#472). */}
      <div className={styles.spacer} aria-hidden="true" />
      {/* Hidden while the sheet is open: it would sit over the sheet's own footer. */}
      {!open && (
      <div className={styles.bar} data-selection-bar role="region" aria-label={t('selection.title')}>
        <span className={styles.count}>{t('selection.count', { count })}</span>
        <Button variant="secondary" size="compact" onClick={selection.stop}>
          {t('selection.cancel')}
        </Button>
        {/* Every selected title's reading sheet, one per page (#551). */}
        <button
          type="button"
          className={styles.iconAction}
          disabled={count === 0}
          aria-label={t('selection.printSheets')}
          title={t('selection.printSheets')}
          onClick={() => navigate(sheetsUrl(selection.selected))}
        >
          <Icon name="print" size={20} color="currentColor" />
        </button>
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
            <div role="group" aria-labelledby="selection-kind">
              <h3 id="selection-kind" className={styles.sheetTitle}>{t('selection.kind')}</h3>
              <div className={styles.kinds}>
                {ALL_KINDS.map((kind) => (
                  <Chip key={kind} selected={draft.kind === kind}
                    onClick={() => set({ kind: draft.kind === kind ? undefined : kind })}>
                    {t(KIND_LABEL_KEY[kind])}
                  </Chip>
                ))}
              </div>
            </div>
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

/**
 * The button that starts selecting on a screen. Once it has scrolled out of sight the same
 * action floats at the bottom of the screen (#557): a reader who decides mid-browse to pick
 * several titles need not scroll back to the top for it.
 */
export function SelectButton({ selection }: { selection: Selection }) {
  const { t } = useTranslation();
  const anchor = useRef<HTMLSpanElement>(null);
  const [inSight, setInSight] = useState(true);

  useEffect(() => {
    const element = anchor.current;
    if (!element || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(([entry]) => setInSight(entry.isIntersecting));
    observer.observe(element);
    return () => observer.disconnect();
  }, [selection.active]);

  if (selection.active) return null;
  return (
    <>
      <span ref={anchor} className={styles.anchor}>
        <Button variant="secondary" size="compact" onClick={selection.start}>
          {t('selection.start')}
        </Button>
      </span>
      {!inSight && (
        <button type="button" className={styles.floatingStart} onClick={selection.start}>
          <Icon name="check_circle" size={20} color="currentColor" />
          {t('selection.start')}
        </button>
      )}
    </>
  );
}
