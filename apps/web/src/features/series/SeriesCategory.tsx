import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiLibraryQueryKey,
  getGetApiSeriesQueryKey,
  useGetApiCategories,
  useGetApiSeries,
  usePutApiSeriesIdRank,
} from '../../api/generated/librarius';
import { CategoryChoice } from '../detail/CategoryChoice';
import { toast } from '../../shared/ui/toast';
import styles from './SeriesPage.module.css';

/**
 * Files the whole run under one category (#451) — every volume the reader holds — so that a
 * library split by category shows the series as one block on that shelf.
 */
export function SeriesCategory({ seriesId }: { seriesId: string }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: categories = [] } = useGetApiCategories();
  const { data: allSeries = [] } = useGetApiSeries();
  const rankCode = allSeries.find((s) => s.id === seriesId)?.rankCode;
  const selectedId = categories.find((c) => c.code === rankCode)?.id;

  const { mutate } = usePutApiSeriesIdRank({
    mutation: {
      onSuccess: () => {
        toast.success(t('series.categorySaved'), 'military_tech');
        void queryClient.invalidateQueries({ queryKey: getGetApiSeriesQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });

  return (
    <section className={styles.category} aria-label={t('series.categoryTitle')}>
      <h3 className={styles.sectionTitle}>{t('series.categoryTitle')}</h3>
      <p className={styles.categoryHint}>{t('series.categoryHint')}</p>
      <CategoryChoice
        categories={categories}
        selectedId={selectedId}
        onSelect={(categoryId) => mutate({ id: seriesId, data: { categoryId } })}
      />
    </section>
  );
}
