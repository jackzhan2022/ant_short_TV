import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createAiServiceConfig,
  deleteAiServiceConfig,
  queryAiProviders,
  queryAiServiceConfigs,
  setDefaultAiServiceConfig,
  testAiServiceConfig,
  updateAiServiceConfig,
  updateAiServiceConfigStatus,
} from './service';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('ai service management service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: [] });
  });

  it('queries providers and tenant service configs from backend', async () => {
    await queryAiProviders();
    await queryAiServiceConfigs(10);

    expect(request).toHaveBeenCalledWith('/api/ai-providers');
    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs');
  });

  it('creates and updates service configs through tenant scoped APIs', async () => {
    const values = {
      name: 'OpenAI 文本服务',
      provider: 'OpenAI' as const,
      serviceType: 'TEXT' as const,
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test',
      model: 'gpt-4.1-mini',
      priority: 100,
      enabled: true,
      isDefault: true,
    };

    await createAiServiceConfig(10, values);
    await updateAiServiceConfig(10, 20, values);

    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    });
    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs/20', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    });
  });

  it('calls status default test and delete APIs', async () => {
    await updateAiServiceConfigStatus(10, 20, false);
    await setDefaultAiServiceConfig(10, 20);
    await testAiServiceConfig(10, 20);
    await deleteAiServiceConfig(10, 20);

    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs/20/status', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { enabled: false },
    });
    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs/20/default', {
      method: 'PUT',
    });
    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs/20/test', {
      method: 'POST',
    });
    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-service-configs/20', {
      method: 'DELETE',
    });
  });
});
