import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import VideoPreviewPlayer from './VideoPreviewPlayer';

const props = {
  src: '/first.mp4',
  label: '分镜1',
  sourceLabel: '分镜原始视频',
  empty: <div>无视频</div>,
  leadingControls: null,
  trailingControls: null,
  previous: null,
  next: null,
};
const pause = vi.fn();
const play = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  play.mockResolvedValue(undefined);
  Object.defineProperty(HTMLMediaElement.prototype, 'pause', {
    configurable: true,
    value: pause,
  });
  Object.defineProperty(HTMLMediaElement.prototype, 'play', {
    configurable: true,
    value: play,
  });
  Object.defineProperty(HTMLMediaElement.prototype, 'load', {
    configurable: true,
    value: vi.fn(),
  });
});
afterEach(() => vi.useRealTimers());
describe('video preview controls', () => {
  it('seeks by actual media duration and changes the real element volume', () => {
    render(<VideoPreviewPlayer {...props} previewDuration={6} />);
    const video = screen.getByLabelText('当前视频') as HTMLVideoElement;
    Object.defineProperty(video, 'duration', { configurable: true, value: 12 });
    fireEvent.loadedMetadata(video);
    fireEvent.change(screen.getByLabelText('播放进度'), {
      target: { value: '4' },
    });
    fireEvent.change(screen.getByLabelText('音量'), {
      target: { value: '0.5' },
    });
    expect(video.currentTime).toBe(4);
    expect(video.volume).toBe(0.5);
    expect(screen.getByText('/ 00:12')).toBeInTheDocument();
  });
  it('releases the old media when switching shots and pauses while task records are open', () => {
    const view = render(<VideoPreviewPlayer {...props} active />);
    const oldVideo = screen.getByLabelText('当前视频');
    view.rerender(<VideoPreviewPlayer {...props} src="/second.mp4" active />);
    expect(oldVideo).not.toHaveAttribute('src');
    const calls = pause.mock.calls.length;
    view.rerender(
      <VideoPreviewPlayer {...props} src="/second.mp4" active={false} />,
    );
    expect(pause.mock.calls.length).toBeGreaterThan(calls);
  });
  it('does not apply an old play failure to a newly selected shot', async () => {
    let reject: (error: Error) => void = () => {};
    play.mockImplementation(
      () =>
        new Promise((_resolve, fail) => {
          reject = fail;
        }),
    );
    const view = render(<VideoPreviewPlayer {...props} />);
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    view.rerender(<VideoPreviewPlayer {...props} src="/second.mp4" />);
    await act(async () => reject(new Error('old video failed')));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('plays, pauses, seeks, and replays a placeholder using the shot duration', () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] });
    render(<VideoPreviewPlayer {...props} src={undefined} previewDuration={6} />);
    const seek = screen.getByLabelText('播放进度');
    expect(seek).toHaveAttribute('max', '6');
    expect(screen.getByText('分镜预演')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    act(() => vi.advanceTimersByTime(1500));
    expect(Number((seek as HTMLInputElement).value)).toBeCloseTo(1.5);
    fireEvent.click(screen.getByRole('button', { name: '暂停' }));
    act(() => vi.advanceTimersByTime(1000));
    expect(Number((seek as HTMLInputElement).value)).toBeCloseTo(1.5);
    fireEvent.change(seek, { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    act(() => vi.advanceTimersByTime(2100));
    expect(seek).toHaveValue('6');
    expect(screen.getByRole('button', { name: '播放' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    act(() => vi.advanceTimersByTime(1000));
    expect(Number((seek as HTMLInputElement).value)).toBeCloseTo(1);
    expect(play).not.toHaveBeenCalled();
  });

  it('resets between missing clips and pauses placeholder playback when inactive', () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] });
    const view = render(<VideoPreviewPlayer {...props} src={undefined} previewDuration={6} playbackKey={1} />);
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    act(() => vi.advanceTimersByTime(1000));
    view.rerender(<VideoPreviewPlayer {...props} src={undefined} previewDuration={6} playbackKey={2} />);
    expect(screen.getByLabelText('播放进度')).toHaveValue('0');
    fireEvent.click(screen.getByRole('button', { name: '播放' }));
    act(() => vi.advanceTimersByTime(1000));
    view.rerender(<VideoPreviewPlayer {...props} src={undefined} previewDuration={6} playbackKey={2} active={false} />);
    act(() => vi.advanceTimersByTime(2000));
    expect(Number((screen.getByLabelText('播放进度') as HTMLInputElement).value)).toBeCloseTo(1);
    expect(screen.getByRole('button', { name: '播放' })).toBeInTheDocument();
  });

  it('disables playback when there is neither media nor a storyboard to preview', () => {
    render(<VideoPreviewPlayer {...props} src={undefined} />);
    expect(screen.getByRole('button', { name: '播放' })).toBeDisabled();
  });
});
