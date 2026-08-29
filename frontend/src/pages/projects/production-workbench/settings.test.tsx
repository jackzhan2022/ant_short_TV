import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchSettings from './settings';

const mocks = vi.hoisted(() => ({
  confirmScriptElement: vi.fn(),
  deleteScriptElement: vi.fn(),
  extractScriptElements: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  updateScriptElement: vi.fn(),
  pollExecution: vi.fn(),
  queryAssetCandidates: vi.fn(),
  decideAssetCandidate: vi.fn(),
  createVisualVariant: vi.fn(),
  selectPrimaryVisualVariant: vi.fn(),
  bindVisualVariantEpisodes: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ id: '1' }),
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));

vi.mock('./service', () => ({
  confirmScriptElement: mocks.confirmScriptElement,
  deleteScriptElement: mocks.deleteScriptElement,
  extractScriptElements: mocks.extractScriptElements,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  updateScriptElement: mocks.updateScriptElement,
  queryAssetCandidates: mocks.queryAssetCandidates,
  decideAssetCandidate: mocks.decideAssetCandidate,
  createVisualVariant: mocks.createVisualVariant,
  selectPrimaryVisualVariant: mocks.selectPrimaryVisualVariant,
  bindVisualVariantEpisodes: mocks.bindVisualVariantEpisodes,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: { poll: mocks.pollExecution },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task }: any) => (
    <div>
      execution-{task.id}-{task.status}
    </div>
  ),
}));

vi.mock('@ant-design/icons', () => ({
  CheckOutlined: () => <span>check</span>,
  DeleteOutlined: () => <span>delete</span>,
  EditOutlined: () => <span>edit</span>,
  PlusOutlined: () => <span>plus</span>,
  RobotOutlined: () => <span>robot</span>,
  SearchOutlined: () => <span>search</span>,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, ...props }: any) => (
    <button type="button" onClick={onClick} {...props}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Input: Object.assign(
    ({ value, onChange, ...props }: any) => (
      <input value={value} onChange={(event) => onChange?.(event)} {...props} />
    ),
    {
      TextArea: ({ value, onChange, ...props }: any) => (
        <textarea
          value={value}
          onChange={(event) => onChange?.(event)}
          {...props}
        />
      ),
    },
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
  },
}));

const workspace = {
  projectId: 1,
  script: null,
  versions: [],
  characters: [
    {
      id: 1,
      name: '斌斌',
      roleType: '主角',
      gender: '男',
      ageRange: '6岁',
      identity: '走失儿童',
      personality: ['好奇', '胆小'],
      appearance: '圆脸，黄色上衣',
      prompt: '6岁男孩，写实都市风格',
      status: 'CONFIRMED',
      visual: {
        variantCount: 2,
        primaryVariant: {
          id: 11,
          name: '日常形象',
          primary: true,
          usable: true,
          generationStatus: 'COMPLETED',
          currentImageUrl: '/daily.png',
        },
        variants: [
          {
            id: 11,
            name: '日常形象',
            primary: true,
            usable: true,
            generationStatus: 'COMPLETED',
            currentImageUrl: '/daily.png',
          },
          {
            id: 12,
            name: '婚礼礼服',
            primary: false,
            usable: false,
            generationStatus: 'FAILED',
            errorMessage: '生成超时',
          },
        ],
        generationSummary: { COMPLETED: 1, FAILED: 1 },
        episodeBindings: [],
        resolvedImageUrl: '/daily.png',
        resolvedImageSource: 'PRIMARY_VARIANT',
      },
    },
  ],
  scenes: [
    {
      id: 2,
      name: '地下停车场',
      sceneType: '室内',
      atmosphere: '压抑',
      description: '灰色轿车停在昏暗车位',
      visualStyle: '写实冷色调',
      prompt: '地下停车场，低照度',
    },
  ],
  props: [
    {
      id: 3,
      name: '灰色轿车后备箱',
      propType: '关键道具',
      appearance: '半开后备箱',
      plotFunction: '困住斌斌',
      prompt: '灰色轿车后备箱特写',
    },
  ],
  storyboards: [],
  episodes: [
    { episodeId: 101, episodeNo: 1, title: '骗局开始', content: 'A' },
    { episodeId: 102, episodeNo: 2, title: '真相浮现', content: 'B' },
  ],
};

describe('ProductionWorkbenchSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({ data: workspace });
    localStorage.setItem('currentTenantId', '10');
    mocks.extractScriptElements.mockResolvedValue({
      data: { id: 601, businessId: 41, status: 'PENDING', progress: 0 },
    });
    mocks.pollExecution.mockResolvedValue({
      id: 601,
      businessId: 41,
      status: 'SUCCEEDED',
      progress: 100,
    });
    mocks.confirmScriptElement.mockResolvedValue({ data: workspace });
    mocks.deleteScriptElement.mockResolvedValue({ data: workspace });
    mocks.updateScriptElement.mockResolvedValue({ data: workspace });
    mocks.queryAssetCandidates.mockResolvedValue({
      data: {
        items: [
          {
            id: 21,
            runId: 20,
            assetType: 'CHARACTER',
            sourceIndex: 0,
            name: '林夏',
            normalizedName: '林夏',
            candidateJson: '{"name":"林夏"}',
            validationStatus: 'VALID',
            duplicateGroupKey: 'character:林夏',
            proposedTargetId: 1,
            matchType: 'NORMALIZED_NAME',
            matchConfidence: 0.95,
            reviewStatus: 'PENDING_REVIEW',
            aliases: [],
          },
          {
            id: 22,
            runId: 20,
            assetType: 'CHARACTER',
            sourceIndex: 1,
            candidateJson: '{}',
            validationStatus: 'INVALID',
            validationErrorsJson: '["name不能为空"]',
            reviewStatus: 'PENDING_REVIEW',
            aliases: [],
          },
        ],
        total: 2,
        page: 1,
        pageSize: 20,
      },
    });
    mocks.decideAssetCandidate.mockResolvedValue({ data: {} });
    mocks.createVisualVariant.mockResolvedValue({ data: {} });
    mocks.selectPrimaryVisualVariant.mockResolvedValue({ data: {} });
    mocks.bindVisualVariantEpisodes.mockResolvedValue({ data: [] });
  });

  it('shows normalized candidates separately from canonical assets', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText('待审核识别结果')).toBeInTheDocument();
    expect(screen.getByText('林夏')).toBeInTheDocument();
    expect(screen.getByText('name不能为空')).toBeInTheDocument();
    expect(screen.getByText(/建议合并到/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '合并林夏' }));
    await waitFor(() => {
      expect(mocks.decideAssetCandidate).toHaveBeenCalledWith(
        1,
        21,
        expect.objectContaining({
          decisionType: 'ACCEPT_MERGE',
          targetAssetId: 1,
        }),
      );
    });
  });

  it('manages visual variants and stable episode bindings from an asset card', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText('2 个视觉形象')).toBeInTheDocument();
    expect(screen.getByText('主形象：日常形象')).toBeInTheDocument();
    expect(screen.getByText('来源：主形象')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '管理斌斌视觉形象' }));

    expect(screen.getByText('婚礼礼服')).toBeInTheDocument();
    expect(screen.getByText('生成超时')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('新视觉形象名称'), {
      target: { value: '雨夜造型' },
    });
    fireEvent.click(screen.getByRole('button', { name: '新增视觉形象' }));
    await waitFor(() => {
      expect(mocks.createVisualVariant).toHaveBeenCalledWith(
        1,
        'CHARACTER',
        1,
        expect.objectContaining({ name: '雨夜造型' }),
      );
    });

    fireEvent.click(
      screen.getAllByRole('checkbox', { name: '第1集 骗局开始' })[1],
    );
    fireEvent.click(screen.getByRole('button', { name: '绑定婚礼礼服到剧集' }));
    await waitFor(() => {
      expect(mocks.bindVisualVariantEpisodes).toHaveBeenCalledWith(1, 12, {
        episodeIds: [101],
        preferred: true,
      });
    });
  });

  it('renders restored setting assets instead of the old image task table', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText('设定资产')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /角色/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /场景/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /道具/ })).toBeInTheDocument();
    expect(screen.getAllByText('斌斌').length).toBeGreaterThan(0);
    expect(screen.queryByText('地下停车场')).not.toBeInTheDocument();
    expect(screen.queryByText('灰色轿车后备箱')).not.toBeInTheDocument();
    expect(screen.queryByText('AI图片生产')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
  });

  it('reuses existing element backend actions', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: /AI提取角色/ }));
    fireEvent.click(screen.getByRole('button', { name: '确认斌斌' }));

    await waitFor(() => {
      expect(mocks.extractScriptElements).toHaveBeenCalledWith(1, {
        elementType: 'CHARACTER',
      });
      expect(mocks.confirmScriptElement).toHaveBeenCalledWith(
        1,
        'CHARACTER',
        1,
      );
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        10,
        601,
        expect.any(Function),
      );
      expect(
        mocks.queryScriptWorkspace.mock.calls.length,
      ).toBeGreaterThanOrEqual(2);
    });
    expect(screen.getByText('execution-601-SUCCEEDED')).toBeInTheDocument();
  });
});
