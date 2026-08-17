import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiImageProductionWorkspace from './AiImageProductionWorkspace';

const mocks = vi.hoisted(() => ({
  createAiImageTask: vi.fn(),
  queryAiImageTasks: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  DeleteOutlined: () => <span data-testid="delete-icon" />,
  EyeOutlined: () => <span data-testid="eye-icon" />,
  PictureOutlined: () => <span data-testid="picture-icon" />,
  ReloadOutlined: () => <span data-testid="reload-icon" />,
  SaveOutlined: () => <span data-testid="save-icon" />,
  StarOutlined: () => <span data-testid="star-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, disabled }: any) => (
    <button type="button" disabled={disabled} onClick={onClick}>
      {icon}
      {children}
    </button>
  ),
  Descriptions: ({ items = [] }: any) => (
    <dl>
      {items.map((item: any) => (
        <div key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.children}</dd>
        </div>
      ))}
    </dl>
  ),
  Image: Object.assign(
    ({ alt, src }: any) => <img alt={alt} src={src} />,
    { PreviewGroup: ({ children }: any) => <div>{children}</div> },
  ),
  Popconfirm: ({ children }: any) => <>{children}</>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, onFinish, title, trigger }: any) => (
    <div>
      {trigger}
      <form
        aria-label={title}
        onSubmit={(event) => {
          event.preventDefault();
          onFinish?.({
            taskType: 'STORYBOARD_FIRST_FRAME',
            targetType: 'STORYBOARD',
            targetId: 9,
            prompt: '雨夜豪门门口首帧',
            aspectRatio: '9:16',
            imageCount: 1,
            quality: 'STANDARD',
          });
        }}
      >
        {children}
        <button type="submit">提交{title}</button>
      </form>
    </div>
  ),
  ProCard: ({ children, title, extra }: any) => (
    <section>
      {title && <h2>{title}</h2>}
      {extra}
      {children}
    </section>
  ),
  ProFormDigit: ({ label }: any) => <span>{label}</span>,
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ actionRef, columns = [], request, toolBarRender }: any) => {
    const reload = () => request?.({}).then(() => undefined);
    if (actionRef && !actionRef.current) {
      actionRef.current = { reload };
      reload();
    }
    return (
      <section>
        <div>{toolBarRender?.()}</div>
        <table>
          <thead>
            <tr>
              {columns.map((column: any) => (
                <th key={column.dataIndex || column.key || column.title}>
                  {column.title}
                </th>
              ))}
            </tr>
          </thead>
        </table>
      </section>
    );
  },
}));

vi.mock('./service', () => ({
  cancelAiImageTask: vi.fn(),
  createAiImageTask: mocks.createAiImageTask,
  deleteAiImageTask: vi.fn(),
  queryAiImageTask: vi.fn(),
  queryAiImageTasks: mocks.queryAiImageTasks,
  regenerateAiImageTask: vi.fn(),
  saveAiImageResultAsMaterial: vi.fn(),
  selectAiImageResult: vi.fn(),
}));

describe('AiImageProductionWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
    mocks.queryAiImageTasks.mockResolvedValue({
      success: true,
      data: [],
    });
    mocks.createAiImageTask.mockResolvedValue({
      success: true,
      data: { id: 1, results: [] },
    });
  });

  it('renders Ant Pro image task controls and submits a storyboard task', async () => {
    render(<AiImageProductionWorkspace projectId={1} />);

    expect(screen.getByText('图片生产')).toBeInTheDocument();
    expect(screen.getByText('新建图片任务')).toBeInTheDocument();
    expect(screen.getAllByText('任务类型').length).toBeGreaterThan(0);
    expect(screen.getByText('正向提示词')).toBeInTheDocument();

    fireEvent.submit(screen.getByRole('form', { name: '新建图片生成任务' }));

    await waitFor(() => {
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(1, {
        aspectRatio: '9:16',
        imageCount: 1,
        prompt: '雨夜豪门门口首帧',
        quality: 'STANDARD',
        targetId: 9,
        targetType: 'STORYBOARD',
        taskType: 'STORYBOARD_FIRST_FRAME',
      });
    });
  });

  it('polls while image tasks are pending', async () => {
    vi.useFakeTimers();
    mocks.queryAiImageTasks.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          projectId: 1,
          taskType: 'STORYBOARD_FIRST_FRAME',
          targetType: 'STORYBOARD',
          targetId: 9,
          serviceConfigId: 1,
          providerCode: 'OpenAI',
          model: 'local-image-model',
          prompt: '雨夜豪门门口首帧',
          referenceImages: [],
          aspectRatio: '9:16',
          imageCount: 1,
          status: 'PENDING',
          createdBy: 1,
          results: [],
        },
      ],
    });

    render(<AiImageProductionWorkspace projectId={1} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(mocks.queryAiImageTasks).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(2500);
      await Promise.resolve();
    });

    expect(mocks.queryAiImageTasks).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });
});
