import { expect, it } from 'vitest';
import { normalizeEraseRegion, moveEraseRegion } from './subtitle-erase-region';
it('normalizes against the actual video frame and clamps reversed/outside drags', () => {
  const bounds = { left: 100, top: 50, width: 200, height: 400 };
  expect(normalizeEraseRegion({ x: 280, y: 410 }, { x: 120, y: 90 }, bounds))
    .toEqual({ left: 0.1, top: 0.1, right: 0.9, bottom: 0.9 });
  expect(normalizeEraseRegion({ x: -10, y: 0 }, { x: 400, y: 600 }, bounds))
    .toEqual({ left: 0, top: 0, right: 1, bottom: 1 });
  expect(normalizeEraseRegion({ x: 120, y: 90 }, { x: 120, y: 90 }, bounds)).toBeUndefined();
});
it('moves a region without changing its size or leaving the video', () => {
  expect(moveEraseRegion({ left: 0.1, top: 0.7, right: 0.9, bottom: 0.9 }, 2, 2))
    .toEqual({ left: 0.2, top: 0.8, right: 1, bottom: 1 });
});
