import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ClickToPlayVideo from './index';

describe('ClickToPlayVideo', () => {
  beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
  });
  afterEach(() => vi.restoreAllMocks());

  it('mounts a video source only after an explicit play command', () => {
    const view = render(
      <ClickToPlayVideo src="/api/videos/1" poster="/thumb.jpg" alt="视频 A" />,
    );
    expect(view.container.querySelector('video,source')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '播放视频 A' }));
    const video = view.container.querySelector('video');
    expect(video).toHaveAttribute('src', '/api/videos/1');
    expect(video).toHaveAttribute('preload', 'none');
    expect(video).toHaveAttribute('controls');
  });

  it('starts browser playback from the explicit command and retains controls when the browser blocks it', async () => {
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new DOMException('User activation required', 'NotAllowedError'));
    const view = render(<ClickToPlayVideo src="/a.mp4" alt="A" />);
    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '播放A' }));
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1);
    await act(async () => {});
    expect(view.container.querySelector('video')).toHaveAttribute('controls');
    expect(screen.queryByText('视频暂时无法播放')).not.toBeInTheDocument();
  });

  it('releases the previous player when another surface is activated and when a surface closes', () => {
    const view = render(
      <>
        <ClickToPlayVideo src="/a.mp4" alt="A" />
        <ClickToPlayVideo src="/b.mp4" alt="B" />
      </>,
    );
    fireEvent.click(screen.getByRole('button', { name: '播放A' }));
    const previous = view.container.querySelector('video');
    fireEvent.click(screen.getByRole('button', { name: '播放B' }));
    expect(previous).not.toHaveAttribute('src');
    expect(view.container.querySelectorAll('video')).toHaveLength(1);
    expect(view.container.querySelector('video')).toHaveAttribute(
      'src',
      '/b.mp4',
    );
    view.rerender(<ClickToPlayVideo src="/b.mp4" alt="B" active={false} />);
    expect(view.container.querySelector('video')).toBeNull();
  });

  it('cancels delayed authorization after a source change', async () => {
    let finish: (url: string) => void = () => {};
    let signal: AbortSignal | undefined;
    const resolveSrc = vi.fn((next: AbortSignal) => {
      signal = next;
      return new Promise<string>((resolve) => {
        finish = resolve;
      });
    });
    const view = render(<ClickToPlayVideo resolveSrc={resolveSrc} alt="A" />);
    expect(resolveSrc).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '播放A' }));
    view.rerender(<ClickToPlayVideo src="/b.mp4" alt="B" />);
    await act(async () => finish('/stale.mp4'));
    expect(signal?.aborted).toBe(true);
    expect(view.container.querySelector('video')).toBeNull();
  });

  it('unmounts and clears resources when the document becomes hidden', () => {
    const view = render(<ClickToPlayVideo src="/a.mp4" alt="A" />);
    fireEvent.click(screen.getByRole('button', { name: '播放A' }));
    const video = view.container.querySelector('video');
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: true,
    });
    fireEvent(document, new Event('visibilitychange'));
    expect(view.container.querySelector('video')).toBeNull();
    expect(video).not.toHaveAttribute('src');
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    });
  });
});
