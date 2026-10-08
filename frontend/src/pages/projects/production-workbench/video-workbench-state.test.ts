import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { StoryboardShot } from './service';
import {
  getShotState,
  loadVideoEpisode,
  readStoryboardTarget,
  storyboardEditPath,
} from './video-workbench-state';

const mocks = vi.hoisted(() => ({ query: vi.fn() }));
vi.mock('./service', () => ({ queryStoryboardWorkspace: mocks.query }));
const shot = {
  id: 31,
  episodeNo: 2,
  shotNo: 23,
  durationSeconds: 8,
} as StoryboardShot;

describe('video workbench state', () => {
  beforeEach(() => vi.clearAllMocks());
  it('loads every page using the actual server page size', async () => {
    mocks.query
      .mockResolvedValueOnce({
        data: {
          episodes: [{ episodeNo: 2 }],
          episodeNo: 2,
          current: 1,
          pageSize: 1,
          total: 2,
          storyboards: [shot],
        },
      })
      .mockResolvedValueOnce({
        data: {
          episodeNo: 2,
          current: 2,
          pageSize: 1,
          total: 2,
          storyboards: [{ ...shot, id: 32 }],
        },
      });
    const result = await loadVideoEpisode(44, 2);
    expect(result.storyboards.map((item) => item.id)).toEqual([31, 32]);
    expect(mocks.query).toHaveBeenLastCalledWith(44, {
      episodeNo: 2,
      current: 2,
      pageSize: 1,
    });
  });
  it('does not treat an incomplete page as a complete episode', async () => {
    mocks.query.mockResolvedValue({
      data: {
        episodes: [],
        episodeNo: 2,
        current: 1,
        pageSize: 1,
        total: 2,
        storyboards: [],
      },
    });
    await expect(loadVideoEpisode(44, 2)).rejects.toThrow();
  });
  it('prefers the composed video and does not hide it when a later generation failed', () => {
    const state = getShotState(
      {
        ...shot,
        currentShotVideoUrl: '/composed.mp4',
        currentVideoUrl: '/raw.mp4',
      },
      [],
    );
    expect(state.src).toBe('/composed.mp4');
    expect(state.kind).toBe('ready');
    expect(state.sourceLabel).toBe('单镜头合成结果');
  });
  it('does not mistake a first frame for a playable video', () => {
    expect(
      getShotState({ ...shot, firstFrameUrl: '/frame.jpg' }, []).kind,
    ).toBe('pending');
    expect(
      getShotState(shot, [{ id: 5, storyboardId: 31, status: 'FAILED' } as any])
        .kind,
    ).toBe('failed');
  });
  it('links by real shot id and its list position instead of shot number', () => {
    expect(storyboardEditPath(44, shot, 21)).toBe(
      '/projects/44/production-workbench/storyboard?episodeNo=2&current=2&storyboardId=31',
    );
  });
  it('validates route parameters and ignores unsafe targets', () => {
    expect(
      readStoryboardTarget('?episodeNo=2&current=3&storyboardId=31'),
    ).toEqual({ episodeNo: 2, current: 3, storyboardId: 31 });
    expect(
      readStoryboardTarget('?episodeNo=-1&current=NaN&storyboardId=oops'),
    ).toEqual({});
  });
});
