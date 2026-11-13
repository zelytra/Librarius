import type { AchievementDto } from '../../api/generated/librarius';

/** The badge drawn for each achievement (#287); the codes are the API's. */
export const ACHIEVEMENT_ICONS: Record<string, string> = {
  FIRST_BOOK: 'menu_book',
  BOOKWORM: 'auto_stories',
  PAGE_EATER: 'library_books',
  FINISHER: 'collections_bookmark',
  MARATHON: 'timer',
  COMEBACK: 'replay',
  ON_A_ROLL: 'local_fire_department',
  GOAL_GETTER: 'flag',
  AHEAD_OF_THE_CURVE: 'bolt',
  CRITIC: 'star',
  WORDSMITH: 'rate_review',
  LIBRARIAN: 'workspace_premium',
  SORTER: 'category',
  EXPLORER: 'explore',
  POLYGLOT: 'translate',
  OMNIVORE: 'military_tech',
  SCOUT: 'search',
  FAN: 'favorite',
  COMPLETIONIST: 'check_circle',
};

export const FAMILIES = ['READING', 'CONSISTENCY', 'CURATION', 'DISCOVERY'] as const;

/** Name of a tier: bronze, silver, gold, platinum — the shelves' own metals, then one more. */
export const TIER_KEYS = ['bronze', 'silver', 'gold', 'platinum'];

/** The tier key of tier `tier` (1-based) of an achievement with `tiers` tiers; null for a one-shot. */
export function tierKey(tier: number, tiers: number): string | null {
  if (tiers <= 1 || tier < 1) return null;
  return TIER_KEYS[Math.min(tier, TIER_KEYS.length) - 1];
}

/** How far towards the next tier, 0 to 1; 1 once every tier is unlocked. */
export function progressToNext(a: AchievementDto): number {
  if (a.nextThreshold == null) return 1;
  const value = a.value ?? 0;
  return Math.max(0, Math.min(1, value / a.nextThreshold));
}

/** How far into the current level the XP stands, 0 to 1. */
export function levelProgress(xp: number, levelXp: number, nextLevelXp: number): number {
  const span = nextLevelXp - levelXp;
  return span <= 0 ? 1 : Math.max(0, Math.min(1, (xp - levelXp) / span));
}
