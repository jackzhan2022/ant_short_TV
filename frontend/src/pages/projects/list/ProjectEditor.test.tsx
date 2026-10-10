import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Project } from '@/services/account-team/types';
import ProjectEditor from './ProjectEditor';

const mocks = vi.hoisted(() => ({
  update: vi.fn(),
  start: vi.fn(),
  bind: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}));
vi.mock('./service', () => ({
  updateProject: mocks.update,
  startProjectCoverUpload: mocks.start,
  bindProjectCoverUpload: mocks.bind,
}));
vi.mock('@/components/ProjectCoverImage', () => ({
  default: ({ src, alt }: any) => <img src={src} alt={alt} />,
}));
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    App: {
      useApp: () => ({
        message: {
          success: mocks.success,
          error: mocks.error,
          warning: mocks.warning,
        },
      }),
    },
  };
});
vi.mock('@ant-design/pro-components', async () => {
  const React = await import('react');
  return {
    ModalForm: ({
      children,
      onFinish,
      onOpenChange,
      submitter,
      title,
      trigger,
    }: any) => {
      const [open, setOpen] = React.useState(false);
      const toggle = (value: boolean) => {
        setOpen(value);
        onOpenChange?.(value);
      };
      return (
        <div>
          {React.cloneElement(trigger, { onClick: () => toggle(true) })}
          {open && (
            <form
              aria-label={title}
              onSubmit={async (event) => {
                event.preventDefault();
                const result = await onFinish({
                  name: '项目45',
                  ownerId: 8,
                  description: '说明',
                  coverUrl: '/api/projects/45/cover',
                });
                if (result) toggle(false);
              }}
            >
              {children}
              <button
                type="submit"
                disabled={submitter?.submitButtonProps?.disabled}
              >
                确定
              </button>
              <button
                type="button"
                disabled={submitter?.resetButtonProps?.disabled}
                onClick={() => toggle(false)}
              >
                取消
              </button>
            </form>
          )}
        </div>
      );
    },
    ProFormText: ({ label }: any) => (
      <label>
        {label}
        <input />
      </label>
    ),
    ProFormTextArea: ({ label }: any) => (
      <label>
        {label}
        <textarea />
      </label>
    ),
    ProFormSelect: ({ label }: any) => (
      <label>
        {label}
        <select />
      </label>
    ),
    ProFormDatePicker: ({ label }: any) => <span>{label}</span>,
  };
});
const project = {
  id: 45,
  name: '项目45',
  ownerId: 8,
  coverUrl: '/api/projects/45/cover',
  coverStatus: 'READY',
} as Project;
const done = vi.fn();
const handle = {
  attempt: Promise.resolve({ sessionToken: 'cover-session' }),
  cancel: vi.fn(),
};
const view = () =>
  render(
    <ProjectEditor
      project={project}
      members={[]}
      onDone={done}
      onOpen={vi.fn()}
    />,
  );
const select = (file: File) =>
  fireEvent.change(screen.getByLabelText('选择封面图片'), {
    target: { files: [file] },
  });
const file = (name = 'cover.png', type = 'image/png') =>
  new File(['image'], name, { type });
beforeEach(() => {
  vi.clearAllMocks();
  mocks.update.mockResolvedValue({ success: true });
  mocks.start.mockResolvedValue(handle);
  mocks.bind.mockResolvedValue({ success: true, data: { status: 'PENDING' } });
  vi.mocked(URL.createObjectURL).mockReturnValue('blob:cover-preview');
});

describe('project cover editor', () => {
  it('shows the existing image, not a cover URL text field', () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    expect(screen.getByAltText('当前封面')).toHaveAttribute(
      'src',
      project.coverUrl,
    );
    expect(screen.queryByText('封面地址')).not.toBeInTheDocument();
    expect(
      screen.queryByDisplayValue(project.coverUrl || ''),
    ).not.toBeInTheDocument();
  });
  it('previews locally, defers upload until confirm, and cancel keeps the original cover', async () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    expect(await screen.findByAltText('新封面预览')).toHaveAttribute(
      'src',
      'blob:cover-preview',
    );
    expect(mocks.start).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(mocks.update).not.toHaveBeenCalled();
    expect(mocks.bind).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    expect(screen.getByAltText('当前封面')).toHaveAttribute(
      'src',
      project.coverUrl,
    );
  });
  it('uploads and binds only after valid preview and confirmation', async () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    const picked = file();
    select(picked);
    const preview = await screen.findByAltText('新封面预览');
    expect(screen.getByRole('button', { name: '确定' })).toBeDisabled();
    fireEvent.load(preview);
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() =>
      expect(mocks.bind).toHaveBeenCalledWith(45, 'cover-session'),
    );
    expect(mocks.start).toHaveBeenCalledWith(45, picked);
    expect(mocks.update).toHaveBeenCalledWith(
      45,
      expect.not.objectContaining({ coverUrl: expect.anything() }),
    );
    expect(done).toHaveBeenCalledTimes(1);
  });
  it('clears the cover only on confirmation', async () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getByRole('button', { name: '移除封面' }));
    expect(mocks.update).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() =>
      expect(mocks.update).toHaveBeenCalledWith(
        45,
        expect.objectContaining({ clearCover: true }),
      ),
    );
    expect(mocks.start).not.toHaveBeenCalled();
  });
  it('preserves cover fields when only other project information changes', async () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(done).toHaveBeenCalled());
    const payload = mocks.update.mock.calls[0][1];
    expect(payload).not.toHaveProperty('coverUrl');
    expect(payload).not.toHaveProperty('clearCover');
  });
  it('rejects unsupported files and oversized images without replacing the preview', () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file('notes.txt', 'text/plain'));
    const huge = file();
    Object.defineProperty(huge, 'size', { value: 20 * 1024 * 1024 + 1 });
    select(huge);
    expect(mocks.error).toHaveBeenCalledTimes(2);
    expect(screen.getByAltText('当前封面')).toBeInTheDocument();
    expect(mocks.start).not.toHaveBeenCalled();
  });
  it('does not save project fields when the upload fails', async () => {
    mocks.start.mockRejectedValue(new Error('offline'));
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    fireEvent.load(await screen.findByAltText('新封面预览'));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(mocks.error).toHaveBeenCalled());
    expect(mocks.update).not.toHaveBeenCalled();
    expect(done).not.toHaveBeenCalled();
  });
  it('retains the completed upload for retry if binding fails', async () => {
    mocks.bind
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValue({ success: true, data: { status: 'PENDING' } });
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    fireEvent.load(await screen.findByAltText('新封面预览'));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(mocks.error).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(done).toHaveBeenCalledTimes(1));
    expect(mocks.start).toHaveBeenCalledTimes(1);
    expect(mocks.bind).toHaveBeenCalledTimes(2);
  });
  it('disables changing or canceling while saving', async () => {
    let finish: (value: any) => void = () => {};
    mocks.start.mockResolvedValue({
      attempt: new Promise((resolve) => {
        finish = resolve;
      }),
      cancel: vi.fn(),
    });
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    fireEvent.load(await screen.findByAltText('新封面预览'));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(mocks.start).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: '取消' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '更换图片' })).toBeDisabled();
    await act(async () => finish({ sessionToken: 'cover-session' }));
  });
  it('rejects corrupt previews without uploading or changing the stored cover', async () => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    fireEvent.error(await screen.findByAltText('新封面预览'));
    expect(screen.getByAltText('当前封面')).toBeInTheDocument();
    expect(mocks.error).toHaveBeenCalled();
    expect(mocks.start).not.toHaveBeenCalled();
    expect(mocks.update).not.toHaveBeenCalled();
  });
  it.each([
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
  ])('accepts %s for local preview', async (type) => {
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file('cover', type));
    expect(await screen.findByAltText('新封面预览')).toBeInTheDocument();
    expect(mocks.error).not.toHaveBeenCalled();
  });
  it('preserves existing video settings when saving the project', async () => {
    render(
      <ProjectEditor
        project={{
          ...project,
          aspectRatio: '16:9',
          visualStyle: '3D风格',
          videoGenerateAudio: false,
          videoWatermark: true,
        }}
        members={[]}
        onDone={done}
        onOpen={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() =>
      expect(mocks.update).toHaveBeenCalledWith(
        45,
        expect.objectContaining({
          aspectRatio: '16:9',
          visualStyle: '3D风格',
          videoGenerateAudio: false,
          videoWatermark: true,
        }),
      ),
    );
  });
  it('reuses the upload handle when completion verification fails after transfer', async () => {
    const failed = Promise.reject(new Error('verification request lost'));
    void failed.catch(() => undefined);
    const retry = vi.fn().mockResolvedValue({ sessionToken: 'cover-session' });
    const cancel = vi.fn();
    mocks.start.mockResolvedValue({ attempt: failed, retry, cancel });
    view();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    select(file());
    fireEvent.load(await screen.findByAltText('新封面预览'));
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(mocks.error).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    await waitFor(() => expect(done).toHaveBeenCalledTimes(1));
    expect(mocks.start).toHaveBeenCalledTimes(1);
    expect(retry).toHaveBeenCalledTimes(1);
    expect(cancel).not.toHaveBeenCalled();
  });
});
