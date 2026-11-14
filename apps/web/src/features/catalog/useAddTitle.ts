import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiLibraryQueryKey,
  getGetApiWishlistQueryKey,
  usePostApiLibrary,
  usePostApiWishlist,
  type ManualBookDto,
} from '../../api/generated/librarius';
import { STATS_QUERIES } from '../../shared/statsQueries';
import { addsToWishlistOnly } from '../../shared/statuses';
import { toast } from '../../shared/ui/toast';
import type { AddTarget } from './addTargets';

/**
 * Files a title where the add sheet said (#411), refreshes what shows it, and says so (#413).
 *
 * <p>A title wanted and not read in any way is a plain wish, with its priority and price, as
 * the wishlist always held. Anything else is a title of the library carrying both statuses —
 * the API puts a {@code WISHLIST} one on the wishlist too.
 */
export function useAddTitle() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { mutateAsync: addToLibrary } = usePostApiLibrary();
  const { mutateAsync: addToWishlist } = usePostApiWishlist();

  return async function addTitle(book: ManualBookDto, target: AddTarget) {
    const title = book.title ?? '';
    try {
      if (addsToWishlistOnly(target.status, target.ownership)) {
        await addToWishlist({ data: { book, priority: 'SOON' } });
        toast.success(t('toasts.addedWishlist', { title }), 'favorite');
      } else {
        await addToLibrary({
          data: { book, status: target.status, ownership: target.ownership, format: target.format ?? undefined },
        });
        toast.success(t('toasts.addedLibrary', { title }), 'library_books');
      }
    } catch (error) {
      toast.error(t('toasts.failed'));
      throw error;
    }
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    void queryClient.invalidateQueries(STATS_QUERIES);
  };
}
