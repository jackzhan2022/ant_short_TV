import { describe, expect, it } from 'vitest';
import { fitWithin, formatFileSize } from './imageCompression';

describe('imageCompression', () => {
  it('resizes proportionally within the longest edge', () => {
    expect(fitWithin(3840, 2160)).toEqual({ width: 1920, height: 1080 });
    expect(fitWithin(800, 1200)).toEqual({ width: 800, height: 1200 });
  });

  it('formats before and after sizes for display', () => {
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(1536)).toBe('1.5 KB');
    expect(formatFileSize(1572864)).toBe('1.5 MB');
  });
});
