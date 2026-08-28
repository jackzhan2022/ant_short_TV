import { describe, expect, it } from 'vitest';
import {
  displayFieldValue,
  entitlementTypeText,
  fieldText,
  metricText,
  serviceTypeText,
  statusText,
} from './fieldDictionary';

describe('后台字段字典', () => {
  it('将常见状态和服务类型转换为中文', () => {
    expect(statusText('PENDING_REVIEW')).toBe('待审核');
    expect(serviceTypeText('VIDEO_UNDERSTANDING')).toBe('视频理解');
  });

  it('将计费指标和权益类型转换为中文', () => {
    expect(metricText('INPUT_TOKEN')).toBe('输入 Token 数');
    expect(entitlementTypeText('GLOBAL_DISCOUNT')).toBe('全局折扣');
  });

  it('未知值不直接回显英文编码', () => {
    expect(displayFieldValue('status', 'NEW_BACKEND_STATE')).toBe('其他');
    expect(displayFieldValue('metric', 'NEW_METRIC')).toBe('其他');
    expect(fieldText('unknown_field')).toBe('其他');
  });
});
