import { expect, it, vi } from 'vitest';
import { saveVideoFrame } from './video-frame-service';
const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: mocks.request }));
it('binds the upload session and frozen timestamp to the specified video version', async () => {
  const signal = new AbortController().signal;
  mocks.request.mockResolvedValueOnce({ success: true, data: { id: 3 } });
  expect(await saveVideoFrame(1, 22, 'token', 1.24, signal)).toEqual({ id: 3 });
  expect(mocks.request).toHaveBeenCalledWith('/api/projects/1/ai-video-results/22/frames', {
    method: 'POST', data: { sessionToken: 'token', timeSeconds: 1.24 }, signal, skipErrorHandler: true,
  });
});
it('does not report success for a rejected save', async () => {
  mocks.request.mockResolvedValueOnce({ success: false, errorMessage: '无权限' });
  await expect(saveVideoFrame(1, 22, 'token', 1, new AbortController().signal)).rejects.toThrow('无权限');
});
