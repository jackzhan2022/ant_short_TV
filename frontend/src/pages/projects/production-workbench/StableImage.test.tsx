import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StableImage from './StableImage';

describe('StableImage', () => {
  const decode = vi.fn<() => Promise<void>>();

  beforeEach(() => {
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

  it('keeps a decoded preview visible until the target is decoded', async () => {
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
    expect(preview).not.toBeNull();

    fireEvent.load(preview as HTMLImageElement);
    await waitFor(() => expect(preview).toHaveStyle({ opacity: '1' }));
    expect(target).toHaveStyle({ opacity: '0' });
    expect(screen.queryByLabelText('角色图加载中')).not.toBeInTheDocument();

    fireEvent.load(target);
    await waitFor(() => expect(target).toHaveStyle({ opacity: '1' }));
    expect(preview).toHaveStyle({ opacity: '0' });
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
});
