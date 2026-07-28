/**
 * Rank attributes (gold / silver / bronze).
 *
 * They used to live in `features/collection/mockData.ts`, a leftover of the
 * mockup phase that three screens still imported.
 *
 * The colours mirror `--gold`, `--silver` and `--bronze` in `tokens.css`, but
 * are kept as literals: the selected rank is drawn with the colour blended at
 * 13% (`${color}22`), an alpha suffix a CSS variable cannot carry.
 */
export type RankCode = 'or' | 'argent' | 'bronze';

export const RANK_COLORS: Record<RankCode, string> = {
  or: '#d9b94e',
  argent: '#b3b7bf',
  bronze: '#c08a5a',
};

export const RANK_ICONS: Record<RankCode, string> = {
  or: 'workspace_premium',
  argent: 'military_tech',
  bronze: 'military_tech',
};

/** Narrows a free-form code coming from the API to a known rank. */
export function isRankCode(code: string | null | undefined): code is RankCode {
  return code === 'or' || code === 'argent' || code === 'bronze';
}
