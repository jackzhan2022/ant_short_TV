import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getConcurrencyConfig, updateConcurrencyConfig } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
const config = {
  settings: {
    teamTextLimit: 17,
    teamImageLimit: 19,
    teamVideoLimit: 23,
    modelLimit: 29,
    providerLimit: 31,
    textWorkers: 3,
    imageWorkers: 5,
    videoSubmitWorkers: 7,
    videoQueryWorkers: 9,
  },
  databaseMaxConnections: 43,
};

describe('concurrency config service', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: config });
  });

  it('reads the platform endpoint and sends only flat settings on PUT', async () => {
    expect(await getConcurrencyConfig()).toEqual(config);
    await updateConcurrencyConfig({
      ...config.settings,
      databaseMaxConnections: 43,
    } as typeof config.settings);
    expect(request).toHaveBeenNthCalledWith(
      1,
      '/api/platform/ai/concurrency-config',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/platform/ai/concurrency-config',
      expect.objectContaining({ method: 'PUT', data: config.settings }),
    );
  });

  it.each([
    getConcurrencyConfig,
    () => updateConcurrencyConfig(config.settings),
  ])('rejects business failures instead of reporting success', async (run) => {
    vi.mocked(request).mockResolvedValue({
      success: false,
      errorMessage: '没有权限',
      data: config,
    });
    await expect(run()).rejects.toThrow('没有权限');
  });

  it.each([
    undefined,
    { success: true },
    { success: true, data: { settings: {}, databaseMaxConnections: 43 } },
  ])('rejects incomplete success responses', async (response) => {
    vi.mocked(request).mockResolvedValue(response);
    await expect(getConcurrencyConfig()).rejects.toThrow();
  });
});
