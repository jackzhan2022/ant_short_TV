import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench, { preserveReferenceClientKeys } from './storyboard';
import {
  imageFor,
  reorderReferencesWithinType,
  sortableIdFor,
} from './StoryboardAssetReferenceEditor';
import type { ScriptEpisode } from './service';

const mocks = vi.hoisted(() => ({
  queryProject: vi.fn(),
  queryProjectAiModels: vi.fn(),
  queryProjectAiConfig: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryScriptPageWorkspace: vi.fn(),
  queryAssetSettingsSummary: vi.fn(),
  queryAssetVisualWorkspace: vi.fn(),
  queryStoryboardWorkspace: vi.fn(),
  queryAiImageTasks: vi.fn(),
  queryAiImageTask: vi.fn(),
  selectAiImageResult: vi.fn(),
  createAiImageTask: vi.fn(),
  regenerateAiImageTask: vi.fn(),
  cancelAiImageTask: vi.fn(),
  queryAiVideoTasks: vi.fn(),
  bindAiVideoResultToStoryboard: vi.fn(),
  queryAiVoiceTasks: vi.fn(),
  createAiVoiceTask: vi.fn(),
  createAiVideoTask: vi.fn(),
  cancelAiVideoTask: vi.fn(),
  regenerateAiVideoTask: vi.fn(),
  replaceStoryboardAssetReferences: vi.fn(),
  createAssetImageBatch: vi.fn(),
  updateVisualVariant: vi.fn(),
  createStoryboard: vi.fn(),
  updateStoryboard: vi.fn(),
  deleteStoryboard: vi.fn(),
  breakdownStoryboards: vi.fn(),
  createStoryboardBatch: vi.fn(),
  queryStoryboardBatch: vi.fn(),
  queryLatestStoryboardBatch: vi.fn(),
  pollExecution: vi.fn(),
  cancelExecution: vi.fn(),
  retryExecution: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: vi.fn(),
  },
  useParams: () => ({ id: '1' }),
}));

vi.mock('@/services/account-team/project', () => ({
  queryProject: mocks.queryProject,
}));

vi.mock('./ai-config/service', () => ({
  queryProjectAiModels: mocks.queryProjectAiModels,
  queryProjectAiConfig: mocks.queryProjectAiConfig,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: {
    poll: mocks.pollExecution,
    cancel: mocks.cancelExecution,
    retry: mocks.retryExecution,
  },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task, onCancel, onRetry }: any) => (
    <span>
      execution:{task.status}
      {onCancel ? (
        <button
          type="button"
          aria-label={`execution-cancel-${task.id}`}
          onClick={onCancel}
        />
      ) : null}
      {onRetry ? (
        <button
          type="button"
          aria-label={`execution-retry-${task.id}`}
          onClick={onRetry}
        />
      ) : null}
    </span>
  ),
}));

vi.mock('./service', () => ({
  queryScriptPageWorkspace: mocks.queryScriptPageWorkspace,
  queryAssetSettingsSummary: mocks.queryAssetSettingsSummary,
  queryAssetVisualWorkspace: mocks.queryAssetVisualWorkspace,
  queryStoryboardWorkspace: mocks.queryStoryboardWorkspace,
  queryAiImageTasks: mocks.queryAiImageTasks,
  queryAiImageTask: mocks.queryAiImageTask,
  selectAiImageResult: mocks.selectAiImageResult,
  createAiImageTask: mocks.createAiImageTask,
  regenerateAiImageTask: mocks.regenerateAiImageTask,
  cancelAiImageTask: mocks.cancelAiImageTask,
  queryAiVideoTasks: mocks.queryAiVideoTasks,
  bindAiVideoResultToStoryboard: mocks.bindAiVideoResultToStoryboard,
  queryAiVoiceTasks: mocks.queryAiVoiceTasks,
  createAiVoiceTask: mocks.createAiVoiceTask,
  createAiVideoTask: mocks.createAiVideoTask,
  cancelAiVideoTask: mocks.cancelAiVideoTask,
  regenerateAiVideoTask: mocks.regenerateAiVideoTask,
  replaceStoryboardAssetReferences: mocks.replaceStoryboardAssetReferences,
  createAssetImageBatch: mocks.createAssetImageBatch,
  updateVisualVariant: mocks.updateVisualVariant,
  createStoryboard: mocks.createStoryboard,
  updateStoryboard: mocks.updateStoryboard,
  deleteStoryboard: mocks.deleteStoryboard,
  breakdownStoryboards: mocks.breakdownStoryboards,
  createStoryboardBatch: mocks.createStoryboardBatch,
  queryStoryboardBatch: mocks.queryStoryboardBatch,
  queryLatestStoryboardBatch: mocks.queryLatestStoryboardBatch,
}));

vi.mock('./ShotProductionWorkspace', () => ({
  default: () => null,
}));

vi.mock('@ant-design/icons', () => ({
  AppstoreOutlined: () => <span>appstore</span>,
  ArrowDownOutlined: () => <span>down</span>,
  ArrowUpOutlined: () => <span>up</span>,
  ArrowLeftOutlined: () => <span>back</span>,
  AudioOutlined: () => <span>audio</span>,
  BarsOutlined: () => <span>bars</span>,
  BulbOutlined: () => <span>bulb</span>,
  CheckCircleOutlined: () => <span>check</span>,
  CloseOutlined: () => <span>close</span>,
  CopyOutlined: () => <span>copy</span>,
  DeleteOutlined: () => <span>delete</span>,
  EditOutlined: () => <span>edit</span>,
  ExpandOutlined: () => <span>expand</span>,
  FileTextOutlined: () => <span>file</span>,
  HolderOutlined: () => <span>holder</span>,
  InfoCircleOutlined: () => <span>info</span>,
  BookOutlined: () => <span>book</span>,
  MoreOutlined: () => <span>more</span>,
  PictureOutlined: () => <span>image</span>,
  PlusOutlined: () => <span>plus</span>,
  ReloadOutlined: () => <span>reload</span>,
  SettingOutlined: () => <span>setting</span>,
  SoundOutlined: () => <span>sound</span>,
  SplitCellsOutlined: () => <span>split</span>,
  PlayCircleOutlined: () => <span>play</span>,
  ThunderboltOutlined: () => <span>thunderbolt</span>,
  UploadOutlined: () => <span>upload</span>,
  VideoCameraOutlined: () => <span>video</span>,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({
      message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
    }),
  },
  Button: ({
    children,
    icon,
    onClick,
    block: _block,
    loading: _loading,
    ...rest
  }: any) => (
    <button type="button" onClick={onClick} {...rest}>
      {icon}
      {children}
    </button>
  ),
  Cascader: ({
    'aria-label': ariaLabel,
    options = [],
    value = [],
    onChange,
    allowClear: _allowClear,
    className: _className,
    displayRender: _displayRender,
    placeholder: _placeholder,
    showSearch: _showSearch,
    changeOnSelect = false,
    ...rest
  }: any) => {
    const flattened = options.flatMap((asset: any) => asset.children?.length
      ? [
          ...(changeOnSelect ? [{ label: asset.label, path: [asset.value] }] : []),
          ...asset.children.map((variant: any) => ({
          label: `${asset.label} / ${variant.label}`,
          path: [asset.value, variant.value],
          })),
        ]
      : [{ label: asset.label, path: [asset.value] }]);
    return (
      <select
        aria-label={ariaLabel}
        value={value.join(':')}
        onChange={(event) => {
          const selected = flattened.find((item: any) => item.path.join(':') === event.target.value);
          onChange?.(selected?.path || []);
        }}
        {...rest}
      >
        <option value="">请选择</option>
        {flattened.map((option: any) => (
          <option key={option.path.join(':')} value={option.path.join(':')}>
            {option.label}
          </option>
        ))}
      </select>
    );
  },
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Image: ({ alt, src }: any) => <img alt={alt} src={src} />,
  Modal: ({ children, onCancel, onOk, okText, open, title }: any) =>
    open ? (
      <div role="dialog" aria-label={title}>
        {children}
        <button type="button" onClick={onCancel}>取消</button>
        <button type="button" onClick={onOk}>{okText || '确定'}</button>
      </div>
    ) : null,
  Popover: ({ children, content }: any) => (
    <div>
      {children}
      {content}
    </div>
  ),
  Tooltip: ({ children }: any) => children,
  Input: {
    TextArea: ({ 'aria-label': ariaLabel, onBlur, onChange, value }: any) => (
      <textarea
        aria-label={ariaLabel}
        onBlur={onBlur}
        onChange={onChange}
        value={value}
      />
    ),
  },
  InputNumber: ({ 'aria-label': ariaLabel, onChange, value, ...rest }: any) => (
    <input
      aria-label={ariaLabel}
      type="number"
      value={value}
      onChange={(event) => onChange?.(Number(event.target.value))}
      {...rest}
    />
  ),
  Select: ({
    'aria-label': ariaLabel,
    options = [],
    value,
    onChange,
    showSearch: _showSearch,
    allowClear: _allowClear,
    ...rest
  }: any) => (
    <select
      aria-label={ariaLabel}
      onChange={(event) => {
        const option = options.find((item: any) => String(item.value) === event.target.value);
        onChange?.(option?.value ?? event.target.value);
      }}
      value={value}
      {...rest}
    >
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Spin: ({ children }: any) => <div>{children}</div>,
  Switch: ({ 'aria-label': ariaLabel, checked, onChange }: any) => (
    <input
      aria-label={ariaLabel}
      checked={checked}
      type="checkbox"
      onChange={(event) => onChange?.(event.target.checked)}
    />
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Tabs: ({ items = [] }: any) => (
    <div>
      {items.map((item: any) => (
        <section key={item.key}>{item.children}</section>
      ))}
    </div>
  ),
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
  },
}));

const setupWorkspaceResponse = (
  overrides?: Partial<{
    episodes: ScriptEpisode[];
    characters: any[];
    scenes: any[];
    props: any[];
    storyboards: any[];
    imageTasks: any[];
    videoTasks: any[];
    voiceTasks: any[];
  }>,
) => {
  const characters = Array.from({ length: 13 }, (_, index) => {
    const names = [
      '斌斌',
      '冯建业',
      '李慧',
      '刘凤英',
      '物业经理',
      '司机',
      '保安',
      '邻居阿姨',
      '护士',
      '保姆',
      '同学',
      '老师',
      '警察',
    ];
    const name = names[index];
    return {
      id: index + 1,
      name,
      roleType: index === 0 ? 'LEAD' : 'SUPPORTING',
      gender: index % 2 === 0 ? '男' : '女',
      ageRange: '常规',
      identity: `${name}身份`,
      personality: ['活泼', '好奇'],
      appearance: `${name}外观`,
      prompt: `${name}提示词`,
    };
  });
  const scenes = Array.from({ length: 21 }, (_, index) => ({
    id: index + 1,
    name:
      index === 0
        ? '停车场'
        : index === 1
          ? '小区广场'
          : `${['停车场', '后备箱内部', '小区广场', '新闻播音室'][index % 4]}${index + 1}`,
    sceneType: index % 2 === 0 ? '室外' : '室内',
    atmosphere: '日间',
    description: '场景描述',
    visualStyle: '写实',
    prompt: '场景提示词',
    visual:
      index === 0
        ? {
            variantCount: 2,
            variants: [],
            generationSummary: {},
            episodeBindings: [],
            resolvedImageUrl: '/episode-parking.png',
            resolvedImageSource: 'EPISODE_PREFERRED',
          }
        : undefined,
  }));
  const props = [
    {
      id: 1,
      name: '棒棒糖',
      propType: '食品',
      appearance: '棒棒糖',
      plotFunction: '道具',
      prompt: '棒棒糖提示词',
      visual: {
        variantCount: 1,
        variants: [],
        generationSummary: {},
        episodeBindings: [],
        resolvedImageUrl: '/legacy-candy.png',
        resolvedImageSource: 'LEGACY_FALLBACK',
      },
    },
    {
      id: 2,
      name: '灰色轿车后备箱',
      propType: '交通工具',
      appearance: '灰色轿车后备箱',
      plotFunction: '剧情道具',
      prompt: '后备箱提示词',
    },
    {
      id: 3,
      name: '拼图盒',
      propType: '玩具',
      appearance: '拼图盒',
      plotFunction: '玩具道具',
      prompt: '拼图盒提示词',
    },
    {
      id: 4,
      name: '玩具汽车',
      propType: '玩具',
      appearance: '玩具汽车',
      plotFunction: '玩具道具',
      prompt: '玩具汽车提示词',
    },
  ];
  mocks.queryProject.mockResolvedValue({
    data: {
      id: 1,
      name: '最危险的捉迷藏',
      code: 'DANGEROUS_HIDE_AND_SEEK',
      status: 'IN_PROGRESS',
      coverUrl: '/cover.png',
      aspectRatio: '16:9',
      videoResolution: '1080p',
      videoGenerateAudio: false,
      videoWatermark: true,
    },
  });
  mocks.queryProjectAiModels.mockResolvedValue({
    data: {
      textModels: [],
      imageModels: [],
      videoModels: [
        {
          id: 10,
          name: 'Seedance 2.0 Standard',
          constraints: {
            duration: { min: 4, max: 15, intelligent: true },
            resolutions: ['480p', '720p', '1080p', '4k'],
          },
        },
        {
          id: 11,
          name: 'Seedance 2.0 Fast',
          constraints: {
            duration: { min: 4, max: 15, intelligent: true },
            resolutions: ['480p', '720p'],
          },
        },
      ],
      audioModels: [],
    },
  });
  mocks.queryProjectAiConfig.mockResolvedValue({
    data: { projectId: 1, videoModelId: 10 },
  });
  const workspaceData = {
      script: {
        id: 11,
        projectId: 1,
        title: '最危险的捉迷藏',
        sourceType: 'AI_GENERATE',
        content:
          '剧本正文\n\n1. 第一集\n2. 第二集\n人物：斌斌、冯建业、李慧、刘凤英、物业经理、司机',
        status: 'CONFIRMED',
      },
      characters: overrides?.characters ?? characters,
      scenes: overrides?.scenes ?? scenes,
      props: overrides?.props ?? props,
      episodes: overrides?.episodes ?? [
        { episodeId: 1001, episodeNo: 1, title: '致命捉迷藏', content: '第一集正文', summary: '第一集概要' },
        { episodeId: 1002, episodeNo: 2, title: '夜色警报', content: '第二集正文', summary: '第二集概要' },
      ],
      storyboards: overrides?.storyboards ?? [
        {
          id: 101,
          shotNo: 1,
          episodeNo: 1,
          shotType: '远景',
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
          characters: '李慧',
          scene: '停车场',
          props: '灰色轿车后备箱',
          dialogue:
            '李慧VO: “停车场，灰色轿车的车主卸下货物，忘记关上后备箱。”',
          durationSeconds: 5,
          imagePrompt: '停车场首帧提示词',
          videoPrompt: '画风：写实都市。镜头1 1s 远景摇镜停车场内灰色轿车。',
          firstFrameUrl: 'https://example.com/shot-101-first.png',
          currentVideoUrl: 'https://example.com/shot-101.mp4',
        },
        {
          id: 102,
          shotNo: 2,
          episodeNo: 1,
          shotType: '中景',
          visualDescription: '斌斌在小区寻找可以躲藏的地方。',
          characters: '斌斌',
          scene: '小区广场',
          props: '',
          dialogue: '斌斌VO: “这时，我儿子在找捉迷藏可以躲藏的地方。”',
          durationSeconds: 3.5,
          imagePrompt: '小区首帧提示词',
          videoPrompt: '镜头2 3.5s 中景固定镜头斌斌跑过。',
          firstFrameUrl: 'https://example.com/shot-102-first.png',
          currentVideoUrl: null,
        },
        {
          id: 201,
          shotNo: 1,
          episodeNo: 2,
          shotType: '特写',
          visualDescription: '夜色中楼道灯忽明忽暗。',
          characters: '冯建业',
          scene: '楼道',
          props: '手机',
          dialogue: '冯建业: “别出声。”',
          durationSeconds: 4,
          imagePrompt: '楼道首帧提示词',
          videoPrompt: '镜头1 4s 夜色楼道压迫感。',
          firstFrameUrl: 'https://example.com/shot-201-first.png',
          currentVideoUrl: null,
        },
      ],
  };
  mocks.queryScriptWorkspace.mockResolvedValue({ data: workspaceData });
  mocks.queryScriptPageWorkspace.mockResolvedValue({
    data: {
      script: workspaceData.script,
      versions: [],
      episodes: workspaceData.episodes,
      analysis: null,
      globalUnderstanding: null,
    },
  });
  mocks.queryAssetSettingsSummary.mockResolvedValue({
    data: { characters: workspaceData.characters, scenes: workspaceData.scenes, props: workspaceData.props },
  });
  mocks.queryStoryboardWorkspace.mockResolvedValue({
    data: { episodes: workspaceData.episodes, episodeNo: 1, current: 1, pageSize: 20, total: workspaceData.storyboards.length, storyboards: workspaceData.storyboards },
  });
  mocks.queryAiImageTasks.mockResolvedValue({
    data: overrides?.imageTasks ?? [
      {
        id: 101,
        projectId: 1,
        taskType: 'CHARACTER',
        targetType: 'CHARACTER',
        targetId: 1,
        modelId: 1,
        providerCode: 'mock',
        model: 'mock',
        prompt: 'child',
        referenceImages: [],
        aspectRatio: '3:4',
        imageCount: 4,
        status: 'SUCCESS',
        createdBy: 1,
        results: [
          {
            id: 11,
            taskId: 101,
            targetType: 'CHARACTER',
            targetId: 1,
            imageUrl: 'https://example.com/character-1.png',
            thumbnailUrl: 'https://example.com/character-1-thumb.png',
            selected: true,
            status: 'SUCCESS',
          },
        ],
      },
    ],
  });
  mocks.queryAiVideoTasks.mockResolvedValue({
    data: overrides?.videoTasks ?? [
      {
        id: 9001,
        projectId: 1,
        storyboardId: 101,
        modelId: 1,
        providerCode: 'doubao',
        model: 'Doubao-Seedance-2.5',
        prompt: '画风：写实都市。',
        firstFrameUrl: 'https://example.com/shot-101-first.png',
        durationSeconds: 5,
        aspectRatio: '9:16',
        resolution: '720p',
        status: 'SUCCEEDED',
        results: [
          {
            id: 9101,
            taskId: 9001,
            storyboardId: 101,
            videoUrl: 'https://example.com/shot-101.mp4',
            storagePath: 'shot-101.mp4',
            coverUrl: 'https://example.com/shot-101-cover.png',
            isSelected: true,
            status: 'SUCCEEDED',
          },
        ],
      },
    ],
  });
  mocks.queryAiVoiceTasks.mockResolvedValue({
    data: overrides?.voiceTasks ?? [],
  });
  mocks.createAiVideoTask.mockResolvedValue({
    data: { id: 9002, storyboardId: 101, executionId: 7002, results: [] },
  });
  mocks.breakdownStoryboards.mockResolvedValue({
    data: { id: 7100, status: 'PENDING', progress: 0 },
  });
  mocks.queryLatestStoryboardBatch.mockResolvedValue({ data: null });
  mocks.createAiImageTask.mockResolvedValue({
    data: {
      id: 1200,
      projectId: 1,
      taskType: 'STORYBOARD_FIRST_FRAME',
      targetType: 'STORYBOARD',
      targetId: 101,
      prompt: '首帧提示词',
      aspectRatio: '9:16',
      imageCount: 1,
      status: 'PENDING',
      results: [],
    },
  });
  mocks.createStoryboard.mockImplementation((_projectId, values) =>
    Promise.resolve({
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters,
        scenes,
        props,
        storyboards: [
          ...((overrides?.storyboards ?? []) as any[]),
          {
            id: 999,
            shotNo: values.shotNo,
            episodeNo: values.episodeNo,
            shotType: values.shotType,
            visualDescription: values.visualDescription,
            characters: values.characters || '',
            scene: values.scene || '',
            props: values.props || '',
            dialogue: values.dialogue || '',
            durationSeconds: values.durationSeconds || 5,
            imagePrompt: values.imagePrompt || '',
            videoPrompt: values.videoPrompt || '',
            firstFrameUrl: null,
            currentVideoUrl: null,
          },
        ],
      },
    }),
  );
  mocks.updateStoryboard.mockImplementation(
    (_projectId, _storyboardId, values) =>
      Promise.resolve({
        data: {
          projectId: 1,
          script: null,
          versions: [],
          characters,
          scenes,
          props,
          storyboards: overrides?.storyboards ?? [],
          ...values,
        },
      }),
  );
  mocks.deleteStoryboard.mockResolvedValue({ data: {} });
};

describe('ProductionWorkbench script page', () => {
  it('keeps draft card identity stable after persistence and resolves default variant images', () => {
    const draft = {
      clientKey: 'draft-local-1',
      assetType: 'CHARACTER',
      assetId: 1,
      variantId: null,
      imageUrl: '/old.png',
      referenceRole: 'VISIBLE',
      sortOrder: 0,
      resolutionStatus: 'ASSET_PENDING',
      sourceType: 'MANUAL',
      lockedByUser: true,
    } as any;
    const persisted = { ...draft, id: 99 };
    const asset = {
      id: 1,
      visual: {
        variants: [{ id: 11, primary: true, usable: true, currentImageUrl: '/new.png' }],
      },
    } as any;

    expect(sortableIdFor(draft, 'CHARACTER', 0)).toBe(
      sortableIdFor(persisted, 'CHARACTER', 2),
    );
    expect(imageFor(draft, [asset])).toBe('/new.png');
  });

  it('preserves draft card keys when saved references are reordered by asset type', () => {
    const scene = {
      id: 10, assetType: 'SCENE', assetId: 4, variantId: 40, sourceName: '夜景',
      referenceRole: 'MAIN', sortOrder: 0, resolutionStatus: 'RESOLVED',
      sourceType: 'MANUAL', lockedByUser: true,
    } as any;
    const character = {
      clientKey: 'draft-character-1', assetType: 'CHARACTER', assetId: 2,
      variantId: null, sourceName: 'Serena', referenceRole: 'VISIBLE', sortOrder: 0,
      resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true,
    } as any;
    const savedCharacter = {
      ...character,
      id: 11,
      variantId: 20,
      clientKey: undefined,
    };

    const merged = preserveReferenceClientKeys(
      [scene, character],
      [savedCharacter, scene],
    ) as any[];

    expect(merged[0].clientKey).toBe('draft-character-1');
    expect(merged[1].clientKey).toBeUndefined();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('currentTenantId', '1');
    mocks.pollExecution.mockImplementation(
      async (
        _tenantId: number,
        executionId: number,
        onUpdate?: (task: any) => void,
      ) => {
        const task = { id: executionId, status: 'SUCCEEDED', progress: 100 };
        onUpdate?.(task);
        return task;
      },
    );
    mocks.cancelAiVideoTask.mockResolvedValue({
      data: { id: 9003, status: 'CANCELED' },
    });
    mocks.regenerateAiVideoTask.mockResolvedValue({
      data: { id: 9004, executionId: 7004 },
    });
    mocks.createAiVoiceTask.mockResolvedValue({
      data: { id: 9200, storyboardId: 101, status: 'PENDING', results: [] },
    });
    mocks.replaceStoryboardAssetReferences.mockImplementation(
      async (_projectId: number, _storyboardId: number, references: any[]) => ({
        data: references.map((reference, index) => ({
          ...reference,
          id: 5000 + index,
          resolutionStatus: reference.variantId ? 'RESOLVED' : 'ASSET_PENDING',
          sourceType: 'MANUAL',
          lockedByUser: true,
        })),
      }),
    );
    mocks.createAssetImageBatch.mockResolvedValue({ data: { id: 8800 } });
    setupWorkspaceResponse();
    mocks.queryAssetVisualWorkspace.mockResolvedValue({ data: { variants: [], episodeBindings: [] } });
  });

  it('loads current shot visuals and renders a legacy scene marker as a bound tag', async () => {
    setupWorkspaceResponse({ storyboards: [{
      id: 301, shotNo: 1, episodeNo: 1, scene: '停车场', characters: '', visualDescription: '镜头', durationSeconds: 5,
      videoPrompt: '【场景】\n<停车场>参考停车场。\n【道具】\n<手机>对应手机。',
      promptDocument: { version: 2, nodes: [{ type: 'text', text: '【场景】\n<停车场>参考停车场。\n【道具】\n<手机>对应手机。' }] },
    }] });
    mocks.queryAssetVisualWorkspace.mockResolvedValue({ data: {
      variants: [{ id: 33, assetType: 'SCENE', assetId: 1, name: '停车场主形象', currentImageResultId: 77, currentImageUrl: '/parking.png', usable: true, primary: true }],
      episodeBindings: [],
    } });
    render(<ProductionWorkbench />);

    await waitFor(() => expect(mocks.queryAssetVisualWorkspace).toHaveBeenCalledWith(1, 'SCENE', 1));
    const editor = await screen.findByRole('textbox', { name: '分镜1视频提示词' });
    await waitFor(() => expect(editor.querySelector('[data-source-id="33"]')).not.toBeNull());
    expect(editor.querySelector('[data-unbound-reference="手机"]')).not.toBeNull();
    expect(screen.getAllByRole('button', { name: '引用素材 停车场主形象' }).length).toBeGreaterThan(0);
  });

  it('adds and selects independent asset variants through one cascader', async () => {
    const variants = (assetId: number) => ({
      variants: [
        { id: assetId * 10 + 1, assetId, name: `形态${assetId}-1`, primary: true, usable: true, currentImageUrl: `/${assetId}-1.png` },
        { id: assetId * 10 + 2, assetId, name: `形态${assetId}-2`, primary: false, usable: true, currentImageUrl: `/${assetId}-2.png` },
      ],
      episodeBindings: [],
      variantCount: 2,
      generationSummary: {},
    });
    setupWorkspaceResponse({
      characters: [
        { id: 11, name: 'Serena', visual: variants(11) },
        { id: 12, name: 'Rowan', visual: variants(12) },
        { id: 13, name: 'Mirabel', visual: variants(13) },
      ],
      storyboards: [{
        id: 301, shotNo: 1, episodeNo: 1, visualDescription: '三人同框', durationSeconds: 12,
        characters: 'Serena、Rowan', scene: '', props: '', videoPrompt: '镜头',
        assetReferences: [
          { id: 1, assetType: 'CHARACTER', assetId: 11, assetName: 'Serena', variantId: 111, variantName: '形态11-1', referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'RESOLVED', sourceType: 'MANUAL', lockedByUser: true },
          { id: 2, assetType: 'CHARACTER', assetId: 12, assetName: 'Rowan', variantId: 121, variantName: '形态12-1', referenceRole: 'VISIBLE', sortOrder: 1, resolutionStatus: 'RESOLVED', sourceType: 'MANUAL', lockedByUser: true },
        ],
      }],
    });
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: '添加分镜1角色' }));
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenLastCalledWith(
      1, 301, expect.arrayContaining([expect.objectContaining({ assetId: 13, variantId: 131 })]),
    ));

    fireEvent.change(screen.getByLabelText('分镜1角色3资产形态'), {
      target: { value: '13:132' },
    });
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenLastCalledWith(
      1, 301, expect.arrayContaining([expect.objectContaining({ assetId: 13, variantId: 132 })]),
    ));

    fireEvent.change(screen.getByLabelText('分镜1角色3资产形态'), {
      target: { value: '13' },
    });
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenLastCalledWith(
      1, 301, expect.arrayContaining([expect.objectContaining({ assetId: 13, variantId: null })]),
    ));

    expect(screen.getAllByRole('button', { name: /拖拽分镜1角色\d排序/ })).toHaveLength(3);
    expect(screen.queryByRole('button', { name: /上移分镜1角色/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('分镜1角色1作用')).not.toBeInTheDocument();
    expect(screen.getByRole('list', { name: '分镜1角色资产' })).toBeInTheDocument();
  });

  it('releases a sticky storyboard after its active content reaches the scroll boundary', async () => {
    render(<ProductionWorkbench />);
    const prompt = await screen.findByRole('textbox', { name: '分镜1视频提示词' });
    const card = prompt.closest('.storyboard-card');

    expect(card).not.toBeNull();
    Object.defineProperties(prompt, {
      clientHeight: { configurable: true, value: 400 },
      scrollHeight: { configurable: true, value: 1000 },
      scrollTop: { configurable: true, writable: true, value: 600 },
    });

    fireEvent.wheel(prompt, { deltaY: 120 });
    expect(card).toHaveClass('is-scroll-released');

    prompt.scrollTop = 0;
    fireEvent.wheel(prompt, { deltaY: -120 });
    expect(card).not.toHaveClass('is-scroll-released');
  });

  it('keeps default roles while hiding role controls', async () => {
    setupWorkspaceResponse({
      scenes: [{ id: 21, name: '走廊' }, { id: 22, name: '卧室' }],
      props: [{ id: 31, name: '手机' }, { id: 32, name: '钥匙' }],
      storyboards: [{
        id: 302, shotNo: 1, episodeNo: 1, visualDescription: '转场', durationSeconds: 12,
        characters: '', scene: '走廊', props: '手机', videoPrompt: '镜头',
        assetReferences: [
          { id: 1, assetType: 'SCENE', assetId: 21, assetName: '走廊', referenceRole: 'MAIN', sortOrder: 0, resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true },
          { id: 2, assetType: 'PROP', assetId: 31, assetName: '手机', referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true },
        ],
      }],
    });
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: '添加分镜1场景' }));
    await waitFor(() => expect(screen.getByLabelText('分镜1场景2资产形态')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '添加分镜1道具' }));
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenCalled());
    expect(screen.queryByLabelText(/作用/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('分镜1道具2资产形态')).toBeInTheDocument();
    expect(mocks.replaceStoryboardAssetReferences).toHaveBeenCalledWith(
      1,
      302,
      expect.arrayContaining([expect.objectContaining({ assetId: 22, referenceRole: 'MAIN' })]),
    );
    fireEvent.click(screen.getByRole('button', { name: '移除分镜1道具1' }));
    await waitFor(() => {
      const references = mocks.replaceStoryboardAssetReferences.mock.calls.at(-1)?.[2];
      expect(references.filter((reference: any) => reference.assetType === 'PROP')).toHaveLength(1);
    });
  });

  it('reorders references only within the dragged asset group', () => {
    const references: any[] = [
      { id: 1, assetType: 'CHARACTER', assetId: 11, sortOrder: 0 },
      { id: 2, assetType: 'SCENE', assetId: 21, sortOrder: 0 },
      { id: 3, assetType: 'CHARACTER', assetId: 12, sortOrder: 1 },
      { id: 4, assetType: 'PROP', assetId: 31, sortOrder: 0 },
    ];

    const reordered = reorderReferencesWithinType(references, 'CHARACTER', 1, 0);

    expect(reordered.map((reference) => reference.assetId)).toEqual([12, 21, 11, 31]);
    expect(reordered.filter((reference) => reference.assetType === 'CHARACTER')
      .map((reference) => reference.sortOrder)).toEqual([0, 1]);
  });

  it('serializes reference saves and keeps the latest optimistic selection', async () => {
    let resolveFirst!: (value: any) => void;
    let resolveSecond!: (value: any) => void;
    const firstSave = new Promise((resolve) => { resolveFirst = resolve; });
    const secondSave = new Promise((resolve) => { resolveSecond = resolve; });
    mocks.replaceStoryboardAssetReferences
      .mockReturnValueOnce(firstSave)
      .mockReturnValueOnce(secondSave);
    setupWorkspaceResponse({
      characters: [{
        id: 1,
        name: 'Serena',
        visual: {
          variants: [
            { id: 11, assetId: 1, name: '正面', primary: true, usable: true },
            { id: 12, assetId: 1, name: '侧面', primary: false, usable: true },
            { id: 13, assetId: 1, name: '背面', primary: false, usable: true },
          ],
          episodeBindings: [],
        },
      }],
      storyboards: [{
        id: 307, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
        characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
        assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
          variantId: 11, variantName: '正面', referenceRole: 'VISIBLE', sortOrder: 0,
          resolutionStatus: 'RESOLVED', sourceType: 'MANUAL', lockedByUser: true }],
      }],
    });
    render(<ProductionWorkbench />);
    const selector = await screen.findByLabelText('分镜1角色1资产形态');

    fireEvent.change(selector, { target: { value: '1:12' } });
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByLabelText('分镜1角色1资产形态'), {
      target: { value: '1:13' },
    });

    expect(mocks.replaceStoryboardAssetReferences).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('分镜1角色1资产形态')).toHaveValue('1:13');

    const firstReferences = mocks.replaceStoryboardAssetReferences.mock.calls[0][2];
    await act(async () => resolveFirst({ data: firstReferences }));
    await waitFor(() => expect(mocks.replaceStoryboardAssetReferences).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText('分镜1角色1资产形态')).toHaveValue('1:13');

    const secondReferences = mocks.replaceStoryboardAssetReferences.mock.calls[1][2];
    await act(async () => resolveSecond({ data: secondReferences }));
    await waitFor(() => expect(screen.getByLabelText('分镜1角色1资产形态')).toHaveValue('1:13'));
  });

  it('rolls back only the affected card when focused reference saving fails', async () => {
    setupWorkspaceResponse({ storyboards: [{
      id: 303, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
      characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
      assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: '斌斌',
        referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'ASSET_PENDING',
        sourceType: 'MANUAL', lockedByUser: true }],
    }] });
    mocks.replaceStoryboardAssetReferences.mockRejectedValueOnce(new Error('save failed'));
    render(<ProductionWorkbench />);
    await screen.findByLabelText('分镜1角色1资产形态');
    const pageCalls = mocks.queryStoryboardWorkspace.mock.calls.length;

    fireEvent.click(screen.getByRole('button', { name: '移除分镜1角色1' }));

    await waitFor(() => expect(screen.getByLabelText('分镜1角色1资产形态')).toBeInTheDocument());
    expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledTimes(pageCalls);
  });

  it('opens the shared variant generator and shows loading on the submitted thumbnail', async () => {
    const character = {
      id: 1, name: 'Serena', roleType: 'LEAD', gender: '女', ageRange: '成年',
      identity: '女主角', personality: [], appearance: '黑色长发', prompt: '人物主体提示词',
      visual: {
        variantCount: 2,
        primaryVariant: { id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
          prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'COMPLETED',
          currentImageUrl: '/serena-primary.png', primary: true, usable: true },
        variants: [
          { id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
            prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'COMPLETED',
            currentImageUrl: '/serena-primary.png', primary: true, usable: true },
          { id: 12, assetType: 'CHARACTER', assetId: 1, name: '晚宴礼服',
            prompt: '白色晚宴礼服', sourceType: 'USER', generationStatus: 'NOT_STARTED',
            currentImageUrl: '/old-dress.png', primary: false, usable: false },
        ],
        generationSummary: {}, episodeBindings: [], resolvedImageUrl: '/serena-primary.png',
      },
    };
    setupWorkspaceResponse({
      characters: [character],
      storyboards: [304, 305].map((id, index) => ({
        id, shotNo: index + 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
        characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
        assetReferences: [{ id: index + 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
          variantId: 12, variantName: '晚宴礼服', imageUrl: '/old-reference.png',
          referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'ASSET_PENDING',
          sourceType: 'MANUAL', lockedByUser: true }],
      })),
    });
    mocks.queryProjectAiModels.mockResolvedValue({ data: {
      textModels: [], imageModels: [{ id: 8, name: 'GPT Image 2' }], videoModels: [], audioModels: [],
    } });
    mocks.queryProjectAiConfig.mockResolvedValue({ data: { projectId: 1, imageModelId: 8 } });
    mocks.createAiImageTask.mockResolvedValueOnce({ data: {
      id: 1201, executionId: 7201, taskType: 'CHARACTER', targetType: 'VISUAL_VARIANT',
      targetId: 12, status: 'PENDING', results: [],
    } });
    mocks.queryAssetVisualWorkspace.mockResolvedValue({ data: {
      ...character.visual,
      variants: character.visual.variants.map((variant) =>
        variant.id === 12 ? { ...variant, generationStatus: 'GENERATING' } : variant),
    } });
    let resolvePoll!: (value: API.AiExecutionResponse) => void;
    mocks.pollExecution.mockReturnValueOnce(new Promise((resolve) => {
      resolvePoll = resolve;
    }));
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: '生成分镜1角色1资产图' }));
    expect(screen.getByLabelText('晚宴礼服引用图')).toHaveAttribute('src', '/serena-primary.png');
    fireEvent.change(screen.getByLabelText('晚宴礼服生成提示词'), {
      target: { value: '白色晚宴礼服，电影感' },
    });
    fireEvent.click(screen.getByRole('button', { name: '提交晚宴礼服生成' }));

    await waitFor(() => {
      expect(mocks.updateVisualVariant).toHaveBeenCalledWith(
        1, 12, expect.objectContaining({ prompt: '白色晚宴礼服，电影感' }),
      );
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(1, expect.objectContaining({
        taskType: 'CHARACTER', targetType: 'VISUAL_VARIANT', targetId: 12,
        modelId: 8, prompt: '白色晚宴礼服，电影感',
        referenceImages: ['/serena-primary.png'], aspectRatio: '16:9', imageCount: 1,
      }));
      expect(screen.getByRole('status', { name: '分镜1角色1资产图生成中' })).toBeInTheDocument();
      expect(screen.queryByRole('status', { name: '分镜2角色1资产图生成中' })).not.toBeInTheDocument();
    });
    await waitFor(() => expect(mocks.queryAssetVisualWorkspace)
      .toHaveBeenCalledWith(1, 'CHARACTER', 1), { timeout: 2500 });

    mocks.queryAssetVisualWorkspace.mockResolvedValueOnce({ data: {
      ...character.visual,
      variants: character.visual.variants.map((variant) =>
        variant.id === 12
          ? { ...variant, currentImageUrl: '/new-dress.png', usable: true }
          : variant),
    } });
    await act(async () => {
      resolvePoll({ id: 7201, status: 'SUCCEEDED' });
    });
    await waitFor(() => {
      expect(screen.queryByRole('status', { name: '分镜1角色1资产图生成中' })).not.toBeInTheDocument();
      expect(screen.getAllByAltText('Serena参考图')).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ src: expect.stringContaining('/new-dress.png') }),
        ]),
      );
    });
  });

  it('restores thumbnail loading from a generating visual variant after page reload', async () => {
    const character = {
      id: 1, name: 'Serena', roleType: 'LEAD', gender: '女', ageRange: '成年',
      identity: '女主角', personality: [], appearance: '黑色长发', prompt: '人物主体提示词',
      visual: {
        variantCount: 1,
        primaryVariant: { id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
          prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'GENERATING',
          primary: true, usable: false },
        variants: [{ id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
          prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'GENERATING',
          primary: true, usable: false }],
        generationSummary: { GENERATING: 1 }, episodeBindings: [], resolvedImageUrl: null,
      },
    };
    setupWorkspaceResponse({ characters: [character], storyboards: [{
      id: 308, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
      characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
      assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
        variantId: 11, variantName: '默认形态', referenceRole: 'VISIBLE', sortOrder: 0,
        resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true }],
    }] });

    render(<ProductionWorkbench />);

    expect(await screen.findByRole('status', {
      name: '分镜1角色1资产图生成中',
    })).toBeInTheDocument();
  });

  it('keeps refreshing a generating visual variant after page reload', async () => {
    const generatingVariant = {
      id: 11, assetType: 'CHARACTER' as const, assetId: 1, name: '默认形态',
      prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'GENERATING',
      primary: true, usable: false,
    };
    const character = {
      id: 1, name: 'Serena', roleType: 'LEAD', gender: '女', ageRange: '成年',
      identity: '女主角', personality: [], appearance: '黑色长发', prompt: '人物主体提示词',
      visual: {
        variantCount: 1, primaryVariant: generatingVariant, variants: [generatingVariant],
        generationSummary: { GENERATING: 1 }, episodeBindings: [], resolvedImageUrl: null,
      },
    };
    setupWorkspaceResponse({ characters: [character], storyboards: [{
      id: 308, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
      characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
      assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
        variantId: 11, variantName: '默认形态', referenceRole: 'VISIBLE', sortOrder: 0,
        resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true }],
    }] });
    mocks.queryAssetVisualWorkspace.mockResolvedValueOnce({ data: {
      ...character.visual,
      primaryVariant: { ...generatingVariant, generationStatus: 'COMPLETED',
        currentImageUrl: '/serena-completed.png', usable: true },
      variants: [{ ...generatingVariant, generationStatus: 'COMPLETED',
        currentImageUrl: '/serena-completed.png', usable: true }],
      generationSummary: { COMPLETED: 1 }, resolvedImageUrl: '/serena-completed.png',
    } });

    render(<ProductionWorkbench />);

    expect(await screen.findByRole('status', {
      name: '分镜1角色1资产图生成中',
    })).toBeInTheDocument();
    await waitFor(() => expect(mocks.queryAssetVisualWorkspace)
      .toHaveBeenCalledWith(1, 'CHARACTER', 1), { timeout: 2500 });
    await waitFor(() => {
      expect(screen.queryByRole('status', {
        name: '分镜1角色1资产图生成中',
      })).not.toBeInTheDocument();
      expect(screen.getAllByAltText('Serena参考图')).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ src: expect.stringContaining('/serena-completed.png') }),
        ]),
      );
    });
  });

  it('does not fall back when the explicitly selected visual variant is stale', async () => {
    const character = {
      id: 1, name: 'Serena', roleType: 'LEAD', gender: '女', ageRange: '成年',
      identity: '女主角', personality: [], appearance: '黑色长发', prompt: '人物主体提示词',
      visual: {
        variantCount: 1,
        primaryVariant: { id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
          prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'COMPLETED',
          currentImageUrl: '/serena-primary.png', primary: true, usable: true },
        variants: [{ id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
          prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'COMPLETED',
          currentImageUrl: '/serena-primary.png', primary: true, usable: true }],
        generationSummary: {}, episodeBindings: [], resolvedImageUrl: '/serena-primary.png',
      },
    };
    setupWorkspaceResponse({ characters: [character], storyboards: [{
      id: 307, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
      characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
      assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
        variantId: 999, variantName: '已删除形态', referenceRole: 'VISIBLE', sortOrder: 0,
        resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true }],
    }] });
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: '生成分镜1角色1资产图' }));

    expect(screen.queryByLabelText('默认形态生成提示词')).not.toBeInTheDocument();
    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
  });

  it('blocks a non-primary character variant when the primary image is missing', async () => {
    const character = {
      id: 1, name: 'Serena', roleType: 'LEAD', gender: '女', ageRange: '成年',
      identity: '女主角', personality: [], appearance: '黑色长发', prompt: '人物主体提示词',
      visual: {
        variantCount: 2,
        variants: [
          { id: 11, assetType: 'CHARACTER', assetId: 1, name: '默认形态',
            prompt: '人物主体提示词', sourceType: 'USER', generationStatus: 'NOT_STARTED',
            primary: true, usable: false },
          { id: 12, assetType: 'CHARACTER', assetId: 1, name: '晚宴礼服',
            prompt: '白色晚宴礼服', sourceType: 'USER', generationStatus: 'NOT_STARTED',
            primary: false, usable: false },
        ],
        generationSummary: {}, episodeBindings: [], resolvedImageUrl: null,
      },
    };
    setupWorkspaceResponse({ characters: [character], storyboards: [{
      id: 306, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
      characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
      assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
        variantId: 12, variantName: '晚宴礼服', referenceRole: 'VISIBLE', sortOrder: 0,
        resolutionStatus: 'ASSET_PENDING', sourceType: 'MANUAL', lockedByUser: true }],
    }] });
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: '生成分镜1角色1资产图' }));

    expect(screen.queryByLabelText('晚宴礼服生成提示词')).not.toBeInTheDocument();
    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
    expect(mocks.createAssetImageBatch).not.toHaveBeenCalled();
  });

  it('shows model media limits and omitted provider references without removing bindings', async () => {
    setupWorkspaceResponse({
      storyboards: [{
        id: 305, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 12,
        characters: 'Serena', scene: '', props: '', videoPrompt: '镜头',
        assetReferences: [{ id: 1, assetType: 'CHARACTER', assetId: 1, assetName: 'Serena',
          referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'RESOLVED',
          sourceType: 'MANUAL', lockedByUser: true }],
      }],
      videoTasks: [{
        id: 9005, storyboardId: 305, status: 'SUCCEEDED', results: [],
        referenceDiagnostics: {
          limits: { image: 2, video: 1, audio: 1 },
          kept: [],
          omitted: [{ assetType: 'CHARACTER', assetId: 1, displayName: 'Serena', reason: 'MODEL_COUNT_LIMIT' }],
        },
      }],
    });
    mocks.queryProjectAiModels.mockResolvedValue({ data: {
      textModels: [], imageModels: [], audioModels: [],
      videoModels: [{ id: 10, name: 'Seedance', constraints: {
        duration: { min: 4, max: 15 }, resolutions: ['720p'],
        image: { maxCount: 2 }, video: { maxCount: 1 }, audio: { maxCount: 1 },
      } }],
    } });

    render(<ProductionWorkbench />);

    expect(await screen.findByText('图片上限 2')).toBeInTheDocument();
    expect(screen.getByText('视频上限 1')).toBeInTheDocument();
    expect(screen.getByText('音频上限 1')).toBeInTheDocument();
    expect(screen.getByText('已省略 Serena：MODEL_COUNT_LIMIT')).toBeInTheDocument();
    expect(screen.getByLabelText('分镜1角色1资产形态')).toBeInTheDocument();
  });

  it('suggests one visible character and uses available asset thumbnails', async () => {
    setupWorkspaceResponse({
      characters: [
        { id: 11, name: 'Serena', mainImageThumbnailUrl: '/serena.png' },
        { id: 12, name: 'Rowan' },
      ],
      scenes: [{ id: 21, name: '封印深渊', mainImageThumbnailUrl: '/scene.png' }],
      props: [{ id: 31, name: 'Serena的手机', mainImageThumbnailUrl: '/phone.png' }],
      storyboards: [{
        id: 301, shotNo: 1, episodeNo: 1, characters: '', scene: '封印深渊', props: 'Serena的手机',
        visualDescription: 'Serena站在门外，双手握着手机。', durationSeconds: 5, videoPrompt: '镜头提示',
      }],
    });
    render(<ProductionWorkbench />);

    const role = await screen.findByRole('combobox', { name: '分镜1出镜角色' });
    expect(role).toHaveValue('Serena');
    expect(screen.getByAltText('Serena参考图')).toHaveAttribute('src', '/serena.png');
    expect(screen.getAllByAltText('封印深渊参考图')[0]).toHaveAttribute('src', '/scene.png');
    expect(screen.getByAltText('Serena的手机')).toHaveAttribute('src', '/phone.png');
    expect(mocks.updateStoryboard).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '确认分镜1出镜角色' }));
    await waitFor(() => expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 301, expect.objectContaining({ characters: 'Serena' })));
  });

  it('does not guess among multiple characters and marks a missing scene image', async () => {
    setupWorkspaceResponse({
      characters: [{ id: 11, name: 'Serena' }, { id: 12, name: 'Rowan' }],
      scenes: [{ id: 21, name: '走廊' }],
      storyboards: [{
        id: 301, shotNo: 1, episodeNo: 1, characters: '', scene: '走廊', visualDescription: 'Serena和Rowan站在走廊。', durationSeconds: 5, videoPrompt: '镜头提示',
      }],
    });
    render(<ProductionWorkbench />);

    await screen.findByRole('combobox', { name: '分镜1出镜角色' });
    expect(screen.queryByText('按画面推断，待确认')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认分镜1出镜角色' })).not.toBeInTheDocument();
    expect(screen.getByText('未生成场景参考图')).toBeInTheDocument();
  });

  it('waits for the latest prompt document to save before creating a video task', async () => {
    let resolveSave: (value: unknown) => void = () => undefined;
    mocks.updateStoryboard.mockImplementationOnce(() => new Promise((resolve) => { resolveSave = resolve; }));
    render(<ProductionWorkbench />);
    const generate = await screen.findByRole('button', { name: '生成分镜1视频' });
    fireEvent.click(generate);

    await waitFor(() => expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, expect.objectContaining({
      promptDocument: expect.objectContaining({ nodes: expect.arrayContaining([expect.objectContaining({ sourceType: 'STORYBOARD_FIRST_FRAME', sourceId: 101 })]) }),
    })));
    expect(mocks.createAiVideoTask).not.toHaveBeenCalled();
    resolveSave({ data: {} });
    await waitFor(() => expect(mocks.createAiVideoTask).toHaveBeenCalled());
  });

  it('does not overwrite a just-edited script while saving the video prompt', async () => {
    let finishScriptSave: (value: unknown) => void = () => undefined;
    mocks.updateStoryboard.mockImplementationOnce(() => new Promise((resolve) => { finishScriptSave = resolve; }));
    render(<ProductionWorkbench />);
    const script = await screen.findByRole('textbox', { name: '分镜1剧本原文' });
    fireEvent.change(script, { target: { value: '▲停车场\n最新画面描述' } });
    fireEvent.blur(script);
    await waitFor(() => expect(mocks.updateStoryboard).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '生成分镜1视频' }));
    finishScriptSave({ data: {} });

    await waitFor(() => expect(mocks.updateStoryboard).toHaveBeenCalledTimes(2));
    expect(mocks.updateStoryboard).toHaveBeenLastCalledWith(1, 101, expect.objectContaining({
      visualDescription: '最新画面描述',
    }));
  });

  it('blocks paid video creation without a usable image reference', async () => {
    setupWorkspaceResponse({ storyboards: [{
      id: 301, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 5,
      videoPrompt: '没有图片', promptDocument: { version: 2, nodes: [{ type: 'text', text: '没有图片' }] },
    }] });
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '生成分镜1视频' }));
    await waitFor(() => expect(mocks.createAiVideoTask).not.toHaveBeenCalled());
    expect(mocks.updateStoryboard).not.toHaveBeenCalled();
  });

  it('uses a newly selected first-frame result without reloading the storyboard page', async () => {
    setupWorkspaceResponse({
      storyboards: [{ id: 301, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 5, videoPrompt: '镜头提示', promptDocument: { version: 2, nodes: [{ type: 'text', text: '镜头提示' }] }, firstFrameUrl: null }],
      imageTasks: [{ id: 801, targetType: 'STORYBOARD', targetId: 301, status: 'SUCCESS', results: [{ id: 811, selected: true, status: 'ACTIVE', imageUrl: '/first-frame.png' }] }],
    });
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '生成分镜1视频' }));

    await waitFor(() => expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 301, expect.objectContaining({
      promptDocument: expect.objectContaining({ nodes: expect.arrayContaining([expect.objectContaining({ sourceType: 'STORYBOARD_FIRST_FRAME', sourceId: 301 })]) }),
    })));
    expect(mocks.createAiVideoTask).toHaveBeenCalledWith(1, expect.objectContaining({ firstFrameUrl: '/first-frame.png' }));
  });

  it('selects a completed image result as the storyboard first frame', async () => {
    setupWorkspaceResponse({
      storyboards: [{ id: 301, shotNo: 1, episodeNo: 1, visualDescription: '镜头', durationSeconds: 5, videoPrompt: '镜头提示', firstFrameUrl: null }],
      imageTasks: [
        { id: 802, targetType: 'STORYBOARD', targetId: 301, status: 'PENDING', createdAt: '2026-09-24', results: [] },
        { id: 801, targetType: 'STORYBOARD', targetId: 301, status: 'SUCCESS', createdAt: '2026-09-23', results: [{ id: 811, selected: false, status: 'ACTIVE', imageUrl: '/first-frame.png' }] },
      ],
    });
    mocks.selectAiImageResult.mockResolvedValue({ data: { id: 811, selected: true, status: 'ACTIVE', targetId: 301, imageUrl: '/first-frame.png' } });
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '设为分镜1首帧' }));

    await waitFor(() => expect(mocks.selectAiImageResult).toHaveBeenCalledWith(1, 811));
    const editor = screen.getByRole('textbox', { name: '分镜1视频提示词' });
    await waitFor(() => expect(editor.querySelector('[data-source-type="STORYBOARD_FIRST_FRAME"]')).not.toBeNull());
  });

  it('binds a generated video result as the current storyboard video', async () => {
    setupWorkspaceResponse({ videoTasks: [{
      id: 9001, storyboardId: 101, status: 'SUCCEEDED', results: [{ id: 9101, taskId: 9001, storyboardId: 101, videoUrl: '/video.mp4', isSelected: false }],
    }] });
    mocks.bindAiVideoResultToStoryboard.mockResolvedValue({ data: { id: 9101, storyboardId: 101, videoUrl: '/video.mp4', isSelected: true } });
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '设为当前分镜视频' }));
    await waitFor(() => expect(mocks.bindAiVideoResultToStoryboard).toHaveBeenCalledWith(1, 9101));
    expect(await screen.findByText('当前分镜视频')).toBeInTheDocument();
  });

  it('renders the storyboard section from the screenshot and switches episodes', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.queryScriptPageWorkspace).not.toHaveBeenCalled();
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledWith(1);
      expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryScriptWorkspace).not.toHaveBeenCalled();
      expect(mocks.queryAiImageTasks).toHaveBeenCalledWith(1, undefined);
      expect(mocks.queryAiVideoTasks).toHaveBeenCalledWith(1, undefined);
    });

    expect(screen.getByText('分镜表')).toBeInTheDocument();
    expect(screen.getByText('批量生成视频')).toBeInTheDocument();
    expect(screen.getByText('第1集 致命捉迷藏')).toBeInTheDocument();
    expect(screen.getByText('第一集概要')).toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
    expect(screen.getByText('分镜2')).toBeInTheDocument();
    expect(screen.getAllByText('全能参考生视频').length).toBeGreaterThan(0);
    expect(screen.queryByText('首尾帧生视频')).not.toBeInTheDocument();
    expect(
      screen.getAllByDisplayValue(/停车场，灰色轿车的车主卸下货物/).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText('李慧 - 李慧').length).toBeGreaterThan(0);
    expect(screen.getAllByText('停车场 - 停车场').length).toBeGreaterThan(0);
    expect(
      screen.getAllByText('场景参考来源：剧集首选形象').length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText('灰色轿车后备箱 - 灰色轿车后备箱').length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText('3D导演台').length).toBeGreaterThan(0);
    expect(
      screen.getAllByRole('textbox', { name: /视频提示词/ })[0],
    ).toHaveTextContent(/停车场首帧提示词|写实都市|镜头1/);
    expect(screen.getAllByText('当前预览').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() => {
      expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledWith(1, {
        episodeNo: 2,
        current: 1,
        pageSize: 20,
      });
    });
    expect(screen.getByText('第2集 夜色警报')).toBeInTheDocument();
    expect(screen.getByText('第二集概要')).toBeInTheDocument();
    expect(screen.queryByText('第一集概要')).not.toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
    expect(screen.getAllByDisplayValue(/别出声/).length).toBeGreaterThan(0);
    expect(screen.queryByText('分镜2')).not.toBeInTheDocument();
  });

  it('exposes only working controls and copies the persisted prompt', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    render(<ProductionWorkbench />);

    const copyPrompt = await screen.findByRole('button', { name: '复制分镜1提示词' });
    expect(screen.queryByRole('button', { name: '分镜表' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '详情' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '更多角色操作' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '更多场景操作' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '更多道具操作' })).not.toBeInTheDocument();
    expect(screen.queryByText('首尾帧生视频')).not.toBeInTheDocument();

    fireEvent.click(copyPrompt);

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(expect.stringContaining('停车场')));
  });

  it('creates a real voice task from the storyboard dialogue', async () => {
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '分镜1自定义音色' }));
    fireEvent.change(screen.getByRole('textbox', { name: '分镜1音色ID' }), {
      target: { value: 'female-cn-01' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成语音' }));

    await waitFor(() => expect(mocks.createAiVoiceTask).toHaveBeenCalledWith(1, {
      storyboardId: 101,
      voiceType: 'DIALOGUE',
      speakerName: '李慧',
      voiceId: 'female-cn-01',
      textContent: expect.stringContaining('停车场'),
      speed: 1,
      pitch: 1,
      volume: 1,
    }));
  });

  it('persists clearing storyboard asset selections', async () => {
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '清空分镜1出镜角色' }));
    fireEvent.click(screen.getByRole('button', { name: '清空分镜1场景' }));
    fireEvent.click(screen.getByRole('button', { name: '清空分镜1场景道具' }));

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, expect.objectContaining({ characters: '' }));
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, expect.objectContaining({ scene: '' }));
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, expect.objectContaining({ props: '' }));
    });
  });

  it('expands the selected episode formal summary details', async () => {
    setupWorkspaceResponse({ episodes: [{
      episodeId: 1001,
      episodeNo: 1,
      title: '致命捉迷藏',
      formalSummary: {
        id: 1,
        schemaVersion: 1,
        source: 'AI',
        content: {
          summary: '第一集概要',
          highlights: ['后备箱成为关键线索'],
          endingHook: '车内传来异响',
        },
      },
    }] });
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '查看本集详情' }));

    expect(screen.getByText('后备箱成为关键线索')).toBeInTheDocument();
    expect(screen.getByText('车内传来异响')).toBeInTheDocument();
  });

  it('switches the main preview from video to the selected reference image', async () => {
    render(<ProductionWorkbench />);
    fireEvent.click(await screen.findByRole('button', { name: '查看分镜1首帧' }));

    expect(screen.getByRole('img', { name: '分镜1主预览' })).toHaveAttribute(
      'src',
      'https://example.com/shot-101-first.png',
    );
  });

  it('uses the selected episode formal summary instead of demo or legacy content', async () => {
    setupWorkspaceResponse({
      episodes: [
        {
          episodeId: 1001,
          episodeNo: 1,
          title: '门缝里的阴谋',
          summary: '旧概要',
          formalSummary: {
            id: 1,
            schemaVersion: 1,
            source: 'MANUAL',
            content: { summary: 'Serena在门外听见阴谋。', highlights: [] },
          },
        },
        {
          episodeId: 1002,
          episodeNo: 2,
          title: '暴雨追杀',
          formalSummary: {
            id: 2,
            schemaVersion: 1,
            source: 'AI',
            content: { summary: 'Serena带着证据逃向露台。', highlights: [] },
          },
        },
      ],
      storyboards: [],
    });
    render(<ProductionWorkbench />);

    expect(await screen.findByText('Serena在门外听见阴谋。')).toBeInTheDocument();
    expect(screen.getByText('第1集 门缝里的阴谋')).toBeInTheDocument();
    expect(screen.queryByText('旧概要')).not.toBeInTheDocument();
    expect(screen.queryByText(/斌斌独自下楼玩耍/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(await screen.findByText('Serena带着证据逃向露台。')).toBeInTheDocument();
    expect(screen.getByText('第2集 暴雨追杀')).toBeInTheDocument();
    expect(screen.queryByText('Serena在门外听见阴谋。')).not.toBeInTheDocument();
    expect(screen.queryByText(/夜幕压低小区楼影/)).not.toBeInTheDocument();
  });

  it('shows the first episode before optional media requests finish', async () => {
    const pending = new Promise<never>(() => undefined);
    mocks.queryAiImageTasks.mockReturnValue(pending);
    mocks.queryAiVideoTasks.mockReturnValue(pending);
    mocks.queryAiVoiceTasks.mockReturnValue(pending);
    render(<ProductionWorkbench />);

    expect(await screen.findByText('第1集 致命捉迷藏')).toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
    expect(mocks.queryScriptPageWorkspace).not.toHaveBeenCalled();
  });

  it('shows the selected episode while asset and model requests are still pending', async () => {
    const pending = new Promise<never>(() => undefined);
    mocks.queryAssetSettingsSummary.mockReturnValue(pending);
    mocks.queryProjectAiModels.mockReturnValue(pending);
    render(<ProductionWorkbench />);

    expect(await screen.findByText('第1集 致命捉迷藏')).toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
  });

  it('keeps episode navigation and the workbench in separate viewport scroll areas', async () => {
    render(<ProductionWorkbench />);
    const episodeButton = await screen.findByRole('button', { name: '1' });
    const navigation = episodeButton.closest('aside');
    const workbench = screen.getByText('分镜表').closest('[data-testid="storyboard-workbench"]');

    expect(navigation).toHaveStyle({ overflowY: 'auto' });
    expect(workbench).toHaveStyle({ overflowY: 'auto' });
  });
  it('shows an honest empty summary and episode number when episode metadata is missing', async () => {
    setupWorkspaceResponse({
      episodes: [
        { episodeNo: 1, title: '  ', summary: '  ' },
        { episodeNo: 2, title: '' },
      ],
      storyboards: [],
    });
    render(<ProductionWorkbench />);

    await screen.findByRole('button', { name: '2' });
    expect(screen.getByRole('heading', { name: '第1集' })).toBeInTheDocument();
    expect(screen.getByText('暂无本集概要')).toBeInTheDocument();
    expect(screen.queryByText(/致命捉迷藏|斌斌独自下楼玩耍/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(screen.getByRole('heading', { name: '第2集' })).toBeInTheDocument();
    expect(screen.getByText('暂无本集概要')).toBeInTheDocument();
    expect(screen.queryByText(/夜色警报|夜幕压低小区楼影/)).not.toBeInTheDocument();
  });

  it('loads the requested shot page independently from the selected episode', async () => {
    mocks.queryStoryboardWorkspace.mockResolvedValue({
      data: {
        current: 1,
        pageSize: 20,
        total: 21,
        storyboards: [
          {
            id: 101,
            shotNo: 1,
            episodeNo: 1,
            visualDescription: '第一页镜头',
            durationSeconds: 5,
          },
        ],
      },
    });
    render(<ProductionWorkbench />);

    await screen.findByRole('button', { name: '下一页' });
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => {
      expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledWith(1, {
        episodeNo: 1,
        current: 2,
        pageSize: 20,
      });
      expect(mocks.queryScriptPageWorkspace).not.toHaveBeenCalled();
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(1);
    });
  });

  it('keeps storyboards visible when optional media requests fail', async () => {
    mocks.queryAiImageTasks.mockRejectedValue(new Error('image unavailable'));
    mocks.queryAiVideoTasks.mockRejectedValue(new Error('video unavailable'));
    render(<ProductionWorkbench />);
    expect(await screen.findByLabelText('分镜1剧本原文')).toBeInTheDocument();
    expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledWith(1);
    expect(mocks.queryScriptWorkspace).not.toHaveBeenCalled();
  });

  it('ignores a stale episode page response after switching back', async () => {
    let resolveEpisodeTwo: (value: unknown) => void = () => undefined;
    let resolveEpisodeOne: (value: unknown) => void = () => undefined;
    const episodeTwo = new Promise((resolve) => {
      resolveEpisodeTwo = resolve;
    });
    const episodeOne = new Promise((resolve) => {
      resolveEpisodeOne = resolve;
    });
    render(<ProductionWorkbench />);
    await screen.findByText('分镜表');
    mocks.queryStoryboardWorkspace
      .mockImplementationOnce(() => episodeTwo)
      .mockImplementationOnce(() => episodeOne);

    fireEvent.click(screen.getByRole('button', { name: '2' }));
    fireEvent.click(screen.getByRole('button', { name: '1' }));
    await waitFor(() => {
      expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledTimes(3);
    });
    resolveEpisodeOne({
      data: {
        current: 1,
        pageSize: 20,
        total: 1,
        storyboards: [{ id: 301, shotNo: 1, episodeNo: 1, visualDescription: '最新第一集镜头', durationSeconds: 5 }],
      },
    });
    expect(await screen.findByText('最新第一集镜头')).toBeInTheDocument();
    resolveEpisodeTwo({
      data: {
        current: 1,
        pageSize: 20,
        total: 1,
        storyboards: [{ id: 302, shotNo: 1, episodeNo: 2, visualDescription: '过期第二集镜头', durationSeconds: 5 }],
      },
    });
    await new Promise((resolve) => setTimeout(resolve, 0));

    await waitFor(() => {
      expect(screen.getByText('最新第一集镜头')).toBeInTheDocument();
      expect(screen.queryByText('过期第二集镜头')).not.toBeInTheDocument();
    });
  });

  it('creates an AI video task from the edited storyboard prompt', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1视频提示词')).toBeInTheDocument();
    });

    const editor = screen.getByLabelText('分镜1视频提示词');
    editor.textContent = '新的分镜视频提示词';
    fireEvent.input(editor);
    fireEvent.click(screen.getByRole('button', { name: '生成分镜1视频' }));

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledWith(1, {
        storyboardId: 101,
        modelId: 10,
        prompt: expect.stringContaining('新的分镜视频提示词'),
        firstFrameUrl: 'https://example.com/shot-101-first.png',
        durationSeconds: 5,
        aspectRatio: '16:9',
        resolution: '1080p',
        generateAudio: false,
        watermark: true,
      });
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7002,
        expect.any(Function),
      );
    });
  });

  it('submits only the selected active episode and refreshes after completion', async () => {
    render(<ProductionWorkbench />);
    await screen.findByRole('button', { name: /生成本集分镜/ });

    fireEvent.click(screen.getByRole('button', { name: /生成本集分镜/ }));

    await waitFor(() => {
      expect(mocks.breakdownStoryboards).toHaveBeenCalledWith(1, {
        episodeId: 1001,
      });
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7100,
        expect.any(Function),
      );
      expect(mocks.queryStoryboardWorkspace.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('submits all active episodes and shows separate batch outcome totals', async () => {
    const batch = {
      id: 88,
      projectId: 1,
      name: '分镜批次-88',
      status: 'COMPLETED_WITH_FAILURES',
      total: 3,
      pending: 0,
      running: 0,
      succeeded: 1,
      warning: 1,
      failed: 1,
      businessCallCount: 3,
      technicalRetryCount: 2,
      settledPoints: 30,
      items: [],
      createdAt: '2026-09-05T19:00:00',
    };
    mocks.createStoryboardBatch.mockResolvedValue({ data: batch });
    render(<ProductionWorkbench />);

    fireEvent.click(await screen.findByRole('button', { name: /批量生成分镜/ }));

    await waitFor(() => {
      expect(mocks.createStoryboardBatch).toHaveBeenCalledWith(1, {
        episodeIds: [1001, 1002],
      });
    });
    expect(screen.getByText('成功 1 集')).toBeInTheDocument();
    expect(screen.getByText('有告警 1 集')).toBeInTheDocument();
    expect(screen.getByText('失败 1 集')).toBeInTheDocument();
    expect(screen.getByText('业务调用 3 次')).toBeInTheDocument();
    expect(screen.getByText('技术重试 2 次')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试告警|修复告警/ })).not.toBeInTheDocument();
  });

  it('shows successful storyboard warnings without offering a paid warning retry', async () => {
    setupWorkspaceResponse({
      storyboards: [
        {
          id: 401,
          shotNo: 1,
          storyboardNo: 1,
          episodeId: 1001,
          episodeNo: 1,
          shotType: '中景',
          visualDescription: '人物转身走向门口。',
          characters: '李慧',
          scene: '停车场',
          props: '',
          dialogue: '',
          durationSeconds: 12,
          imagePrompt: '',
          videoPrompt: '镜头提示',
          shotPlan: {
            storyboardNo: 1,
            durationSeconds: 12,
            diagnostics: {
              normalizationCount: 5,
              derivedSoundCount: 3,
              classificationWarnings: [
                { code: 'SOURCE_SPEAKER_UNCONFIRMED', segmentId: 'S0004' },
              ],
              actionWarnings: [],
              businessCallCount: 1,
              technicalRetryCount: 2,
            },
            warnings: [
              {
                code: 'ACTION_DENSITY',
                storyboardNo: 1,
                shotNo: 2,
                message: '动作描述可能包含多个连续节拍，请按需人工检查。',
              },
            ],
            shots: [],
          },
        },
      ],
    });
    render(<ProductionWorkbench />);
    await screen.findByText('分镜1');

    fireEvent.click(screen.getByRole('button', { name: /生成本集分镜/ }));

    expect(await screen.findByText('生成成功，有 1 条质量告警')).toBeInTheDocument();
    expect(screen.getByText('后端规范化 5 项')).toBeInTheDocument();
    expect(screen.getByText('派生声音 3 条')).toBeInTheDocument();
    expect(screen.getByText('来源分类告警 1 条')).toBeInTheDocument();
    expect(screen.getByText('业务调用 1 次')).toBeInTheDocument();
    expect(screen.getByText('技术重试 2 次')).toBeInTheDocument();
    expect(screen.getByText('镜头2：动作描述可能包含多个连续节拍，请按需人工检查。'))
      .toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试告警|修复告警/ })).not.toBeInTheDocument();
  });

  it('keeps one rich prompt editor and does not rebind deleted material text', async () => {
    const storyboard = {
      id: 301,
      shotNo: 1,
      storyboardNo: 1,
      episodeId: 1001,
      episodeNo: 1,
      shotType: '中景',
      visualDescription: '李慧站在停车场。',
      characters: '李慧',
      scene: '停车场',
      props: '',
      dialogue: '',
      durationSeconds: 13,
      imagePrompt: '',
      videoPrompt: '画风: 现代都市通用\n李慧\n镜头1 3s\n镜头2 3s',
      promptDocument: {
        version: 2,
        nodes: [
          { type: 'text', text: '画风: 现代都市通用\n' },
          {
            type: 'mention',
            mediaType: 'IMAGE',
            sourceType: 'ASSET_VISUAL_VARIANT',
            sourceId: 33,
            assetType: 'CHARACTER',
            assetId: 3,
            variantId: 33,
            displayName: '李慧',
          },
          { type: 'text', text: '\n镜头1 3s\n镜头2 3s' },
        ],
      },
      firstFrameUrl: null,
      currentVideoUrl: null,
    };
    setupWorkspaceResponse({ storyboards: [storyboard] });
    render(<ProductionWorkbench />);

    const editor = await screen.findByRole('textbox', {
      name: '分镜1视频提示词',
    });
    expect(screen.getAllByRole('textbox', { name: /视频提示词/ })).toHaveLength(1);
    expect(editor.querySelector('[data-asset-id="3"]')).not.toBeNull();
    expect(editor).toHaveTextContent('镜头1 3s');
    expect(editor).toHaveTextContent('镜头2 3s');

    editor.textContent = '李慧';
    fireEvent.input(editor);
    await waitFor(() => expect(editor).toHaveTextContent('李慧'));
    fireEvent.blur(editor);

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        301,
        expect.objectContaining({
          videoPrompt: '李慧',
          promptDocument: {
            version: 2,
            nodes: [{ type: 'text', text: '李慧' }],
          },
        }),
      );
    });
  });

  it('recovers shared video execution polling after a page reload', async () => {
    setupWorkspaceResponse({
      videoTasks: [
        {
          id: 9003,
          projectId: 1,
          storyboardId: 101,
          executionId: 7003,
          status: 'GENERATING',
          results: [],
        },
      ],
    });

    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7003,
        expect.any(Function),
      );
    });
  });

  it('uses the video domain control to cancel a shared execution', async () => {
    mocks.pollExecution.mockImplementation(
      async (
        _tenantId: number,
        executionId: number,
        onUpdate?: (task: any) => void,
      ) => {
        const task = { id: executionId, status: 'RUNNING', progress: 40 };
        onUpdate?.(task);
        return task;
      },
    );
    setupWorkspaceResponse({
      videoTasks: [
        {
          id: 9003,
          projectId: 1,
          storyboardId: 101,
          executionId: 7003,
          status: 'GENERATING',
          results: [],
        },
      ],
    });

    render(<ProductionWorkbench />);
    fireEvent.click(
      await screen.findByRole('button', { name: 'execution-cancel-7003' }),
    );

    await waitFor(() => {
      expect(mocks.cancelAiVideoTask).toHaveBeenCalledWith(1, 9003);
    });
  });

  it('creates a durable storyboard image task from the first-frame action', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: '生成首帧' }).length,
      ).toBeGreaterThan(0);
    });

    fireEvent.click(screen.getAllByRole('button', { name: '生成首帧' })[0]);

    await waitFor(() => {
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(1, {
        taskType: 'STORYBOARD_FIRST_FRAME',
        targetType: 'STORYBOARD',
        targetId: 101,
        prompt: expect.any(String),
        aspectRatio: '9:16',
        imageCount: 1,
        quality: 'STANDARD',
      });
      expect(mocks.queryAiImageTasks).toHaveBeenCalledWith(1, undefined);
    });
  });

  it('inherits project settings and submits the real video model id', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜2视频提示词')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '生成分镜2视频' }));

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          storyboardId: 102,
          modelId: 10,
          durationSeconds: 4,
          aspectRatio: '16:9',
          resolution: '1080p',
          generateAudio: false,
          watermark: true,
        }),
      );
    });
  });

  it('saves edited storyboard script fields without persisting display scaffolding', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1剧本原文')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('分镜1剧本原文'), {
      target: {
        value: '▲停车场\n新的画面描述。\n李慧VO: “新的对白。”',
      },
    });
    fireEvent.blur(screen.getByLabelText('分镜1剧本原文'));

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, {
        visualDescription: '新的画面描述。',
        scene: '停车场',
        dialogue: '李慧VO: “新的对白。”',
        characters: '李慧',
        props: '灰色轿车后备箱',
        durationSeconds: 5,
        imagePrompt: '停车场首帧提示词',
        videoPrompt: '画风：写实都市。镜头1 1s 远景摇镜停车场内灰色轿车。',
      });
      expect(mocks.queryStoryboardWorkspace).toHaveBeenLastCalledWith(1, { episodeNo: 1, current: 1, pageSize: 20 });
      expect(mocks.queryScriptPageWorkspace).not.toHaveBeenCalled();
      expect(mocks.queryAssetSettingsSummary).toHaveBeenCalledTimes(1);
    });
  });

  it('updates storyboard references through the left panel selectors', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1出镜角色')).toBeInTheDocument();
    });
    mocks.queryStoryboardWorkspace.mockClear();

    fireEvent.change(screen.getByLabelText('分镜1出镜角色'), {
      target: { value: '斌斌' },
    });

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({
          characters: '斌斌',
          scene: '停车场',
          props: '灰色轿车后备箱',
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
        }),
      );
    });

    fireEvent.change(screen.getByLabelText('分镜1场景'), {
      target: { value: '小区广场' },
    });
    fireEvent.change(screen.getByLabelText('分镜1场景道具'), {
      target: { value: '棒棒糖' },
    });

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({ scene: '小区广场' }),
      );
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({ props: '棒棒糖' }),
      );
      expect(mocks.queryStoryboardWorkspace).not.toHaveBeenCalled();
    });
  });

  it('retries only the page read after a successful creation cannot refresh', async () => {
    render(<ProductionWorkbench />);
    await screen.findByText('分镜1');
    mocks.createStoryboard.mockResolvedValueOnce({ data: null });
    mocks.queryStoryboardWorkspace.mockRejectedValueOnce(new Error('offline'));
    fireEvent.click(screen.getAllByRole('button', { name: '新增分镜' })[0]);
    fireEvent.click(await screen.findByRole('button', { name: '重试加载分镜' }));
    await waitFor(() => expect(mocks.queryStoryboardWorkspace).toHaveBeenCalledTimes(3));
    expect(mocks.createStoryboard).toHaveBeenCalledTimes(1);
  });

  it('falls back to 720p when the selected model does not support the current resolution', async () => {
    mocks.queryProject.mockResolvedValue({
      data: {
        id: 1,
        name: '4k 项目',
        aspectRatio: '16:9',
        videoResolution: '4k',
        videoGenerateAudio: true,
        videoWatermark: false,
      },
    });
    render(<ProductionWorkbench />);

    const selector = await screen.findByRole('combobox', {
      name: '视频生成模型',
    });
    await waitFor(() => expect(selector).toHaveValue('10'));
    fireEvent.change(selector, { target: { value: '11' } });

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: /视频参数/ })[0]).toHaveTextContent('720p');
    });
  });

  it('submits intelligent duration and per-storyboard video overrides', async () => {
    render(<ProductionWorkbench />);

    const duration = await screen.findByRole('spinbutton', {
      name: '分镜1视频时长',
    });
    fireEvent.change(duration, { target: { value: '-1' } });
    fireEvent.change(screen.getByRole('combobox', { name: '分镜1视频分辨率' }), {
      target: { value: '720p' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: '分镜1生成音频' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '分镜1视频水印' }));
    fireEvent.click(screen.getByRole('button', { name: '生成分镜1视频' }));

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          storyboardId: 101,
          durationSeconds: -1,
          resolution: '720p',
          generateAudio: true,
          watermark: false,
        }),
      );
    });
  });

  it('continues successful batch video submissions when one storyboard fails', async () => {
    mocks.createAiVideoTask
      .mockRejectedValueOnce(new Error('invalid storyboard'))
      .mockResolvedValueOnce({
        data: { id: 9300, storyboardId: 102, executionId: 7300, results: [] },
      });
    render(<ProductionWorkbench />);

    fireEvent.click(
      await screen.findByRole('button', { name: /批量生成视频/ }),
    );

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledTimes(2);
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7300,
        expect.any(Function),
      );
    });
  });

  it('inserts typed image video and audio references from the existing material controls', async () => {
    const storyboard = {
      id: 301,
      shotNo: 1,
      storyboardNo: 1,
      episodeId: 1001,
      episodeNo: 1,
      shotType: '中景',
      visualDescription: '李慧站在停车场。',
      characters: '李慧',
      scene: '停车场',
      props: '',
      dialogue: '',
      durationSeconds: 13,
      imagePrompt: '',
      videoPrompt: '参考素材：',
      promptDocument: {
        version: 2,
        nodes: [{ type: 'text', text: '参考素材：' }],
      },
    };
    const character = {
      id: 3,
      name: '李慧',
      roleType: 'LEAD',
      gender: '女',
      ageRange: '常规',
      identity: '母亲',
      personality: [],
      appearance: '短发',
      prompt: '李慧提示词',
      visual: {
        variantCount: 1,
        variants: [
          {
            id: 33,
            assetType: 'CHARACTER',
            assetId: 3,
            name: '李慧主形态',
            sourceType: 'USER',
            generationStatus: 'SUCCEEDED',
            currentImageResultId: 77,
            currentImageUrl: '/lihui.png',
            primary: true,
            usable: true,
          },
        ],
        generationSummary: {},
        episodeBindings: [],
      },
    };
    setupWorkspaceResponse({
      storyboards: [storyboard],
      characters: [character],
      videoTasks: [
        {
          id: 801,
          storyboardId: 301,
          status: 'SUCCEEDED',
          results: [
            {
              id: 811,
              materialId: 501,
              videoUrl: '/reference.mp4',
              status: 'ACTIVE',
            },
          ],
        },
      ],
      voiceTasks: [
        {
          id: 901,
          storyboardId: 301,
          speakerName: '旁白',
          status: 'SUCCEEDED',
          results: [
            {
              id: 911,
              materialId: 601,
              audioUrl: '/reference.mp3',
              status: 'ACTIVE',
            },
          ],
        },
      ],
    });
    render(<ProductionWorkbench />);

    await screen.findByRole('textbox', { name: '分镜1视频提示词' });
    fireEvent.click(screen.getAllByRole('button', { name: '引用素材 李慧主形态' })[0]);
    fireEvent.click(screen.getAllByRole('button', { name: '引用素材 分镜1视频' })[0]);
    fireEvent.click(screen.getAllByRole('button', { name: '引用素材 旁白音频' })[0]);

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenLastCalledWith(
        1,
        301,
        expect.objectContaining({
          promptDocument: expect.objectContaining({
            version: 2,
            nodes: expect.arrayContaining([
              expect.objectContaining({ mediaType: 'IMAGE', sourceId: 33 }),
              expect.objectContaining({ mediaType: 'VIDEO', sourceId: 501 }),
              expect.objectContaining({ mediaType: 'AUDIO', sourceId: 601 }),
            ]),
          }),
        }),
      );
    });
  });

  it('adds and copies storyboards using the existing storyboard backend', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByText('分镜1')).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByRole('button', { name: '新增分镜' })[0]);

    await waitFor(() => {
      expect(mocks.createStoryboard).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          episodeNo: 1,
          shotNo: 3,
          visualDescription: '新增镜头画面描述',
          durationSeconds: 5,
        }),
      );
    });

    fireEvent.click(screen.getAllByRole('button', { name: '复制分镜' })[0]);

    await waitFor(() => {
      expect(mocks.createStoryboard).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          episodeNo: 1,
          shotNo: 4,
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
          characters: '李慧',
          scene: '停车场',
          props: '灰色轿车后备箱',
        }),
      );
    });
  });

  it('renders selected generated storyboard video when available', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1成片预览')).toBeInTheDocument();
    });

    expect(screen.getByLabelText('分镜1成片预览')).toHaveAttribute(
      'src',
      'https://example.com/shot-101.mp4',
    );
  });

  it('shows an empty state instead of sample storyboards when backend returns no shots', async () => {
    setupWorkspaceResponse({
      characters: [],
      scenes: [],
      props: [],
      storyboards: [],
      imageTasks: [],
      videoTasks: [],
    });

    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(
        screen.getByText('暂无分镜，请先完成剧本分镜拆解'),
      ).toBeInTheDocument();
    });

    expect(screen.queryByText('斌斌')).not.toBeInTheDocument();
    expect(screen.queryByText('分镜1')).not.toBeInTheDocument();
  });
});
