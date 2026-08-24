import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadEpisodeVideoVersion } from './service';

describe('downloadEpisodeVideoVersion', () => {
  const originalCreateElement = document.createElement.bind(document);
  const originalObjectUrl = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('accessToken', 'token-1');
    localStorage.setItem('currentTenantId', '7');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        blob: async () => new Blob(['video-bytes']),
        headers: {
          get: (name: string) =>
            name.toLowerCase() === 'content-disposition'
              ? 'attachment; filename="episode_1_v1.mp4"'
              : null,
        },
      }),
    );
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:episode');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.createElement = originalCreateElement;
    URL.createObjectURL = originalObjectUrl;
    URL.revokeObjectURL = originalRevoke;
  });

  it('downloads the episode video with auth headers', async () => {
    const click = vi.fn();
    const anchor = {
      href: '',
      download: '',
      rel: '',
      click,
    } as unknown as HTMLAnchorElement;
    vi.spyOn(document, 'createElement').mockReturnValue(anchor);

    await downloadEpisodeVideoVersion(5, 11);

    expect(fetch).toHaveBeenCalledWith(
      '/api/projects/5/episode-video-versions/11/download',
      expect.objectContaining({
        headers: {
          Authorization: 'Bearer token-1',
          'X-Tenant-Id': '7',
        },
      }),
    );
    expect(click).toHaveBeenCalled();
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:episode');
  });
});
