import { describe, expect, test } from 'vitest';
import { project } from './orthographic';

describe('project (#573)', () => {
  test('the point the globe faces lands in its centre', () => {
    expect(project([10, 45], { lon: 10, lat: 45 }, 100)).toEqual({ x: 0, y: 0, visible: true });
  });

  test('east is to the right and north is up', () => {
    const east = project([30, 0], { lon: 0, lat: 0 }, 100);
    const north = project([0, 30], { lon: 0, lat: 0 }, 100);
    expect(east.x).toBeCloseTo(50);
    expect(east.y).toBeCloseTo(0);
    expect(north.y).toBeCloseTo(-50);
  });

  test('the far side is hidden, and pinned to the rim', () => {
    const far = project([180, 0], { lon: 0, lat: 0 }, 100);
    expect(far.visible).toBe(false);
    expect(Math.hypot(far.x, far.y)).toBeLessThanOrEqual(100.0001);
  });
});
