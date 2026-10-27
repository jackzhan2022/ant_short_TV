import type { EraseRegion } from './subtitle-erase-service';
type Point = { x: number; y: number };
type Bounds = { left: number; top: number; width: number; height: number };
const round = (value: number) => Math.round(value * 1_000_000) / 1_000_000;
const clamp = (value: number, max = 1) => Math.max(0, Math.min(max, value));
export function normalizeEraseRegion(
  start: Point,
  end: Point,
  bounds: Bounds,
): EraseRegion | undefined {
  if (bounds.width <= 0 || bounds.height <= 0) return undefined;
  const left = round(
    clamp((Math.min(start.x, end.x) - bounds.left) / bounds.width),
  );
  const right = round(
    clamp((Math.max(start.x, end.x) - bounds.left) / bounds.width),
  );
  const top = round(
    clamp((Math.min(start.y, end.y) - bounds.top) / bounds.height),
  );
  const bottom = round(
    clamp((Math.max(start.y, end.y) - bounds.top) / bounds.height),
  );
  return right > left && bottom > top
    ? { left, top, right, bottom }
    : undefined;
}
export function moveEraseRegion(
  region: EraseRegion,
  x: number,
  y: number,
): EraseRegion {
  const width = region.right - region.left,
    height = region.bottom - region.top;
  const left = round(clamp(region.left + x, 1 - width)),
    top = round(clamp(region.top + y, 1 - height));
  return { left, top, right: round(left + width), bottom: round(top + height) };
}
