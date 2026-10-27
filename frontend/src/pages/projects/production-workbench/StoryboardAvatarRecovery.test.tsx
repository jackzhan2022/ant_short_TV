import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import StoryboardAvatarRecovery from './StoryboardAvatarRecovery';
import { queryAvatarRecovery } from './avatar-recovery-service';
vi.mock('./avatar-recovery-service', () => ({ queryAvatarRecovery: vi.fn() }));
beforeEach(() => { vi.mocked(queryAvatarRecovery).mockReset(); });
afterEach(() => { cleanup(); vi.useRealTimers(); });
it('shows ingestion progress and follows the single linked retry after readiness', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.mocked(queryAvatarRecovery)
    .mockResolvedValueOnce({ id: 8, sourceTaskId: 1, status: 'RUNNING', phase: '等待素材处理完成' })
    .mockResolvedValue({ id: 8, sourceTaskId: 1, status: 'SUCCEEDED', phase: '已创建关联重试视频', retryTaskId: 22 });
  const ready = vi.fn().mockResolvedValue(undefined), busy = vi.fn();
  render(<StoryboardAvatarRecovery projectId={2} taskId={1} onReady={ready} onBusyChange={busy} />);
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('等待素材处理完成'));
  expect(busy).toHaveBeenCalledWith(true);
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(ready).toHaveBeenCalledExactlyOnceWith(22);
  expect(queryAvatarRecovery).toHaveBeenCalledTimes(2);
});
it('shows an actionable failure without creating a video in the browser', async () => {
  vi.mocked(queryAvatarRecovery).mockResolvedValue({ id: 8, sourceTaskId: 1, status: 'FAILED', phase: '自动恢复未完成', errorMessage: '参考图片素材处理失败' });
  const ready = vi.fn();
  render(<StoryboardAvatarRecovery projectId={2} taskId={1} onReady={ready} />);
  await waitFor(() => expect(screen.getByText('参考图片素材处理失败')).toBeInTheDocument());
  expect(ready).not.toHaveBeenCalled();
});
it('aborts in-flight status requests when leaving the storyboard', async () => {
  vi.mocked(queryAvatarRecovery).mockImplementation(() => new Promise(() => {}));
  const view = render(<StoryboardAvatarRecovery projectId={2} taskId={1} onReady={vi.fn()} />);
  await waitFor(() => expect(queryAvatarRecovery).toHaveBeenCalledOnce());
  const signal = vi.mocked(queryAvatarRecovery).mock.calls[0][2];
  view.unmount();
  expect(signal?.aborted).toBe(true);
});
