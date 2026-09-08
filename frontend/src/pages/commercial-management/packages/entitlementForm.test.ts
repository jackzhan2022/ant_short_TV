import { describe, expect, it } from 'vitest';
import {
  activeEntitlementOptions,
  entitlementRequiresValue,
  normalizePackageEntitlements,
} from './entitlementForm';
import type { CommercialEntitlementDefinition } from './service';

const definitions: CommercialEntitlementDefinition[] = [
  { id: 1, code: 'ONE_TIME_POINTS', name: '一次性积分', category: 'SYSTEM', status: 'ACTIVE', sortOrder: 10, createdAt: '', updatedAt: '' },
  { id: 2, code: 'DISPLAY_ALL_MODELS', name: '使用全部模型', category: 'DISPLAY', status: 'ACTIVE', sortOrder: 20, createdAt: '', updatedAt: '' },
  { id: 3, code: 'DISPLAY_ARCHIVED', name: '已停用展示项', category: 'DISPLAY', status: 'INACTIVE', sortOrder: 30, createdAt: '', updatedAt: '' },
];

describe('package entitlement form rules', () => {
  it('offers only active catalog definitions in catalog order', () => {
    expect(activeEntitlementOptions(definitions)).toEqual([
      { label: '一次性积分', value: 'ONE_TIME_POINTS' },
      { label: '使用全部模型', value: 'DISPLAY_ALL_MODELS' },
    ]);
  });

  it('requires a numeric value only for system entitlements', () => {
    expect(entitlementRequiresValue(definitions, 'ONE_TIME_POINTS')).toBe(true);
    expect(entitlementRequiresValue(definitions, 'DISPLAY_ALL_MODELS')).toBe(false);
  });

  it('removes display-only values from submitted payloads', () => {
    expect(normalizePackageEntitlements([
      { type: 'ONE_TIME_POINTS', value: 100 },
      { type: 'DISPLAY_ALL_MODELS', value: 999 },
    ], definitions)).toEqual([
      { type: 'ONE_TIME_POINTS', value: 100 },
      { type: 'DISPLAY_ALL_MODELS' },
    ]);
  });
});
