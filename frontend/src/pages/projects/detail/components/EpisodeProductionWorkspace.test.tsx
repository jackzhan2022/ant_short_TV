import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import EpisodeProductionWorkspace from './EpisodeProductionWorkspace';

const mocks = vi.hoisted(() => ({
  createEpisodeComposeTask: vi.fn(),
  deleteEpisodeComposeTask: vi.fn(),
  deleteEpisodeVideoVersion: vi.fn(),
  downloadEpisodeVideoVersion: vi.fn(),
  queryEpisodeComposeTasks: vi.fn(),
  queryEpisodeExportRecords: vi.fn(),
  queryEpisodeVideoVersions: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  regenerateEpisodeComposeTask: vi.fn(),
  saveEpisodeVideoMaterial: vi.fn(),
  setCurrentEpisodeVideoVersion: vi.fn(),
  useAccess: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  DownloadOutlined: () => <span data-testid="download-icon" />,
  PlayCircleOutlined: () => <span data-testid="play-icon" />,
  PlusOutlined: () => <span data-testid="plus-icon" />,
  ReloadOutlined: () => <span data-testid="reload-icon" />,
  SaveOutlined: () => <span data-testid="save-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: vi.fn(), warning: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, type }: any) => (
    <button type="button" data-button-type={type} onClick={onClick}>
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
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Popconfirm: ({ children }: any) => <>{children}</>,
  Select: ({ options = [], value, onChange, ...props }: any) => (
    <select
      aria-label={props['aria-label']}
      value={value}
      onChange={(event) => onChange?.(Number(event.target.value))}
    >
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tabs: ({ items = [] }: any) => (
    <div>
      {items.map((item: any) => (
        <section key={item.key}>
          <h3>{item.label}</h3>
          {item.children}
        </section>
      ))}
    </div>
  ),
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
            episodeNo: 1,
            taskName: '第1集成片合成',
            versionName: '第1集 成片 v1',
            outputFormat: 'mp4',
            quality: 'STANDARD',
            generateCover: true,
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
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormSwitch: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ columns = [], request, toolBarRender }: any) => {
    request?.({});
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
  createEpisodeComposeTask: mocks.createEpisodeComposeTask,
  deleteEpisodeComposeTask: mocks.deleteEpisodeComposeTask,
  deleteEpisodeVideoVersion: mocks.deleteEpisodeVideoVersion,
  downloadEpisodeVideoVersion: mocks.downloadEpisodeVideoVersion,
  queryEpisodeComposeTasks: mocks.queryEpisodeComposeTasks,
  queryEpisodeExportRecords: mocks.queryEpisodeExportRecords,
  queryEpisodeVideoVersions: mocks.queryEpisodeVideoVersions,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  regenerateEpisodeComposeTask: mocks.regenerateEpisodeComposeTask,
  saveEpisodeVideoMaterial: mocks.saveEpisodeVideoMaterial,
  setCurrentEpisodeVideoVersion: mocks.setCurrentEpisodeVideoVersion,
}));

vi.mock('@umijs/max', () => ({
  useAccess: mocks.useAccess,
}));

describe('EpisodeProductionWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({
      success: true,
      data: {
        storyboards: [
          {
            id: 7,
            episodeNo: 1,
            shotNo: 1,
            durationSeconds: 5,
            currentShotVideoUrl: '/materials/shot-1.mp4',
            currentVideoUrl: '/materials/shot-1.mp4',
          },
          {
            id: 8,
            episodeNo: 2,
            shotNo: 1,
            durationSeconds: 6,
            currentShotVideoUrl: '/materials/shot-2.mp4',
            currentVideoUrl: '/materials/shot-2.mp4',
          },
        ],
      },
    });
    mocks.queryEpisodeComposeTasks.mockResolvedValue({ success: true, data: [] });
    mocks.queryEpisodeVideoVersions.mockResolvedValue({ success: true, data: [] });
    mocks.queryEpisodeExportRecords.mockResolvedValue({ success: true, data: [] });
    mocks.createEpisodeComposeTask.mockResolvedValue({
      success: true,
      data: { id: 1, status: 'SUCCEEDED' },
    });
    mocks.useAccess.mockReturnValue({
      canViewEpisodeComposeTasks: true,
      canViewEpisodeVersions: true,
      canCreateEpisodeComposeTasks: true,
      canCancelEpisodeComposeTasks: true,
      canDeleteEpisodeComposeTasks: true,
      canSetCurrentEpisodeVersion: true,
      canDownloadEpisodeVersions: true,
      canDeleteEpisodeVersions: true,
      canSaveEpisodeVersions: true,
    });
  });

  it('renders episode compose controls and loads episode data', async () => {
    render(<EpisodeProductionWorkspace projectId={1} />);

    expect(screen.getByText('单集合成与成片管理')).toBeInTheDocument();
    expect(screen.getByText('发起单集合成')).toBeInTheDocument();
    expect(screen.getByText('暂无可合成分镜，请先完成单镜头合成')).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryEpisodeComposeTasks).toHaveBeenCalledWith(1, {
        episodeNo: 1,
      });
      expect(mocks.queryEpisodeVideoVersions).toHaveBeenCalledWith(1, 1);
      expect(mocks.queryEpisodeExportRecords).toHaveBeenCalledWith(1, {
        episodeNo: 1,
      });
    });
  });

  it('submits an episode compose task', async () => {
    render(<EpisodeProductionWorkspace projectId={1} />);

    fireEvent.submit(screen.getByRole('form', { name: '发起单集合成' }));

    await waitFor(() => {
      expect(mocks.createEpisodeComposeTask).toHaveBeenCalledWith(1, {
        episodeNo: 1,
        generateCover: true,
        outputFormat: 'mp4',
        quality: 'STANDARD',
        taskName: '第1集成片合成',
        versionName: '第1集 成片 v1',
      });
    });
  });

  it('reloads episode scoped tables after switching episode', async () => {
    render(<EpisodeProductionWorkspace projectId={1} />);

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });

    fireEvent.change(screen.getByLabelText('选择单集'), {
      target: { value: '2' },
    });

    await waitFor(() => {
      expect(mocks.queryEpisodeComposeTasks).toHaveBeenCalledWith(1, {
        episodeNo: 2,
      });
      expect(mocks.queryEpisodeVideoVersions).toHaveBeenCalledWith(1, 2);
      expect(mocks.queryEpisodeExportRecords).toHaveBeenCalledWith(1, {
        episodeNo: 2,
      });
    });
  });

  it('hides restricted actions without episode permissions', async () => {
    mocks.useAccess.mockReturnValue({
      canViewEpisodeComposeTasks: false,
      canViewEpisodeVersions: false,
      canCreateEpisodeComposeTasks: false,
      canCancelEpisodeComposeTasks: false,
      canDeleteEpisodeComposeTasks: false,
      canSetCurrentEpisodeVersion: false,
      canDownloadEpisodeVersions: false,
      canDeleteEpisodeVersions: false,
      canSaveEpisodeVersions: false,
    });

    render(<EpisodeProductionWorkspace projectId={1} />);

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
    expect(screen.queryByRole('button', { name: '发起单集合成' })).not.toBeInTheDocument();
    expect(mocks.queryEpisodeComposeTasks).not.toHaveBeenCalled();
    expect(mocks.queryEpisodeVideoVersions).not.toHaveBeenCalled();
    expect(mocks.queryEpisodeExportRecords).not.toHaveBeenCalled();
  });
});
