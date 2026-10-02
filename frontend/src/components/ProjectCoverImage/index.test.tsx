import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ProjectCoverImage from './index';

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: mocks.request }));
let visibility: IntersectionObserverCallback;
vi.mock('antd', () => ({
  Image: ({ alt, src, ...props }: any) => (
    <img
      alt={alt}
      src={src}
      loading={props.loading}
      decoding={props.decoding}
    />
  ),
}));

describe('ProjectCoverImage', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.request.mockReset();
    mocks.request.mockResolvedValue({ data: { status: 'PENDING' } });
    vi.stubGlobal(
      'IntersectionObserver',
      vi.fn(function (this: any, callback: IntersectionObserverCallback) {
        visibility = callback;
        this.observe = vi.fn();
        this.disconnect = vi.fn();
      }),
    );
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });
  const show = async (visible = true) =>
    act(async () => {
      visibility(
        [{ isIntersecting: visible }] as IntersectionObserverEntry[],
        {} as IntersectionObserver,
      );
    });

  it('polls pending status only while visible and loads the unchanged display entry when ready', async () => {
    render(
      <ProjectCoverImage
        src="/api/projects/7/cover"
        alt="Cover"
        status="PENDING"
      />,
    );
    expect(mocks.request).not.toHaveBeenCalled();
    expect(screen.getByAltText('Cover')).not.toHaveAttribute('src');
    await show();
    expect(mocks.request).toHaveBeenCalledWith(
      '/api/projects/7/cover/status',
      expect.anything(),
    );
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(mocks.request).toHaveBeenCalledTimes(2);
    await show(false);
    await act(async () => vi.advanceTimersByTimeAsync(15000));
    expect(mocks.request).toHaveBeenCalledTimes(2);
    mocks.request.mockResolvedValue({ data: { status: 'READY' } });
    await show();
    expect(screen.getByAltText('Cover')).toHaveAttribute(
      'src',
      '/api/projects/7/cover',
    );
    expect(screen.getByAltText('Cover')).toHaveAttribute('loading', 'lazy');
  });

  it('ignores stale status responses after the source changes', async () => {
    let finish: (value: unknown) => void = () => {};
    mocks.request.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const view = render(
      <ProjectCoverImage
        src="/api/projects/7/cover"
        alt="Cover"
        status="PENDING"
      />,
    );
    await show();
    view.rerender(
      <ProjectCoverImage
        src="/api/projects/8/cover"
        alt="Cover"
        status="FAILED"
      />,
    );
    await act(async () => finish({ data: { status: 'READY' } }));
    expect(screen.getByAltText('Cover')).not.toHaveAttribute('src');
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });

  it('stops after twelve pending checks without requesting an original', async () => {
    render(
      <ProjectCoverImage
        src="/api/projects/7/cover"
        alt="Cover"
        status="PENDING"
      />,
    );
    await show();
    await act(async () => vi.advanceTimersByTimeAsync(90000));
    expect(mocks.request).toHaveBeenCalledTimes(12);
    expect(screen.getByAltText('Cover')).not.toHaveAttribute('src');
  });

  it('retries a failed compressed cover only after an explicit command', async () => {
    mocks.request.mockResolvedValueOnce({ data: { status: 'PENDING' } });
    render(
      <ProjectCoverImage src="/api/projects/7/cover" alt="Cover" status="FAILED" />,
    );
    await show();
    expect(mocks.request).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '重试Cover' }));
    await act(async () => undefined);
    expect(mocks.request).toHaveBeenCalledWith(
      '/api/projects/7/cover/retry', { method: 'POST' },
    );
  });

  it('pauses pending checks while the browser document is hidden', async () => {
    render(
      <ProjectCoverImage
        src="/api/projects/7/cover"
        alt="Cover"
        status="PENDING"
      />,
    );
    await show();
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    fireEvent(document, new Event('visibilitychange'));
    await act(async () => vi.advanceTimersByTimeAsync(15000));
    expect(mocks.request).toHaveBeenCalledTimes(1);
    hidden.mockRestore();
    fireEvent(document, new Event('visibilitychange'));
  });
});
