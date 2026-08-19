import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queryCurrent } from './service';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('account settings service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ data: [] });
  });

  it('uses the real current user endpoint', async () => {
    await queryCurrent();

    expect(request).toHaveBeenCalledWith('/api/currentUser');
  });
});
