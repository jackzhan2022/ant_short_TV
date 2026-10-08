import { act, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
describe('video preview controls', () => {
  it('seeks by actual media duration and changes the real element volume', () => {
    render(<VideoPreviewPlayer {...props} />);
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
});
