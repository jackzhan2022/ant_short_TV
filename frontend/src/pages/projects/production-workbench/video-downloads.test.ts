import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  downloadEpisodeStoryboardVideos,
  downloadStoryboardVideo,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
const fetchMock = vi.fn();
const downloads: Array<{ href: string; name: string }> = [];
beforeEach(() => {
  vi.clearAllMocks();
  downloads.length = 0;
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    downloads.push({ href: this.href, name: this.download });
  });
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
describe('episode video file downloads', () => {
  it('uses the selected storyboard binary endpoint and releases the blob URL', async () => {
    fetchMock.mockResolvedValue(
      new Response('video', { headers: { 'Content-Type': 'video/mp4' } }),
    );
    await downloadStoryboardVideo(44, 31);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/44/storyboards/31/download-video',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
    expect(downloads[0].name).toBe('storyboard_31.mp4');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:download');
  });
  it('requests one episode ZIP and decodes the server UTF-8 filename', async () => {
    fetchMock.mockResolvedValue(
      new Response('zip', {
        headers: {
          'Content-Type': 'application/zip',
          'Content-Disposition':
            "attachment; filename*=UTF-8''%E7%AC%AC02%E9%9B%86.zip",
        },
      }),
    );
    await downloadEpisodeStoryboardVideos(44, 2);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/44/episodes/2/download-videos',
      expect.anything(),
    );
    expect(downloads[0].name).toBe('第02集.zip');
  });
  it('never saves an HTML error page as a video', async () => {
    fetchMock.mockResolvedValue(
      new Response('<html>login</html>', {
        headers: { 'Content-Type': 'text/html' },
      }),
    );
    await expect(downloadStoryboardVideo(44, 31)).rejects.toThrow();
    expect(downloads).toHaveLength(0);
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
  it('never saves forbidden JSON responses as a ZIP', async () => {
    fetchMock.mockResolvedValue(
      new Response('{}', {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    await expect(downloadEpisodeStoryboardVideos(44, 2)).rejects.toThrow();
    expect(downloads).toHaveLength(0);
  });
});
