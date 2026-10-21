import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import AssetLocalImageUpload from './AssetLocalImageUpload';

const mocks = vi.hoisted(() => ({ start: vi.fn(), bind: vi.fn(), error: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
vi.mock('./service', () => ({ bindVisualVariantImageUpload: mocks.bind }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { error: mocks.error } }) },
  Button: ({ children, onClick, disabled, loading }: any) => (
    <button type="button" onClick={onClick} disabled={disabled || loading}>{children}</button>
  ),
}));

const file = () => new File(['image'], 'portrait.png', { type: 'image/png' });
const choose = (image: File) => fireEvent.change(screen.getByLabelText('选择本地图片'), { target: { files: [image] } });
beforeEach(() => {
  vi.resetAllMocks();
  mocks.bind.mockResolvedValue({ success: true });
});

it('shows progress and binds the verified image to the selected variant before refreshing', async () => {
  let finish!: (value: { sessionToken: string }) => void;
  const attempt = new Promise((resolve) => { finish = resolve; });
  mocks.start.mockResolvedValue({ attempt, cancel: vi.fn() });
  const refresh = vi.fn();
  render(<AssetLocalImageUpload projectId={1} variantId={22} disabled={false} onUploaded={refresh} />);
  choose(file());
  await waitFor(() => expect(mocks.start).toHaveBeenCalled());
  act(() => mocks.start.mock.calls[0][1].onProgress({ percent: 0.45 }));
  expect(screen.getByText('上传中 45%')).toBeTruthy();
  await act(async () => finish({ sessionToken: 'verified-token' }));
  expect(mocks.bind).toHaveBeenCalledWith(1, 22, 'verified-token');
  expect(refresh).toHaveBeenCalledOnce();
  expect(screen.getByText('本地上传')).toBeTruthy();
});

it('rejects non-images and oversized images before uploading', () => {
  render(<AssetLocalImageUpload projectId={1} variantId={22} disabled={false} onUploaded={vi.fn()} />);
  choose(new File(['x'], 'video.mp4', { type: 'video/mp4' }));
  const large = file();
  Object.defineProperty(large, 'size', { value: 21 * 1024 * 1024 });
  choose(large);
  expect(mocks.error).toHaveBeenCalledTimes(2);
  expect(mocks.start).not.toHaveBeenCalled();
});

it('cancels an upload when switching variants and never binds it to the new variant', async () => {
  let finish!: (value: { sessionToken: string }) => void;
  const cancel = vi.fn();
  mocks.start.mockResolvedValue({ attempt: new Promise((resolve) => { finish = resolve; }), cancel });
  const refresh = vi.fn();
  const view = render(<AssetLocalImageUpload key={22} projectId={1} variantId={22} disabled={false} onUploaded={refresh} />);
  choose(file());
  await waitFor(() => expect(mocks.start).toHaveBeenCalledOnce());
  view.rerender(<AssetLocalImageUpload key={23} projectId={1} variantId={23} disabled={false} onUploaded={refresh} />);
  await act(async () => finish({ sessionToken: 'old-token' }));
  expect(cancel).toHaveBeenCalledOnce();
  expect(mocks.bind).not.toHaveBeenCalled();
  expect(refresh).not.toHaveBeenCalled();
});

it('preserves the existing image when binding fails and allows choosing the same file again', async () => {
  mocks.start.mockResolvedValue({ attempt: Promise.resolve({ sessionToken: 'token' }), cancel: vi.fn() });
  mocks.bind.mockRejectedValueOnce(new Error('保存失败'));
  const refresh = vi.fn();
  render(<AssetLocalImageUpload projectId={1} variantId={22} disabled={false} onUploaded={refresh} />);
  const image = file();
  choose(image);
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('保存失败'));
  expect(refresh).not.toHaveBeenCalled();
  choose(image);
  await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
});

it('disables upload during image generation or asset normalization', () => {
  render(<AssetLocalImageUpload projectId={1} variantId={22} disabled onUploaded={vi.fn()} />);
  expect((screen.getByRole('button', { name: '本地上传' }) as HTMLButtonElement).disabled).toBe(true);
  choose(file());
  expect(mocks.start).not.toHaveBeenCalled();
});
