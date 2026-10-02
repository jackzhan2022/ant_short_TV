import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StableImage from './StableImage';

describe('StableImage', () => {
  const decode = vi.fn<() => Promise<void>>();

  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', undefined);
    decode.mockReset();
    decode.mockResolvedValue(undefined);
    Object.defineProperty(HTMLImageElement.prototype, 'decode', {
      configurable: true,
      value: decode,
    });
  });

  it('reveals the target only after load and decode complete', async () => {
    let finishDecode: (() => void) | undefined;
    decode.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        finishDecode = resolve;
      }),
    );
    render(<StableImage src="/role.png" alt="角色图" fallback="角" />);

    const image = screen.getByAltText('角色图');
    expect(image).toHaveStyle({ opacity: '0' });
    expect(screen.getByLabelText('角色图加载中')).toBeInTheDocument();

    fireEvent.load(image);
    expect(image).toHaveStyle({ opacity: '0' });
    finishDecode?.();

    await waitFor(() => expect(image).toHaveStyle({ opacity: '1' }));
    expect(screen.queryByLabelText('角色图加载中')).not.toBeInTheDocument();
  });

  it('returns to loading when the target URL changes', async () => {
    const { rerender } = render(
      <StableImage src="/first.png" alt="角色图" fallback="角" />,
    );
    fireEvent.load(screen.getByAltText('角色图'));
    await waitFor(() =>
      expect(screen.getByAltText('角色图')).toHaveStyle({ opacity: '1' }),
    );

    rerender(<StableImage src="/second.png" alt="角色图" fallback="角" />);

    expect(screen.getByAltText('角色图')).toHaveAttribute('src', '/second.png');
    expect(screen.getByAltText('角色图')).toHaveStyle({ opacity: '0' });
    expect(screen.getByLabelText('角色图加载中')).toBeInTheDocument();
  });

  it('loads only the compressed preview when one is available', async () => {
    const { container } = render(
      <StableImage
        src="/original.png"
        previewSrc="/thumbnail.png"
        alt="角色图"
        fallback="角"
      />,
    );
    const target = screen.getByAltText('角色图');
    const preview = container.querySelector<HTMLImageElement>(
      'img[src="/thumbnail.png"]',
    );
    expect(preview).toBe(target);
    expect(container.querySelector('img[src="/original.png"]')).toBeNull();
    fireEvent.load(target);
    await waitFor(() => expect(target).toHaveStyle({ opacity: '1' }));
  });
  afterEach(() => vi.unstubAllGlobals());

  it('does not assign a source until the image enters the viewport margin', () => {
    let callback: IntersectionObserverCallback | undefined;
    const disconnect = vi.fn();
    vi.stubGlobal(
      'IntersectionObserver',
      vi.fn(function (
        this: any,
        next: IntersectionObserverCallback,
        options: IntersectionObserverInit,
      ) {
        callback = next;
        expect(options.rootMargin).toBe('200px');
        this.observe = vi.fn();
        this.disconnect = disconnect;
      }),
    );
    const view = render(<StableImage src="/thumbnail.png" alt="延迟角色" />);
    expect(screen.getByAltText('延迟角色')).not.toHaveAttribute('src');
    act(() =>
      callback?.(
        [{ isIntersecting: true }] as IntersectionObserverEntry[],
        {} as IntersectionObserver,
      ),
    );
    expect(screen.getByAltText('延迟角色')).toHaveAttribute(
      'src',
      '/thumbnail.png',
    );
    view.unmount();
    expect(disconnect).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('uses the fallback when the URL is missing or fails', () => {
    const { rerender } = render(
      <StableImage src={undefined} alt="角色图" fallback="角" />,
    );
    expect(screen.getByText('角')).toBeInTheDocument();
    expect(screen.queryByAltText('角色图')).not.toBeInTheDocument();

    rerender(<StableImage src="/broken.png" alt="角色图" fallback="角" />);
    fireEvent.error(screen.getByAltText('角色图'));

    expect(screen.getByText('角')).toBeInTheDocument();
    expect(screen.queryByLabelText('角色图加载中')).not.toBeInTheDocument();
  });

  it('reveals a loaded image even when the optional decode API rejects', async () => {
    decode.mockRejectedValueOnce(new Error('Decode unavailable'));
    render(<StableImage src="/thumbnail.png" alt="解码图" />);
    fireEvent.load(screen.getByAltText('解码图'));
    await waitFor(() =>
      expect(screen.getByAltText('解码图')).toHaveStyle({ opacity: '1' }),
    );
  });
});
