import type { BulkUpdateDto } from '../../api/generated/librarius';
import type { BookFormat, Ownership, ReadingStatus } from '../statuses';
import { SERIES_KEY } from './useSelection';

/** The changes picked in the sheet, applied together. Undefined means "leave it". */
export interface Draft {
  status?: ReadingStatus;
  ownership?: Ownership;
  format?: BookFormat;
  /** A category to file under, or `clear` to take the category off. */
  category?: string;
  /** A rating, or `clear` to take it off. */
  rating?: number | 'clear';
}

/** The request a draft stands for, for the titles and the series picked. */
export function bulkRequest(selected: Iterable<string>, draft: Draft): BulkUpdateDto {
  const keys = [...selected];
  return {
    ids: keys.filter((k) => !k.startsWith(SERIES_KEY)),
    seriesIds: keys.filter((k) => k.startsWith(SERIES_KEY)).map((k) => k.slice(SERIES_KEY.length)),
    status: draft.status,
    ownership: draft.ownership,
    format: draft.format,
    categoryId: draft.category && draft.category !== 'clear' ? draft.category : undefined,
    clearCategory: draft.category === 'clear' || undefined,
    rating: typeof draft.rating === 'number' ? draft.rating : undefined,
    clearRating: draft.rating === 'clear' || undefined,
  };
}

