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
import Composer from './Composer';
import { newDraft } from './draft';
import type { CreationModel } from './service';

const mocks = vi.hoisted(() => ({
  start: vi.fn(),
  projects: vi.fn(),
  assets: vi.fn(),
}));
vi.mock('@/services/account-team/project', () => ({
  queryProjects: mocks.projects,
}));
vi.mock('@/pages/asset-library/service', () => ({
  queryProjectAssetLibrary: mocks.assets,
}));
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
  it.each([
    'IMAGE',
    'VIDEO',
  ] as const)('accepts pasted images from the %s prompt without sending them to the other mode', async (mode) => {
    const model = (type: 'IMAGE' | 'VIDEO'): CreationModel => ({
      id: type === 'IMAGE' ? 1 : 2,
      name: type,
      serviceType: type,
      isDefault: true,
      configStatus: 'configured',
      references: { image: { maxCount: 2 } },
      resolutions: [],
    });
    render(
      <>
        {(['IMAGE', 'VIDEO'] as const).map((type) => (
          <div key={type} hidden={type !== mode}>
            <Composer
              mode={type}
              models={[model(type)]}
              loading={false}
              draft={newDraft(model(type))}
              onChange={vi.fn()}
              example="示例"
            />
          </div>
        ))}
      </>,
    );
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: { files: [file], items: [] },
    });
    fireEvent(screen.getByRole('textbox', { name: '创意描述' }), event);
    await waitFor(() => expect(mocks.start).toHaveBeenCalledTimes(1));
    expect(screen.getAllByText('reference.png')).toHaveLength(1);
  });
  it('falls back to clipboard image items when files are unavailable', async () => {
    render(<ReferenceFiles kind="image" />);
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: {
        files: [],
        items: [{ type: 'image/png', getAsFile: () => file }],
      },
    });
    fireEvent(screen.getByLabelText('选择参考图片'), event);
    await waitFor(() => expect(mocks.start).toHaveBeenCalledOnce());
  });
  it('lets users retry project loading and cancel without adding a reference', async () => {
    mocks.projects
      .mockRejectedValueOnce(new Error('项目读取失败'))
      .mockResolvedValueOnce({ success: true, data: { data: [], total: 0 } });
    const change = vi.fn();
    render(<ReferenceFiles kind="image" onChange={change} />);
    fireEvent.click(screen.getByRole('button', { name: '项目资产' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('项目读取失败');
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(await screen.findByText('暂无可访问的项目')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(change).not.toHaveBeenCalled();
  });

  it('adds project images as references without uploading them again and avoids duplicates', async () => {
    mocks.projects.mockResolvedValue({
      success: true,
      data: { data: [{ id: 3, name: '示例项目' }], total: 1 },
    });
    mocks.assets.mockResolvedValue({
      success: true,
      data: {
        items: {
          data: [
            {
              id: 9,
              resourceType: 'AI_IMAGE_RESULT',
              name: '主角形象',
              thumbnailUrl: '/thumb',
              previewUrl: '/display',
            },
          ],
          total: 1,
        },
      },
    });
    const change = vi.fn();
    render(
      <ReferenceFiles
        kind="image"
        onChange={change}
        constraints={{ maxCount: 1 }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '项目资产' }));
    fireEvent.click(await screen.findByRole('button', { name: '示例项目' }));
    fireEvent.click(await screen.findByRole('checkbox', { name: '主角形象' }));
    fireEvent.click(screen.getByRole('button', { name: /引用选中图片/ }));
    await waitFor(() =>
      expect(change.mock.lastCall?.[0]).toEqual([
        expect.objectContaining({
          projectImage: { projectId: 3, resultId: 9 },
          status: 'complete',
          name: '主角形象',
        }),
      ]),
    );
    expect(mocks.start).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '项目资产' }));
    fireEvent.click(await screen.findByRole('button', { name: '示例项目' }));
    expect(
      await screen.findByRole('checkbox', { name: '主角形象' }),
    ).toBeDisabled();
  });

  it('accepts pasted images through the same verified upload lifecycle', async () => {
    render(<ReferenceFiles kind="image" />);
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: { files: [file], items: [] },
    });
    fireEvent(screen.getByLabelText('选择参考图片'), event);
    await waitFor(() =>
      expect(mocks.start).toHaveBeenCalledWith(file, expect.anything()),
    );
    expect(event.defaultPrevented).toBe(true);
    expect(screen.getByText('reference.png')).toBeInTheDocument();
  });
  it('keeps ordinary text paste unchanged and rejects images over the model count', async () => {
    render(<ReferenceFiles kind="image" constraints={{ maxCount: 1 }} />);
    const input = screen.getByLabelText('选择参考图片');
    const text = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(text, 'clipboardData', {
      value: { files: [], items: [] },
    });
    fireEvent(input, text);
    expect(text.defaultPrevented).toBe(false);
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: { files: [file, file], items: [] },
    });
    fireEvent(input, event);
    expect(
      await screen.findByText('当前模型最多允许 1 个参考图片'),
    ).toBeInTheDocument();
    expect(mocks.start).not.toHaveBeenCalled();
  });
  it('does not upload pasted images while disabled', () => {
    render(<ReferenceFiles kind="image" disabled />);
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', {
      value: { files: [file], items: [] },
    });
    fireEvent(screen.getByLabelText('选择参考图片'), event);
    expect(mocks.start).not.toHaveBeenCalled();
  });

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
