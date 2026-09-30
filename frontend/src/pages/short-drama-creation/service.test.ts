import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  startMediaUpload,
  type MediaUploadHandle,
  type VerifiedMediaUpload,
} from '@/services/mediaUpload';
import { createManagedInspiration } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: vi.fn() }));

describe('inspiration management uploads', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uploads bytes directly to COS before submitting only metadata', async () => {
    const file = new File(['image'], 'cover.png', { type: 'image/png' });
    const uploaded: VerifiedMediaUpload = {
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.png',
      contentType: 'image/png',
      size: file.size,
      eTag: 'etag-1',
    };
    let finishAttempt!: (value: VerifiedMediaUpload) => void;
    const attempt = new Promise<VerifiedMediaUpload>((resolve) => {
      finishAttempt = resolve;
    });
    const handle: MediaUploadHandle = {
      session: {
        sessionToken: 'session-1',
        bucket: 'antv-1418200553',
        region: 'ap-guangzhou',
        storageClass: 'INTELLIGENT_TIERING',
        objectKey: 'uploads/11/session-1/source.png',
        status: 'PENDING',
        expiresAt: '2026-10-07T00:00:00Z',
      },
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.png',
      taskId: 'task-1',
      attempt,
      pause: vi.fn(),
      resume: vi.fn().mockResolvedValue(uploaded),
      cancel: vi.fn(),
      retryCompletion: vi.fn().mockResolvedValue(uploaded),
    };
    vi.mocked(startMediaUpload).mockResolvedValue(handle);
    vi.mocked(request).mockResolvedValue({ success: true, data: { id: 1 } });

    const creation = createManagedInspiration({
      file,
      title: '测试灵感',
      tags: ['都市'],
      promptText: '提示词',
      publishStatus: 'UNPUBLISHED',
    });

    await vi.waitFor(() => expect(startMediaUpload).toHaveBeenCalledWith(file));
    expect(request).not.toHaveBeenCalled();
    finishAttempt(uploaded);
    await creation;

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
