import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queryAiCallLogs } from './service';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('ai call log service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: { records: [], total: 0, current: 1, pageSize: 20 },
    });
  });

  it('queries tenant scoped AI call logs with table filters', async () => {
    await queryAiCallLogs(10, {
      current: 2,
      pageSize: 30,
      serviceType: 'TEXT',
      status: 'SUCCESS',
      businessScene: 'chatbot',
    });

    expect(request).toHaveBeenCalledWith('/api/tenants/10/ai-call-logs', {
      params: {
        current: 2,
        pageSize: 30,
        serviceType: 'TEXT',
        status: 'SUCCESS',
        businessScene: 'chatbot',
      },
    });
  });
});
