import { describe, expect, it } from 'vitest';
import {
  buildPointComponents,
  findEffectiveCostVersion,
  formatBillingUnit,
  formatPricingDimensions,
  parsePricingDimensions,
} from './pricingEntry';

describe('pricing entry helpers', () => {
  it('formats token units in millions and calls by count', () => {
    expect(formatBillingUnit('INPUT_TOKEN', 1_000_000)).toBe('1 百万 Token');
    expect(formatBillingUnit('CACHED_INPUT_TOKEN', 2_000_000)).toBe(
      '2 百万 Token',
    );
    expect(formatBillingUnit('CALL', 1)).toBe('1 次');
    expect(formatBillingUnit('IMAGE', 1)).toBe('1 张');
    expect(formatBillingUnit('INPUT_IMAGE', 1)).toBe('1 张');
    expect(formatBillingUnit('VIDEO_SECOND', 1)).toBe('1 秒');
    expect(formatBillingUnit('AUDIO_SECOND', 1)).toBe('1 秒');
    expect(formatBillingUnit('CHARACTER', 1)).toBe('1 字符');
  });

  it('selects the cost version effective at the requested local time', () => {
    const versions = [
      {
        id: 1,
        status: 'PUBLISHED',
        effectiveFrom: '2026-01-01 00:00:00',
        effectiveTo: '2026-02-01 00:00:00',
      },
      { id: 2, status: 'PUBLISHED', effectiveFrom: '2026-02-01T00:00:00' },
    ];

    expect(findEffectiveCostVersion(versions, '2026-01-31T23:59:59')?.id).toBe(
      1,
    );
    expect(findEffectiveCostVersion(versions, '2026-02-01 00:00:00')?.id).toBe(
      2,
    );
  });

  it('excludes revoked cost versions', () => {
    const versions = [
      { id: 1, status: 'REVOKED', effectiveFrom: '2026-01-01T00:00:00' },
      { id: 2, status: 'PUBLISHED', effectiveFrom: '2026-01-01T00:00:00' },
    ];

    expect(findEffectiveCostVersion(versions, '2026-01-01T00:00:00')?.id).toBe(
      2,
    );
  });

  it('selects a published cost version instead of a later draft', () => {
    const versions = [
      { id: 1, status: 'PUBLISHED', effectiveFrom: '2026-01-01T00:00:00' },
      { id: 2, status: 'DRAFT', effectiveFrom: '2026-02-01T00:00:00' },
    ];

    expect(findEffectiveCostVersion(versions, '2026-03-01T00:00:00')?.id).toBe(
      1,
    );
  });

  it('multiplies USD and CNY costs directly without conversion', () => {
    expect(
      buildPointComponents(
        [
          { metric: 'CALL', unitSize: 1, unitPrice: 0.25, currency: 'USD' },
          {
            metric: 'INPUT_TOKEN',
            unitSize: 1_000_000,
            unitPrice: 2,
            currency: 'CNY',
          },
        ],
        10,
      ),
    ).toEqual([
      { metric: 'CALL', unitSize: 1, pointRate: 2.5, dimensionsJson: '{}' },
      { metric: 'INPUT_TOKEN', unitSize: 1_000_000, pointRate: 20, dimensionsJson: '{}' },
    ]);
  });
  it('parses JSON pricing conditions and treats blank input as unrestricted', () => {
    expect(parsePricingDimensions(undefined)).toEqual({});
    expect(parsePricingDimensions('  ')).toEqual({});
    expect(parsePricingDimensions('{}')).toEqual({});
    expect(parsePricingDimensions('{"resolution":"720p","referenceVideo":"false"}'))
      .toEqual({ resolution: '720p', referenceVideo: 'false' });
  });

  it.each(['{', 'null', '[]', '"720p"', '1', '{"referenceVideo":false}',
    '{"resolution":720}', '{"resolution":null}', '{"resolution":{}}'])
    ('rejects invalid or non-string condition JSON: %s', (value) => {
      expect(() => parsePricingDimensions(value)).toThrow(/计费条件/);
    });

  it('formats pricing conditions as editable JSON', () => {
    expect(formatPricingDimensions(undefined)).toBe('{}');
    expect(formatPricingDimensions({ resolution: '720p' }))
      .toBe(JSON.stringify({ resolution: '720p' }, null, 2));
  });

  it('preserves conditions in generated point-price form entries', () => {
    const dimensions = { resolution: '720p', referenceVideo: 'false' };
    expect(buildPointComponents([{
      metric: 'OUTPUT_TOKEN', unitSize: 1_000_000, unitPrice: 70, currency: 'CNY', dimensions,
    }], 2)).toEqual([{
      metric: 'OUTPUT_TOKEN', unitSize: 1_000_000, pointRate: 140,
      dimensionsJson: JSON.stringify(dimensions, null, 2),
    }]);
  });

});
