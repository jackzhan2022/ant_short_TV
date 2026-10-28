import { request } from '@umijs/max';
import { beforeEach, expect, it, vi } from 'vitest';
import {
  createDownloadTask,
  effectiveStatus,
  receiveDownload,
  sourceFromDownloadUrl,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
beforeEach(() => {
  vi.clearAllMocks();
  localStorage.setItem('currentTenantId', '8');
});
it('submits typed references without fetching original file bytes', async () => {
  vi.mocked(request).mockResolvedValue({
    success: true,
    data: { id: 'task-1', status: 'QUEUED' },
  });
  const event = vi.fn();
  window.addEventListener('download-center-created', event);
  const result = await createDownloadTask('图片下载', [
    { kind: 'IMAGE', projectId: 2, id: 3 },
  ]);
  expect(result.status).toBe('QUEUED');
  expect(request).toHaveBeenCalledWith(
    '/api/downloads',
    expect.objectContaining({
      method: 'POST',
      data: {
        name: '图片下载',
        sources: [{ kind: 'IMAGE', projectId: 2, id: 3 }],
      },
    }),
  );
  expect(event).toHaveBeenCalledOnce();
  window.removeEventListener('download-center-created', event);
});
it('fails clearly without publishing a successful submission event', async () => {
  vi.mocked(request).mockResolvedValue({
    success: false,
    errorMessage: '无权访问',
  });
  await expect(
    createDownloadTask('图片', [{ kind: 'IMAGE', projectId: 2, id: 3 }]),
  ).rejects.toThrow('无权访问');
});
it('expires at the exact completion-based deadline', () => {
  const task = { status: 'READY' as const, expiresAt: '2026-10-29T02:00:00Z' };
  expect(effectiveStatus(task, Date.parse('2026-10-29T01:59:59Z'))).toBe(
    'READY',
  );
  expect(effectiveStatus(task, Date.parse('2026-10-29T02:00:00Z'))).toBe(
    'EXPIRED',
  );
});
it('maps supported existing routes and never accepts arbitrary remote URLs', () => {
  expect(
    sourceFromDownloadUrl('/api/projects/12/ai-video-results/9/download-file'),
  ).toEqual({ kind: 'VIDEO', projectId: 12, id: 9 });
  expect(
    sourceFromDownloadUrl('https://example.com/private.mp4'),
  ).toBeUndefined();
  expect(sourceFromDownloadUrl('/materials/1/2/private.mp4')).toBeUndefined();
});

it('blocks native file delivery when current source permissions have been revoked', async () => {
  vi.mocked(request).mockResolvedValue({
    success: false,
    errorMessage: '无权下载',
  });
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  try {
    await expect(
      receiveDownload(1, {
        id: 'task-1',
        name: '作品',
        status: 'READY',
        sourceType: 'IMAGE',
        fileCount: 1,
        createdAt: '2026-10-28T00:00:00Z',
      }),
    ).rejects.toThrow('无权下载');
    expect(click).not.toHaveBeenCalled();
  } finally {
    click.mockRestore();
  }
});
