import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import StoryboardVideoStrip, { mergeVideoVersions } from './StoryboardVideoStrip';
import type { AiVideoResult } from './service';

const video = (id: number, createdAt: string): AiVideoResult => ({
  id, taskId: id, storyboardId: 1, videoUrl: `/${id}.mp4`, storagePath: `/${id}.mp4`,
  coverUrl: `/${id}.jpg`, isSelected: false, status: 'ACTIVE', createdAt,
});
afterEach(cleanup);

describe('storyboard video versions', () => {
  it('sorts by generation time and then id without promoting the current version', () => {
    const oldest = { ...video(99, '2026-01-01T00:00:00'), isSelected: true };
    const newer = video(2, '2026-02-01T00:00:00');
    const newest = video(1, '2026-03-01T00:00:00');
    expect(mergeVideoVersions([oldest, newer], [newest, newer]).map((item) => item.id)).toEqual([1, 2, 99]);
    expect(mergeVideoVersions([video(2, newest.createdAt || ''), newest]).map((item) => item.id)).toEqual([2, 1]);
  });

  it('loads older versions on demand and keeps the current badge independent of the preview', async () => {
    const recent = video(1, '2026-03-01T00:00:00');
    const older = video(2, '2026-02-01T00:00:00');
    const onPreview = vi.fn();
    const onLoad = vi.fn().mockResolvedValue({ data: [older], current: 2, pageSize: 1, total: 2 });
    render(<StoryboardVideoStrip storyboardNo={1} results={[recent]} currentResultId={1} previewResultId={2}
      history={{ data: [recent], current: 1, pageSize: 1, total: 2 }} onPreview={onPreview} onLoad={onLoad} />);
    expect(onLoad).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '加载更多分镜1视频' }));
    const oldButton = await screen.findByRole('button', { name: '预览分镜1视频版本2' });
    expect(onLoad).toHaveBeenCalledWith(2, 1, expect.any(AbortSignal));
    expect(oldButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('当前分镜').closest('button')).toHaveAttribute('aria-label', '预览分镜1视频版本1');
    fireEvent.click(oldButton);
    expect(onPreview).toHaveBeenCalledWith(older);
    await waitFor(() => expect(screen.queryByRole('button', { name: '加载更多分镜1视频' })).not.toBeInTheDocument());
  });
});
