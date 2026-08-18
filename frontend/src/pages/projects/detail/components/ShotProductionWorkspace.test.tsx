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
  queryShotComposeTasks: vi.fn(),
  queryStoryboardSubtitles: vi.fn(),
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
  Button: ({ children, icon, onClick, type }: any) => (
    <button type="button" data-button-type={type} onClick={onClick}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
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

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, onFinish, title, trigger }: any) => (
    <div>
      {trigger}
      <form
        aria-label={title}
        onSubmit={(event) => {
          event.preventDefault();
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
  ProFormSwitch: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
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
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  queryShotComposeTasks: mocks.queryShotComposeTasks,
  queryStoryboardSubtitles: mocks.queryStoryboardSubtitles,
  regenerateAiVoiceTask: mocks.regenerateAiVoiceTask,
  regenerateShotComposeTask: mocks.regenerateShotComposeTask,
  saveAiVoiceResultAsMaterial: mocks.saveAiVoiceResultAsMaterial,
  selectStoryboardSubtitle: mocks.selectStoryboardSubtitle,
  updateStoryboardSubtitle: mocks.updateStoryboardSubtitle,
}));

describe('ShotProductionWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({
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
    mocks.queryAiVoiceTasks.mockResolvedValue({ success: true, data: [] });
    mocks.queryShotComposeTasks.mockResolvedValue({ success: true, data: [] });
    mocks.queryStoryboardSubtitles.mockResolvedValue({ success: true, data: [] });
    mocks.createAiVoiceTask.mockResolvedValue({ success: true, data: { id: 1 } });
    mocks.createStoryboardSubtitle.mockResolvedValue({ success: true, data: { id: 2 } });
    mocks.createShotComposeTask.mockResolvedValue({ success: true, data: { id: 3 } });
  });

  it('renders Ant Pro shot production controls', async () => {
    render(<ShotProductionWorkspace projectId={1} />);

    expect(screen.getByText('语音字幕与单镜头')).toBeInTheDocument();
    expect(screen.getByText('新建语音任务')).toBeInTheDocument();
    expect(screen.getByText('生成字幕')).toBeInTheDocument();
    expect(screen.getByText('开始单镜头合成')).toBeInTheDocument();
    expect(screen.getByText('合成文本')).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryAiVoiceTasks).toHaveBeenCalledWith(1, {});
      expect(mocks.queryStoryboardSubtitles).toHaveBeenCalledWith(1, {});
      expect(mocks.queryShotComposeTasks).toHaveBeenCalledWith(1, {});
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
});
