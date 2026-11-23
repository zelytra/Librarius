import type { TFunction } from 'i18next';

/** A tab's label with how many things wait in it (#635), or the bare label at zero. */
export function withCount(t: TFunction, label: string, count: number | undefined): string {
  return count ? t('admin.tabs.withCount', { label, count }) : label;
}
