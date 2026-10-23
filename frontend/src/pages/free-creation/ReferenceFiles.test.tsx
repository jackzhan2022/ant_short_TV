import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  MediaUploadHandle,
  VerifiedMediaUpload,
} from '@/services/mediaUpload';
import ReferenceFiles, { referenceFileError } from './ReferenceFiles';

const mocks = vi.hoisted(() => ({ start: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function upload() {
  const done = deferred<VerifiedMediaUpload>();
  const handle = {
    attempt: done.promise,
    cancel: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    retry: vi.fn(),
    sessionToken: 'verified-session',
  } as unknown as MediaUploadHandle;
  return { ...done, handle };
}
const file = new File(['image'], 'reference.png', { type: 'image/png' });
const select = () =>
  fireEvent.change(screen.getByLabelText('选择参考图片'), {
    target: { files: [file] },
  });
beforeEach(() => {
  vi.restoreAllMocks();
  mocks.start
    .mockReset()
    .mockImplementation(() => Promise.resolve(upload().handle));
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local-reference');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
});
describe('reference upload lifecycle', () => {
  it('rejects invalid types, zero bytes and model byte limits', () => {
    expect(
      referenceFileError(
        new File(['x'], 'text.txt', { type: 'text/plain' }),
        'image',
      ),
    ).toContain('格式');
    expect(
      referenceFileError(new File([], 'a.png', { type: 'image/png' }), 'image'),
    ).toContain('空文件');
    expect(referenceFileError(file, 'image', { maxBytes: 2 })).toContain(
      '大小',
    );
    expect(referenceFileError(file, 'image', { formats: ['webp'] })).toContain(
      '格式',
    );
  });
  it('enforces count, displays upload progress and only exposes a session after verification', async () => {
    const task = upload();
    const change = vi.fn();
    mocks.start.mockResolvedValue(task.handle);
    render(
      <ReferenceFiles
        kind="image"
        constraints={{ maxCount: 1 }}
        onChange={change}
      />,
    );
    select();
    await waitFor(() => expect(mocks.start).toHaveBeenCalledTimes(1));
    select();
    expect(screen.getByRole('alert')).toHaveTextContent('最多允许 1');
    expect(change.mock.lastCall?.[0][0].uploadSessionToken).toBeUndefined();
    act(() => mocks.start.mock.calls[0][1].onProgress({ percent: 0.6 }));
    expect(screen.getByText('上传中 60%')).toBeInTheDocument();
    act(() => mocks.start.mock.calls[0][1].onProgress({ percent: 1 }));
    expect(screen.getByText('完成校验中')).toBeInTheDocument();
    await act(async () =>
      task.resolve({
        sessionToken: 'verified-session',
        objectKey: 'key',
        size: 5,
        eTag: 'etag',
        contentType: file.type,
      }),
    );
    expect(screen.getByText('上传完成')).toBeInTheDocument();
    expect(change.mock.lastCall?.[0][0]).toMatchObject({
      status: 'complete',
      kind: 'image',
      uploadSessionToken: 'verified-session',
    });
  });
  it('pauses, resumes and retries verification without creating another upload', async () => {
    const task = upload();
    const resumed = deferred<VerifiedMediaUpload>();
    const retried = deferred<VerifiedMediaUpload>();
    vi.mocked(task.handle.resume).mockReturnValue(resumed.promise);
    vi.mocked(task.handle.retry).mockReturnValue(retried.promise);
    mocks.start.mockResolvedValue(task.handle);
    render(<ReferenceFiles kind="image" />);
    select();
    await screen.findByRole('button', { name: '暂停reference.png' });
    fireEvent.click(screen.getByRole('button', { name: '暂停reference.png' }));
    expect(task.handle.pause).toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '继续reference.png' }));
    await act(async () => resumed.reject(new Error('完成校验失败')));
    expect(screen.getByText('完成校验失败')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试reference.png' }));
    expect(task.handle.retry).toHaveBeenCalled();
    await act(async () =>
      retried.resolve({
        sessionToken: 'verified-session',
        objectKey: 'key',
        size: 5,
        eTag: 'etag',
        contentType: file.type,
      }),
    );
    expect(screen.getByText('上传完成')).toBeInTheDocument();
    expect(mocks.start).toHaveBeenCalledTimes(1);
  });
  it('cancels handles arriving after removal or unmount and releases previews', async () => {
    const pending = deferred<MediaUploadHandle>();
    const task = upload();
    mocks.start.mockReturnValue(pending.promise);
    const view = render(<ReferenceFiles kind="image" />);
    select();
    await waitFor(() => expect(mocks.start).toHaveBeenCalledOnce());
    fireEvent.click(screen.getByRole('button', { name: '移除reference.png' }));
    await act(async () => pending.resolve(task.handle));
    expect(task.handle.cancel).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce();
    const second = upload();
    mocks.start.mockResolvedValue(second.handle);
    select();
    await waitFor(() => expect(mocks.start).toHaveBeenCalledTimes(2));
    view.unmount();
    expect(second.handle.cancel).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });
});
