import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import StoryboardImageUpload from './StoryboardImageUpload';

const mocks = vi.hoisted(() => ({ start: vi.fn(), bind: vi.fn(), error: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
vi.mock('./storyboard-image-upload', () => ({ bindStoryboardImageUpload: mocks.bind }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { error: mocks.error } }) },
  Button: ({ icon: _icon, loading, ...props }: any) => <button type="button" {...props} disabled={props.disabled || loading} />,
}));
const props = { projectId: 1, storyboardId: 22, storyboardNo: 2, disabled: false };
const image = () => new File(['image'], '参考图.png', { type: 'image/png' });
const choose = (file: File) => fireEvent.change(screen.getByLabelText('选择分镜2本地图片'), { target: { files: [file] } });
beforeEach(() => {
  vi.resetAllMocks();
  mocks.bind.mockResolvedValue({ success: true, data: { materialId: 99, name: '参考图.png' } });
});

it('stores the verified image in the correct shot and inserts it using the latest editor callback', async () => {
  let finish!: (value: { sessionToken: string }) => void;
  mocks.start.mockResolvedValue({ attempt: new Promise((resolve) => { finish = resolve; }), cancel: vi.fn() });
  const stale = vi.fn();
  const latest = vi.fn();
  const view = render(<StoryboardImageUpload {...props} onUploaded={stale} />);
  choose(image());
  await waitFor(() => expect(mocks.start).toHaveBeenCalledOnce());
  act(() => mocks.start.mock.calls[0][1].onProgress({ percent: 0.45 }));
  expect(screen.getByRole('status')).toHaveTextContent('上传中 45%');
  expect(screen.getByRole('button', { name: '上传分镜2参考图片' })).toBeDisabled();
  view.rerender(<StoryboardImageUpload {...props} onUploaded={latest} />);
  await act(async () => finish({ sessionToken: 'verified-token' }));
  expect(mocks.bind).toHaveBeenCalledWith(1, 22, 'verified-token', '参考图.png');
  expect(latest).toHaveBeenCalledWith({ materialId: 99, name: '参考图.png' });
  expect(stale).not.toHaveBeenCalled();
});

it('rejects unsupported, empty and oversized files before creating an upload', () => {
  render(<StoryboardImageUpload {...props} onUploaded={vi.fn()} />);
  choose(new File(['video'], 'video.mp4', { type: 'video/mp4' }));
  choose(new File([], 'empty.png', { type: 'image/png' }));
  const large = image();
  Object.defineProperty(large, 'size', { value: 21 * 1024 * 1024 });
  choose(large);
  expect(mocks.error).toHaveBeenCalledTimes(3);
  expect(mocks.start).not.toHaveBeenCalled();
});

it('does not insert a reference when saving fails and permits retrying the same file', async () => {
  mocks.start.mockResolvedValue({ attempt: Promise.resolve({ sessionToken: 'token' }), cancel: vi.fn() });
  mocks.bind.mockRejectedValueOnce(new Error('图片保存失败'));
  const inserted = vi.fn();
  render(<StoryboardImageUpload {...props} onUploaded={inserted} />);
  choose(image());
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('图片保存失败'));
  expect(inserted).not.toHaveBeenCalled();
  choose(image());
  await waitFor(() => expect(inserted).toHaveBeenCalledOnce());
});

it('cancels when leaving the shot and never inserts into another shot', async () => {
  let finish!: (value: { sessionToken: string }) => void;
  const cancel = vi.fn();
  mocks.start.mockResolvedValue({ attempt: new Promise((resolve) => { finish = resolve; }), cancel });
  const inserted = vi.fn();
  const view = render(<StoryboardImageUpload key={22} {...props} onUploaded={inserted} />);
  choose(image());
  await waitFor(() => expect(mocks.start).toHaveBeenCalledOnce());
  view.rerender(<StoryboardImageUpload key={23} {...props} storyboardId={23} onUploaded={inserted} />);
  await act(async () => finish({ sessionToken: 'token' }));
  expect(cancel).toHaveBeenCalledOnce();
  expect(mocks.bind).not.toHaveBeenCalled();
  expect(inserted).not.toHaveBeenCalled();
});

it('prevents upload for viewers', () => {
  render(<StoryboardImageUpload {...props} disabled onUploaded={vi.fn()} />);
  choose(image());
  expect(screen.getByRole('button', { name: '上传分镜2参考图片' })).toBeDisabled();
  expect(mocks.start).not.toHaveBeenCalled();
});
