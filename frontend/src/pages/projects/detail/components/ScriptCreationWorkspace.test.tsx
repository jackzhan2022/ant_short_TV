import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ScriptCreationWorkspace from './ScriptCreationWorkspace';

const mocks = vi.hoisted(() => ({
  breakdownStoryboards: vi.fn(),
  bindAiVideoResultToStoryboard: vi.fn(),
  cancelAiVideoTask: vi.fn(),
  createAiVideoTask: vi.fn(),
  deleteAiVideoResult: vi.fn(),
  deleteAiVideoTask: vi.fn(),
  extractScriptElements: vi.fn(),
  downloadAiVideoResult: vi.fn(),
  generateScript: vi.fn(),
  generateWorkflowPrompts: vi.fn(),
  getCurrentTenantId: vi.fn(),
  pollAiVideoTask: vi.fn(),
  queryAiServiceConfigs: vi.fn(),
  queryAiVideoTasks: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  rewriteScript: vi.fn(),
  saveCurrentScript: vi.fn(),
  regenerateAiVideoTask: vi.fn(),
  saveAiVideoResultAsMaterial: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  BulbOutlined: () => <span data-testid="bulb-icon" />,
  DeleteOutlined: () => <span data-testid="delete-icon" />,
  EditOutlined: () => <span data-testid="edit-icon" />,
  FileTextOutlined: () => <span data-testid="file-icon" />,
  LinkOutlined: () => <span data-testid="link-icon" />,
  PlusOutlined: () => <span data-testid="plus-icon" />,
  RobotOutlined: () => <span data-testid="robot-icon" />,
  SaveOutlined: () => <span data-testid="save-icon" />,
  SplitCellsOutlined: () => <span data-testid="split-icon" />,
  StopOutlined: () => <span data-testid="stop-icon" />,
  TagsOutlined: () => <span data-testid="tags-icon" />,
  VideoCameraOutlined: () => <span data-testid="video-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick }: any) => (
    <button type="button" onClick={onClick}>
      {icon}
      {children}
    </button>
  ),
  Drawer: ({ children, extra, open, title }: any) =>
    open ? (
      <section aria-label={title}>
        <h2>{title}</h2>
        {extra}
        {children}
      </section>
    ) : null,
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Form: {
    useForm: () => [
      {
        resetFields: vi.fn(),
        submit: vi.fn(),
        setFieldsValue: vi.fn(),
      },
    ],
  },
  Input: Object.assign(
    ({ value, onChange, ...props }: any) => (
      <input
        value={value}
        onChange={(event) => onChange?.(event)}
        {...props}
      />
    ),
    {
      TextArea: ({ value, onChange, autoSize: _autoSize, ...props }: any) => (
        <textarea
          value={value}
          onChange={(event) => onChange?.(event)}
          {...props}
        />
      ),
    },
  ),
  Modal: {
    confirm: vi.fn(),
  },
  Popconfirm: ({ children }: any) => <>{children}</>,
  Select: ({ options = [], value, onChange }: any) => (
    <select value={value} onChange={(event) => onChange?.(event.target.value)}>
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tabs: ({ items }: any) => (
    <div>
      <nav>
        {items.map((item: any) => (
          <button key={item.key} type="button">
            {item.label}
          </button>
        ))}
      </nav>
      {items.map((item: any) => (
        <section key={item.key}>{item.children}</section>
      ))}
    </div>
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h3>{children}</h3>,
  },
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, onFinish, title, trigger }: any) => (
    <div>
      {trigger}
      <form
        aria-label={title}
        onSubmit={(event) => {
          event.preventDefault();
          if (title === 'AI改写剧本') {
            onFinish?.({
              rewriteType: '冲突增强',
              requirement: '强化前三秒钩子',
              outputLength: 'KEEP',
            });
            return;
          }
          onFinish?.({
            storyIdea: '落魄千金重回豪门',
            genre: '逆袭',
            episodeCount: 12,
            duration: 90,
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
  ProTable: ({ columns = [], dataSource = [], toolBarRender }: any) => (
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
          {dataSource.map((record: any) => (
            <tr key={record.id}>
              {columns.map((column: any) => (
                <td key={column.dataIndex || column.key || column.title}>
                  {column.render
                    ? column.render(record[column.dataIndex], record)
                    : record[column.dataIndex]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  ),
}));

vi.mock('@umijs/max', () => ({
  useAccess: () => ({
    canViewAiVideoTasks: true,
    canCreateAiVideoTasks: true,
    canCancelAiVideoTasks: true,
    canDeleteAiVideoTasks: true,
    canSaveAiVideoResults: true,
    canBindAiVideoResults: true,
    canDownloadAiVideoResults: true,
  }),
}));

vi.mock('./service', () => ({
  breakdownStoryboards: mocks.breakdownStoryboards,
  bindAiVideoResultToStoryboard: mocks.bindAiVideoResultToStoryboard,
  cancelAiVideoTask: mocks.cancelAiVideoTask,
  createAiVideoTask: mocks.createAiVideoTask,
  deleteAiVideoResult: mocks.deleteAiVideoResult,
  deleteAiVideoTask: mocks.deleteAiVideoTask,
  extractScriptElements: mocks.extractScriptElements,
  downloadAiVideoResult: mocks.downloadAiVideoResult,
  generateScript: mocks.generateScript,
  generateWorkflowPrompts: mocks.generateWorkflowPrompts,
  getCurrentTenantId: mocks.getCurrentTenantId,
  pollAiVideoTask: mocks.pollAiVideoTask,
  queryAiServiceConfigs: mocks.queryAiServiceConfigs,
  queryAiVideoTasks: mocks.queryAiVideoTasks,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  rewriteScript: mocks.rewriteScript,
  regenerateAiVideoTask: mocks.regenerateAiVideoTask,
  saveCurrentScript: mocks.saveCurrentScript,
  saveAiVideoResultAsMaterial: mocks.saveAiVideoResultAsMaterial,
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('@/pages/ai-service-management/services/service', () => ({
  queryAiServiceConfigs: mocks.queryAiServiceConfigs,
}));

describe('ScriptCreationWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.queryAiVideoTasks.mockResolvedValue({
      success: true,
      data: [],
    });
    mocks.queryAiServiceConfigs.mockResolvedValue({
      success: true,
      data: [],
    });
    mocks.getCurrentTenantId.mockReturnValue(1);
    mocks.generateScript.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: {
          id: 1,
          projectId: 1,
          title: '短剧项目',
          sourceType: 'AI_GENERATE',
          content: '落魄千金重回豪门后发现当年的陷害另有隐情',
          status: 'DRAFT',
          currentVersionId: 1,
        },
        versions: [{ id: 1, versionNo: 1, sourceType: 'AI_GENERATE' }],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.extractScriptElements.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters: [
          {
            id: 1,
            name: '主角',
            roleType: 'LEAD',
            gender: '女',
            ageRange: '25-30',
            identity: '落魄千金',
            personality: ['坚韧'],
            appearance: '雨夜拖着行李箱',
            prompt: '短剧女主角色图',
          },
        ],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.rewriteScript.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: {
          id: 1,
          projectId: 1,
          title: '短剧项目',
          sourceType: 'AI_REWRITE',
          content: '改写后剧本正文',
          status: 'DRAFT',
          currentVersionId: 2,
        },
        versions: [
          { id: 2, versionNo: 2, sourceType: 'AI_REWRITE' },
          { id: 1, versionNo: 1, sourceType: 'AI_GENERATE' },
        ],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.saveCurrentScript.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: {
          id: 1,
          projectId: 1,
          title: '短剧项目',
          sourceType: 'MANUAL_EDIT',
          content: '手工保存剧本',
          status: 'DRAFT',
          currentVersionId: 3,
        },
        versions: [{ id: 3, versionNo: 3, sourceType: 'MANUAL_EDIT' }],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.breakdownStoryboards.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [
          {
            id: 1,
            shotNo: 1,
            episodeNo: 1,
            shotType: '远景',
            visualDescription: '雨夜老宅门口',
            characters: '主角',
            scene: '林家老宅门口',
            dialogue: '我回来了。',
            durationSeconds: 5,
            imagePrompt: '首帧提示词',
            videoPrompt: '竖屏短剧提示词',
          },
        ],
      },
    });
    mocks.generateWorkflowPrompts.mockResolvedValue({
      success: true,
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters: [
          {
            id: 1,
            name: '主角',
            roleType: 'LEAD',
            gender: '女',
            ageRange: '25-30',
            identity: '落魄千金',
            personality: ['坚韧'],
            appearance: '雨夜拖着行李箱',
            prompt: '角色定妆提示词：主角',
          },
        ],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
  });

  it('renders the Ant Pro creation workspace tabs', async () => {
    render(<ScriptCreationWorkspace projectId={1} projectName="短剧项目" />);

    expect(screen.getByText('剧本')).toBeInTheDocument();
    expect(screen.getAllByText('角色').length).toBeGreaterThan(0);
    expect(screen.getByText('场景/道具')).toBeInTheDocument();
    expect(screen.getByText('视频任务')).toBeInTheDocument();
    expect(screen.getAllByText('分镜').length).toBeGreaterThan(0);
    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
  });

  it('applies generated script preview to the editable script area', async () => {
    render(<ScriptCreationWorkspace projectId={1} projectName="短剧项目" />);

    fireEvent.click(screen.getAllByRole('button', { name: /AI生成剧本/ })[0]);
    fireEvent.submit(screen.getByRole('form', { name: 'AI生成剧本' }));

    await waitFor(() => {
      expect(mocks.generateScript).toHaveBeenCalledWith(1, {
        duration: 90,
        episodeCount: 12,
        genre: '逆袭',
        storyIdea: '落魄千金重回豪门',
      });
    });
    expect(screen.getByText('生成结果预览')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '应用到剧本' }));

    expect(
      screen.getByDisplayValue(/落魄千金重回豪门后发现当年的陷害另有隐情/),
    ).toBeInTheDocument();
  });

  it('extracts characters when the AI extract button is clicked', async () => {
    render(<ScriptCreationWorkspace projectId={1} projectName="短剧项目" />);

    fireEvent.click(screen.getAllByRole('button', { name: /AI提取角色/ })[0]);

    await waitFor(() => {
      expect(mocks.extractScriptElements).toHaveBeenCalledWith(1, {
        elementType: 'CHARACTER',
      });
    });
    await waitFor(() => {
      expect(screen.getAllByText('主角').length).toBeGreaterThan(0);
    });
  });

  it('runs the remaining phase two workflow actions from real buttons', async () => {
    render(<ScriptCreationWorkspace projectId={1} projectName="短剧项目" />);

    fireEvent.click(screen.getAllByRole('button', { name: /AI改写剧本/ })[0]);
    fireEvent.submit(screen.getByRole('form', { name: 'AI改写剧本' }));

    await waitFor(() => {
      expect(mocks.rewriteScript).toHaveBeenCalledWith(1, {
        outputLength: 'KEEP',
        requirement: '强化前三秒钩子',
        rewriteType: '冲突增强',
      });
    });

    fireEvent.change(screen.getByPlaceholderText('暂无剧本内容'), {
      target: { value: '手工保存剧本' },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    await waitFor(() => {
      expect(mocks.saveCurrentScript).toHaveBeenCalledWith(1, {
        content: '手工保存剧本',
        status: 'DRAFT',
        title: '短剧项目',
      });
    });

    fireEvent.click(screen.getByRole('button', { name: '拆解分镜' }));
    await waitFor(() => {
      expect(mocks.breakdownStoryboards).toHaveBeenCalledWith(1, {
        scope: 'FULL',
      });
    });

    fireEvent.click(screen.getByRole('button', { name: '生成提示词' }));
    await waitFor(() => {
      expect(mocks.generateWorkflowPrompts).toHaveBeenCalledWith(1, {
        targetType: 'ALL',
      });
    });
  });
});
