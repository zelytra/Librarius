import { describe, expect, it } from 'vitest';
import { RANK_ICONS, isRankCode } from './ranks';

describe('ranks', () => {
  it('knows Platine, the metal above Or (#608)', () => {
    expect(isRankCode('platine')).toBe(true);
    expect(RANK_ICONS.platine).toBe('workspace_premium');
  });

  it('leaves a custom category and a missing code to the generic medal', () => {
    expect(isRankCode('dore')).toBe(false);
    expect(isRankCode(null)).toBe(false);
  });
});
