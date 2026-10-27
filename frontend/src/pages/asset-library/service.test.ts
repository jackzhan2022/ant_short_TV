import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { downloadAssetImage, downloadAssetImages } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
const fetchMock = vi.fn();
const createUrl = vi.fn(() => 'blob:asset-download');
const revokeUrl = vi.fn();
let click: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = createUrl;
    static revokeObjectURL = revokeUrl;
  });
  fetchMock.mockReset(); createUrl.mockClear(); revokeUrl.mockClear();
  click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  localStorage.setItem('accessToken', 'test-token');
  localStorage.setItem('currentTenantId', '10');
  // biome-ignore lint/suspicious/noDocumentCookie: Emulate the CSRF cookie issued by the authenticated backend.
  document.cookie = 'XSRF-TOKEN=csrf%20token; path=/';
});
afterEach(() => {
  vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear();
  // biome-ignore lint/suspicious/noDocumentCookie: Clear the emulated backend cookie between tests.
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
});

it('downloads an original through the authenticated application endpoint as a file', async () => {
  fetchMock.mockResolvedValue(new Response(new Blob(['original']), { headers: {
    'Content-Type': 'image/png', 'Content-Disposition': "attachment; filename*=UTF-8''%E8%A7%92%E8%89%B2.png",
  } }));
  const signal = new AbortController().signal;
  await downloadAssetImage(22, 51, signal);
  expect(fetchMock).toHaveBeenCalledWith('/api/projects/22/ai-image-results/51/download-file', expect.objectContaining({
    credentials: 'same-origin', redirect: 'error', signal,
    headers: expect.objectContaining({ 'X-Tenant-Id': '10' }),
  }));
  expect(fetchMock.mock.calls[0]?.[1].headers).not.toHaveProperty('Authorization');
  expect(click.mock.instances[0]).toHaveProperty('download', '角色.png');
  expect(createUrl).toHaveBeenCalledOnce();
  expect(revokeUrl).toHaveBeenCalledWith('blob:asset-download');
});

it('posts the selected IDs and downloads a single ZIP', async () => {
  fetchMock.mockResolvedValue(new Response(new Blob(['zip']), { headers: { 'Content-Type': 'application/zip' } }));
  await downloadAssetImages(22, [51, 52]);
  expect(fetchMock).toHaveBeenCalledWith('/api/projects/22/ai-image-results/download-archive', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ resultIds: [51, 52] }),
    headers: expect.objectContaining({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf token' }),
  }));
  expect(click.mock.instances[0]?.download).toMatch(/\.zip$/);
});

it('shows authorization errors and never saves an error page as an image', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ errorMessage: '无权访问该项目' }), {
    status: 403, headers: { 'Content-Type': 'application/json' },
  }));
  await expect(downloadAssetImage(22, 51)).rejects.toThrow('无权访问该项目');
  expect(createUrl).not.toHaveBeenCalled();
  expect(click).not.toHaveBeenCalled();
});

it('rejects successful HTML responses such as an expired-session login page', async () => {
  fetchMock.mockResolvedValue(new Response('<html>login</html>', { headers: { 'Content-Type': 'text/html' } }));
  await expect(downloadAssetImages(22, [51])).rejects.toThrow();
  expect(click).not.toHaveBeenCalled();
});
