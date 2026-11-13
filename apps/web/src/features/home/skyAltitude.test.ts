import { describe, expect, it } from 'vitest';
import { SKY_MILESTONES, formatDistance, skyPosition } from './skyAltitude';

describe('skyPosition (#415)', () => {
  it('starts on the ground, under a blue sky', () => {
    const at = skyPosition(0);
    expect(at.current.scene).toBe('ground');
    expect(at.next?.scene).toBe('clouds');
    expect(at.pagesToNext).toBe(1500);
  });

  it('reaches the Moon, then each planet in turn', () => {
    expect(skyPosition(120_000).current.scene).toBe('moon');
    expect(skyPosition(250_000).current.scene).toBe('mars');
    expect(skyPosition(650_000).current.scene).toBe('neptune');
  });

  it('stops at the edge of the universe', () => {
    const at = skyPosition(10_000_000);
    expect(at.current.scene).toBe('edge');
    expect(at.next).toBeUndefined();
    expect(at.progress).toBe(1);
  });

  it('climbs monotonically, on a log scale between milestones', () => {
    let last = 0;
    for (let pages = 0; pages <= 3_000_000; pages += 25_000) {
      const { km } = skyPosition(pages);
      expect(km).toBeGreaterThanOrEqual(last);
      last = km;
    }
    const halfway = skyPosition(160_000);
    expect(halfway.km).toBeGreaterThan(384_400);
    expect(halfway.km).toBeLessThan(78e6);
  });

  it('keeps the milestones in order', () => {
    for (let i = 1; i < SKY_MILESTONES.length; i++) {
      expect(SKY_MILESTONES[i].pages).toBeGreaterThan(SKY_MILESTONES[i - 1].pages);
      expect(SKY_MILESTONES[i].km).toBeGreaterThan(SKY_MILESTONES[i - 1].km);
    }
  });
});

const U = { au: 'UA', ly: 'al', mly: 'M al' };

describe('formatDistance', () => {
  it('picks a unit that reads well', () => {
    expect(formatDistance(0.5, 'fr', U)).toMatch(/500\s?m/);
    expect(formatDistance(384_400, 'fr', U)).toMatch(/km/);
    expect(formatDistance(78e6, 'fr', U)).toMatch(/UA/);
    expect(formatDistance(4e13, 'fr', U)).toMatch(/al/);
  });
});
