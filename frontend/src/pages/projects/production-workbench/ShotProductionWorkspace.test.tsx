import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ShotProductionWorkspace from './ShotProductionWorkspace';

const mocks = vi.hoisted(() => ({
  cancelAiVoiceTask: vi.fn(),
  cancelShotComposeTask: vi.fn(),
  deleteAiVoiceResult: vi.fn(),
  deleteAiVoiceTask: vi.fn(),
  deleteShotComposeResult: vi.fn(),
  deleteShotComposeTask: vi.fn(),
  deleteStoryboardSubtitle: vi.fn(),
  createAiVoiceTask: vi.fn(),
  createShotComposeTask: vi.fn(),
  createStoryboardSubtitle: vi.fn(),
  queryAiVoiceTasks: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryStoryboardWorkspace: vi.fn(),
  queryShotComposeTasks: vi.fn(),
  queryStoryboardSubtitles: vi.fn(),
  queryStoryboardSubtitle: vi.fn(),
  regenerateAiVoiceTask: vi.fn(),
  regenerateShotComposeTask: vi.fn(),
  saveAiVoiceResultAsMaterial: vi.fn(),
  selectStoryboardSubtitle: vi.fn(),
  updateStoryboardSubtitle: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  AudioOutlined: () => <span data-testid="audio-icon" />,
  EditOutlined: () => <span data-testid="edit-icon" />,
  VideoCameraOutlined: () => <span data-testid="shot-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: vi.fn(), warning: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, type, loading }: any) => (
    <button
      type="button"
      data-button-type={type}
      disabled={loading}
      onClick={onClick}
    >
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Popconfirm: ({ children }: any) => <div>{children}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
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
}));

vi.mock('@ant-design/pro-components', async () => {
  const React = await import('react');
  return {
    ModalForm: ({
      children,
      onFinish,
      title,
      trigger,
      open,
      initialValues,
    }: any) => {
      const [triggered, setTriggered] = React.useState(false);
      const visible = title === '编辑字幕' ? (open ?? triggered) : true;
      return (
        <div>
          {trigger
            ? React.cloneElement(trigger, { onClick: () => setTriggered(true) })
            : null}
          {visible && (
            <form
              aria-label={title}
              data-initial-values={JSON.stringify(initialValues)}
              onSubmit={(event) => {
                event.preventDefault();
                if (title === '编辑字幕') {
                  onFinish?.(initialValues);
                  return;
                }
                if (title.includes('语音')) {
                  onFinish?.({
                    storyboardId: 7,
                    voiceType: 'DIALOGUE',
                    speakerName: '女主',
                    voiceId: 'female-cn-01',
                    textContent: '你终于来了。',
                    speed: 1,
                    pitch: 1,
                    volume: 1,
                  });
                  return;
                }
                if (title.includes('字幕')) {
                  onFinish?.({
                    storyboardId: 7,
                    voiceResultId: 11,
                    subtitleType: 'DIALOGUE',
                    textContent: '你终于来了。',
                  });
                  return;
                }
                onFinish?.({
                  storyboardId: 7,
                  voiceResultId: 11,
                  subtitleId: 21,
                  includeSubtitle: true,
                  audioVolume: 1,
                  outputFormat: 'mp4',
                });
              }}
            >
              {children}
              <button type="submit">提交{title}</button>
            </form>
          )}
        </div>
      );
    },
    ProCard: ({ children, title, extra }: any) => (
      <section>
        {title && <h2>{title}</h2>}
        {extra}
        {children}
      </section>
    ),
    ProFormDigit: ({ label }: any) => <span>{label}</span>,
    ProFormSelect: ({ label }: any) => <span>{label}</span>,
    ProFormSwitch: ({ label }: any) => <span>{label}</span>,
    ProFormText: ({ label }: any) => <span>{label}</span>,
    ProFormTextArea: ({ label }: any) => <span>{label}</span>,
    ProTable: ({ columns = [], request, toolBarRender }: any) => {
      const [rows, setRows] = React.useState<any[]>([]);
      React.useEffect(() => {
        let active = true;
        request?.({ current: 2, pageSize: 5 }).then((response: any) => {
          if (active) setRows(response.data);
        });
        return () => {
          active = false;
        };
      }, [request]);
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
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  {columns.map((column: any) => (
                    <td key={column.dataIndex || column.key || column.title}>
                      {column.render?.(row[column.dataIndex], row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      );
    },
  };
});

vi.mock('./service', () => ({
  cancelAiVoiceTask: mocks.cancelAiVoiceTask,
  cancelShotComposeTask: mocks.cancelShotComposeTask,
  deleteAiVoiceResult: mocks.deleteAiVoiceResult,
  deleteAiVoiceTask: mocks.deleteAiVoiceTask,
  deleteShotComposeResult: mocks.deleteShotComposeResult,
  deleteShotComposeTask: mocks.deleteShotComposeTask,
  deleteStoryboardSubtitle: mocks.deleteStoryboardSubtitle,
  createAiVoiceTask: mocks.createAiVoiceTask,
  createShotComposeTask: mocks.createShotComposeTask,
  createStoryboardSubtitle: mocks.createStoryboardSubtitle,
  queryAiVoiceTasks: mocks.queryAiVoiceTasks,
  queryStoryboardWorkspace: mocks.queryStoryboardWorkspace,
  queryShotComposeTasks: mocks.queryShotComposeTasks,
  queryStoryboardSubtitles: mocks.queryStoryboardSubtitles,
  queryStoryboardSubtitle: mocks.queryStoryboardSubtitle,
  regenerateAiVoiceTask: mocks.regenerateAiVoiceTask,
  regenerateShotComposeTask: mocks.regenerateShotComposeTask,
  saveAiVoiceResultAsMaterial: mocks.saveAiVoiceResultAsMaterial,
  selectStoryboardSubtitle: mocks.selectStoryboardSubtitle,
  updateStoryboardSubtitle: mocks.updateStoryboardSubtitle,
}));

describe('ShotProductionWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryStoryboardWorkspace.mockResolvedValue({
      success: true,
      data: {
        storyboards: [
          {
            id: 7,
            episodeNo: 1,
            shotNo: 3,
            dialogue: '你终于来了。',
            visualDescription: '雨夜门口',
            currentVideoUrl: '/materials/video.mp4',
          },
        ],
      },
    });
    mocks.queryAiVoiceTasks.mockResolvedValue({
      success: true,
      data: { data: [], current: 2, pageSize: 5, total: 15 },
    });
    mocks.queryShotComposeTasks.mockResolvedValue({
      success: true,
      data: { data: [], current: 2, pageSize: 5, total: 15 },
    });
    mocks.queryStoryboardSubtitles.mockResolvedValue({
      success: true,
      data: { data: [], current: 2, pageSize: 5, total: 15 },
    });
    mocks.createAiVoiceTask.mockResolvedValue({
      success: true,
      data: { id: 1 },
    });
    mocks.createStoryboardSubtitle.mockResolvedValue({
      success: true,
      data: { id: 2 },
    });
    mocks.createShotComposeTask.mockResolvedValue({
      success: true,
      data: { id: 3 },
    });
  });

  it('renders Ant Pro shot production controls', async () => {
    render(<ShotProductionWorkspace projectId={1} />);

    expect(screen.getByText('语音字幕与单镜头')).toBeInTheDocument();
    expect(screen.getByText('新建语音任务')).toBeInTheDocument();
    expect(screen.getByText('生成字幕')).toBeInTheDocument();
    expect(screen.getByText('开始单镜头合成')).toBeInTheDocument();
    expect(screen.getByText('合成文本')).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryScriptWorkspace).not.toHaveBeenCalled();
      expect(mocks.queryAiVoiceTasks).toHaveBeenCalledWith(1, {
        current: 2,
        pageSize: 5,
      });
      expect(mocks.queryStoryboardSubtitles).toHaveBeenCalledWith(1, {
        current: 2,
        pageSize: 5,
      });
      expect(mocks.queryShotComposeTasks).toHaveBeenCalledWith(1, {
        current: 2,
        pageSize: 5,
      });
    });
  });

  it('submits voice subtitle and shot compose requests', async () => {
    render(<ShotProductionWorkspace projectId={1} />);

    fireEvent.submit(screen.getByRole('form', { name: '新建语音合成任务' }));
    fireEvent.submit(screen.getByRole('form', { name: '生成字幕' }));
    fireEvent.submit(screen.getByRole('form', { name: '新建单镜头合成任务' }));

    await waitFor(() => {
      expect(mocks.createAiVoiceTask).toHaveBeenCalledWith(1, {
        pitch: 1,
        speakerName: '女主',
        speed: 1,
        storyboardId: 7,
        textContent: '你终于来了。',
        voiceId: 'female-cn-01',
        voiceType: 'DIALOGUE',
        volume: 1,
      });
      expect(mocks.createStoryboardSubtitle).toHaveBeenCalledWith(1, {
        storyboardId: 7,
        subtitleType: 'DIALOGUE',
        textContent: '你终于来了。',
        voiceResultId: 11,
      });
      expect(mocks.createShotComposeTask).toHaveBeenCalledWith(1, {
        audioVolume: 1,
        includeSubtitle: true,
        outputFormat: 'mp4',
        storyboardId: 7,
        subtitleId: 21,
        voiceResultId: 11,
      });
    });
  });

  it('fetches full subtitle editing values on demand before opening the editor', async () => {
    const summary = {
      id: 21,
      storyboardId: 7,
      subtitleType: 'DIALOGUE',
      textContent: null,
      segments: [],
      styleConfig: null,
      selected: false,
      status: 'ACTIVE',
    };
    mocks.queryStoryboardSubtitles.mockResolvedValue({
      success: true,
      data: { data: [summary], current: 2, pageSize: 5, total: 15 },
    });
    mocks.queryStoryboardSubtitle.mockResolvedValue({
      success: true,
      data: {
        ...summary,
        textContent: '完整保存的对白',
        segments: [{ text: '完整保存的对白', startTime: 12, endTime: 18 }],
        styleConfig: '{"fontSize":"LARGE","position":"TOP"}',
      },
    });
    render(<ShotProductionWorkspace projectId={1} />);
    expect(mocks.queryStoryboardSubtitle).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
    const editor = await screen.findByRole('form', { name: '编辑字幕' });
    expect(mocks.queryStoryboardSubtitle).toHaveBeenCalledWith(
      1,
      21,
      expect.any(AbortSignal),
    );
    expect(
      JSON.parse(editor.getAttribute('data-initial-values') || '{}'),
    ).toEqual({
      textContent: '完整保存的对白',
      startTime: 12,
      endTime: 18,
      styleConfig: { fontSize: 'LARGE', position: 'TOP' },
    });
    fireEvent.submit(editor);
    await waitFor(() =>
      expect(mocks.updateStoryboardSubtitle).toHaveBeenCalledWith(
        1,
        21,
        expect.objectContaining({
          textContent: '完整保存的对白',
          startTime: 12,
          endTime: 18,
        }),
      ),
    );
  });
});
