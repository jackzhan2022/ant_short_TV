import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchSettings from './settings';

const mocks = vi.hoisted(() => ({
  confirmScriptElement: vi.fn(),
  deleteScriptElement: vi.fn(),
  extractScriptElements: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryAssetSettingsWorkspace: vi.fn(),
  queryAssetSettingsSummary: vi.fn(),
  queryAssetVisualWorkspace: vi.fn(),
  updateScriptElement: vi.fn(),
  pollExecution: vi.fn(),
  queryAssetCandidates: vi.fn(),
  decideAssetCandidate: vi.fn(),
  createVisualVariant: vi.fn(),
  deleteVisualVariant: vi.fn(),
  createAiImageTask: vi.fn(),
  selectPrimaryVisualVariant: vi.fn(),
  bindVisualVariantEpisodes: vi.fn(),
  updateVisualVariant: vi.fn(),
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
  queryAssetSettingsSummary: mocks.queryAssetSettingsSummary,
  queryAssetVisualWorkspace: mocks.queryAssetVisualWorkspace,
  updateScriptElement: mocks.updateScriptElement,
  queryAssetCandidates: mocks.queryAssetCandidates,
  decideAssetCandidate: mocks.decideAssetCandidate,
  createVisualVariant: mocks.createVisualVariant,
  deleteVisualVariant: mocks.deleteVisualVariant,
  createAiImageTask: mocks.createAiImageTask,
  selectPrimaryVisualVariant: mocks.selectPrimaryVisualVariant,
  updateVisualVariant: mocks.updateVisualVariant,
  bindVisualVariantEpisodes: mocks.bindVisualVariantEpisodes,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: { poll: mocks.pollExecution },
}));

vi.mock('./ai-config/service', () => ({
  queryProjectAiConfig: vi
    .fn()
    .mockResolvedValue({ data: { imageModelId: 8 } }),
  queryProjectAiModels: vi.fn().mockResolvedValue({
    data: {
      textModels: [],
      imageModels: [{ id: 8, name: 'GPT Image 2' }],
      videoModels: [],
      audioModels: [],
    },
  }),
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
  Drawer: ({ children, open, title }: any) =>
    open ? <section aria-label={title}>{children}</section> : null,
  Modal: ({ children, open, title }: any) =>
    open ? <section aria-label={title}>{children}</section> : null,
  Popconfirm: ({ children, onConfirm, title }: any) => (
    <span>
      {children}
      <button type="button" aria-label={title} onClick={onConfirm}>
        确认
      </button>
    </span>
  ),
  Empty: ({ children, description }: any) => (
    <div>
      {description || '暂无数据'}
      {children}
    </div>
  ),
  Skeleton: () => <div>资产设定加载中</div>,
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
        episodeBindings: [
          {
            id: 31,
            variantId: 11,
            episodeId: 101,
            episodeNo: 1,
            episodeTitle: '骗局开始',
            preferred: true,
            status: 'ACTIVE',
          },
          {
            id: 32,
            variantId: 12,
            episodeId: 102,
            episodeNo: 2,
            episodeTitle: '真相浮现',
            preferred: true,
            status: 'ACTIVE',
          },
        ],
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
    mocks.queryAssetSettingsSummary.mockResolvedValue({
      data: {
        projectId: 1,
        characters: workspace.characters,
        scenes: workspace.scenes,
        props: workspace.props,
      },
    });
    mocks.queryAssetVisualWorkspace.mockResolvedValue({
      data: workspace.characters[0].visual,
    });
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
    mocks.createAiImageTask.mockResolvedValue({ data: {} });
    mocks.selectPrimaryVisualVariant.mockResolvedValue({ data: {} });
    mocks.updateVisualVariant.mockResolvedValue({ data: {} });
    mocks.bindVisualVariantEpisodes.mockResolvedValue({ data: [] });
  });

  it('shows a skeleton while the initial asset settings data is loading', () => {
    mocks.queryAssetSettingsSummary.mockReturnValue(new Promise(() => {}));
    mocks.queryAssetCandidates.mockReturnValue(new Promise(() => {}));

    render(<ProductionWorkbenchSettings />);

    expect(screen.getByLabelText('资产设定加载中')).toBeInTheDocument();
  });

  it('shows a retry action when the initial asset settings request fails', async () => {
    mocks.queryAssetSettingsSummary
      .mockRejectedValueOnce(new Error('load failed'))
      .mockResolvedValueOnce({
        data: {
          projectId: 1,
          characters: workspace.characters,
          scenes: workspace.scenes,
          props: workspace.props,
        },
      });

    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText('设定页加载失败')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => {
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(2);
    });
  });

  it('keeps normalized candidates out of the formal asset grid until the review drawer opens', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(
      await screen.findByRole('button', { name: '审核资产 2' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('林夏')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '审核资产 2' }));
    expect(screen.getByText('审核队列')).toBeInTheDocument();
    expect(screen.getByText('候选信息与正式资产对比')).toBeInTheDocument();
    expect(screen.getAllByText('林夏').length).toBeGreaterThan(0);
    expect(screen.getByText('建议合并至「斌斌」')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '审核候选22' }));
    expect(screen.getAllByText('name不能为空').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: '审核候选林夏' }));

    fireEvent.click(screen.getByRole('button', { name: '确认合并' }));
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

  it('manages visual variants and shows their existing episode bindings on the preview', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText(/变装 2 个/)).toBeInTheDocument();
    expect(screen.getByText('关联 2 集')).toHaveAttribute(
      'title',
      expect.stringContaining('婚礼礼服：第2集'),
    );
    expect(screen.queryByText('主形象：日常形象')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '确认斌斌' }),
    ).not.toBeInTheDocument();
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    expect(
      screen.getByRole('button', { name: '确认斌斌' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '管理斌斌视觉形象' }));

    expect(
      screen.getByRole('region', { name: '视觉形象画廊' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('视觉形象主图预览')).toHaveStyle({
      height: 'clamp(260px, 48vh, 430px)',
    });
    expect(
      screen.getByRole('button', { name: '选择日常形象' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '选择婚礼礼服' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    expect(screen.getAllByText('婚礼礼服').length).toBeGreaterThan(0);
    expect(screen.getByText('生成超时')).toBeInTheDocument();
    expect(screen.getByLabelText('婚礼礼服关联剧集')).toHaveTextContent('2');
    expect(screen.getByLabelText('婚礼礼服关联剧集')).toHaveAttribute(
      'title',
      '第2集 真相浮现',
    );
    expect(
      screen.queryByRole('button', { name: '绑定婚礼礼服到剧集' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '新增变装' }));
    fireEvent.change(screen.getByLabelText('新视觉形象名称'), {
      target: { value: '雨夜造型' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认新增视觉形象' }));
    await waitFor(() => {
      expect(mocks.createVisualVariant).toHaveBeenCalledWith(
        1,
        'CHARACTER',
        1,
        expect.objectContaining({ name: '雨夜造型' }),
      );
    });
    expect(
      screen.queryByRole('button', { name: '删除' }),
    ).not.toBeInTheDocument();
    const thumbnailViewport = screen.getByLabelText('视觉形象缩略图列表');
    Object.defineProperties(thumbnailViewport, {
      clientWidth: { configurable: true, value: 180 },
      scrollWidth: { configurable: true, value: 600 },
    });
    const wheelEvent = new WheelEvent('wheel', {
      bubbles: true,
      cancelable: true,
      deltaY: 80,
    });
    fireEvent(thumbnailViewport, wheelEvent);
    expect(thumbnailViewport.scrollLeft).toBe(80);
    expect(wheelEvent.defaultPrevented).toBe(true);
    expect(screen.getByLabelText('视觉形象新增入口')).toContainElement(
      screen.getByRole('button', { name: '新增变装' }),
    );
    fireEvent.mouseEnter(screen.getByTestId('视觉形象缩略图-婚礼礼服'));
    fireEvent.click(screen.getByRole('button', { name: '删除婚礼礼服' }));
    expect(mocks.deleteVisualVariant).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '确认删除婚礼礼服' }));
    await waitFor(() => {
      expect(mocks.deleteVisualVariant).toHaveBeenCalledWith(1, 12);
    });
  });

  it('opens a separate generator and saves the changed prompt before submitting', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByRole('button', { name: '审核资产 2' });
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '管理斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    fireEvent.click(screen.getByRole('button', { name: '重新生成婚礼礼服' }));
    expect(screen.getByLabelText('婚礼礼服引用图')).toHaveAttribute(
      'src',
      '/daily.png',
    );
    await screen.findByRole('option', { name: 'GPT Image 2' });
    fireEvent.change(screen.getByLabelText('图片模型'), {
      target: { value: '8' },
    });
    fireEvent.change(screen.getByLabelText('婚礼礼服生成提示词'), {
      target: { value: '婚礼礼服，电影感' },
    });
    fireEvent.click(screen.getByRole('button', { name: '提交婚礼礼服生成' }));

    await waitFor(() => {
      expect(mocks.updateVisualVariant).toHaveBeenCalledWith(
        1,
        12,
        expect.objectContaining({ prompt: '婚礼礼服，电影感' }),
      );
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          targetId: 12,
          prompt: '婚礼礼服，电影感',
          referenceImages: ['/daily.png'],
          modelId: 8,
          aspectRatio: '3:4',
          imageCount: 1,
        }),
      );
    });
  });

  it('submits the existing prompt without an unnecessary visual variant update', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByRole('button', { name: '审核资产 2' });
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '管理斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '生成图片日常形象' }));
    fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));

    await waitFor(() => {
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          targetId: 11,
          prompt: expect.stringContaining('日常形象'),
        }),
      );
    });
    expect(mocks.updateVisualVariant).not.toHaveBeenCalled();
  });
  it('renders the reference-style asset workbench instead of the old image task table', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(
      await screen.findByText(/请确保角色、场景及道具已全部生成。/),
    ).toBeInTheDocument();
    expect(screen.queryByText('正式资产库')).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /角色/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /角色/ })).toHaveAttribute(
      'style',
      expect.stringContaining('var(--app-color-primary)'),
    );
    expect(screen.getByRole('tab', { name: /场景/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /道具/ })).toBeInTheDocument();
    expect(
      screen.getByRole('toolbar', { name: '角色资产操作' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('角色设定')).not.toBeInTheDocument();
    expect(screen.getAllByText('斌斌').length).toBeGreaterThan(0);
    expect(screen.queryByText('地下停车场')).not.toBeInTheDocument();
    expect(screen.queryByText('灰色轿车后备箱')).not.toBeInTheDocument();
    expect(screen.queryByText('AI图片生产')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledWith(1);
    });
  });

  it('reuses existing element backend actions', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: /AI提取角色/ }));
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
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
        mocks.queryAssetSettingsSummary.mock.calls.length,
      ).toBeGreaterThanOrEqual(2);
    });
    expect(screen.getByText('execution-601-SUCCEEDED')).toBeInTheDocument();
  });
});
