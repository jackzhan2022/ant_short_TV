import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { getCreationDownload } from './api';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/point-estimate', () => ({ pointEstimatedRequest: vi.fn() }));
const fetchMock = vi.fn();
const createUrl = vi.fn(() => 'blob:creation-download');
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('URL', class extends URL { static createObjectURL = createUrl; });
  fetchMock.mockReset(); createUrl.mockClear();
  localStorage.setItem('currentTenantId', '10');
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });

it.each(['image/png', 'video/mp4'])('downloads %s through the authenticated application endpoint', async (type) => {
  fetchMock.mockResolvedValue(new Response(new Blob(['original']), { headers: {
    'Content-Type': type, 'Content-Disposition': "attachment; filename*=UTF-8''%E4%BD%9C%E5%93%81.png",
  } }));
  const signal = new AbortController().signal;
  await expect(getCreationDownload(14, 20, signal)).resolves.toEqual({ url: 'blob:creation-download', fileName: '作品.png' });
  expect(fetchMock).toHaveBeenCalledWith('/api/free-creations/14/outputs/20/download-file', expect.objectContaining({
    method: 'GET', credentials: 'same-origin', redirect: 'error', cache: 'no-store', signal,
    headers: { 'X-Tenant-Id': '10' },
  }));
  expect(createUrl).toHaveBeenCalledOnce();
});

it('surfaces permission errors without creating a download', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ errorMessage: '无权下载该作品' }), {
    status: 403, headers: { 'Content-Type': 'application/json' },
  }));
  await expect(getCreationDownload(14, 20)).rejects.toThrow('无权下载该作品');
  expect(createUrl).not.toHaveBeenCalled();
});

it.each([
  ['text/html', '<html>login</html>'],
  ['application/json', '{"success":false}'],
  ['image/png', ''],
])('rejects invalid or empty file responses (%s)', async (type, body) => {
  fetchMock.mockResolvedValue(new Response(body, { headers: { 'Content-Type': type } }));
  await expect(getCreationDownload(14, 20)).rejects.toThrow();
  expect(createUrl).not.toHaveBeenCalled();
});

it('does not create a file when the download is canceled', async () => {
  const controller = new AbortController();
  fetchMock.mockImplementation(async () => {
    controller.abort();
    return new Response('original', { headers: { 'Content-Type': 'image/png' } });
  });
  await expect(getCreationDownload(14, 20, controller.signal)).rejects.toThrow();
  expect(createUrl).not.toHaveBeenCalled();
});
