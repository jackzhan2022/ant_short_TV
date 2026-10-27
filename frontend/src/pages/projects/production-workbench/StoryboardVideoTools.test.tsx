import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import StoryboardVideoTools from './StoryboardVideoTools';
vi.mock('./VideoFrameCaptureModal', () => ({ FrameCameraIcon: () => <span>相机</span> }));
it('offers capture for the displayed version without triggering parent playback', () => {
  const play = vi.fn(), capture = vi.fn();
  render(<div onClick={play}><StoryboardVideoTools enabled resultId={22} onCapture={capture} /></div>);
  fireEvent.click(screen.getByRole('button', { name: '取视频帧' }));
  expect(capture).toHaveBeenCalledOnce(); expect(play).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '擦除字幕' })).toBeDisabled();
});
it('disables capture when there is no permission or resolvable video version', () => {
  const capture = vi.fn();
  const view = render(<StoryboardVideoTools enabled={false} resultId={22} onCapture={capture} />);
  expect(screen.getByRole('button', { name: '取视频帧' })).toBeDisabled();
  view.rerender(<StoryboardVideoTools enabled onCapture={capture} />);
  expect(screen.getByRole('button', { name: '取视频帧' })).toBeDisabled();
});

it('erases the displayed version without triggering parent playback and respects its own permission', () => {
  const parent = vi.fn(), erase = vi.fn();
  const view = render(<div onClick={parent}><StoryboardVideoTools enabled resultId={44} onCapture={vi.fn()} canErase onErase={erase} /></div>);
  fireEvent.click(screen.getByRole('button', { name: '擦除字幕' }));
  expect(erase).toHaveBeenCalledOnce(); expect(parent).not.toHaveBeenCalled();
  view.rerender(<StoryboardVideoTools enabled resultId={44} onCapture={vi.fn()} canErase eraseRunning onErase={erase} />);
  expect(screen.getByRole('button', { name: '擦除字幕' })).toBeDisabled();
});
