import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import VideoFrameCaptureModal from './VideoFrameCaptureModal';
const mocks = vi.hoisted(() => ({ capture: vi.fn(), thumbnails: vi.fn(), upload: vi.fn(), save: vi.fn(), cancel: vi.fn() }));
vi.mock('./video-frame-capture', () => ({ captureVideoFrame: mocks.capture, generateVideoThumbnails: mocks.thumbnails,
  formatFrameTime: (seconds: number) => `${seconds.toFixed(3)}s` }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.upload }));
vi.mock('./video-frame-service', () => ({ saveVideoFrame: mocks.save, videoFrameSource: (project: number, result: number) => `/api/projects/${project}/ai-video-results/${result}/frame-source` }));
vi.mock('antd', () => ({ Modal: ({ children, title, onCancel, closable }: any) => <div role="dialog" aria-label={title}>{closable !== false && <button type="button" onClick={onCancel}>关闭</button>}{children}</div>,
  Button: ({ children, onClick, disabled, loading, icon, ...props }: any) => <button type="button" onClick={onClick} disabled={disabled || loading} aria-label={props['aria-label']}>{icon}{children}</button> }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
  mocks.thumbnails.mockResolvedValue([]);
  mocks.capture.mockImplementation(async () => ({ blob: new Blob(['frame'], { type: 'image/png' }), timeSeconds: 1.24, width: 1080, height: 1920 }));
  mocks.upload.mockResolvedValue({ attempt: Promise.resolve({ sessionToken: 'session-1' }), cancel: mocks.cancel });
  mocks.save.mockResolvedValue({ id: 33 });
});
afterEach(() => vi.restoreAllMocks());
function ready() {
  const video = screen.getByLabelText('截帧视频预览') as HTMLVideoElement;
  Object.defineProperties(video, { readyState: { value: 2 }, videoWidth: { value: 1080 }, videoHeight: { value: 1920 }, duration: { value: 5 } });
  fireEvent.loadedMetadata(video); fireEvent.loadedData(video);
  return video;
}
it('blocks capture before decoding and saves repeatedly without closing', async () => {
  const close = vi.fn();
  render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={close} />);
  expect(screen.getByRole('button', { name: '截取当前帧并保存' })).toBeDisabled();
  const video = ready();
  expect(video).toHaveAttribute('src', '/api/projects/1/ai-video-results/22/frame-source');
  fireEvent.click(screen.getByRole('button', { name: '截取当前帧并保存' }));
  await screen.findByText(/本次已保存 1 张/);
  expect(mocks.save).toHaveBeenCalledWith(1, 22, 'session-1', 1.24, expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('button', { name: '截取当前帧并保存' }));
  await screen.findByText(/本次已保存 2 张/);
  expect(close).not.toHaveBeenCalled();
});
it('retries a failed binding using the same completed upload and frame timestamp', async () => {
  mocks.save.mockRejectedValueOnce(new Error('保存失败'));
  render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} />); ready();
  fireEvent.click(screen.getByRole('button', { name: '截取当前帧并保存' }));
  await screen.findByText(/保存失败/);
  fireEvent.click(screen.getByRole('button', { name: '重试保存当前截图' }));
  await screen.findByText(/本次已保存 1 张/);
  expect(mocks.upload).toHaveBeenCalledOnce(); expect(mocks.capture).toHaveBeenCalledOnce();
  expect(mocks.save).toHaveBeenCalledTimes(2);
});
it('prevents duplicate submission and closing while saving', async () => {
  let finish!: (value: unknown) => void;
  mocks.save.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} />); ready();
  const button = screen.getByRole('button', { name: '截取当前帧并保存' });
  fireEvent.click(button); fireEvent.click(button);
  await waitFor(() => expect(mocks.save).toHaveBeenCalledOnce());
  expect(screen.queryByRole('button', { name: '关闭' })).not.toBeInTheDocument();
  await act(async () => finish({ id: 1 }));
});
it('disables capture while seeking and releases both video and thumbnail work on close', async () => {
  const view = render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} />);
  const video = ready(); fireEvent.seeking(video);
  expect(screen.getByRole('button', { name: '截取当前帧并保存' })).toBeDisabled();
  fireEvent.seeked(video);
  expect(screen.getByRole('button', { name: '截取当前帧并保存' })).not.toBeDisabled();
  const signal = mocks.thumbnails.mock.calls[0][1]; view.unmount();
  expect(signal.aborted).toBe(true); expect(video).not.toHaveAttribute('src');
});

it('bases microseek on live playback time instead of the previous timeupdate event', () => {
  render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} />);
  const video = ready(); video.currentTime = 1.2;
  fireEvent.click(screen.getByRole('button', { name: '前进0.04秒' }));
  expect(video.currentTime).toBeCloseTo(1.24);
});
it('clears a failed pending image when explicitly reloading the video', async () => {
  mocks.save.mockRejectedValueOnce(new Error('保存失败'));
  render(<VideoFrameCaptureModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} />);
  const video = ready();
  fireEvent.click(screen.getByRole('button', { name: '截取当前帧并保存' }));
  await screen.findByText(/保存失败/);
  fireEvent.error(video);
  fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
  ready();
  fireEvent.click(screen.getByRole('button', { name: '截取当前帧并保存' }));
  await screen.findByText(/本次已保存 1 张/);
  expect(mocks.capture).toHaveBeenCalledTimes(2);
  expect(mocks.upload).toHaveBeenCalledTimes(2);
});
