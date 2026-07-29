/**
 * Rank attributes (gold / silver / bronze).
 *
 * They used to live in `features/collection/mockData.ts`, a leftover of the
 * mockup phase that three screens still imported.
 *
 * What is left is the medal drawn on a cover, which is a drawing and not data.
 * The colours went the other way: a screen that paints a rank now reads
 * `CategoryDto.color`, the one the server stores, because the row it paints is
 * no longer three known metals but whatever categories the user has.
 */
export type RankCode = 'or' | 'argent' | 'bronze';

export const RANK_ICONS: Record<RankCode, string> = {
  or: 'workspace_premium',
  argent: 'military_tech',
  bronze: 'military_tech',
};

/** Narrows a free-form code coming from the API to a known rank. */
export function isRankCode(code: string | null | undefined): code is RankCode {
  return code === 'or' || code === 'argent' || code === 'bronze';
}
