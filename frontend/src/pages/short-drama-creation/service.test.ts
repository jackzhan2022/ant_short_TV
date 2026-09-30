import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { startMediaUpload } from '@/services/mediaUpload';
import { createManagedInspiration } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: vi.fn() }));

describe('inspiration management uploads', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uploads bytes directly to COS before submitting only metadata', async () => {
    const file = new File(['image'], 'cover.png', { type: 'image/png' });
    vi.mocked(startMediaUpload).mockResolvedValue({
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.png',
      contentType: 'image/png',
      size: file.size,
      eTag: 'etag-1',
    });
    vi.mocked(request).mockResolvedValue({ success: true, data: { id: 1 } });

    await createManagedInspiration({
      file,
      title: '测试灵感',
      tags: ['都市'],
      promptText: '提示词',
      publishStatus: 'UNPUBLISHED',
    });

    expect(startMediaUpload).toHaveBeenCalledWith(file);
    expect(request).toHaveBeenCalledWith(
      '/api/platform/inspiration-creations',
      {
        method: 'POST',
        data: {
          uploadSessionToken: 'session-1',
          title: '测试灵感',
          tags: ['都市'],
          promptText: '提示词',
          publishStatus: 'UNPUBLISHED',
        },
      },
    );
  });
});
