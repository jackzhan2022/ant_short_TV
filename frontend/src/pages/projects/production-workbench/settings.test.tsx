import { recordedPointEstimates } from '@root/tests/mockPointEstimate';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AssetSettingsWorkspace } from './service';
import ProductionWorkbenchSettings from './settings';
vi.mock('./CharacterVoiceModal', () => ({
  default: ({ characterName, onClose }: { characterName: string; onClose: () => void }) => <section aria-label={`配置${characterName}音色弹窗`}><button type="button" onClick={onClose}>关闭音色弹窗</button></section>,
}));
vi.mock('./manual-assets', async (original) => ({
  ...await original<object>(),
  queryManualAssetOptions: vi.fn().mockResolvedValue({ success: true, data: {
    scriptId: 1, canCreate: false, disabledReason: '测试中禁用新增入口', definitions: {},
  } }),
}));
vi.mock('./AssetNormalizationPanel', () => ({
  default: ({ onBlocked }: { onBlocked: (blocked: boolean, reason?: string) => void }) => (
    <>
    <button type="button" onClick={() => onBlocked(true)}>模拟归一处理中</button>
    <button type="button" onClick={() => onBlocked(true, '资产归一失败，请重试。')}>模拟归一失败</button>
    <button type="button" onClick={() => onBlocked(true, '状态读取失败，正在自动重试。')}>模拟状态读取失败</button>
    <button type="button" onClick={() => onBlocked(false)}>模拟归一恢复</button>
    </>
  ),
}));

const mocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  canEdit: true,
  confirmScriptElement: vi.fn(),
  deleteScriptElement: vi.fn(),
  queryAssetDeletionImpact: vi.fn(),
  queryAssetReextractionPreflight: vi.fn(),
  submitAssetReextraction: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryAssetSettingsWorkspace: vi.fn(),
  queryAssetSettingsSummary: vi.fn(),
  queryAssetVisualWorkspace: vi.fn(),
  queryAiImageResults: vi.fn(),
  selectAiImageResult: vi.fn(),
  updateScriptElement: vi.fn(),
  pollExecution: vi.fn(),
  queryAssetCandidates: vi.fn(),
  decideAssetCandidate: vi.fn(),
  createVisualVariant: vi.fn(),
  deleteVisualVariant: vi.fn(),
  createAiImageTask: vi.fn(),
  queryAssetImageBatchPreflight: vi.fn(),
  createAssetImageBatch: vi.fn(),
  retryAssetImageBatch: vi.fn(),
  queryAssetImageBatch: vi.fn(),
  selectPrimaryVisualVariant: vi.fn(),
  bindVisualVariantEpisodes: vi.fn(),
  updateVisualVariant: vi.fn(),
  renameScriptElement: vi.fn(),
  renameVisualVariant: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ id: '1' }),
  useOutletContext: () => ({ project: { capabilities: { canEdit: mocks.canEdit } } }),
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));

vi.mock('./service', () => ({
  confirmScriptElement: mocks.confirmScriptElement,
  deleteScriptElement: mocks.deleteScriptElement,
  queryAssetDeletionImpact: mocks.queryAssetDeletionImpact,
  queryAssetReextractionPreflight: mocks.queryAssetReextractionPreflight,
  submitAssetReextraction: mocks.submitAssetReextraction,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  queryAssetSettingsSummary: mocks.queryAssetSettingsSummary,
  queryAssetVisualWorkspace: mocks.queryAssetVisualWorkspace,
  queryAiImageResults: mocks.queryAiImageResults,
  selectAiImageResult: mocks.selectAiImageResult,
  updateScriptElement: mocks.updateScriptElement,
  renameScriptElement: mocks.renameScriptElement,
  renameVisualVariant: mocks.renameVisualVariant,
  queryAssetCandidates: mocks.queryAssetCandidates,
  decideAssetCandidate: mocks.decideAssetCandidate,
  createVisualVariant: mocks.createVisualVariant,
  deleteVisualVariant: mocks.deleteVisualVariant,
  createAiImageTask: mocks.createAiImageTask,
  queryAssetImageBatchPreflight: mocks.queryAssetImageBatchPreflight,
  createAssetImageBatch: mocks.createAssetImageBatch,
  retryAssetImageBatch: mocks.retryAssetImageBatch,
  queryAssetImageBatch: mocks.queryAssetImageBatch,
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
  CompressOutlined: () => <span>compress</span>,
  AudioOutlined: () => <span>audio</span>,
  CheckOutlined: () => <span>check</span>,
  CloseOutlined: () => <span>close</span>,
  DeleteOutlined: () => <span>delete</span>,
  EditOutlined: () => <span>edit</span>,
  PictureOutlined: () => <span>picture</span>,
  PlusOutlined: () => <span>plus</span>,
  RobotOutlined: () => <span>robot</span>,
  SearchOutlined: () => <span>search</span>,
  ScanOutlined: () => <span>scan</span>,
  UploadOutlined: () => <span>upload</span>,
}));

vi.mock('antd', async (importOriginal) => ({
  Image: (await importOriginal<typeof import('antd')>()).Image,
  Tooltip: ({ children }: any) => <>{children}</>,
  App: {
    useApp: () => ({
      message: { error: mocks.messageError, success: vi.fn() },
    }),
  },
  Button: ({ children, icon, onClick, ...props }: any) => (
    <button type="button" onClick={onClick} {...props}>
      {icon}
      {children}
    </button>
  ),
  Drawer: ({ children, open, title }: any) =>
    open ? <section aria-label={title}>{children}</section> : null,
  Modal: ({ children, open, title, onOk, onCancel, okText, okButtonProps, confirmLoading }: any) =>
    open ? (
      <section aria-label={title}>
        {children}
        <button type="button" onClick={onCancel}>
          取消
        </button>
        <button type="button" onClick={onOk} disabled={okButtonProps?.disabled || confirmLoading}>
          {okText || '确认提交'}
        </button>
      </section>
    ) : null,
  Radio: {
    Group: ({ options, value, onChange }: any) => (
      <div>
        {options.map((option: any) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={value === option.value}
            onClick={() => onChange({ target: { value: option.value } })}
          >
            {option.label}
          </button>
        ))}
      </div>
    ),
  },
  Popconfirm: ({ children, onConfirm, title, disabled, okButtonProps }: any) => (
    <span>
      {children}
      <button type="button" aria-label={title} onClick={onConfirm} disabled={disabled || okButtonProps?.disabled}>
        确认
      </button>
    </span>
  ),
  Pagination: ({ current, onChange }: any) => (
    <button className="ant-pagination-next" type="button" onClick={() => onChange(current + 1, 20)}>
      下一页
    </button>
  ),
  Empty: ({ children, description }: any) => (
    <div>
      {description || '暂无数据'}
      {children}
    </div>
  ),
  Skeleton: () => <div>资产设定加载中</div>,
  Spin: () => <span role="progressbar" aria-label="图片生成中" />,
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
  Tag: ({ children, color, ...props }: any) => (
    <span data-color={color} {...props}>
      {children}
    </span>
  ),
  Checkbox: ({ children, checked, onChange, ...props }: any) => (
    <label>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) =>
          onChange?.({ target: { checked: event.target.checked } })
        }
        {...props}
      />
      {children}
    </label>
  ),
  Dropdown: ({ children, menu, disabled }: any) => (
    <div>
      {children}
      {menu.items.map((item: any) => (
        <button
          key={item.key}
          type="button"
          disabled={disabled}
          onClick={() => menu.onClick?.({ key: item.key })}
        >
          {item.label}
        </button>
      ))}
    </div>
  ),
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children, level, ...props }: any) => <h2 {...props}>{children}</h2>,
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
      mainImageThumbnailUrl: '/daily-thumb.png',
      visual: {
        variantCount: 2,
        primaryVariant: {
          id: 11,
          assetType: 'CHARACTER',
          assetId: 1,
          name: '日常形象',
          primary: true,
          usable: true,
          generationStatus: 'COMPLETED',
          currentImageUrl: '/daily.png',
          currentImageThumbnailUrl: '/daily-thumb.png',
        },
        variants: [
          {
            id: 11,
            assetType: 'CHARACTER',
            assetId: 1,
            name: '日常形象',
            prompt: '6岁男孩，写实都市风格',
            primary: true,
            usable: true,
            generationStatus: 'COMPLETED',
            currentImageUrl: '/daily.png',
            currentImageThumbnailUrl: '/daily-thumb.png',
            virtualAvatarAssetId: null,
          },
          {
            id: 12,
            assetType: 'CHARACTER',
            assetId: 1,
            name: '婚礼礼服',
            prompt: '性别:男；衣着描述:白色礼服，领结',
            primary: false,
            usable: false,
            generationStatus: 'FAILED',
            currentImageUrl: '/wedding.png',
            currentImageThumbnailUrl: '/wedding-thumb.png',
            virtualAvatarAssetId: null,
            errorMessage: '生成超时',
          },
        ],
        generationSummary: { COMPLETED: 1, GENERATING: 0, FAILED: 1 },
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
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });
  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', class {
      constructor(private callback: IntersectionObserverCallback) {}
      observe() {
        this.callback([{ isIntersecting: true }] as IntersectionObserverEntry[], this as unknown as IntersectionObserver);
      }
      disconnect() {}
    });
    sessionStorage.clear();
    vi.clearAllMocks();
    mocks.canEdit = true;
    mocks.queryAiImageResults.mockResolvedValue({ success: true, data: { data: [], total: 0 } });
    mocks.queryAssetSettingsSummary.mockReset();
    mocks.queryAssetVisualWorkspace.mockReset();
    workspace.characters[0].visual.variants[0].prompt = '6岁男孩，写实都市风格';
    workspace.characters[0].visual.variants[1].prompt =
      '性别:男；衣着描述:白色礼服，领结';
    workspace.characters[0].visual.variants[1].generationStatus = 'FAILED';
    workspace.characters[0].mainImageThumbnailUrl = '/daily-thumb.png';
    workspace.characters[0].visual.variants[0].currentImageUrl = '/daily.png';
    workspace.characters[0].visual.variants[0].currentImageThumbnailUrl =
      '/daily-thumb.png';
    workspace.characters[0].visual.variants[0].virtualAvatarAssetId = null;
    workspace.characters[0].visual.variants[1].currentImageUrl = '/wedding.png';
    workspace.characters[0].visual.variants[1].currentImageThumbnailUrl =
      '/wedding-thumb.png';
    workspace.characters[0].visual.generationSummary.GENERATING = 0;
    workspace.characters[0].visual.generationSummary.FAILED = 1;
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
    mocks.queryAssetReextractionPreflight.mockResolvedValue({
      data: {
        targetType: 'CHARACTER',
        existingAssets: 0,
        existingVariants: 0,
        existingPrompts: 0,
        requiresConfirmation: false,
      },
    });
    mocks.submitAssetReextraction.mockResolvedValue({
      data: { id: 601, businessId: 41, status: 'PENDING', progress: 0 },
    });
    mocks.pollExecution.mockResolvedValue({
      id: 601,
      businessId: 41,
      status: 'SUCCEEDED',
      progress: 100,
    });
    mocks.confirmScriptElement.mockResolvedValue({ data: workspace });
    mocks.deleteScriptElement.mockResolvedValue({ data: null });
    mocks.queryAssetDeletionImpact.mockReset();
    mocks.queryAssetDeletionImpact.mockResolvedValue({ data: { assetName: '斌斌', affectedStoryboardCount: 5, promptMentionCount: 9 } });
    mocks.updateScriptElement.mockResolvedValue({ data: null });
    mocks.renameScriptElement.mockReset().mockResolvedValue({ data: null });
    mocks.renameVisualVariant.mockReset().mockResolvedValue({ data: null });
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
    mocks.queryAssetImageBatchPreflight.mockResolvedValue({
      data: {
        assetType: 'CHARACTER',
        mode: 'PRIMARY',
        selectedAssets: 1,
        plannedTasks: 1,
        waitingDependencies: 0,
        skippedCompleted: 0,
        skippedGenerating: 0,
        skippedMissingPrompt: 0,
        skippedOther: 0,
        totalImages: 1,
      },
    });
    mocks.createAssetImageBatch.mockResolvedValue({
      data: {
        id: 91,
        status: 'PENDING',
        total: 1,
        pending: 1,
        running: 0,
        succeeded: 0,
        failed: 0,
        skipped: 0,
        items: [],
      },
    });
    mocks.queryAssetImageBatch.mockResolvedValue({
      data: {
        id: 91,
        status: 'SUCCEEDED',
        total: 1,
        pending: 0,
        running: 0,
        succeeded: 1,
        failed: 0,
        skipped: 0,
        items: [],
      },
    });
    mocks.selectPrimaryVisualVariant.mockResolvedValue({ data: {} });
    mocks.updateVisualVariant.mockResolvedValue({ data: {} });
    mocks.bindVisualVariantEpisodes.mockResolvedValue({ data: [] });
  });

  it.each([
    ['CHARACTER', '角色', '斌斌', 1],
    ['SCENE', '场景', '地下停车场', 2],
    ['PROP', '道具', '灰色轿车后备箱', 3],
  ] as const)('renames %s from its card using a name-only request', async (type, label, name, id) => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByRole('button', { name: '查看斌斌视觉形象' });
    fireEvent.click(screen.getByRole('tab', { name: new RegExp(label) }));
    fireEvent.click(screen.getByRole('button', { name: `修改${label}名称：${name}` }));
    const dialog = screen.getByRole('region', { name: `修改${label}名称` });
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: `  新${name}  ` } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));
    await waitFor(() => expect(mocks.renameScriptElement).toHaveBeenCalledWith(1, type, id, `新${name}`));
    expect(await screen.findByRole('button', { name: `查看新${name}视觉形象` })).toBeInTheDocument();
    expect(mocks.updateScriptElement).not.toHaveBeenCalled();
  });

  it('cancels or rejects blank names without changing the asset', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '修改角色名称：斌斌' }));
    let dialog = screen.getByRole('region', { name: '修改角色名称' });
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: '取消的新名' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '取消' }));
    expect(mocks.renameScriptElement).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '修改角色名称：斌斌' }));
    dialog = screen.getByRole('region', { name: '修改角色名称' });
    expect(within(dialog).getByRole('textbox')).toHaveValue('斌斌');
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: '   ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('名称不能为空');
    expect(mocks.renameScriptElement).not.toHaveBeenCalled();
  });

  it('renames the selected variant independently and preserves its image and prompt', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(await screen.findByRole('button', { name: '选择婚礼礼服' }));
    fireEvent.click(screen.getByRole('button', { name: '修改形态名称：婚礼礼服' }));
    const dialog = screen.getByRole('region', { name: '修改形态名称' });
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: '晚宴礼服' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存' }));
    await waitFor(() => expect(mocks.renameVisualVariant).toHaveBeenCalledWith(1, 12, '晚宴礼服'));
    expect(await screen.findByRole('button', { name: '修改形态名称：晚宴礼服' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择晚宴礼服' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('img', { name: '晚宴礼服预览图' })).toHaveAttribute('src', '/wedding-thumb.png');
    expect(mocks.updateVisualVariant).not.toHaveBeenCalled();
    expect(mocks.renameScriptElement).not.toHaveBeenCalled();
    const gallery = screen.getByRole('region', { name: '视觉形象画廊' });
    fireEvent.click(within(gallery).getByRole('button', { name: '修改角色名称：斌斌' }));
    const assetDialog = screen.getByRole('region', { name: '修改角色名称' });
    fireEvent.change(within(assetDialog).getByRole('textbox'), { target: { value: '新角色' } });
    fireEvent.click(within(assetDialog).getByRole('button', { name: '保存' }));
    await screen.findByRole('button', { name: '查看新角色视觉形象' });
    expect(within(gallery).getByRole('button', { name: '修改角色名称：新角色' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择晚宴礼服' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('ignores an older background response after renaming with Enter', async () => {
    const snapshot = structuredClone(workspace);
    snapshot.characters[0].visual.generationSummary.GENERATING = 1;
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: snapshot });
    mocks.queryAssetVisualWorkspace.mockResolvedValue({ data: snapshot.characters[0].visual });
    vi.useFakeTimers();
    await act(async () => { render(<ProductionWorkbenchSettings />); });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' })); });
    let finishSummary!: (response: unknown) => void;
    let finishDetail!: (response: unknown) => void;
    mocks.queryAssetSettingsSummary.mockImplementationOnce(() => new Promise((resolve) => { finishSummary = resolve; }));
    mocks.queryAssetVisualWorkspace.mockImplementationOnce(() => new Promise((resolve) => { finishDetail = resolve; }));
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    fireEvent.click(screen.getByRole('button', { name: '修改形态名称：日常形象' }));
    const dialog = screen.getByRole('region', { name: '修改形态名称' });
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: '新默认形态' } });
    await act(async () => { fireEvent.keyDown(within(dialog).getByRole('textbox'), { key: 'Enter' }); });
    expect(mocks.renameVisualVariant).toHaveBeenCalledWith(1, 11, '新默认形态');
    await act(async () => {
      finishSummary({ data: snapshot });
      finishDetail({ data: snapshot.characters[0].visual });
    });
    expect(screen.getByRole('button', { name: '修改形态名称：新默认形态' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择新默认形态' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('does not offer rename to a read-only project member', async () => {
    mocks.canEdit = false;
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    await screen.findByRole('button', { name: '选择日常形象' });
    expect(screen.queryByRole('button', { name: /^修改(角色|形态)名称：/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /场景/ }));
    expect(screen.queryByRole('button', { name: /^修改场景名称：/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /道具/ }));
    expect(screen.queryByRole('button', { name: /^修改道具名称：/ })).not.toBeInTheDocument();
    expect(mocks.renameScriptElement).not.toHaveBeenCalled();
    expect(mocks.renameVisualVariant).not.toHaveBeenCalled();
  });

  it('hides name edit controls while asset normalization blocks editing', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    await screen.findByRole('button', { name: '修改形态名称：日常形象' });
    fireEvent.click(screen.getByRole('button', { name: '模拟归一处理中' }));
    expect(screen.queryByRole('button', { name: /^修改(角色|形态)名称：/ })).not.toBeInTheDocument();
  });

  it('keeps input on rename failure and prevents duplicate saves', async () => {
    let rejectSave!: (reason: unknown) => void;
    mocks.renameScriptElement.mockImplementation(() => new Promise((_, reject) => { rejectSave = reject; }));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '修改角色名称：斌斌' }));
    const dialog = screen.getByRole('region', { name: '修改角色名称' });
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: '新角色' } });
    const save = within(dialog).getByRole('button', { name: '保存' });
    fireEvent.click(save);
    fireEvent.click(save);
    expect(mocks.renameScriptElement).toHaveBeenCalledTimes(1);
    await act(async () => rejectSave({ info: { errorMessage: '该角色名称已存在' } }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('该角色名称已存在');
    expect(within(dialog).getByRole('textbox')).toHaveValue('新角色');
    expect(screen.getByRole('button', { name: '查看斌斌视觉形象' })).toBeInTheDocument();
  });

  it('shows a skeleton while the initial asset settings data is loading', () => {
    mocks.queryAssetSettingsSummary.mockReturnValue(new Promise(() => {}));
    mocks.queryAssetCandidates.mockReturnValue(new Promise(() => {}));

    render(<ProductionWorkbenchSettings />);

    expect(screen.getByLabelText('资产设定加载中')).toBeInTheDocument();
  });

  it('uses formal assets without requesting the retired candidate or confirmation paths', async () => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    expect(mocks.queryAssetCandidates).not.toHaveBeenCalled();
    expect(screen.queryByText('批量确认')).not.toBeInTheDocument();
    expect(screen.queryByText(/审核资产/)).not.toBeInTheDocument();
  });

  it('reveals summary thumbnails only after stable image loading', async () => {
    render(<ProductionWorkbenchSettings />);

    const thumbnail = await screen.findByAltText('斌斌当前视觉形象');
    await waitFor(() => expect(thumbnail).toHaveAttribute('src', '/daily-thumb.png'));
    expect(thumbnail).toHaveAttribute('loading', 'lazy');
    expect(thumbnail).toHaveStyle({ opacity: '0' });
    expect(screen.getByLabelText('斌斌当前视觉形象加载中')).toHaveTextContent('加载中');

    fireEvent.load(thumbnail);

    await waitFor(() => expect(thumbnail).toHaveStyle({ opacity: '1' }));
    expect(
      screen.queryByLabelText('斌斌当前视觉形象加载中'),
    ).not.toBeInTheDocument();
  });

  it('loads only the opened visual detail and retains assets on detail failure', async () => {
    mocks.queryAssetVisualWorkspace.mockRejectedValueOnce(new Error('offline'));
    render(<ProductionWorkbenchSettings />);
    const open = await screen.findByRole('button', {
      name: '查看斌斌视觉形象',
    });
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
    expect(mocks.queryScriptWorkspace).not.toHaveBeenCalled();
    fireEvent.click(open);
    await screen.findByText('视觉形象加载失败');
    expect(screen.getByTestId('asset-image-CHARACTER-1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试视觉形象' }));
    await waitFor(() =>
      expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalledTimes(2),
    );
    await waitFor(() =>
      expect(screen.queryByText('视觉形象加载失败')).not.toBeInTheDocument(),
    );
    expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(1);
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

  it('opens image details beside the browsable list and closes them when changing category', async () => {
    render(<ProductionWorkbenchSettings />);
    const image = await screen.findByRole('button', { name: '查看斌斌视觉形象' });
    fireEvent.click(screen.getByText('斌斌', { exact: true }));

    const panel = screen.getByRole('region', { name: '视觉形象画廊' });
    const list = screen.getByRole('region', { name: '资产列表' });
    expect(panel.parentElement).toContainElement(list);
    expect(list).not.toContainElement(panel);
    expect(panel.closest('.ant-modal')).toBeNull();
    expect(image).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(screen.getByLabelText('选择斌斌'));
    expect(screen.getByLabelText('选择斌斌')).toBeChecked();
    expect(panel).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '关闭视觉形象' }));
    expect(screen.queryByRole('region', { name: '视觉形象画廊' })).not.toBeInTheDocument();
    expect(image).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('tab', { name: /场景/ }));
    expect(screen.queryByRole('region', { name: '视觉形象画廊' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看地下停车场视觉形象' }));
    expect(mocks.queryAssetVisualWorkspace).toHaveBeenLastCalledWith(
      1, 'SCENE', 2, { current: 1, pageSize: 20 },
    );
    expect(screen.getByRole('heading', { name: /^地下停车场/ })).toBeInTheDocument();
  });

  it('switches asset details without applying a late response or keeping the previous add form', async () => {
    const secondCharacter = { ...workspace.characters[0], id: 4, name: '林夏', visual: undefined };
    mocks.queryAssetSettingsSummary.mockResolvedValue({
      data: { ...workspace, characters: [...workspace.characters, secondCharacter] },
    });
    let resolveFirst!: (value: unknown) => void;
    mocks.queryAssetVisualWorkspace.mockImplementationOnce(() => new Promise((resolve) => {
      resolveFirst = resolve;
    }));
    mocks.queryAssetVisualWorkspace.mockResolvedValueOnce({
      data: {
        ...workspace.characters[0].visual,
        primaryVariant: undefined,
        variants: [{ ...workspace.characters[0].visual.variants[0], id: 41, name: '林夏日常' }],
      },
    });
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '新增变装' }));
    fireEvent.change(screen.getByLabelText('新视觉形象名称'), { target: { value: '未保存造型' } });
    fireEvent.click(screen.getByRole('button', { name: '查看林夏视觉形象' }));
    expect(await screen.findByAltText('林夏日常预览图')).toBeInTheDocument();
    expect(screen.queryByLabelText('新视觉形象名称')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看斌斌视觉形象' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('button', { name: '查看林夏视觉形象' })).toHaveAttribute('aria-expanded', 'true');

    await act(async () => { resolveFirst({ data: workspace.characters[0].visual }); });
    expect(screen.getByRole('heading', { name: /^林夏/ })).toBeInTheDocument();
    expect(screen.getByAltText('林夏日常预览图')).toBeInTheDocument();
    expect(screen.queryByAltText('日常形象预览图')).not.toBeInTheDocument();
  });

  it('keeps the current asset and its add form when an earlier asset mutation finishes', async () => {
    const secondCharacter = { ...workspace.characters[0], id: 4, name: '林夏' };
    mocks.queryAssetSettingsSummary.mockResolvedValue({
      data: { ...workspace, characters: [...workspace.characters, secondCharacter] },
    });
    let finishCreate!: (value: unknown) => void;
    mocks.createVisualVariant.mockImplementationOnce(() => new Promise((resolve) => {
      finishCreate = resolve;
    }));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '新增变装' }));
    fireEvent.change(screen.getByLabelText('新视觉形象名称'), { target: { value: '斌斌雨衣' } });
    fireEvent.click(screen.getByRole('button', { name: '确认新增视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '查看林夏视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '新增变装' }));
    fireEvent.change(screen.getByLabelText('新视觉形象名称'), { target: { value: '林夏礼服' } });

    await act(async () => { finishCreate({ data: {} }); });
    expect(screen.getByRole('heading', { name: /^林夏/ })).toBeInTheDocument();
    expect(screen.getByLabelText('新视觉形象名称')).toHaveValue('林夏礼服');
  });

  it.each([
    { visual: undefined, variantCount: 25, episodeCount: 12 },
    { visual: workspace.characters[0].visual, variantCount: 25, episodeCount: 12 },
    { visual: workspace.characters[0].visual, variantCount: 0, episodeCount: 0 },
  ])('shows authoritative summary counts without loading visual details ($variantCount variants)', async ({ visual, variantCount, episodeCount }) => {
    mocks.queryAssetSettingsSummary.mockResolvedValue({
      data: {
        projectId: 1,
        characters: [{
          ...workspace.characters[0],
          visual,
          visualSummary: { variantCount, episodeCount },
        }],
        scenes: [],
        props: [],
      },
    });
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText(`变装 ${variantCount} 个`)).toBeInTheDocument();
    expect(screen.getByText(`关联 ${episodeCount} 集`)).toHaveAttribute(
      'title', episodeCount ? `共关联 ${episodeCount} 集` : '尚未关联剧集',
    );
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
  });

  it('replaces character visual buttons with voice configuration while preserving image access', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '配置斌斌音色' }));
    expect(screen.getByRole('region', { name: '配置斌斌音色弹窗' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: '视觉形象画廊' })).not.toBeInTheDocument();
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '关闭音色弹窗' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    expect(await screen.findByRole('region', { name: '视觉形象画廊' })).toBeInTheDocument();
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
      screen.queryByRole('button', { name: '保存斌斌' }),
    ).not.toBeInTheDocument();
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    expect(
      screen.getByRole('button', { name: '保存斌斌' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));

    expect(
      screen.getByRole('region', { name: '视觉形象画廊' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^斌斌/ })).toBeInTheDocument();
    expect(screen.getByText('出镜剧集')).toBeInTheDocument();
    expect(screen.getByTestId('视觉形象主图预览')).not.toContainElement(
      screen.getByLabelText('日常形象关联剧集'),
    );
    expect(screen.getByLabelText('视觉形象操作')).toContainElement(
      screen.getByRole('button', { name: '生成图片日常形象' }),
    );
    expect(screen.getByAltText('日常形象缩略图')).toHaveAttribute(
      'src',
      '/daily-thumb.png',
    );
    expect(screen.getByLabelText('斌斌主要形态提示词')).toHaveValue('6岁男孩，写实都市风格');
    const mainPreview = screen.getByTestId('视觉形象主图预览');
    const dailyOriginal = screen.getByAltText('日常形象预览图');
    const dailyPreview = mainPreview.querySelector<HTMLImageElement>(
      'img[src="/daily-thumb.png"]',
    );
    expect(dailyOriginal).toHaveAttribute('src', '/daily-thumb.png');
    expect(dailyOriginal).toHaveStyle({ opacity: '0' });
    expect(dailyPreview).not.toBeNull();
    fireEvent.load(dailyPreview as HTMLImageElement);
    await waitFor(() => expect(dailyPreview).toHaveStyle({ opacity: '1' }));
    fireEvent.load(dailyOriginal);
    await waitFor(() => expect(dailyOriginal).toHaveStyle({ opacity: '1' }));
    expect(
      screen.getByRole('button', { name: '选择日常形象' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '选择婚礼礼服' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    const weddingOriginal = screen.getByAltText('婚礼礼服预览图');
    const weddingPreview = mainPreview.querySelector<HTMLImageElement>(
      'img[src="/wedding-thumb.png"]',
    );
    expect(weddingOriginal).toHaveAttribute('src', '/wedding-thumb.png');
    expect(weddingOriginal).toHaveStyle({ opacity: '0' });
    expect(weddingPreview).not.toBeNull();
    fireEvent.load(weddingPreview as HTMLImageElement);
    await waitFor(() => expect(weddingPreview).toHaveStyle({ opacity: '1' }));
    fireEvent.load(weddingOriginal);
    await waitFor(() => expect(weddingOriginal).toHaveStyle({ opacity: '1' }));
    expect(screen.getAllByText('婚礼礼服').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('斌斌主要形态提示词')).toHaveValue('6岁男孩，写实都市风格');
    expect(screen.queryByRole('tab', { name: '当前变装' })).not.toBeInTheDocument();
    expect(screen.getByText('生成超时')).toBeInTheDocument();
    expect(screen.getByLabelText('婚礼礼服主预览生成状态')).toHaveTextContent(
      '生成失败',
    );
    expect(
      within(screen.getByLabelText('婚礼礼服主预览生成状态')).queryByRole(
        'progressbar',
      ),
    ).not.toBeInTheDocument();
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
      expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalledTimes(2);
      expect(mocks.queryAssetVisualWorkspace).toHaveBeenLastCalledWith(
        1,
        'CHARACTER',
        1,
        expect.objectContaining({ current: 1, pageSize: 20 }),
      );
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(2);
      expect(mocks.queryScriptWorkspace).not.toHaveBeenCalled();
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
    fireEvent.click(within(screen.getByRole('region', { name: '确认删除此形态？' })).getByRole('button', { name: '确认删除' }));
    await waitFor(() => {
      expect(mocks.deleteVisualVariant).toHaveBeenCalledWith(1, 12);
    });
  });

  it('confirms deletion, permits cancellation and refreshes without the deleted selection', async () => {
    const primary = workspace.characters[0].visual.variants[0];
    let deleted = false;
    let finishDelete!: () => void;
    mocks.deleteVisualVariant.mockImplementation(() => new Promise<void>((resolve) => {
      finishDelete = () => { deleted = true; resolve(); };
    }));
    mocks.queryAssetVisualWorkspace.mockImplementation((_project, _type, _asset, params = {}) => {
      if (deleted && params.selectedVariantId === 12) return Promise.reject(new Error('deleted selection'));
      return Promise.resolve({ data: deleted
        ? { ...workspace.characters[0].visual, variants: [primary], selectedVariant: primary, primaryVariant: primary }
        : workspace.characters[0].visual });
    });
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(await screen.findByRole('button', { name: '选择婚礼礼服' }));
    fireEvent.mouseEnter(screen.getByTestId('视觉形象缩略图-婚礼礼服'));
    fireEvent.click(screen.getByRole('button', { name: '删除婚礼礼服' }));
    let dialog = within(screen.getByRole('region', { name: '确认删除此形态？' }));
    expect(dialog.getByText('删除后，引用此形态的分镜将自动改用该人物的默认形象。')).toBeInTheDocument();
    expect(mocks.deleteVisualVariant).not.toHaveBeenCalled();
    fireEvent.click(dialog.getByRole('button', { name: '取消' }));
    expect(screen.queryByRole('region', { name: '确认删除此形态？' })).not.toBeInTheDocument();
    expect(mocks.deleteVisualVariant).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '删除婚礼礼服' }));
    dialog = within(screen.getByRole('region', { name: '确认删除此形态？' }));
    fireEvent.click(dialog.getByRole('button', { name: '确认删除' }));
    expect(dialog.getByRole('button', { name: '确认删除' })).toBeDisabled();
    await act(async () => finishDelete());
    await waitFor(() => expect(screen.queryByRole('button', { name: '选择婚礼礼服' })).not.toBeInTheDocument());
    expect(mocks.deleteVisualVariant).toHaveBeenCalledTimes(1);
    expect(mocks.queryAssetVisualWorkspace).toHaveBeenLastCalledWith(1, 'CHARACTER', 1,
      expect.objectContaining({ current: 1, selectedVariantId: undefined }));
    expect(screen.getByAltText('日常形象预览图')).toBeInTheDocument();
    expect(mocks.messageError).not.toHaveBeenCalled();
  });

  it('marks only virtual-avatar visual variants with the green virtual tag', async () => {
    (workspace.characters[0].visual.variants[0] as any).virtualAvatarAssetId = 'va_123';

    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));

    expect(screen.getAllByText('虚拟')).toHaveLength(1);
    expect(screen.getByTestId('视觉形象缩略图-日常形象')).toHaveTextContent('虚拟');
    expect(screen.getByTestId('视觉形象缩略图-婚礼礼服')).not.toHaveTextContent('虚拟');
  });

  it('pages visual candidates while keeping a selected variant outside the page', async () => {
    const primary = workspace.characters[0].visual.variants[0];
    const selected = { ...workspace.characters[0].visual.variants[1], id: 99, name: '历史礼服' };
    mocks.queryAssetVisualWorkspace.mockImplementation((_projectId, _type, _assetId, params = {}) =>
      Promise.resolve({ data: {
        ...workspace.characters[0].visual,
        variants: params.current === 2 ? [{ ...selected, id: 25, name: '第25形象' }] : [primary],
        primaryVariant: primary,
        selectedVariant: selected,
        current: params.current || 1,
        pageSize: 20,
        total: 25,
      } }));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    expect(await screen.findByRole('button', { name: '选择历史礼服' })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: '下一页' }));
    await waitFor(() => expect(mocks.queryAssetVisualWorkspace).toHaveBeenLastCalledWith(
      1, 'CHARACTER', 1,
      expect.objectContaining({ current: 2, pageSize: 20, selectedVariantId: 99 }),
    ));
    expect(screen.getByRole('button', { name: '选择历史礼服' })).toBeInTheDocument();
  });

  it('selects assets only within the active tab and disables generation without a selection', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    const generate = screen.getByRole('button', { name: '角色图生成' });
    expect(generate).toBeDisabled();
    expect(screen.getByText('已选择 0 项')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: '选择斌斌' }));
    expect(screen.getByText('已选择 1 项')).toBeInTheDocument();
    expect(generate).not.toBeDisabled();

    fireEvent.click(screen.getByRole('tab', { name: /场景/ }));
    expect(screen.getByText('已选择 0 项')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '场景图生成' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '生成所选场景主图' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '生成所选场景全部形态图' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /道具/ }));
    expect(screen.getByRole('button', { name: '道具图生成' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '生成所选道具主图' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '生成所选道具全部形态图' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /生成所选角色/ })).not.toBeInTheDocument();
  });

  it.each([
    ['角色', '斌斌', '斌'],
    ['场景', '地下停车场', '停车'],
    ['道具', '灰色轿车后备箱', '后备箱'],
  ])('filters %s names as the user types and restores the list when cleared', async (label, name, keyword) => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByRole('button', { name: '查看斌斌视觉形象' });
    fireEvent.click(screen.getByRole('tab', { name: new RegExp(label) }));
    const input = screen.getByRole('textbox', { name: '搜索资产' });
    fireEvent.change(input, { target: { value: `  ${keyword}  ` } });
    expect(screen.getByRole('button', { name: `查看${name}视觉形象` })).toBeInTheDocument();
    fireEvent.change(input, { target: { value: '不存在的资产' } });
    expect(screen.getByText(`未找到相关${label}`)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `查看${name}视觉形象` })).not.toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '全选搜索结果' })).toBeDisabled();
    fireEvent.change(input, { target: { value: '' } });
    expect(screen.getByRole('button', { name: `查看${name}视觉形象` })).toBeInTheDocument();
  });

  it('selects only search matches and clears hidden selections when the query or category changes', async () => {
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: {
      projectId: 1,
      characters: [...workspace.characters, { ...workspace.characters[0], id: 99, name: 'Alice', visual: undefined }],
      scenes: workspace.scenes, props: workspace.props,
    } });
    render(<ProductionWorkbenchSettings />);
    await screen.findByRole('checkbox', { name: '选择Alice' });
    fireEvent.click(screen.getByRole('checkbox', { name: '选择斌斌' }));
    const input = screen.getByRole('textbox', { name: '搜索资产' });
    fireEvent.change(input, { target: { value: ' alice ' } });
    expect(screen.getByText('已选择 0 项')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: '选择斌斌' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: '全选搜索结果' }));
    expect(screen.getByRole('checkbox', { name: '选择Alice' })).toBeChecked();
    expect(screen.getByText('已选择 1 项')).toBeInTheDocument();
    fireEvent.change(input, { target: { value: '' } });
    expect(screen.getByRole('checkbox', { name: '选择Alice' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: '选择斌斌' })).not.toBeChecked();
    fireEvent.change(input, { target: { value: 'alice' } });
    fireEvent.click(screen.getByRole('tab', { name: /场景/ }));
    expect(input).toHaveValue('');
    expect(screen.getByRole('button', { name: '查看地下停车场视觉形象' })).toBeInTheDocument();
    expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(1);
  });

  it('consolidates extraction actions and confirms selected primary image generation', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    expect(
      screen.getByRole('button', { name: '资产提取' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '提取当前分类' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '提取全部资产' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: '选择斌斌' }));
    fireEvent.click(screen.getByRole('button', { name: '生成所选角色主图' }));

    await waitFor(() => {
      expect(mocks.queryAssetImageBatchPreflight).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          assetType: 'CHARACTER',
          assetIds: [1],
          mode: 'PRIMARY',
          modelId: 8,
          aspectRatio: '16:9',
          imageCount: 1,
        }),
      );
    });
    expect(
      screen.getByRole('region', { name: '批量生成角色图' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/已选择 1 个角色，计划生成 1 个形态，共 1 张图片/),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '确认提交' }));
    await waitFor(() => {
      expect(mocks.createAssetImageBatch).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          assetType: 'CHARACTER',
          assetIds: [1],
          mode: 'PRIMARY',
          aspectRatio: '16:9',
          imageCount: 1,
        }),
        expect.any(String),
      );
    });
    expect(screen.getByText('已选择 0 项')).toBeInTheDocument();
    await waitFor(() => {
      expect(mocks.queryAssetImageBatch).toHaveBeenCalledWith(1, 91);
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(2);
    });
  });

  it('shows failed image reasons and retries only the failed batch items', async () => {
    const failedBatch = {
      id: 91, projectId: 1, assetType: 'CHARACTER', mode: 'ALL',
      status: 'COMPLETED_WITH_FAILURES', total: 2, pending: 0, running: 0,
      succeeded: 1, failed: 1, skipped: 0, modelId: 8,
      aspectRatio: '16:9', imageCount: 1,
      items: [
        { id: 1, assetId: 1, variantId: 11, variantName: '日常形象', status: 'SUCCEEDED' },
        { id: 2, assetId: 1, variantId: 12, variantName: '婚礼礼服', status: 'FAILED', errorMessage: '生成超时' },
      ],
    };
    mocks.createAssetImageBatch.mockResolvedValue({ data: failedBatch });
    mocks.queryAssetImageBatch.mockResolvedValue({ data: failedBatch });
    mocks.retryAssetImageBatch.mockResolvedValue({ data: { ...failedBatch, id: 92, status: 'PENDING' } });
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('checkbox', { name: '选择斌斌' }));
    fireEvent.click(screen.getByRole('button', { name: '生成所选角色主图' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认提交' }));

    expect(await screen.findByText('婚礼礼服：生成超时')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试失败资产图' }));
    await waitFor(() => expect(mocks.retryAssetImageBatch).toHaveBeenCalledWith(1, 91));
  });

  it('uses unified image placeholders for empty cards and gallery images', async () => {
    (workspace.characters[0] as any).mainImageThumbnailUrl = undefined;
    (workspace.characters[0].visual.variants[0] as any).currentImageUrl =
      undefined;
    (
      workspace.characters[0].visual.variants[0] as any
    ).currentImageThumbnailUrl = undefined;
    (workspace.characters[0].visual.variants[1] as any).currentImageUrl =
      undefined;
    (
      workspace.characters[0].visual.variants[1] as any
    ).currentImageThumbnailUrl = undefined;

    render(<ProductionWorkbenchSettings />);

    const assetImage = await screen.findByTestId('asset-image-CHARACTER-1');
    expect(
      within(assetImage).getByRole('img', { name: '暂无图片' }),
    ).toHaveTextContent('暂无图片');
    fireEvent.mouseEnter(assetImage);
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));

    expect(
      within(screen.getByTestId('视觉形象主图预览')).getByRole('img', {
        name: '暂无图片',
      }),
    ).toHaveTextContent('暂无图片');
    expect(
      within(screen.getByTestId('视觉形象缩略图-日常形象')).getByRole('img', {
        name: '暂无图片',
      }),
    ).not.toHaveTextContent('暂无图片');
    expect(
      within(screen.getByTestId('视觉形象缩略图-婚礼礼服')).getByRole('img', {
        name: '暂无图片',
      }),
    ).not.toHaveTextContent('暂无图片');
  });

  it('shows generation progress from the lightweight summary when the asset has no image', async () => {
    const snapshot = structuredClone(workspace) as unknown as AssetSettingsWorkspace;
    const asset = snapshot.characters[0];
    delete asset.visual;
    asset.mainImageThumbnailUrl = null;
    asset.imagePreview = { generationStatus: 'GENERATING', pendingImageThumbnailUrl: null };
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: snapshot });
    render(<ProductionWorkbenchSettings />);
    expect(await screen.findByLabelText('斌斌视觉形象生成状态')).toHaveTextContent('生成中');
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
  });

  it('shows a pending image on an empty cover and refreshes without opening the gallery', async () => {
    const snapshot = structuredClone(workspace) as unknown as AssetSettingsWorkspace;
    const asset = snapshot.characters[0];
    delete asset.visual;
    asset.mainImageThumbnailUrl = null;
    asset.imagePreview = { generationStatus: 'GENERATING', pendingImageThumbnailUrl: null };
    mocks.queryAssetSettingsSummary.mockImplementation(async () => ({ data: structuredClone(snapshot) }));
    vi.useFakeTimers();
    await act(async () => { render(<ProductionWorkbenchSettings />); });
    expect(screen.getByLabelText('斌斌视觉形象生成状态')).toHaveTextContent('生成中');
    asset.imagePreview = { generationStatus: 'COMPLETED', pendingImageThumbnailUrl: '/pending-thumb.png' };
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    const cover = screen.getByTestId('asset-image-CHARACTER-1');
    expect(within(cover).getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/pending-thumb.png');
    expect(within(cover).getByText('待确认')).toBeInTheDocument();
    expect(screen.queryByLabelText('斌斌视觉形象生成状态')).not.toBeInTheDocument();
    const calls = mocks.queryAssetSettingsSummary.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(calls);
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
    expect(mocks.selectAiImageResult).not.toHaveBeenCalled();
  });

  it.each([
    { key: 'characters' as const, name: '斌斌', tab: '角色', type: 'CHARACTER' },
    { key: 'scenes' as const, name: '地下停车场', tab: '场景', type: 'SCENE' },
    { key: 'props' as const, name: '灰色轿车后备箱', tab: '道具', type: 'PROP' },
  ])('shows an already generated $type cover immediately with a pending confirmation label', async ({ key, name, tab, type }) => {
    const snapshot = structuredClone(workspace) as unknown as AssetSettingsWorkspace;
    const asset = snapshot[key][0];
    delete asset.visual;
    asset.mainImageThumbnailUrl = null;
    asset.status = 'CONFIRMED';
    asset.imagePreview = { generationStatus: 'COMPLETED', pendingImageThumbnailUrl: '/pending-thumb.png' };
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: snapshot });
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    fireEvent.click(screen.getByRole('tab', { name: new RegExp(`^${tab}`) }));
    await waitFor(() => expect(screen.getByAltText(`${name}当前视觉形象`)).toHaveAttribute('src', '/pending-thumb.png'));
    expect(within(screen.getByTestId(`asset-image-${type}-${asset.id}`)).getByText('待确认')).toBeInTheDocument();
    expect(screen.queryByLabelText(`${name}视觉形象生成状态`)).not.toBeInTheDocument();
    expect(mocks.queryAssetVisualWorkspace).not.toHaveBeenCalled();
    expect(mocks.selectAiImageResult).not.toHaveBeenCalled();
  });

  it('prefers the formal cover over a pending image and never obscures it with generation progress', async () => {
    const snapshot = structuredClone(workspace) as unknown as AssetSettingsWorkspace;
    const asset = snapshot.characters[0];
    delete asset.visual;
    asset.imagePreview = { generationStatus: 'GENERATING', pendingImageThumbnailUrl: '/pending-thumb.png' };
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: snapshot });
    render(<ProductionWorkbenchSettings />);
    await waitFor(() => expect(screen.getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/daily-thumb.png'));
    expect(screen.queryByLabelText('斌斌视觉形象生成状态')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('asset-image-CHARACTER-1')).getByText('已确认')).toBeInTheDocument();
  });

  it('keeps a pending cover visible when another generation is running', async () => {
    const snapshot = structuredClone(workspace) as unknown as AssetSettingsWorkspace;
    const asset = snapshot.characters[0];
    delete asset.visual;
    asset.mainImageThumbnailUrl = null;
    asset.imagePreview = { generationStatus: 'GENERATING', pendingImageThumbnailUrl: '/pending-thumb.png' };
    mocks.queryAssetSettingsSummary.mockResolvedValue({ data: snapshot });
    render(<ProductionWorkbenchSettings />);
    await waitFor(() => expect(screen.getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/pending-thumb.png'));
    expect(screen.queryByLabelText('斌斌视觉形象生成状态')).not.toBeInTheDocument();
    expect(within(screen.getByTestId('asset-image-CHARACTER-1')).getByText('待确认')).toBeInTheDocument();
  });

  it('edits only the primary prompt when a wardrobe is selected and reuses the saved prompt for redraw', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    await screen.findByRole('button', { name: '选择婚礼礼服' });
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    const editor = within(screen.getByRole('region', { name: '角色提示词' }));
    const prompt = editor.getByLabelText('斌斌主要形态提示词');
    fireEvent.click(editor.getByRole('button', { name: '编辑主要形态提示词' }));
    fireEvent.change(prompt, { target: { value: '取消的修改' } });
    fireEvent.click(editor.getByRole('button', { name: '取消' }));
    expect(prompt).toHaveValue('6岁男孩，写实都市风格');
    expect(mocks.updateVisualVariant).not.toHaveBeenCalled();
    fireEvent.click(editor.getByRole('button', { name: '编辑主要形态提示词' }));
    fireEvent.change(prompt, { target: { value: '6岁男孩，圆脸，短发，写实都市风格' } });
    fireEvent.click(editor.getByRole('button', { name: '保存' }));
    await waitFor(() => expect(prompt).toHaveAttribute('readonly'));
    expect(mocks.updateVisualVariant).toHaveBeenCalledWith(1, 11, {
      name: '日常形象', appearance: undefined, prompt: '6岁男孩，圆脸，短发，写实都市风格',
    });
    expect(screen.getByRole('button', { name: '选择婚礼礼服' })).toHaveAttribute('aria-pressed', 'true');
    expect(prompt).toHaveValue('6岁男孩，圆脸，短发，写实都市风格');
    fireEvent.click(screen.getByRole('button', { name: '选择日常形象' }));
    fireEvent.click(screen.getByRole('button', { name: '生成图片日常形象' }));
    expect(await screen.findByLabelText('日常形象生成提示词')).toHaveValue('6岁男孩，圆脸，短发，写实都市风格');
    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
  });

  it('ignores a late prompt save after closing the gallery', async () => {
    let finishSave!: (value: { data: object }) => void;
    mocks.updateVisualVariant.mockReturnValueOnce(new Promise((resolve) => { finishSave = resolve; }));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    await screen.findByRole('button', { name: '选择婚礼礼服' });
    fireEvent.click(screen.getByRole('button', { name: '编辑主要形态提示词' }));
    fireEvent.change(screen.getByLabelText('斌斌主要形态提示词'), { target: { value: '旧请求提示词' } });
    fireEvent.click(within(screen.getByRole('region', { name: '角色提示词' })).getByRole('button', { name: '保存' }));
    fireEvent.click(screen.getByRole('button', { name: '关闭视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    await screen.findByRole('button', { name: '选择婚礼礼服' });
    fireEvent.click(screen.getByRole('button', { name: '编辑主要形态提示词' }));
    fireEvent.change(screen.getByLabelText('斌斌主要形态提示词'), { target: { value: '新窗口草稿' } });
    await act(async () => { finishSave({ data: {} }); });
    expect(screen.getByLabelText('斌斌主要形态提示词')).toHaveValue('新窗口草稿');
    expect(screen.getByLabelText('斌斌主要形态提示词')).not.toHaveAttribute('readonly');
  });

  it('shows generation statuses in the gallery without covering an existing asset image', async () => {
    workspace.characters[0].visual.variants[1].generationStatus = 'GENERATING';
    workspace.characters[0].visual.generationSummary.GENERATING = 1;
    workspace.characters[0].visual.generationSummary.FAILED = 0;

    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    expect(screen.queryByLabelText('斌斌视觉形象生成状态')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/daily-thumb.png'));
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));

    expect(
      screen.queryByLabelText('日常形象主预览生成状态'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText('日常形象缩略图生成状态'),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText('婚礼礼服缩略图生成状态')).toHaveTextContent(
      '生成中',
    );
    expect(
      within(screen.getByLabelText('婚礼礼服缩略图生成状态')).getByRole(
        'progressbar',
      ),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    expect(screen.getByLabelText('婚礼礼服主预览生成状态')).toHaveTextContent(
      '生成中',
    );
    expect(
      within(screen.getByLabelText('婚礼礼服主预览生成状态')).getByRole(
        'progressbar',
      ),
    ).toBeInTheDocument();
  });

  it('opens a separate generator and saves the changed prompt before submitting', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    fireEvent.click(screen.getByRole('button', { name: '重新生成婚礼礼服' }));
    expect(screen.getByLabelText('婚礼礼服引用图')).toHaveAttribute(
      'src',
      '/daily-thumb.png',
    );
    await screen.findByRole('option', { name: 'GPT Image 2' });
    fireEvent.change(screen.getByLabelText('图片模型'), {
      target: { value: '8' },
    });
    fireEvent.change(screen.getByLabelText('婚礼礼服生成提示词'), {
      target: { value: '婚礼礼服，电影感' },
    });
    const preview = recordedPointEstimates().flatMap((call) => call.operations || [])
      .filter((operation) => operation.url === '/api/projects/1/ai-image-tasks').at(-1);
    expect(preview?.data).toMatchObject({ targetId: 12, modelId: 8, prompt: '婚礼礼服，电影感', referenceImages: ['/daily.png'] });
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
          aspectRatio: '16:9',
          imageCount: 1,
        }),
      );
    });
    expect(preview?.data).toEqual(mocks.createAiImageTask.mock.calls[0][1]);
  });

  it('submits the existing prompt without an unnecessary visual variant update', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '生成图片日常形象' }));
    fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));

    await waitFor(() => {
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          targetId: 11,
          prompt: '6岁男孩，写实都市风格',
        }),
      );
    });
    expect(mocks.updateVisualVariant).not.toHaveBeenCalled();
  });

  const imageGenerationSnapshot = (status: string) => {
    const next = structuredClone(workspace);
    const asset = next.characters[0];
    asset.visual.variants[0].generationStatus = status;
    asset.visual.primaryVariant.generationStatus = status;
    asset.visual.generationSummary.GENERATING = status === 'GENERATING' ? 1 : 0;
    if (status === 'COMPLETED') {
      asset.mainImageThumbnailUrl = '/new-daily-thumb.png';
      asset.visual.variants[0].currentImageThumbnailUrl = '/new-daily-thumb.png';
      asset.visual.primaryVariant.currentImageThumbnailUrl = '/new-daily-thumb.png';
    }
    return next;
  };

  const submitPrimaryImage = async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(await screen.findByRole('button', { name: '生成图片日常形象' }));
    vi.useFakeTimers();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));
    });
  };

  it.each(['COMPLETED', 'FAILED'])('refreshes a single image generation to %s and stops polling', async (terminalStatus) => {
    let snapshot = structuredClone(workspace);
    mocks.queryAssetSettingsSummary.mockImplementation(async () => ({ data: snapshot }));
    mocks.queryAssetVisualWorkspace.mockImplementation(async () => ({ data: snapshot.characters[0].visual }));
    mocks.createAiImageTask.mockImplementation(async () => {
      snapshot = imageGenerationSnapshot('GENERATING');
      return { data: { id: 80, executionId: 800, status: 'PENDING' } };
    });
    await submitPrimaryImage();
    expect(screen.getByLabelText('日常形象主预览生成状态')).toHaveTextContent('生成中');

    snapshot = imageGenerationSnapshot(terminalStatus);
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });

    if (terminalStatus === 'COMPLETED') {
      expect(screen.queryByLabelText('日常形象主预览生成状态')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('日常形象缩略图生成状态')).not.toBeInTheDocument();
      expect(screen.getByAltText('日常形象预览图')).toHaveAttribute('src', '/new-daily-thumb.png');
      expect(screen.getByAltText('日常形象缩略图')).toHaveAttribute('src', '/new-daily-thumb.png');
      expect(screen.getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/new-daily-thumb.png');
    } else {
      expect(screen.getByLabelText('日常形象主预览生成状态')).toHaveTextContent('生成失败');
      expect(within(screen.getByLabelText('日常形象主预览生成状态')).queryByRole('progressbar')).not.toBeInTheDocument();
    }
    const summaryCalls = mocks.queryAssetSettingsSummary.mock.calls.length;
    const detailCalls = mocks.queryAssetVisualWorkspace.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(summaryCalls);
    expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalledTimes(detailCalls);
    expect(mocks.createAiImageTask).toHaveBeenCalledTimes(1);
  });

  it('keeps refreshing after a temporary single image status request failure', async () => {
    let snapshot = structuredClone(workspace);
    mocks.queryAssetSettingsSummary.mockImplementation(async () => ({ data: snapshot }));
    mocks.queryAssetVisualWorkspace.mockImplementation(async () => ({ data: snapshot.characters[0].visual }));
    mocks.createAiImageTask.mockImplementation(async () => {
      snapshot = imageGenerationSnapshot('GENERATING');
      return { data: { id: 80, status: 'PENDING' } };
    });
    await submitPrimaryImage();
    snapshot = imageGenerationSnapshot('COMPLETED');
    mocks.queryAssetSettingsSummary.mockRejectedValueOnce(new Error('offline'));
    mocks.queryAssetVisualWorkspace.mockRejectedValueOnce(new Error('offline'));
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.getByLabelText('日常形象主预览生成状态')).toHaveTextContent('生成中');
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.queryByLabelText('日常形象主预览生成状态')).not.toBeInTheDocument();
    expect(screen.getByAltText('日常形象预览图')).toHaveAttribute('src', '/new-daily-thumb.png');
    expect(mocks.createAiImageTask).toHaveBeenCalledTimes(1);
  });

  it('resumes refreshing an image already generating when the gallery opens', async () => {
    let snapshot = imageGenerationSnapshot('GENERATING');
    mocks.queryAssetSettingsSummary.mockImplementation(async () => ({ data: snapshot }));
    mocks.queryAssetVisualWorkspace.mockImplementation(async () => ({ data: snapshot.characters[0].visual }));
    vi.useFakeTimers();
    await act(async () => { render(<ProductionWorkbenchSettings />); });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    });
    expect(screen.getByLabelText('日常形象主预览生成状态')).toHaveTextContent('生成中');
    snapshot = imageGenerationSnapshot('COMPLETED');
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.queryByLabelText('日常形象主预览生成状态')).not.toBeInTheDocument();
    expect(screen.getByAltText('日常形象预览图')).toHaveAttribute('src', '/new-daily-thumb.png');
    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
  });

  it('updates the asset card without reopening a gallery closed during status refresh', async () => {
    let snapshot = structuredClone(workspace);
    mocks.queryAssetSettingsSummary.mockImplementation(async () => ({ data: snapshot }));
    mocks.queryAssetVisualWorkspace.mockImplementation(async () => ({ data: snapshot.characters[0].visual }));
    mocks.createAiImageTask.mockImplementation(async () => {
      snapshot = imageGenerationSnapshot('GENERATING');
      return { data: { id: 80, status: 'PENDING' } };
    });
    await submitPrimaryImage();
    let resolveDetail!: (value: unknown) => void;
    mocks.queryAssetVisualWorkspace.mockImplementationOnce(() => new Promise((resolve) => { resolveDetail = resolve; }));
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(resolveDetail).toBeDefined();
    fireEvent.click(within(screen.getByRole('region', { name: '视觉形象画廊' })).getByRole('button', { name: '关闭视觉形象' }));
    snapshot = imageGenerationSnapshot('COMPLETED');
    await act(async () => { resolveDetail({ data: snapshot.characters[0].visual }); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.queryByRole('region', { name: '视觉形象画廊' })).not.toBeInTheDocument();
    expect(screen.getByAltText('斌斌当前视觉形象')).toHaveAttribute('src', '/new-daily-thumb.png');
  });

  it('saves an edited primary prompt through the effective visual update path', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '生成图片日常形象' }));
    expect(screen.getByLabelText('日常形象生成提示词')).toHaveValue(
      '6岁男孩，写实都市风格',
    );
    fireEvent.change(screen.getByLabelText('日常形象生成提示词'), {
      target: { value: '6岁男孩主体角色设定图' },
    });
    fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));

    await waitFor(() => {
      expect(mocks.updateVisualVariant).toHaveBeenCalledWith(
        1,
        11,
        expect.objectContaining({ prompt: '6岁男孩主体角色设定图' }),
      );
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          targetId: 11,
          prompt: '6岁男孩主体角色设定图',
          referenceImages: undefined,
        }),
      );
    });
  });

  it('blocks visual generation when the persisted prompt is empty', async () => {
    workspace.characters[0].visual.variants[1].prompt = '';
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '选择婚礼礼服' }));
    fireEvent.click(screen.getByRole('button', { name: '重新生成婚礼礼服' }));
    fireEvent.click(screen.getByRole('button', { name: '提交婚礼礼服生成' }));

    await waitFor(() => {
      expect(mocks.createAiImageTask).not.toHaveBeenCalled();
      expect(mocks.messageError).toHaveBeenCalledWith(
        '请先完成视觉形象提示词后再生成图片。',
      );
    });
  });

  it('blocks primary generation with a canonical asset prompt message', async () => {
    workspace.characters[0].visual.variants[0].prompt = '';
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    fireEvent.click(screen.getByRole('button', { name: '生成图片日常形象' }));
    fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));

    await waitFor(() => {
      expect(mocks.createAiImageTask).not.toHaveBeenCalled();
      expect(mocks.messageError).toHaveBeenCalledWith(
        '请先完成资产主体提示词后再生成图片。',
      );
    });
  });
  it('renders the reference-style asset workbench instead of the old image task table', async () => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByRole('button', { name: '斌斌资产操作' });
    expect(
      await screen.findByText(/请确保需要独立制作的角色、场景及道具已生成。/),
    ).toBeInTheDocument();
    expect(screen.queryByText('正式资产库')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '提示词相似度检查' })).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole('button', { name: '提取当前分类' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认提交' }));
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '保存斌斌' }));

    await waitFor(() => {
      expect(mocks.queryAssetReextractionPreflight).toHaveBeenCalledWith(
        1,
        'CHARACTER',
      );
      expect(mocks.submitAssetReextraction).toHaveBeenCalledWith(1, {
        targetType: 'CHARACTER',
        promptPolicy: 'FILL_EMPTY',
      });
      expect(mocks.updateScriptElement).toHaveBeenCalledWith(
        1,
        'CHARACTER',
        1,
        expect.objectContaining({ name: '斌斌' }),
      );
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        10,
        601,
        expect.any(Function),
        1500,
        expect.any(AbortSignal),
      );
      expect(
        mocks.queryAssetSettingsSummary.mock.calls.length,
      ).toBeGreaterThanOrEqual(2);
    });
    expect(screen.getByText('execution-601-SUCCEEDED')).toBeInTheDocument();
  });

  it('offers read-only retry when saving succeeds but refresh fails', async () => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    mocks.queryAssetSettingsSummary.mockRejectedValueOnce(new Error('offline'));
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '保存斌斌' }));
    await waitFor(() =>
      expect(mocks.messageError).toHaveBeenCalledWith(
        '操作已完成，但设定刷新失败，请重试加载',
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('斌斌');
    expect(mocks.updateScriptElement).toHaveBeenCalledTimes(1);
  });

  it('ignores a late asset refresh after a newer save refresh completes', async () => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    let finishOld!: (value: unknown) => void;
    mocks.queryAssetSettingsSummary.mockReturnValueOnce(
      new Promise((resolve) => {
        finishOld = resolve;
      }),
    );
    fireEvent.mouseEnter(screen.getByTestId('asset-image-CHARACTER-1'));
    fireEvent.click(screen.getByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '保存斌斌' }));
    await waitFor(() =>
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(2),
    );
    mocks.queryAssetSettingsSummary.mockResolvedValueOnce({
      data: {
        projectId: 1,
        characters: [{ id: 1, name: '最新角色' }],
        scenes: [],
        props: [],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存斌斌' }));
    await screen.findByText('最新角色');
    finishOld({
      data: {
        projectId: 1,
        characters: [{ id: 1, name: '过期角色' }],
        scenes: [],
        props: [],
      },
    });
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByText('过期角色')).not.toBeInTheDocument();
    expect(screen.getByText('最新角色')).toBeInTheDocument();
  });

  it('requires confirmation before replacing existing scoped prompts', async () => {
    mocks.queryAssetReextractionPreflight.mockResolvedValueOnce({
      data: {
        targetType: 'CHARACTER',
        existingAssets: 1,
        existingVariants: 2,
        existingPrompts: 3,
        requiresConfirmation: true,
      },
    });
    render(<ProductionWorkbenchSettings />);

    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '提取当前分类' }));
    expect(
      await screen.findByText(/当前范围已有 1 个资产/),
    ).toBeInTheDocument();
    expect(mocks.submitAssetReextraction).not.toHaveBeenCalled();
    fireEvent.click(
      screen.getByRole('button', { name: '重新生成当前范围提示词' }),
    );
    fireEvent.click(screen.getByRole('button', { name: '确认提交' }));

    await waitFor(() => {
      expect(mocks.submitAssetReextraction).toHaveBeenCalledWith(1, {
        targetType: 'CHARACTER',
        promptPolicy: 'REGENERATE_ALL',
      });
    });
  });

  it('follows a conflicting task without submitting another paid task', async () => {
    mocks.submitAssetReextraction.mockRejectedValueOnce({
      response: {
        data: {
          errorCode: 'ASSET_EXTRACTION_CONFLICT',
          data: { executionId: 700 },
        },
      },
    });
    mocks.pollExecution.mockResolvedValueOnce({ id: 700, status: 'SUCCEEDED' });
    render(<ProductionWorkbenchSettings />);
    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '提取当前分类' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认提交' }));
    await screen.findByText('execution-700-SUCCEEDED');
    expect(screen.getByText(/本次未创建新任务/)).toBeInTheDocument();
    expect(mocks.submitAssetReextraction).toHaveBeenCalledTimes(1);
  });

  it('blocks repeated clicks while the existing execution is being followed', async () => {
    mocks.pollExecution.mockReturnValueOnce(new Promise(() => {}));
    render(<ProductionWorkbenchSettings />);
    await screen.findAllByText('斌斌');
    const button = screen.getByRole('button', { name: '提取当前分类' });
    fireEvent.click(button);
    fireEvent.click(await screen.findByRole('button', { name: '确认提交' }));
    await waitFor(() => expect(button).toBeDisabled());
    fireEvent.click(button);
    fireEvent.click(screen.getByRole('button', { name: '提取全部资产' }));
    expect(mocks.submitAssetReextraction).toHaveBeenCalledTimes(1);
  });

  it('keeps asset categories browsable while normalization blocks changes', async () => {
    render(<ProductionWorkbenchSettings />);
    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '模拟归一处理中' }));
    const sceneTab = screen.getByRole('tab', { name: /场景/ });
    expect(sceneTab).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '角色图生成' })).toBeDisabled();
    fireEvent.click(sceneTab);
    expect(sceneTab).toHaveAttribute('aria-selected', 'true');
  });

  it.each([
    ['模拟归一处理中', '正在提取或归一资产'],
    ['模拟归一失败', '资产归一失败，请重试。'],
    ['模拟状态读取失败', '状态读取失败，正在自动重试。'],
  ])('keeps gallery navigation available during %s and preserves selection on recovery', async (trigger, reason) => {
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    await waitFor(() => expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '新增变装' }));
    fireEvent.click(screen.getByRole('button', { name: trigger }));
    const gallery = within(screen.getByRole('region', { name: '视觉形象画廊' }));
    expect(gallery.getByRole('status', { name: '视觉形象操作提示' })).toHaveTextContent(reason);
    expect(gallery.getByRole('button', { name: '编辑主要形态提示词' })).toBeDisabled();
    expect(gallery.getByLabelText('新视觉形象名称')).toBeDisabled();
    expect(gallery.getByRole('button', { name: '确认新增视觉形象' })).toBeDisabled();
    expect(gallery.getByRole('button', { name: '选择婚礼礼服' })).toBeEnabled();
    fireEvent.click(gallery.getByRole('button', { name: '选择婚礼礼服' }));
    expect(gallery.getByRole('button', { name: '选择婚礼礼服' })).toHaveAttribute('aria-pressed', 'true');
    for (const name of ['重新生成婚礼礼服', '删除婚礼礼服', '设为主形象', /本地上传/]) {
      expect(gallery.getByRole('button', { name })).toBeDisabled();
    }
    expect(gallery.getByRole('button', { name: '查看下一个视觉形象' })).toBeEnabled();
    fireEvent.click(gallery.getByRole('button', { name: '查看下一个视觉形象' }));
    expect(gallery.getByRole('button', { name: '选择日常形象' })).toHaveAttribute('aria-pressed', 'true');
    expect(gallery.getByRole('button', { name: '查看上一个视觉形象' })).toBeEnabled();
    fireEvent.click(gallery.getByRole('button', { name: '查看上一个视觉形象' }));
    expect(gallery.getByRole('button', { name: '选择婚礼礼服' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '模拟归一恢复' }));
    expect(gallery.queryByRole('status', { name: '视觉形象操作提示' })).not.toBeInTheDocument();
    expect(gallery.getByRole('button', { name: '选择婚礼礼服' })).toHaveAttribute('aria-pressed', 'true');
    expect(gallery.getByRole('button', { name: '重新生成婚礼礼服' })).toBeEnabled();
    expect(gallery.getByRole('button', { name: /本地上传/ })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: trigger }));
    fireEvent.click(gallery.getByRole('button', { name: '关闭视觉形象' }));
    expect(screen.queryByRole('region', { name: '视觉形象画廊' })).not.toBeInTheDocument();
  });

  it('only blocks changes to the generating variant and keeps the selected thumbnail visible', async () => {
    workspace.characters[0].visual.variants[1].generationStatus = 'GENERATING';
    render(<ProductionWorkbenchSettings />);
    await screen.findByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '查看斌斌视觉形象' }));
    await waitFor(() => expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalled());
    const viewport = screen.getByLabelText('视觉形象缩略图列表');
    const daily = screen.getByRole('button', { name: '选择日常形象' });
    const wedding = screen.getByRole('button', { name: '选择婚礼礼服' });
    vi.spyOn(viewport, 'getBoundingClientRect').mockReturnValue({ left: 0, right: 180 } as DOMRect);
    vi.spyOn(wedding, 'getBoundingClientRect').mockReturnValue({ left: 200, right: 328 } as DOMRect);
    vi.spyOn(daily, 'getBoundingClientRect').mockImplementation(() => ({ left: -viewport.scrollLeft, right: 128 - viewport.scrollLeft }) as DOMRect);
    fireEvent.click(screen.getByRole('button', { name: '查看下一个视觉形象' }));
    expect(wedding).toHaveAttribute('aria-pressed', 'true');
    expect(viewport.scrollLeft).toBe(148);
    expect(screen.getByRole('button', { name: '生成图片婚礼礼服' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '编辑主要形态提示词' })).toBeEnabled();
    expect(screen.getByRole('button', { name: /本地上传/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '删除婚礼礼服' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '删除日常形象' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '新增变装' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: '查看下一个视觉形象' }));
    expect(daily).toHaveAttribute('aria-pressed', 'true');
    expect(viewport.scrollLeft).toBe(0);
    expect(screen.getByRole('button', { name: '生成图片日常形象' })).toBeEnabled();
    expect(screen.getByRole('button', { name: /本地上传/ })).toBeEnabled();
  });

  it('shows the asset menu without hovering and deletes only after impact confirmation', async () => {
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '删除斌斌' }));
    const dialog = await screen.findByRole('region', { name: '删除斌斌' });
    expect(mocks.queryAssetDeletionImpact).toHaveBeenCalledWith(1, 'CHARACTER', 1);
    expect(dialog).toHaveTextContent('该资产被 5 个分镜引用');
    expect(dialog).toHaveTextContent('提示词引用转为普通文字');
    expect(dialog).toHaveTextContent('已有图片、视频将保留');
    expect(mocks.deleteScriptElement).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: '删除并保留文字' }));
    await waitFor(() => expect(mocks.deleteScriptElement).toHaveBeenCalledExactlyOnceWith(1, 'CHARACTER', 1));
    await waitFor(() => expect(screen.queryByRole('region', { name: '删除斌斌' })).not.toBeInTheDocument());
  });

  it('allows cancelling deletion without sending a delete request', async () => {
    mocks.queryAssetDeletionImpact.mockResolvedValueOnce({ data: { assetName: '斌斌', affectedStoryboardCount: 0, promptMentionCount: 0 } });
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '删除斌斌' }));
    const dialog = await screen.findByRole('region', { name: '删除斌斌' });
    expect(dialog).toHaveTextContent('该资产目前没有分镜引用');
    fireEvent.click(within(dialog).getByRole('button', { name: '取消' }));
    expect(mocks.deleteScriptElement).not.toHaveBeenCalled();
    expect(screen.queryByRole('region', { name: '删除斌斌' })).not.toBeInTheDocument();
  });

  it('does not offer deletion when the impact lookup fails', async () => {
    mocks.queryAssetDeletionImpact.mockRejectedValueOnce(new Error('影响读取失败'));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '删除斌斌' }));
    await waitFor(() => expect(mocks.messageError).toHaveBeenCalledWith('影响读取失败'));
    expect(mocks.deleteScriptElement).not.toHaveBeenCalled();
    expect(screen.queryByRole('region', { name: '删除斌斌' })).not.toBeInTheDocument();
  });

  it('keeps confirmation open after a failed deletion and prevents duplicate submission', async () => {
    let rejectDeletion!: (error: Error) => void;
    mocks.deleteScriptElement.mockReturnValueOnce(new Promise((_resolve, reject) => { rejectDeletion = reject; }));
    render(<ProductionWorkbenchSettings />);
    fireEvent.click(await screen.findByRole('button', { name: '斌斌资产操作' }));
    fireEvent.click(screen.getByRole('button', { name: '删除斌斌' }));
    const dialog = await screen.findByRole('region', { name: '删除斌斌' });
    const confirm = within(dialog).getByRole('button', { name: '删除并保留文字' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(mocks.deleteScriptElement).toHaveBeenCalledTimes(1);
    await act(async () => rejectDeletion(new Error('删除未完成')));
    expect(dialog).toBeInTheDocument();
    expect(confirm).toBeEnabled();
    expect(mocks.messageError).toHaveBeenCalledWith('删除未完成');
  });

  it('shows the terminal backend failure reason', async () => {
    mocks.pollExecution.mockResolvedValueOnce({
      id: 601,
      businessId: 41,
      status: 'FAILED',
      progress: 100,
      errorMessage: '当前资产识别范围不允许提交 scenes。',
    });
    render(<ProductionWorkbenchSettings />);

    await screen.findAllByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: '提取当前分类' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认提交' }));

    await waitFor(() => {
      expect(mocks.messageError).toHaveBeenCalledWith(
        '当前资产识别范围不允许提交 scenes。',
      );
    });
  });
});
