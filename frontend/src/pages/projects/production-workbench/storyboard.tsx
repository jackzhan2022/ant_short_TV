import StoryboardCard from './StoryboardCard';
import { StoryboardCardProvider } from './storyboard-card-context';
import useStoryboardEditorController from './useStoryboardEditorController';
import useStoryboardMediaController from './useStoryboardMediaController';
import { getStoryboardSavePayload, getStoryboardScriptText, getStoryboardPrompt,
  parseStoryboardScriptText } from './storyboard-model';
export { preserveReferenceClientKeys } from './storyboard-model';
import PointEstimateButton from '@/components/PointEstimateButton';
import type { PointEstimateOperation } from '@/services/point-estimate';
import {
  BarsOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useLocation, useOutletContext, useParams } from '@umijs/max';
import {
  App,
  Button,
  Empty,
  Flex,
  Modal,
  Select,
  Spin,
  Tag,
  Typography,
} from 'antd';
import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import type { ProductionWorkbenchOutletContext } from './index';
import ViewportMount from './ViewportMount';
import { queryProjectMetadata } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import { pollingDelay } from '@/utils/polling';
import {
  confirmPointEstimates,
  isPointEstimateCancelled,
  PointEstimateCancelledError,
} from '@/services/point-estimate';
import { readStoryboardTarget, STORYBOARD_PAGE_SIZE } from './video-workbench-state';
import './storyboard.css';
import { stopActiveVideo } from '@/components/ClickToPlayVideo';
import VideoFrameCaptureModal from './VideoFrameCaptureModal';
import SubtitleEraseModal from './SubtitleEraseModal';
import useSubtitleEraseTasks from './useSubtitleEraseTasks';
import AssetVariantGenerationModal, {
  type AssetVariantGenerationValues,
} from './AssetVariantGenerationModal';
import {
  type ProjectModelOption,
  queryProjectAiConfig,
  queryProjectAiModels,
} from './ai-config/service';
import { useStoryboardVideoSettings, type VideoGenerationSettings, } from './useStoryboardVideoSettings';
import BatchVideoGenerationModal from './BatchVideoGenerationModal';
import StoryboardPositionRail from './StoryboardPositionRail';
import StoryboardVideoEmptyState from './StoryboardVideoEmptyState';
import type {
  AiImageTask,
  AiVideoResult,
  AiVideoTask,
  AssetVisualWorkspace,
  CreateAiVideoTaskValues,
  ProductionWorkspaceState,
  SaveStoryboardValues,
  ScriptEpisode,
  StoryboardAssetReference,
  StoryboardBatch,
  StoryboardPromptDocument,
  StoryboardPromptNode,
  StoryboardShot,
  StoryboardMediaSummary,
  StoryboardNavigationItem,
  StoryboardWorkspacePage,
  VisualVariant,
} from './service';
import {
  breakdownStoryboards,
  createAiImageTask,
  createAiVideoTask,
  createStoryboard,
  createStoryboardBatch,
  deleteStoryboard,
  queryAiImageTask,
  queryAssetSettingsSummary,
  queryAssetVisualWorkspace,
  queryLatestStoryboardBatch,
  queryStoryboardBatch,
  queryStoryboardMedia,
  queryStoryboardEditorPage,
  queryStoryboardEpisodeDetails,
  regenerateAiVideoTask,
  updateVisualVariant,
} from './service';
import { hasImageReference, hydratePromptDocument,
  type NamedImageReference,
  promptText, withFirstFrameReference,
} from './storyboard-references';

type PromptReferenceOption = Extract<
  StoryboardPromptNode,
  { type: 'mention' }
>;
type VideoSubmission = {
  storyboard: StoryboardShot;
  scriptText?: string;
  promptDocument: StoryboardPromptDocument;
  values: CreateAiVideoTaskValues;
};
type ReferenceGenerationTarget = {
  assetType: StoryboardAssetReference['assetType'];
  assetId: number;
  variant: VisualVariant;
  primaryImageUrl?: string | null;
  primaryImageThumbnailUrl?: string | null;
  generationKey: string;
};
const successStatuses = ['SUCCESS', 'SUCCEEDED'];
const terminalImageTaskStatuses = new Set(['SUCCESS', 'FAILED', 'CANCELED']);
const terminalStoryboardBatchStatuses = new Set([
  'SUCCEEDED',
  'SUCCEEDED_WITH_WARNING',
  'COMPLETED_WITH_FAILURES',
  'FAILED',
]);
const storyboardPageSize = STORYBOARD_PAGE_SIZE;
const navigationFromPage = (page: StoryboardWorkspacePage): StoryboardNavigationItem[] =>
  page.storyboardNavigation || page.storyboards.filter((shot) => shot.episodeNo === page.episodeNo).map((shot) => ({
    id: shot.id, shotNo: shot.shotNo, storyboardNo: shot.storyboardNo,
    hasVideo: Boolean(shot.currentVideoResultId || shot.currentVideoUrl),
  }));

const normalizeVideoDuration = (
  durationSeconds: number | undefined,
  constraints?: ProjectModelOption['constraints'],
) => {
  const min = constraints?.duration?.min ?? 4;
  const max = constraints?.duration?.max ?? 15;
  if (durationSeconds === -1) return -1;
  return Math.min(max, Math.max(min, Math.round(durationSeconds || min)));
};

const ProductionWorkbenchStoryboard = () => {
  const location = useLocation();
  const navigationTarget = readStoryboardTarget(location.search);
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const [frameCapture, setFrameCapture] = useState<{ resultId: number; storyboardNo: number }>();
  useEffect(() => setFrameCapture(undefined), [projectId]);
  const [subtitleErase, setSubtitleErase] = useState<{ resultId: number; storyboardNo: number }>();
  const [erasedPreviews, setErasedPreviews] = useState<Record<number, AiVideoResult>>({});
  useEffect(() => { setSubtitleErase(undefined); setErasedPreviews({}); }, [projectId]);
  const { message, modal } = App.useApp();
  const [uploadedImageReferences, setUploadedImageReferences] = useState<PromptReferenceOption[]>([]);
  useEffect(() => setUploadedImageReferences([]), [projectId]);
  const [activeEpisode, setActiveEpisode] = useState(1);
  const [storyboardPage, setStoryboardPage] = useState(1);
  const [storyboardTotal, setStoryboardTotal] = useState(0);
  const [storyboardLoading, setStoryboardLoading] = useState(true);
  const [storyboardLoadFailed, setStoryboardLoadFailed] = useState(false);
  const [storyboardLoadAttempt, setStoryboardLoadAttempt] = useState(0);
  const [episodeDetailsOpen, setEpisodeDetailsOpen] = useState(false);
  const [mutationRefreshFailed, setMutationRefreshFailed] = useState(false);
  const outletContext = useOutletContext<ProductionWorkbenchOutletContext | null | undefined>();
  const [standaloneProject, setStandaloneProject] = useState<Project>();
  const project = outletContext
    ? (outletContext.project?.id === projectId ? outletContext.project : undefined)
    : (standaloneProject?.id === projectId ? standaloneProject : undefined);
  const [episodeDetails, setEpisodeDetails] = useState<{
    projectId: number;
    episodeId: number;
    episode: ScriptEpisode;
  }>();
  const [videoModels, setVideoModels] = useState<ProjectModelOption[]>([]);
  const [imageModels, setImageModels] = useState<ProjectModelOption[]>([]);
  const [configuredImageModelId, setConfiguredImageModelId] = useState<number>();
  const [selectedModelId, setSelectedModelId] = useState<number>();
  const explicitVideoModelSelection = useRef(false);
  const [assetVisuals, setAssetVisuals] = useState<Record<string, AssetVisualWorkspace>>({});
  const [referenceGenerationTarget, setReferenceGenerationTarget] =
    useState<ReferenceGenerationTarget>();
  const [referenceGenerationValues, setReferenceGenerationValues] =
    useState<AssetVariantGenerationValues>({
      prompt: '',
      modelId: undefined,
      aspectRatio: '16:9',
      imageCount: 1,
    });
  const [referenceGenerationSubmitting, setReferenceGenerationSubmitting] = useState(false);
  const [generatingReferenceKeys, setGeneratingReferenceKeys] =
    useState<Set<string>>(() => new Set());
  const [referenceVisualPollTargets, setReferenceVisualPollTargets] = useState<
    Record<string, Pick<ReferenceGenerationTarget, 'assetType' | 'assetId'>>
  >({});
  const requestedVisuals = useRef(new Set<string>());
  const visualProjectId = useRef(projectId);
  const referenceGenerationRequestId = useRef(0);
  const [storyboardExecution, setStoryboardExecution] =
    useState<API.AiExecutionResponse>();
  const [storyboardExecutionEpisodeId, setStoryboardExecutionEpisodeId] = useState<number>();
  const [storyboardBusy, setStoryboardBusy] = useState(false);
  const [storyboardBatch, setStoryboardBatch] = useState<StoryboardBatch>();
  const [storyboardBatchBusy, setStoryboardBatchBusy] = useState(false);
  const [videoBatchRetryBusy, setVideoBatchRetryBusy] = useState(false);
  const [storyboardNavigation, setStoryboardNavigation] = useState<StoryboardNavigationItem[]>([]);
  const [activeStoryboardId, setActiveStoryboardId] = useState<number>();
  const workbenchRef = useRef<HTMLDivElement>(null);
  const pendingStoryboardScroll = useRef<number | undefined>(undefined);
  const [videoBatchOpen, setVideoBatchOpen] = useState(false);
  const [videoBatchLoading, setVideoBatchLoading] = useState(false);
  const [videoBatchError, setVideoBatchError] = useState(false);
  const [videoBatchSubmitting, setVideoBatchSubmitting] = useState(false);
  const [videoBatchCandidates, setVideoBatchCandidates] = useState<StoryboardShot[]>([]);
  const [videoBatchSelectedIds, setVideoBatchSelectedIds] = useState<number[]>([]);
  const videoBatchRequestId = useRef(0);
  const videoBatchSubmission = useRef(false);

  useEffect(() => {
    setVideoBatchOpen(false);
    setVideoBatchSelectedIds([]);
    videoBatchSubmission.current = false;
    setVideoBatchSubmitting(false);
    setVideoBatchRetryBusy(false);
    return () => { videoBatchRequestId.current += 1; };
  }, [projectId, activeEpisode, storyboardPage]);
  const reservedShotNos = useRef<Record<number, number>>({});
  const storyboardRequestId = useRef(0);
  const storyboardView = useRef({ episodeNo: 1, current: 1, loading: true });
  const storyboardGenerationScope = useRef({ projectId, active: true, controller: new AbortController() });
  useLayoutEffect(() => {
    const scope = { projectId, active: true, controller: new AbortController() };
    storyboardGenerationScope.current = scope;
    setStoryboardExecution(undefined);
    setStoryboardExecutionEpisodeId(undefined);
    setStoryboardBusy(false);
    setStoryboardBatch(undefined);
    setStoryboardBatchBusy(false);
    return () => {
      scope.active = false;
      scope.controller.abort();
      storyboardRequestId.current += 1;
    };
  }, [projectId]);
  const [workspace, setWorkspace] = useState<ProductionWorkspaceState>({
    projectId: projectId || 0,
    script: null,
    versions: [],
    characters: [],
    scenes: [],
    props: [],
    storyboards: [],
  });
  const patchStoryboard = (id: number, values: Partial<StoryboardShot>) => setWorkspace(previous => ({
    ...previous, storyboards: previous.storyboards.map(shot => shot.id === id ? { ...shot, ...values } : shot),
  }));
  const editor = useStoryboardEditorController(projectId, {
    readShot: id => workspace.storyboards.find(shot => shot.id === id),
    patchShot: patchStoryboard,
    refresh: () => refreshAfterMutation(),
    message,
  });
  const { drafts: draftStore, sources: draftSources, loadDrafts, persist: persistStoryboard } = editor;
  const setDrafts = draftStore.set;
  const media = useStoryboardMediaController(projectId, workspace.storyboards, {
    onStoryboardPatch: patchStoryboard, onAssetVisuals: setAssetVisuals,
  });
  const { imageTasks, videoTasks, voiceTasks, videoHistories, setImageTasks, setVideoTasks,
    setVideoHistories, reloadVideoTasks, followVideoExecution } = media;
  const mediaScopeKey = workspace.storyboards.map(shot => shot.id).join(',');
  const videoSettings = useStoryboardVideoSettings(projectId, (storyboardId, settings) => {
    setWorkspace((previous) => ({ ...previous, storyboards: previous.storyboards.map((item) =>
      item.id === storyboardId ? { ...item, videoGenerationSettings: settings } : item) }));
  }, () => message.error('视频参数保存失败，请重试'));
  useEffect(() => {
    visualProjectId.current = projectId;
    requestedVisuals.current.clear();
    setAssetVisuals({});
    referenceGenerationRequestId.current += 1;
    setReferenceGenerationTarget(undefined);
    setReferenceGenerationSubmitting(false);
    setGeneratingReferenceKeys(new Set());
    setReferenceVisualPollTargets({});
  }, [projectId]);

  const loadAssetVariants = async (
    type: StoryboardAssetReference['assetType'], assetId: number, selectedVariantId?: number | null,
    current = 1, pageSize = 20,
  ) => {
    const key = `${type}:${assetId}`;
    const assets = type === 'CHARACTER' ? characters : type === 'SCENE' ? scenes : props;
    const visual = assets.find((asset) => asset.id === assetId)?.visual;
    if (current === 1 && visual && !visual.summaryOnly
      && visual.variants.length >= (visual.total || visual.variantCount || 1)) return;
    const requestKey = `${key}:${current}:${pageSize}:${selectedVariantId || ''}`;
    if (requestedVisuals.current.has(requestKey)) return;
    requestedVisuals.current.add(requestKey);
    try {
      const response = await queryAssetVisualWorkspace(projectId, type, assetId, {
        current, pageSize, selectedVariantId: selectedVariantId ?? undefined,
      });
      if (visualProjectId.current === projectId && response.data) {
        setAssetVisuals((previous) => {
          const existing = previous[key];
          const variants = [...new Map([
            ...(existing?.variants || []),
            response.data.primaryVariant,
            response.data.selectedVariant,
            ...response.data.variants,
          ].filter((variant): variant is VisualVariant => Boolean(variant))
            .map((variant) => [variant.id, variant])).values()];
          return {
            ...previous,
            [key]: { ...existing, ...response.data, variants, summaryOnly: false },
          };
        });
      }
    } catch {
      requestedVisuals.current.delete(requestKey);
      message.error('资产形态加载失败');
    }
  };

  useEffect(() => {
    if (!projectId || outletContext != null) return;
    let active = true;
    void queryProjectMetadata(projectId).then((response) => {
      if (active) setStandaloneProject(response.data);
    }).catch(() => {
      if (active) message.error('项目信息加载失败');
    });
    return () => { active = false; };
  }, [projectId, outletContext != null]);

  useEffect(() => {
    if (!projectId) return;
    let active = true;
    setDrafts({});
    draftSources.current = {};
    setWorkspace((previous) => previous.projectId === projectId ? previous : {
      projectId, script: null, versions: [], characters: [], scenes: [], props: [], storyboards: [],
    });
    setVideoModels([]);
    setImageModels([]);
    setSelectedModelId(undefined);
    explicitVideoModelSelection.current = false;
    setConfiguredImageModelId(undefined);
    const modelsRequest = queryProjectAiModels(projectId);
    const configRequest = queryProjectAiConfig(projectId);
    void modelsRequest.then((response) => {
      if (!active) return;
      const models = response.data?.videoModels || [];
      setVideoModels(models);
      setImageModels(response.data?.imageModels || []);
    }).catch(() => {
      if (active) message.error('视频模型加载失败');
    });
    void configRequest.then((response) => {
      if (!active) return;
      setConfiguredImageModelId(response.data?.imageModelId || undefined);
    }).catch(() => {
      if (active) message.error('视频模型加载失败');
    });
    void Promise.all([modelsRequest, configRequest]).then(([modelsResponse, configResponse]) => {
      if (!active || explicitVideoModelSelection.current) return;
      setSelectedModelId(configResponse.data?.videoModelId || modelsResponse.data?.videoModels?.[0]?.id);
    }).catch(() => undefined);
    void queryAssetSettingsSummary(projectId).then((response) => {
      if (!active) return;
      setWorkspace((previous) => ({
        ...previous,
        characters: response.data?.characters || [],
        scenes: response.data?.scenes || [],
        props: response.data?.props || [],
      }));
    }).catch(() => {
      if (active) message.error('分镜素材加载失败');
    });
    void queryLatestStoryboardBatch(projectId).then((response) => {
      if (active) setStoryboardBatch(response.data || undefined);
    }).catch(() => undefined);
    return () => { active = false; };
  }, [projectId]);

  useEffect(() => {
    if (!projectId) return;
    let active = true;
    const requestId = ++storyboardRequestId.current;
    storyboardView.current = {
      episodeNo: navigationTarget.episodeNo || 1,
      current: navigationTarget.current || 1,
      loading: true,
    };
    setStoryboardLoading(true);
    setStoryboardLoadFailed(false);
    const initialPage = navigationTarget.episodeNo
      ? queryStoryboardEditorPage(projectId, {
        episodeNo: navigationTarget.episodeNo,
        current: navigationTarget.current,
        pageSize: storyboardPageSize,
        ...(navigationTarget.storyboardId ? { storyboardId: navigationTarget.storyboardId } : {}),
      })
      : queryStoryboardEditorPage(projectId);
    void initialPage
      .then((storyboardResponse) => {
        if (!active || storyboardRequestId.current !== requestId) return;
        const shots = storyboardResponse.data?.storyboards || [];
        const episodes = storyboardResponse.data?.episodes || [];
        const episodeNo = storyboardResponse.data?.episodeNo || episodes[0]?.episodeNo || shots[0]?.episodeNo || 1;
        storyboardView.current = { episodeNo, current: storyboardResponse.data?.current || 1, loading: false };
        setWorkspace((previous) => ({ ...previous, projectId, storyboards: shots, episodes }));
        setStoryboardPage(storyboardResponse.data?.current || 1);
        setStoryboardTotal(storyboardResponse.data?.total ?? shots.length);
        setStoryboardNavigation(navigationFromPage(storyboardResponse.data));
        setActiveEpisode(episodeNo);
        loadDrafts(shots);
        setStoryboardLoading(false);
      })
      .catch(() => {
        if (active && storyboardRequestId.current === requestId) {
          setStoryboardLoadFailed(true);
          setStoryboardLoading(false);
          message.error('分镜页面加载失败');
        }
      });
    return () => {
      active = false;
    };
  }, [projectId, storyboardLoadAttempt, navigationTarget.episodeNo, navigationTarget.current, navigationTarget.storyboardId]);

  useEffect(() => {
    if (storyboardLoading || !navigationTarget.storyboardId) return;
    const target = document.getElementById(`storyboard-${navigationTarget.storyboardId}`);
    target?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    target?.focus({ preventScroll: true });
    if (target) setActiveStoryboardId(navigationTarget.storyboardId);
  }, [storyboardLoading, navigationTarget.storyboardId, navigationTarget.episodeNo, activeEpisode]);

  useEffect(() => {
    if (
      !storyboardBatch?.id ||
      (storyboardBatch.projectId && storyboardBatch.projectId !== projectId) ||
      storyboardBatchBusy ||
      terminalStoryboardBatchStatuses.has(storyboardBatch.status)
    ) {
      return;
    }
    let active = true;
    const scope = storyboardGenerationScope.current;
    const controller = new AbortController();
    const follow = async () => {
      while (active) {
        const response = await queryStoryboardBatch(projectId, storyboardBatch.id, controller.signal);
        if (!active || !scope.active || !response.data) return;
        setStoryboardBatch(response.data);
        if (terminalStoryboardBatchStatuses.has(response.data.status)) {
          await reloadWorkspace(response.data.items.map((item) => item.episodeNo), scope);
          return;
        }
        await pollingDelay(1500, controller.signal);
      }
    };
    void follow().catch(() => {
      if (active && !controller.signal.aborted) message.error('分镜批次状态刷新失败');
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [projectId, storyboardBatch?.id, storyboardBatchBusy]);

  const visualFor = (key: string, existing?: AssetVisualWorkspace) => {
    const loaded = assetVisuals[key];
    return loaded ? {
      ...existing, ...loaded,
      variants: [...new Map([
        loaded.primaryVariant,
        loaded.selectedVariant,
        ...(loaded.variants || []),
      ].filter((variant): variant is VisualVariant => Boolean(variant))
        .map((variant) => [variant.id, variant])).values()],
      resolvedImageUrl: loaded.resolvedImageUrl ?? existing?.resolvedImageUrl,
      resolvedImageThumbnailUrl:
        loaded.resolvedImageThumbnailUrl ?? existing?.resolvedImageThumbnailUrl,
      resolvedImageSource: loaded.resolvedImageSource ?? existing?.resolvedImageSource,
    } : existing;
  };
  const characters = useMemo(() => workspace.characters.map((asset) => ({
    ...asset, visual: visualFor(`CHARACTER:${asset.id}`, asset.visual),
  })), [workspace.characters, assetVisuals]);
  const scenes = useMemo(() => workspace.scenes.map((asset) => ({
    ...asset, visual: visualFor(`SCENE:${asset.id}`, asset.visual),
  })), [workspace.scenes, assetVisuals]);
  const props = useMemo(() => workspace.props.map((asset) => ({
    ...asset, visual: visualFor(`PROP:${asset.id}`, asset.visual),
  })), [workspace.props, assetVisuals]);
  const generatingVisualTargets = useMemo(() => [
    ...characters.map((asset) => ({ assetType: 'CHARACTER' as const, asset })),
    ...scenes.map((asset) => ({ assetType: 'SCENE' as const, asset })),
    ...props.map((asset) => ({ assetType: 'PROP' as const, asset })),
  ].filter(({ asset }) => asset.visual?.variants.some(
    (variant) => variant.generationStatus === 'GENERATING',
  )), [characters, scenes, props]);
  const visualPollTargets = useMemo(() => {
    const targets = new Map<string, Pick<ReferenceGenerationTarget, 'assetType' | 'assetId'>>();
    for (const { assetType, asset } of generatingVisualTargets) {
      targets.set(`${assetType}:${asset.id}`, { assetType, assetId: asset.id });
    }
    for (const [key, target] of Object.entries(referenceVisualPollTargets)) {
      targets.set(key, target);
    }
    return [...targets.entries()].map(([key, target]) => ({ key, ...target }));
  }, [generatingVisualTargets, referenceVisualPollTargets]);

  useEffect(() => {
    if (!projectId || !visualPollTargets.length) return;
    const controller = new AbortController();
    let active = true;
    let timer: number | undefined;
    const refreshGeneratingVisuals = async () => {
      const results = await Promise.allSettled(visualPollTargets.map(async (target) => {
        const response = await queryAssetVisualWorkspace(
          projectId,
          target.assetType,
          target.assetId,
          {}, controller.signal,
        );
        return { key: target.key, visual: response.data };
      }));
      if (!active || visualProjectId.current !== projectId) return;
      const refreshed = results.flatMap((result) =>
        result.status === 'fulfilled' && result.value.visual ? [result.value] : []);
      if (refreshed.length) {
        setAssetVisuals((previous) => ({
          ...previous,
          ...Object.fromEntries(refreshed.map(({ key, visual }) => [key, visual])),
        }));
        setReferenceVisualPollTargets((previous) => {
          const next = { ...previous };
          let changed = false;
          for (const { key, visual } of refreshed) {
            if (!visual.variants.some((variant) => variant.generationStatus === 'GENERATING')) {
              delete next[key];
              changed = changed || key in previous;
            }
          }
          return changed ? next : previous;
        });
      }
      if (active) timer = window.setTimeout(refreshGeneratingVisuals, 1500);
    };
    timer = window.setTimeout(refreshGeneratingVisuals, 1500);
    return () => {
      active = false;
      controller.abort();
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [projectId, visualPollTargets]);

  const selectedFirstFrame = (storyboardId: number) => imageTasks
    .filter((task) => task.targetType === 'STORYBOARD' && task.targetId === storyboardId
      && successStatuses.includes(task.status))
    .flatMap((task) => task.results || [])
    .find((result) => result.selected && result.status === 'ACTIVE' && result.imageUrl);
  const referenceOptions = useMemo<
    Record<'IMAGE' | 'VIDEO' | 'AUDIO', PromptReferenceOption[]>
  >(() => {
    const images = [...characters, ...scenes, ...props].flatMap((asset) =>
      (asset.visual?.variants || [])
        .filter((variant) => variant.usable && variant.currentImageResultId && variant.currentImageUrl)
        .map((variant) => ({
          type: 'mention' as const,
          mediaType: 'IMAGE' as const,
          sourceType: 'ASSET_VISUAL_VARIANT',
          sourceId: variant.id,
          assetType: variant.assetType,
          assetId: variant.assetId,
          variantId: variant.id,
          displayName: variant.name || asset.name,
        })),
    );
    const firstFrames: PromptReferenceOption[] = workspace.storyboards
      .filter((storyboard) => storyboard.firstFrameUrl || selectedFirstFrame(storyboard.id))
      .map((storyboard) => ({
        type: 'mention', mediaType: 'IMAGE', sourceType: 'STORYBOARD_FIRST_FRAME',
        sourceId: storyboard.id,
        displayName: `分镜${storyboard.storyboardNo || storyboard.shotNo}首帧`,
      }));
    const videos = videoTasks.flatMap((task) => {
      const storyboard = workspace.storyboards.find(
        (item) => item.id === task.storyboardId,
      );
      return (task.results || [])
        .filter((result) => result.materialId)
        .map((result) => ({
          type: 'mention' as const,
          mediaType: 'VIDEO' as const,
          sourceType: 'VIDEO_MATERIAL',
          sourceId: result.materialId as number,
          displayName: `分镜${storyboard?.storyboardNo || storyboard?.shotNo || task.storyboardId}视频`,
        }));
    });
    const audio = voiceTasks.flatMap((task) =>
      (task.results || [])
        .filter((result) => result.materialId)
        .map((result) => ({
          type: 'mention' as const,
          mediaType: 'AUDIO' as const,
          sourceType: 'AUDIO_MATERIAL',
          sourceId: result.materialId as number,
          displayName: `${task.speakerName || '分镜旁白'}音频`,
        })),
    );
    const uploadedImages = [...new Map([
      ...workspace.storyboards.flatMap((shot) => shot.promptDocument?.nodes || [])
        .filter((node): node is PromptReferenceOption => node.type === 'mention' && node.sourceType === 'IMAGE_MATERIAL'),
      ...uploadedImageReferences,
    ].map((reference) => [reference.sourceId, reference])).values()];
    return { IMAGE: [...images, ...firstFrames, ...uploadedImages], VIDEO: videos, AUDIO: audio };
  }, [characters, scenes, props, imageTasks, videoTasks, voiceTasks, workspace.storyboards, uploadedImageReferences]);

  const referenceThumbnails = useMemo(() => {
    const thumbnails = new Map<string, string>();
    for (const reference of referenceOptions.IMAGE) {
      if (reference.sourceType === 'IMAGE_MATERIAL') {
        thumbnails.set(`IMAGE_MATERIAL:${reference.sourceId}`,
          `/api/projects/${projectId}/image-materials/${reference.sourceId}/thumbnail`);
      }
    }
    for (const storyboard of workspace.storyboards) {
      const thumbnail = storyboard.firstFrameThumbnailUrl || selectedFirstFrame(storyboard.id)?.thumbnailUrl;
      if (thumbnail) thumbnails.set(`STORYBOARD_FIRST_FRAME:${storyboard.id}`, thumbnail);
      for (const reference of storyboard.assetReferences || []) {
        if (reference.variantId && reference.imageThumbnailUrl) {
          thumbnails.set(`ASSET_VISUAL_VARIANT:${reference.variantId}`, reference.imageThumbnailUrl);
        }
      }
    }
    for (const asset of [...characters, ...scenes, ...props]) {
      for (const variant of [asset.visual?.primaryVariant, asset.visual?.selectedVariant, ...(asset.visual?.variants || [])]) {
        if (variant?.currentImageThumbnailUrl) {
          thumbnails.set(`ASSET_VISUAL_VARIANT:${variant.id}`, variant.currentImageThumbnailUrl);
        }
      }
    }
    return thumbnails;
  }, [characters, scenes, props, imageTasks, workspace.storyboards, projectId, referenceOptions.IMAGE]);

  const promptFor = (storyboard: StoryboardShot) => {
    const draft = draftStore.get(storyboard.id);
    const episodeId = workspace.episodes?.find((episode) => episode.episodeNo === storyboard.episodeNo)?.episodeId;
    const bindings: NamedImageReference[] = [...characters, ...scenes, ...props].flatMap((asset) => {
      const variants = asset.visual?.variants.filter((variant) =>
        variant.usable && variant.currentImageResultId && variant.currentImageUrl,
      ) || [];
      const preferredId = asset.visual?.episodeBindings.find((binding) =>
        binding.episodeId === episodeId && binding.preferred && binding.status === 'ACTIVE',
      )?.variantId;
      const variant = variants.find((item) => item.id === preferredId)
        || variants.find((item) => item.primary)
        || (variants.length === 1 ? variants[0] : undefined);
      return variant ? [{
        assetName: asset.name, type: 'mention' as const, mediaType: 'IMAGE' as const,
        sourceType: 'ASSET_VISUAL_VARIANT', sourceId: variant.id,
        assetType: variant.assetType, assetId: variant.assetId, variantId: variant.id,
        displayName: variant.name || asset.name,
      }] : [];
    });
    const formalImages: NamedImageReference[] = (storyboard.assetReferences ?? []).flatMap((reference) =>
      reference.resolutionStatus === 'RESOLVED' && reference.variantId && reference.assetId ? [{
        assetName: reference.sourceName || reference.assetName || '', type: 'mention' as const, mediaType: 'IMAGE' as const,
        sourceType: 'ASSET_VISUAL_VARIANT', sourceId: reference.variantId, assetId: reference.assetId,
        assetType: reference.assetType, variantId: reference.variantId, displayName: reference.variantName || reference.assetName || '',
      }] : []);
    const productionBindings = [...bindings.filter((binding) => !formalImages.some((reference) => reference.assetId === binding.assetId && reference.assetType === binding.assetType)), ...formalImages];
    let document = hydratePromptDocument(
      draft?.promptDocument || storyboard.promptDocument,
      draft?.videoPrompt || getStoryboardPrompt(storyboard),
      productionBindings,
    );
    document = { ...document, nodes: document.nodes.map((node) => node.type === 'mention' && node.assetId != null
      && storyboard.assetReferences?.some((reference) => reference.assetId === node.assetId && reference.assetType === node.assetType && reference.resolutionStatus === 'SCENE_INCLUDED')
      ? { type: 'text' as const, text: node.displayName } : node) };
    if (!hasImageReference(document) && (storyboard.firstFrameUrl || selectedFirstFrame(storyboard.id))) {
      document = withFirstFrameReference(
        document, storyboard.id, `分镜${storyboard.storyboardNo || storyboard.shotNo}首帧`,
      );
    }
    return document;
  };
  const selectedVideoModel = videoModels.find(
    (model) => model.id === selectedModelId,
  );
  const settingsFor = (storyboard: StoryboardShot): VideoGenerationSettings =>
    videoSettings.latest(storyboard.id) || storyboard.videoGenerationSettings || {
      durationSeconds: normalizeVideoDuration(
        storyboard.durationSeconds,
        selectedVideoModel?.constraints,
      ),
      resolution: project?.videoResolution || '720p',
      generateAudio: project?.videoGenerateAudio !== false,
      watermark: project?.videoWatermark === true,
    };
  const changeSelectedModel = (value: number | string) => {
    explicitVideoModelSelection.current = true;
    const nextId = Number(value);
    const nextModel = videoModels.find((model) => model.id === nextId);
    const supported = nextModel?.constraints?.resolutions || ['720p'];
    let fellBack = false;
    for (const storyboard of workspace.storyboards) {
      const current = settingsFor(storyboard);
      const resolution = supported.includes(current.resolution) ? current.resolution
        : supported.includes('720p') ? '720p' : supported[0] || '720p';
      const durationSeconds = normalizeVideoDuration(current.durationSeconds, nextModel?.constraints);
      if (resolution !== current.resolution) fellBack = true;
      if (resolution !== current.resolution || durationSeconds !== current.durationSeconds) {
        videoSettings.save(storyboard.id, { ...current, resolution, durationSeconds });
      }
    }
    setSelectedModelId(nextId);
    if (fellBack) message.warning('所选模型不支持当前分辨率，已切换为支持的分辨率');
  };
  const episodeNumbers = useMemo(() => {
    const fromEpisodes = (workspace.episodes || []).map(
      (item) => item.episodeNo,
    );
    return Array.from(
      new Set(
        fromEpisodes.length
          ? fromEpisodes
          : workspace.storyboards.map((item) => item.episodeNo),
      ),
    ).sort((a, b) => a - b);
  }, [workspace.episodes, workspace.storyboards]);
  const episodeNavigation = workspace.projectId === projectId
    ? workspace.episodes?.find((episode) => episode.episodeNo === activeEpisode)
    : undefined;
  const currentEpisodeId = episodeNavigation?.episodeId;
  const currentEpisode = episodeDetails?.projectId === projectId && episodeDetails.episodeId === currentEpisodeId
    ? { ...episodeNavigation, ...episodeDetails.episode }
    : episodeNavigation;

  useEffect(() => {
    if (!projectId || !currentEpisodeId) return;
    const controller = new AbortController();
    void queryStoryboardEpisodeDetails(projectId, currentEpisodeId, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted && response.data) {
          setEpisodeDetails({ projectId, episodeId: currentEpisodeId, episode: response.data });
        }
      }).catch(() => {
        // The summary is optional; keep the page editable if it is unavailable.
      });
    return () => controller.abort();
  }, [projectId, currentEpisodeId]);
  const currentStoryboardExecution = storyboardExecutionEpisodeId === currentEpisode?.episodeId
    ? storyboardExecution : undefined;
  const batchEpisodeStatus = storyboardBatch?.items.find(
    (item) => item.episodeId === currentEpisode?.episodeId,
  )?.status;
  const activeGenerationStatus = [currentStoryboardExecution?.status, batchEpisodeStatus]
    .find((status): status is 'PENDING' | 'RUNNING' => status === 'PENDING' || status === 'RUNNING');
  const episodeTitle = currentEpisode?.title?.trim();
  const episodeSummary =
    currentEpisode?.formalSummary?.content.summary?.trim() ||
    currentEpisode?.summary?.trim() ||
    '暂无本集概要';
  const visibleStoryboards = workspace.storyboards.filter(
    (item) => item.episodeNo === activeEpisode,
  );
  const updateActiveStoryboard = () => {
    const root = workbenchRef.current;
    if (!root) return;
    const threshold = root.getBoundingClientRect().top + Math.min(100, root.clientHeight / 4);
    const cards = visibleStoryboards.map((shot) => document.getElementById(`storyboard-${shot.id}`))
      .filter((card): card is HTMLElement => Boolean(card));
    const current = cards.find((card) => card.getBoundingClientRect().bottom > threshold) || cards[0];
    if (current) setActiveStoryboardId(Number(current.id.replace('storyboard-', '')));
  };
  const focusStoryboard = (id: number) => {
    const card = document.getElementById(`storyboard-${id}`);
    if (!card) return;
    card.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
    card.focus({ preventScroll: true });
    setActiveStoryboardId(id);
    pendingStoryboardScroll.current = undefined;
  };
  useLayoutEffect(() => {
    if (storyboardLoading) return;
    if (pendingStoryboardScroll.current != null) focusStoryboard(pendingStoryboardScroll.current);
    else updateActiveStoryboard();
  }, [storyboardLoading, mediaScopeKey]);
  const navigateToStoryboard = async (id: number, position: number) => {
    pendingStoryboardScroll.current = id;
    setActiveStoryboardId(id);
    const page = Math.floor(position / storyboardPageSize) + 1;
    try {
      if (!workbenchRef.current?.contains(document.getElementById(`storyboard-${id}`))) {
        await reloadStoryboardPage(activeEpisode, page);
      } else focusStoryboard(id);
    } catch {
      pendingStoryboardScroll.current = undefined;
      message.error('分镜定位失败，请重试');
    }
  };
  const failedVisibleVideoTasks = visibleStoryboards.flatMap((shot) => {
    const latest = videoTasks.filter((task) => task.storyboardId === shot.id)
      .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || '')
        || right.id - left.id)[0];
    return latest?.status === 'FAILED' ? [latest] : [];
  });
  const storyboardWarnings = visibleStoryboards.flatMap(
    (item) => item.shotPlan?.warnings ?? [],
  );
  const storyboardDiagnostics = visibleStoryboards.find(
    (item) => item.shotPlan?.diagnostics,
  )?.shotPlan?.diagnostics;

  const erasure = useSubtitleEraseTasks(projectId, workspace.storyboards.map(item => item.id),
    Boolean(videoTasks.length || Object.values(videoHistories).some(page => page.total > 0)), async (task) => {
      if (!task.result) return;
      setErasedPreviews(previous => ({ ...previous, [task.storyboardId]: task.result as AiVideoResult }));
      await reloadVideoTasks(task.storyboardId);
      message.success('字幕擦除完成，已新增视频版本，预览满意后可选用');
    });
  const reloadWorkspace = async (
    affectedEpisodes: number[],
    scope: { projectId: number; active: boolean },
  ) => {
    if (!scope.active || scope.projectId !== projectId || scope !== storyboardGenerationScope.current) return;
    // Read the navigation target now, not the view captured when generation began.
    const view = storyboardView.current;
    if (!affectedEpisodes.includes(view.episodeNo)) return;
    const { episodeNo, current } = view;
    const requestId = ++storyboardRequestId.current;
    const isCurrent = () => scope.active && scope === storyboardGenerationScope.current
      && storyboardView.current === view && storyboardRequestId.current === requestId;
    void queryAssetSettingsSummary(projectId).then((response) => {
      if (!isCurrent()) return;
      setWorkspace((previous) => ({
        ...previous,
        characters: response.data?.characters || [],
        scenes: response.data?.scenes || [],
        props: response.data?.props || [],
      }));
    }).catch(() => {
      if (isCurrent()) message.error('分镜素材加载失败');
    });
    try {
      const storyboardResponse = await queryStoryboardEditorPage(projectId, {
        episodeNo, current, pageSize: storyboardPageSize,
      });
      if (!isCurrent()) return;
      if (!storyboardResponse.data) throw new Error('missing storyboard page');
      const shots = storyboardResponse.data.storyboards || [];
      setWorkspace((previous) => ({
        ...previous,
        storyboards: shots,
        episodes: storyboardResponse.data.episodes || [],
      }));
      setStoryboardPage(storyboardResponse.data.current || current);
      setStoryboardTotal(storyboardResponse.data.total ?? shots.length);
      setStoryboardNavigation(navigationFromPage(storyboardResponse.data));
      loadDrafts(shots);
      view.current = storyboardResponse.data.current || current;
      setStoryboardLoadFailed(false);
      setMutationRefreshFailed(false);
    } catch {
      if (isCurrent()) {
        if (view.loading) setStoryboardLoadFailed(true);
        setMutationRefreshFailed(true);
        message.error('分镜生成已结束，但页面刷新失败，请重试加载');
      }
    } finally {
      if (isCurrent()) {
        view.loading = false;
        setStoryboardLoading(false);
      }
    }
  };

  const reloadStoryboardPage = async (episodeNo = activeEpisode, current = storyboardPage) => {
    const view = { episodeNo, current, loading: true };
    storyboardView.current = view;
    const requestId = ++storyboardRequestId.current;
    setStoryboardLoading(true);
    setStoryboardLoadFailed(false);
    try {
      const response = await queryStoryboardEditorPage(projectId, { episodeNo, current, pageSize: storyboardPageSize });
      if (requestId !== storyboardRequestId.current) return;
      const shots = response.data.storyboards || [];
      setWorkspace((previous) => ({ ...previous, episodes: response.data.episodes || previous.episodes, storyboards: shots }));
      setStoryboardPage(response.data.current || current);
      setStoryboardTotal(response.data.total ?? shots.length);
      setStoryboardNavigation(navigationFromPage(response.data));
      loadDrafts(shots);
      view.current = response.data.current || current;
    } catch (error) {
      if (requestId === storyboardRequestId.current) setStoryboardLoadFailed(true);
      throw error;
    } finally {
      if (requestId === storyboardRequestId.current) {
        view.loading = false;
        setStoryboardLoading(false);
      }
    }
  };

  const refreshAfterMutation = async () => {
    try {
      await reloadStoryboardPage();
      setMutationRefreshFailed(false);
      return true;
    } catch {
      setMutationRefreshFailed(true);
      message.error('操作已完成，但分镜刷新失败，请重试加载');
      return false;
    }
  };

  const selectEpisode = async (episodeNo: number) => {
    if (episodeNo === activeEpisode) return;
    setActiveEpisode(episodeNo);
    setEpisodeDetailsOpen(false);
    setStoryboardPage(1);
    setStoryboardNavigation([]);
    setActiveStoryboardId(undefined);
    pendingStoryboardScroll.current = undefined;
    setWorkspace((previous) => ({ ...previous, storyboards: [] }));
    try {
      await reloadStoryboardPage(episodeNo, 1);
    } catch {
      message.error('该集分镜加载失败');
    }
  };

  const changeStoryboardPage = async (nextPage: number) => {
    if (nextPage < 1 || nextPage > Math.ceil(storyboardTotal / storyboardPageSize)) {
      return;
    }
    try {
      await reloadStoryboardPage(activeEpisode, nextPage);
    } catch {
      message.error('分镜分页加载失败');
    }
  };

  const generateEpisodeStoryboards = async () => {
    const scope = storyboardGenerationScope.current;
    const episode = workspace.episodes?.find(
      (item) => item.episodeNo === activeEpisode,
    );
    if (!episode?.episodeId) {
      message.warning('当前集不是可生成分镜的有效剧集');
      return;
    }
    setStoryboardBusy(true);
    try {
      const response = await breakdownStoryboards(projectId, {
        episodeId: episode.episodeId,
      });
      if (!scope.active) return;
      if (!response.data) throw new Error('missing execution');
      setStoryboardExecutionEpisodeId(episode.episodeId);
      setStoryboardExecution(response.data);
      const tenantId = currentTenantId();
      if (!tenantId) throw new Error('missing tenant');
      const terminal = await aiExecutionTaskService.poll(
        tenantId,
        response.data.id || 0,
        (task) => { if (scope.active) setStoryboardExecution(task); },
        1500,
        scope.controller.signal,
      );
      if (!scope.active) return;
      setStoryboardExecution(terminal);
      if (terminal.status === 'SUCCEEDED') {
        await reloadWorkspace([episode.episodeNo], scope);
        if (scope.active) message.success(`第${episode.episodeNo}集分镜已生成`);
      }
    } catch (error) {
      if (!scope.active || isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '本集分镜生成失败');
    } finally {
      if (scope.active) setStoryboardBusy(false);
    }
  };

  const pollStoryboardBatch = async (batchId: number) => {
    const scope = storyboardGenerationScope.current;
    while (scope.active) {
      const response = await queryStoryboardBatch(projectId, batchId, scope.controller.signal);
      if (!scope.active) return;
      if (!response.data) throw new Error('missing storyboard batch');
      setStoryboardBatch(response.data);
      if (terminalStoryboardBatchStatuses.has(response.data.status)) {
        return response.data;
      }
      await pollingDelay(1500, scope.controller.signal);
    }
    return undefined;
  };

  const retryFailedStoryboards = async () => {
    const scope = storyboardGenerationScope.current;
    const episodeIds = (storyboardBatch?.items || []).filter((item) => item.status === 'FAILED')
      .map((item) => item.episodeId);
    if (!episodeIds.length) {
      message.warning('当前项目没有可生成分镜的有效剧集');
      return;
    }
    setStoryboardBatchBusy(true);
    try {
      const response = await createStoryboardBatch(projectId, { episodeIds });
      if (!scope.active) return;
      if (!response.data) throw new Error('missing storyboard batch');
      setStoryboardBatch(response.data);
      const terminal = terminalStoryboardBatchStatuses.has(response.data.status)
        ? response.data
        : await pollStoryboardBatch(response.data.id);
      if (!scope.active || !terminal) return;
      setStoryboardBatch(terminal);
      await reloadWorkspace(terminal.items.map((item) => item.episodeNo), scope);
      if (scope.active) message.success('失败分镜已重试');
    } catch (error) {
      if (!scope.active || isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '批量分镜生成失败');
    } finally {
      if (scope.active) setStoryboardBatchBusy(false);
    }
  };

  const currentTenantId = () => Number(localStorage.getItem('currentTenantId'));

  const videoTaskValues = (
    storyboard: StoryboardShot,
    promptDocument: StoryboardPromptDocument = promptFor(storyboard),
  ): CreateAiVideoTaskValues => {
    if (!selectedModelId) throw new Error('请先选择视频生成模型');
    const prompt = promptText(promptDocument);
    if (!prompt.trim() || prompt.length > 3000) {
      throw new Error('视频提示词不能为空且不能超过 3000 字');
    }
    const settings = settingsFor(storyboard);
    return {
      storyboardId: storyboard.id,
      modelId: selectedModelId,
      prompt,
      firstFrameUrl: storyboard.firstFrameUrl || selectedFirstFrame(storyboard.id)?.imageUrl || undefined,
      durationSeconds: settings.durationSeconds,
      aspectRatio: project?.aspectRatio || '9:16',
      resolution: settings.resolution,
      generateAudio: settings.generateAudio,
      watermark: settings.watermark,
    };
  };

  const missingVideoReferences = (submissions: VideoSubmission[]) => {
    const imageKey = (reference: PromptReferenceOption) =>
      `${reference.sourceType}:${reference.sourceId}`;
    const availableImages = new Set(referenceOptions.IMAGE.map(imageKey));
    for (const { storyboard } of submissions) {
      if (storyboard.firstFrameUrl || selectedFirstFrame(storyboard.id)) {
        availableImages.add(`STORYBOARD_FIRST_FRAME:${storyboard.id}`);
      }
    }
    const missing = submissions.flatMap(({ storyboard, promptDocument }) => {
      const bindings = storyboard.assetReferences || [];
      const names = new Set(bindings
        .filter((binding) => binding.resolutionStatus !== 'SCENE_INCLUDED' && (binding.resolutionStatus !== 'RESOLVED' || !binding.variantId || !binding.imageUrl?.trim()))
        .map((binding) => binding.sourceName || binding.assetName || '参考图片'));
      for (const node of promptDocument.nodes) {
        if (node.type !== 'mention' || node.mediaType !== 'IMAGE') continue;
        const resolvedBinding = node.sourceType === 'ASSET_VISUAL_VARIANT' && bindings.some(
          (binding) => binding.variantId === node.sourceId && binding.resolutionStatus === 'RESOLVED' && binding.imageUrl,
        );
        if (!availableImages.has(imageKey(node)) && !resolvedBinding) names.add(node.displayName);
      }
      return names.size ? [{ storyboard, names: [...names] }] : [];
    });
    return missing;
  };

  const previewVideoOperations = (
    storyboards: StoryboardShot[], resolvePrompt = promptFor,
  ): PointEstimateOperation[] | undefined => {
    if (!storyboards.length) return undefined;
    try {
      const submissions = storyboards.map((storyboard) => {
        const promptDocument = resolvePrompt(storyboard);
        return { storyboard, promptDocument, values: videoTaskValues(storyboard, promptDocument) };
      });
      const missingIds = new Set(missingVideoReferences(submissions).map(({ storyboard }) => storyboard.id));
      return submissions.map(({ storyboard, values, promptDocument }) => ({
        url: `/api/projects/${projectId}/ai-video-tasks`,
        data: { ...values, promptDocument, ...(missingIds.has(storyboard.id) ? { skipUnavailableImages: true } : {}) },
      }));
    } catch { return undefined; }
  };

  const prepareVideoSubmissions = async (storyboards: StoryboardShot[]): Promise<VideoSubmission[]> => {
    const scripts = new Map(storyboards.map((shot) => [shot.id, draftStore.get(shot.id)?.scriptText]));
    await videoSettings.wait(storyboards.map((item) => item.id));
    if (!media.isCurrent()) throw new PointEstimateCancelledError();
    const submissions = storyboards.map((storyboard) => {
      const promptDocument = promptFor(storyboard);
      return { storyboard, scriptText: scripts.get(storyboard.id), promptDocument, values: videoTaskValues(storyboard, promptDocument) };
    });
    const missing = missingVideoReferences(submissions);
    if (!missing.length) return submissions;
    const confirmed = await new Promise<boolean>((resolve) => {
      modal.confirm({
        title: '参考图片尚不可用',
        content: <>
          <p>以下分镜引用的图片尚未生成或已不可用：</p>
          <ul>{missing.map(({ storyboard, names }) => <li key={storyboard.id}>
            分镜{storyboard.storyboardNo || storyboard.shotNo}：{names.join('、')}
          </li>)}</ul>
          <p>继续后，仅跳过上述不可用图片，其余有效参考素材继续使用。缺图素材将按名称和文字描述参与生成，外观可能变化；原有素材绑定会保留。若没有可用参考素材，将按文字提示词生成。</p>
        </>,
        okText: '跳过缺图继续生成',
        cancelText: '返回检查素材',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
    if (!confirmed || !media.isCurrent()) throw new PointEstimateCancelledError();
    const skipMissingIds = new Set(missing.map(({ storyboard }) => storyboard.id));
    return submissions.map((submission) => skipMissingIds.has(submission.storyboard.id)
      ? { ...submission, values: { ...submission.values, skipUnavailableImages: true } }
      : submission);
  };

  const submitVideoTask = async ({ storyboard, scriptText, values, promptDocument }: VideoSubmission) => {
    if (!media.isCurrent()) throw new PointEstimateCancelledError();
    const prompt = values.prompt;
    const latestStoryboard = scriptText && scriptText !== getStoryboardScriptText(storyboard)
      ? { ...storyboard, ...parseStoryboardScriptText(storyboard, scriptText) }
      : storyboard;
    await persistStoryboard(storyboard.id, getStoryboardSavePayload(latestStoryboard, {
      videoPrompt: prompt, promptDocument,
    }));
    if (!media.isCurrent()) throw new PointEstimateCancelledError();
    setWorkspace((previous) => media.isCurrent() ? ({ ...previous, storyboards: previous.storyboards.map((item) =>
      item.id === storyboard.id ? { ...item, videoPrompt: prompt, promptDocument } : item,
    ) }) : previous);
    setDrafts((previous) => ({ ...previous, [storyboard.id]: {
      ...previous[storyboard.id], videoPrompt: prompt, promptDocument,
    } }));
    const response = await createAiVideoTask(projectId, values, true);
    return response.data as AiVideoTask | undefined;
  };

  const generateVideo = async (storyboard: StoryboardShot) => {
    try {
      const [submission] = await prepareVideoSubmissions([storyboard]);
      await confirmPointEstimates([{
        url: `/api/projects/${projectId}/ai-video-tasks`,
        data: { ...submission.values, promptDocument: submission.promptDocument },
      }]);
      const task = await submitVideoTask(submission);
      if (!media.isCurrent()) return;
      if (task) {
        setVideoTasks((previous) => [...previous, task]);
        void followVideoExecution(task).catch(() =>
          media.isCurrent() && message.error('视频任务状态刷新失败'),
        );
      }
      message.success('视频任务已创建');
    } catch (error) {
      if (media.isCurrent() && !isPointEstimateCancelled(error))
        message.error(error instanceof Error ? error.message : '视频任务创建失败');
    }
  };

  const openBatchVideoGeneration = async () => {
    if (videoBatchSubmission.current) return;
    const requestId = ++videoBatchRequestId.current;
    setVideoBatchOpen(true);
    setVideoBatchLoading(true);
    setVideoBatchError(false);
    setVideoBatchSelectedIds([]);
    setVideoBatchCandidates([]);
    const isCurrent = () => videoBatchRequestId.current === requestId;
    try {
      const firstPage = await queryStoryboardEditorPage(projectId, {
        episodeNo: activeEpisode, current: 1, pageSize: storyboardPageSize,
      });
      if (!isCurrent()) return;
      if (!firstPage.data) throw new Error('missing storyboard page');
      const shots = [...firstPage.data.storyboards];
      const pageCount = Math.ceil((firstPage.data.total ?? shots.length) / storyboardPageSize);
      for (let current = 2; current <= pageCount; current += 1) {
        const page = await queryStoryboardEditorPage(projectId, {
          episodeNo: activeEpisode, current, pageSize: storyboardPageSize,
        });
        if (!isCurrent()) return;
        if (!page.data) throw new Error('missing storyboard page');
        shots.push(...page.data.storyboards);
      }
      const candidates = [...new Map(shots.filter((shot) => shot.episodeNo === activeEpisode)
        .map((shot) => [shot.id, workspace.storyboards.find((item) => item.id === shot.id) || shot])).values()]
        .sort((a, b) => (a.storyboardNo || a.shotNo) - (b.storyboardNo || b.shotNo));
      const media: StoryboardMediaSummary[] = [];
      for (let offset = 0; offset < candidates.length; offset += 100) {
        const response = await queryStoryboardMedia(projectId, candidates.slice(offset, offset + 100).map((shot) => shot.id));
        if (!isCurrent()) return;
        if (!response.data) throw new Error('missing storyboard media');
        media.push(response.data);
      }
      setImageTasks((previous) => [...new Map([...previous, ...media.flatMap((item) => item.imageTasks)]
        .map((task) => [task.id, task])).values()]);
      setVideoTasks((previous) => [...new Map([...previous, ...media.flatMap((item) => item.videoTasks)]
        .map((task) => [task.id, task])).values()]);
      setAssetVisuals((previous) => {
        const next = { ...previous };
        for (const { key, visual } of media.flatMap((item) => item.assetVisuals || [])) {
          next[key] = { ...visual, ...next[key], variants: [...new Map([
            ...(next[key]?.variants || []), ...visual.variants,
          ].map((variant) => [variant.id, variant])).values()] };
        }
        return next;
      });
      setVideoBatchCandidates(candidates);
      setVideoHistories((previous) => ({ ...previous,
        ...Object.fromEntries(media.flatMap((item) => item.videoHistory || []).map((page) => [page.storyboardId, page])),
      }));
    } catch {
      if (isCurrent()) setVideoBatchError(true);
    } finally {
      if (isCurrent()) setVideoBatchLoading(false);
    }
  };

  const batchVideoOptions = videoBatchCandidates.map((storyboard) => {
    let disabledReason: string | undefined;
    if (videoTasks.some((task) => task.storyboardId === storyboard.id &&
      ['PENDING', 'RUNNING', 'SUBMITTED', 'GENERATING'].includes(task.execution?.status || task.status))) {
      disabledReason = '视频正在生成中';
    } else {
      try { videoTaskValues(storyboard); } catch (error) {
        disabledReason = error instanceof Error ? error.message : '当前分镜暂不可生成视频';
      }
    }
    return { storyboard, disabledReason };
  });

  const batchGenerateVideo = async () => {
    if (videoBatchSubmission.current || videoBatchLoading || videoBatchError) return;
    const selected = batchVideoOptions.filter((item) => !item.disabledReason && videoBatchSelectedIds.includes(item.storyboard.id))
      .map((item) => item.storyboard);
    if (!selected.length) return;
    videoBatchSubmission.current = true;
    setVideoBatchSubmitting(true);
    try {
      const submissions = await prepareVideoSubmissions(selected);
      await confirmPointEstimates(
        submissions.map(({ values, promptDocument }) => ({
          url: `/api/projects/${projectId}/ai-video-tasks`,
          data: { ...values, promptDocument },
        })),
      );
      const outcomes: PromiseSettledResult<AiVideoTask | undefined>[] = [];
      for (let offset = 0; offset < selected.length; offset += 4) {
        outcomes.push(...await Promise.allSettled(submissions.slice(offset, offset + 4).map(submitVideoTask)));
      }
      if (!media.isCurrent()) return;
      let created = 0;
      const failedIds: number[] = [];
      outcomes.forEach((outcome, index) => {
        const storyboard = selected[index];
        if (outcome.status === 'rejected' || !outcome.value) {
          failedIds.push(storyboard.id);
          const reason = outcome.status === 'rejected' && outcome.reason instanceof Error
            ? outcome.reason.message : '视频任务创建失败';
          message.error(`分镜${storyboard.storyboardNo || storyboard.shotNo}：${reason}`);
          return;
        }
        const task = outcome.value;
        created += 1;
        setVideoTasks((previous) => [...previous, task]);
        void followVideoExecution(task).catch(() =>
          media.isCurrent() && message.error('视频任务状态刷新失败'),
        );
      });
      if (created > 0) message.success(`已创建 ${created} 个视频任务`);
      setVideoBatchSelectedIds(failedIds);
      if (!failedIds.length) setVideoBatchOpen(false);
    } catch (error) {
      if (media.isCurrent() && !isPointEstimateCancelled(error))
        message.error(
          error instanceof Error ? error.message : '批量视频提交失败',
        );
    } finally {
      if (media.isCurrent()) {
        videoBatchSubmission.current = false;
        setVideoBatchSubmitting(false);
      }
    }
  };

  const retryFailedVideos = async () => {
    if (!failedVisibleVideoTasks.length) return;
    setVideoBatchRetryBusy(true);
    try {
      await confirmPointEstimates(
        failedVisibleVideoTasks.map((task) => ({
          url: `/api/projects/${projectId}/ai-video-tasks/${task.id}/regenerate`,
        })),
      );
      if (!media.isCurrent()) return;
      const outcomes = await Promise.allSettled(
        failedVisibleVideoTasks.map((task) =>
          regenerateAiVideoTask(projectId, task.id, true),
        ),
      );
      if (!media.isCurrent()) return;
      let created = 0;
      outcomes.forEach((outcome, index) => {
        if (outcome.status === 'rejected') {
          message.error(`分镜${failedVisibleVideoTasks[index].storyboardId}：${
            outcome.reason instanceof Error ? outcome.reason.message : '视频重试失败'}`);
          return;
        }
        const task = outcome.value.data;
        if (!task) return;
        created += 1;
        setVideoTasks((previous) => [...previous, task]);
        void followVideoExecution(task).catch(() => media.isCurrent() && message.error('视频任务状态刷新失败'));
      });
      if (created) message.success(`已重试 ${created} 条失败视频`);
    } catch (error) {
      if (media.isCurrent() && !isPointEstimateCancelled(error))
        message.error(
          error instanceof Error ? error.message : '批量视频重试失败',
        );
    } finally {
      if (media.isCurrent()) setVideoBatchRetryBusy(false);
    }
  };

  const generateReferenceImage = async (
    reference: StoryboardAssetReference,
    generationKey: string,
  ) => {
    if (!reference.assetId) return;
    const requestId = referenceGenerationRequestId.current + 1;
    referenceGenerationRequestId.current = requestId;
    const requestedProjectId = projectId;
    const assets = reference.assetType === 'CHARACTER'
      ? characters
      : reference.assetType === 'SCENE'
        ? scenes
        : props;
    const asset = assets.find((candidate) => candidate.id === reference.assetId);
    if (!asset) {
      message.error('该资产不存在');
      return;
    }
    try {
      let visual = asset.visual;
      if (!visual?.variants.length) {
        const response = await queryAssetVisualWorkspace(
          projectId,
          reference.assetType,
          reference.assetId,
        );
        visual = response.data;
        if (
          referenceGenerationRequestId.current !== requestId
          || visualProjectId.current !== requestedProjectId
        ) return;
        setAssetVisuals((previous) => ({
          ...previous,
          [`${reference.assetType}:${reference.assetId}`]: response.data,
        }));
      }
      const variant = reference.variantId != null
        ? visual?.variants.find((item) => item.id === reference.variantId)
        : visual?.primaryVariant
          || visual?.variants.find((item) => item.primary)
          || visual?.variants.find((item) => item.usable)
          || visual?.variants[0];
      if (!variant) {
        message.warning(
          reference.variantId != null
            ? '所选视觉形态已失效，请重新选择'
            : '该资产暂无可生成的视觉形态',
        );
        return;
      }
      if (
        reference.assetType === 'CHARACTER'
        && !variant.primary
        && !visual?.resolvedImageUrl
      ) {
        message.error('请先生成主体主图');
        return;
      }
      setReferenceGenerationTarget({
        assetType: reference.assetType,
        assetId: reference.assetId,
        variant,
        primaryImageUrl: visual?.resolvedImageUrl,
        primaryImageThumbnailUrl: visual?.primaryVariant?.currentImageThumbnailUrl
          || visual?.variants.find((item) => item.primary)?.currentImageThumbnailUrl,
        generationKey,
      });
      setReferenceGenerationSubmitting(false);
      setReferenceGenerationValues({
        prompt: variant.prompt || '',
        modelId: configuredImageModelId,
        aspectRatio: project?.aspectRatio || '16:9',
        imageCount: 1,
      });
    } catch {
      if (
        referenceGenerationRequestId.current === requestId
        && visualProjectId.current === requestedProjectId
      ) {
        message.error('视觉形象加载失败');
      }
    }
  };

  const followReferenceVariantGeneration = async (
    target: ReferenceGenerationTarget,
    task: AiImageTask,
    signal: AbortSignal,
  ) => {
    try {
      signal.throwIfAborted();
      const tenantId = currentTenantId();
      if (task.executionId && tenantId) {
        await aiExecutionTaskService.poll(tenantId, task.executionId, undefined, 1500, signal);
      } else {
        let currentTask = task;
        while (!terminalImageTaskStatuses.has(currentTask.status)) {
          await pollingDelay(1500, signal);
          const response = await queryAiImageTask(projectId, currentTask.id, signal);
          signal.throwIfAborted();
          if (!response.data) throw new Error('missing image task');
          currentTask = response.data;
        }
      }
      signal.throwIfAborted();
      const response = await queryAssetVisualWorkspace(
        projectId,
        target.assetType,
        target.assetId,
        {}, signal,
      );
      signal.throwIfAborted();
      setAssetVisuals((previous) => ({
        ...previous,
        [`${target.assetType}:${target.assetId}`]: response.data,
      }));
    } catch {
      if (!signal.aborted) {
        message.error('资产图状态刷新失败');
      }
    } finally {
      if (!signal.aborted) {
        setGeneratingReferenceKeys((previous) => {
          const next = new Set(previous);
          next.delete(target.generationKey);
          return next;
        });
      }
    }
  };

  const submitReferenceVariantGeneration = async () => {
    const signal = storyboardGenerationScope.current.controller.signal;
    const target = referenceGenerationTarget;
    if (!target) return;
    const requestId = referenceGenerationRequestId.current;
    const requestedProjectId = projectId;
    const prompt = referenceGenerationValues.prompt;
    if (!prompt.trim()) {
      message.error(
        target.variant.primary
          ? '请先完成资产主体提示词后再生成图片。'
          : '请先完成视觉形象提示词后再生成图片。',
      );
      return;
    }
    setReferenceGenerationSubmitting(true);
    try {
      if (prompt !== (target.variant.prompt || '')) {
        await updateVisualVariant(projectId, target.variant.id, {
          ...target.variant,
          prompt,
        });
      }
      signal.throwIfAborted();
      const response = await createAiImageTask(projectId, {
        taskType: target.assetType,
        targetType: 'VISUAL_VARIANT',
        targetId: target.variant.id,
        modelId: referenceGenerationValues.modelId,
        prompt,
        referenceImages:
          target.assetType === 'CHARACTER'
          && !target.variant.primary
          && target.primaryImageUrl
            ? [target.primaryImageUrl]
            : undefined,
        aspectRatio: referenceGenerationValues.aspectRatio,
        imageCount: referenceGenerationValues.imageCount,
      });
      if (!response.data) throw new Error('missing image task');
      if (signal.aborted || visualProjectId.current !== requestedProjectId) return;
      const visualKey = `${target.assetType}:${target.assetId}`;
      setReferenceVisualPollTargets((previous) => ({
        ...previous,
        [visualKey]: { assetType: target.assetType, assetId: target.assetId },
      }));
      setGeneratingReferenceKeys((previous) => new Set(previous).add(target.generationKey));
      if (referenceGenerationRequestId.current === requestId) {
        setReferenceGenerationTarget(undefined);
      }
      message.success('已提交生成');
      void followReferenceVariantGeneration(target, response.data, signal);
    } catch (error) {
      if (!signal.aborted && visualProjectId.current === requestedProjectId) {
        if (!isPointEstimateCancelled(error))
          message.error(
            error instanceof Error ? error.message : '提交生成失败');
      }
    } finally {
      if (
        !signal.aborted && visualProjectId.current === requestedProjectId
        && referenceGenerationRequestId.current === requestId
      ) {
        setReferenceGenerationSubmitting(false);
      }
    }
  };

  const appendStoryboard = async (
    values: SaveStoryboardValues,
    successText: string,
  ) => {
    try {
      await createStoryboard(projectId, values);
      if (await refreshAfterMutation()) message.success(successText);
    } catch {
      message.error('分镜创建失败');
    }
  };

  const reserveNextShotNo = (episodeNo: number) => {
    const persistedMaximum = Math.max(
      0,
      ...workspace.storyboards
        .filter((item) => item.episodeNo === episodeNo)
        .map((item) => item.shotNo),
    );
    const shotNo =
      Math.max(persistedMaximum, reservedShotNos.current[episodeNo] ?? 0) + 1;
    reservedShotNos.current[episodeNo] = shotNo;
    return shotNo;
  };

  const addStoryboardAfter = async (storyboard: StoryboardShot) => {
    const shotNo = reserveNextShotNo(storyboard.episodeNo);
    await appendStoryboard(
      {
        episodeNo: storyboard.episodeNo,
        shotNo,
        shotType: '中景',
        visualDescription: '新增镜头画面描述',
        durationSeconds: 5,
        status: 'DRAFT',
      },
      '分镜已新增',
    );
  };

  const copyStoryboard = async (storyboard: StoryboardShot) => {
    const shotNo = reserveNextShotNo(storyboard.episodeNo);
    await appendStoryboard(
      getStoryboardSavePayload(storyboard, {
        episodeNo: storyboard.episodeNo,
        shotNo,
        shotType: storyboard.shotType,
        status: 'DRAFT',
      }),
      '分镜已复制',
    );
  };

  const removeStoryboard = async (storyboard: StoryboardShot) => {
    try {
      await deleteStoryboard(projectId, storyboard.id);
      setStoryboardNavigation((previous) => previous.filter((item) => item.id !== storyboard.id));
      setStoryboardTotal((previous) => Math.max(0, previous - 1));
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.filter(
          (item) => item.id !== storyboard.id,
        ),
      }));
      message.success('分镜已删除');
      return true;
    } catch {
      message.error('删除分镜失败');
      return false;
    }
  };

  if (!projectId) {
    return <Empty description="项目不存在" />;
  }

  return (
    <StoryboardCardProvider projectId={projectId} storyboards={workspace.storyboards}
      editor={editor} media={media}
      resources={{ projectId, canCaptureVideo: project?.capabilities?.canEdit === true,
        characters, scenes, props, episodes: workspace.episodes || [], referenceOptions, referenceThumbnails,
        model: selectedVideoModel?.name || '未配置视频模型', modelId: selectedModelId, aspectRatio: project?.aspectRatio || undefined, modelConstraints: selectedVideoModel?.constraints }}
      video={{ settingsFor, statuses: videoSettings.status, saveSettings: videoSettings.save,
        retrySettings: videoSettings.retry, promptFor, preview: previewVideoOperations, generate: generateVideo }}
      assets={{ uploaded: reference => setUploadedImageReferences(previous => [...previous, reference]),
        generate: generateReferenceImage, generating: generatingReferenceKeys, loadVariants: loadAssetVariants }}
      actions={{ add: addStoryboardAfter, copy: copyStoryboard, remove: removeStoryboard,
        capture: (resultId, storyboardNo) => { stopActiveVideo(); setFrameCapture({ resultId, storyboardNo }); },
        erase: (resultId, storyboardNo) => { stopActiveVideo(); setSubtitleErase({ resultId, storyboardNo }); } }}
      erasure={{ tasks: erasure.tasks, previews: erasedPreviews, retry: erasure.retry }}>
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '64px 24px minmax(0, 1fr)',
        height: '100%',
        minHeight: 0,
        background: '#f8f9fc',
      }}
    >
      <aside
        style={{
          minHeight: 0,
          overflowY: 'auto',
          borderRight: '1px solid #e4e9f2',
          background: '#fff',
          paddingTop: 20,
        }}
      >
        <div style={{ textAlign: 'center', fontWeight: 700, marginBottom: 17 }}>
          集数
        </div>
        <div style={{ position: 'relative', display: 'grid', gap: 10 }}>
          {episodeNumbers.map((episode) => (
            <button
              key={episode}
              type="button"
              onClick={() => void selectEpisode(episode)}
              style={{
                width: 36,
                height: 36,
                margin: '0 auto',
                borderRadius: 6,
                border:
                  episode === activeEpisode
                    ? '1px solid #5454ff'
                    : '1px solid #dfe5f1',
                background: episode === activeEpisode ? '#5454ff' : '#fff',
                color: episode === activeEpisode ? '#fff' : '#1f2937',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              {episode}
            </button>
          ))}
        </div>
      </aside>

      <StoryboardPositionRail
        episodeNo={activeEpisode}
        activeId={activeStoryboardId}
        items={storyboardNavigation.map((shot) => ({ ...shot,
          hasVideo: shot.hasVideo || Boolean(videoHistories[shot.id]?.total)
            || videoTasks.some((task) => task.storyboardId === shot.id && task.results?.some((result) => result.videoUrl)),
        }))}
        onNavigate={(id, position) => void navigateToStoryboard(id, position)}
      />

      <div ref={workbenchRef} onScroll={updateActiveStoryboard} className="storyboard-workbench" data-testid="storyboard-workbench" style={{ minWidth: 0, minHeight: 0, overflowY: 'auto' }}>
        {mutationRefreshFailed ? (
          <div role="alert">
            操作已完成，但分镜刷新失败。
            <button type="button" onClick={() => void refreshAfterMutation()}>重试加载分镜</button>
          </div>
        ) : null}
        <Flex justify="space-between" align="center" wrap gap={12}>
          <Typography.Title level={5} style={{ margin: 0, fontSize: 16 }}>
            分镜表
          </Typography.Title>
          <Flex gap={10} wrap>
            <Select
              aria-label="视频生成模型"
              value={selectedModelId}
              onChange={changeSelectedModel}
              options={videoModels.map((model) => ({
                label: model.name,
                value: model.id,
              }))}
              style={{ width: 202 }}
            />
            <Button icon={<BarsOutlined />} onClick={() => void openBatchVideoGeneration()}>
              批量生成视频
            </Button>
            {failedVisibleVideoTasks.length ? (
              <PointEstimateButton operations={failedVisibleVideoTasks.length ? failedVisibleVideoTasks.map((task) => ({ url: `/api/projects/${projectId}/ai-video-tasks/${task.id}/regenerate` })) : undefined}
                icon={<ReloadOutlined />}
                aria-label="重试失败视频"
                loading={videoBatchRetryBusy}
                onClick={() => void retryFailedVideos()}
              >
                重试失败视频
              </PointEstimateButton>
            ) : null}
          </Flex>
        </Flex>

        {storyboardBatch ? (
          <Flex gap={8} wrap style={{ marginTop: 12 }}>
            <Tag color="success">成功 {storyboardBatch.succeeded} 集</Tag>
            <Tag color="warning">有告警 {storyboardBatch.warning} 集</Tag>
            <Tag color="error">失败 {storyboardBatch.failed} 集</Tag>
            <Tag>进行中 {storyboardBatch.running + storyboardBatch.pending} 集</Tag>
            <Tag>业务调用 {storyboardBatch.businessCallCount} 次</Tag>
            <Tag>技术重试 {storyboardBatch.technicalRetryCount} 次</Tag>
            {storyboardBatch.items.filter((item) => item.status === 'FAILED').map((item) => (
              <div key={item.id} role="alert" style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                第{item.episodeNo}集：{item.errorMessage || '生成失败，请重试'}
              </div>
            ))}
            {storyboardBatch.failed > 0 &&
              ['FAILED', 'COMPLETED_WITH_FAILURES'].includes(storyboardBatch.status) ? (
                <PointEstimateButton operations={(storyboardBatch?.items || []).some((item) => item.status === 'FAILED') ? [{ url: `/api/projects/${projectId}/storyboard-batches`, data: { episodeIds: (storyboardBatch?.items || []).filter((item) => item.status === 'FAILED').map((item) => item.episodeId) } }] : undefined}
                  size="small"
                  loading={storyboardBatchBusy}
                  onClick={() => void retryFailedStoryboards()}
                >
                  重试失败分镜
                </PointEstimateButton>
              ) : null}
          </Flex>
        ) : null}
        {frameCapture ? <VideoFrameCaptureModal key={`${projectId}:${frameCapture.resultId}`} projectId={projectId}
          resultId={frameCapture.resultId} storyboardNo={frameCapture.storyboardNo} onClose={() => setFrameCapture(undefined)} /> : null}
        {subtitleErase ? <SubtitleEraseModal key={`${projectId}:${subtitleErase.resultId}`} projectId={projectId}
          resultId={subtitleErase.resultId} storyboardNo={subtitleErase.storyboardNo} onClose={() => setSubtitleErase(undefined)}
          onSubmitted={(task) => {
            erasure.upsert(task);
            if (task.status === 'SUCCEEDED' && task.result) {
              setErasedPreviews(previous => ({ ...previous, [task.storyboardId]: task.result as AiVideoResult }));
              void reloadVideoTasks(task.storyboardId);
            }
            void erasure.refresh();
            message.success('字幕擦除任务已提交，可在任务中心查看进度');
          }} /> : null}
        {erasure.error ? <div className="subtitle-erase-load-error" role="alert">字幕擦除状态更新失败
          <Button type="link" onClick={() => void erasure.refresh()}>重新加载</Button></div> : null}
        <BatchVideoGenerationModal
          operations={previewVideoOperations(batchVideoOptions.filter((item) => !item.disabledReason && videoBatchSelectedIds.includes(item.storyboard.id)).map((item) => item.storyboard))}
          open={videoBatchOpen}
          loading={videoBatchLoading}
          submitting={videoBatchSubmitting}
          error={videoBatchError}
          candidates={batchVideoOptions}
          selectedIds={videoBatchSelectedIds}
          onSelectionChange={setVideoBatchSelectedIds}
          onCancel={() => {
            if (videoBatchSubmission.current) return;
            videoBatchRequestId.current += 1;
            setVideoBatchOpen(false);
          }}
          onRetry={() => void openBatchVideoGeneration()}
          onSubmit={() => void batchGenerateVideo()}
        />

        <section style={{ marginTop: 18, paddingLeft: 2 }}>
          <Flex justify="space-between" align="center" gap={16} wrap>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 16 }}>
              第{activeEpisode}集{episodeTitle ? ` ${episodeTitle}` : ''}
            </Typography.Title>
            <PointEstimateButton operations={workspace.episodes?.find((item) => item.episodeNo === activeEpisode)?.episodeId ? [{ url: `/api/projects/${projectId}/storyboards/ai-breakdown`, data: { episodeId: workspace.episodes.find((item) => item.episodeNo === activeEpisode)?.episodeId } }] : undefined}
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={storyboardBusy}
              disabled={Boolean(activeGenerationStatus)}
              onClick={generateEpisodeStoryboards}
            >
              生成本集分镜
            </PointEstimateButton>
          </Flex>
          <div className="storyboard-episode-summary-row">
            <p className="storyboard-episode-summary">{episodeSummary}</p>
            <Button
              type="link"
              size="small"
              aria-label="查看本集详情"
              onClick={() => setEpisodeDetailsOpen(true)}
            >
              查看详情
            </Button>
          </div>
          <Modal
            title="本集详情"
            open={episodeDetailsOpen}
            onCancel={() => setEpisodeDetailsOpen(false)}
            footer={null}
            width={760}
            centered
            destroyOnHidden
            styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
          >
            <Typography.Paragraph type="secondary">
              第{activeEpisode}集{episodeTitle ? ` ${episodeTitle}` : ''}
            </Typography.Paragraph>
            <section className="storyboard-episode-detail-section" aria-label="完整剧情摘要">
              <h3>剧情摘要</h3>
              <p>{episodeSummary}</p>
            </section>
            <section className="storyboard-episode-detail-section" aria-label="关键剧情节点">
              <h3>关键剧情节点</h3>
              {currentEpisode?.formalSummary?.content.highlights?.length ? (
                <Flex gap={8} wrap className="storyboard-episode-highlights">
                  {currentEpisode.formalSummary.content.highlights.map((highlight) => (
                    <Tag key={highlight}>{highlight}</Tag>
                  ))}
                </Flex>
              ) : <Typography.Text type="secondary">暂无关键剧情节点</Typography.Text>}
            </section>
            <section className="storyboard-episode-detail-section" aria-label="本集结尾悬念">
              <h3>本集结尾悬念</h3>
              {currentEpisode?.formalSummary?.content.endingHook ? (
                <p>{currentEpisode.formalSummary.content.endingHook}</p>
              ) : <Typography.Text type="secondary">暂无结尾悬念</Typography.Text>}
            </section>
            {storyboardDiagnostics ? (
              <section className="storyboard-episode-detail-section" aria-label="分镜生成统计">
                <h3>分镜生成统计</h3>
                <Flex gap={8} wrap>
                  <Tag>后端规范化 {storyboardDiagnostics.normalizationCount} 项</Tag>
                  <Tag>派生声音 {storyboardDiagnostics.derivedSoundCount} 条</Tag>
                  <Tag color={storyboardDiagnostics.classificationWarnings.length ? 'warning' : 'default'}>
                    来源分类告警 {' '}
                    {storyboardDiagnostics.classificationWarnings.length} 条
                  </Tag>
                  <Tag>业务调用 {storyboardDiagnostics.businessCallCount} 次</Tag>
                  <Tag>技术重试 {storyboardDiagnostics.technicalRetryCount} 次</Tag>
                </Flex>
              </section>
            ) : null}
          </Modal>
          {currentStoryboardExecution ? (
            <AiExecutionStatus task={currentStoryboardExecution} busy={storyboardBusy} />
          ) : null}
          {currentStoryboardExecution?.status === 'SUCCEEDED' && storyboardWarnings.length ? (
            <Tag color="warning" style={{ marginTop: 10 }}>
              生成成功，有 {storyboardWarnings.length} 条质量告警
            </Tag>
          ) : null}
        </section>

        {storyboardLoading ? (
          <div style={{ paddingTop: 100, textAlign: 'center' }}><Spin /></div>
        ) : storyboardLoadFailed ? (
          <div role="alert" style={{ paddingTop: 100, textAlign: 'center' }}>
            <p>分镜数据加载失败，请重新加载。</p>
            <Button onClick={() => {
              if (!workspace.episodes?.length) {
                setStoryboardLoadAttempt((attempt) => attempt + 1);
              } else {
                void reloadStoryboardPage().catch(() => message.error('分镜页面加载失败'));
              }
            }}>重新加载分镜</Button>
          </div>
        ) : visibleStoryboards.length ? (
          <div style={{ display: 'grid', gap: 16 }}>
            {visibleStoryboards.map((item, index) => (
              <ViewportMount
                key={`${projectId}:${item.id}`}
                id={`storyboard-${item.id}`}
                eager={index < 2 || item.id === navigationTarget.storyboardId || item.id === activeStoryboardId}
              >
                <StoryboardCard storyboardId={item.id} />
              </ViewportMount>
            ))}
          </div>
        ) : (
          <div className="storyboard-empty-workspace">
            <StoryboardVideoEmptyState generationStatus={activeGenerationStatus} />
          </div>
        )}
        {storyboardTotal > storyboardPageSize ? (
          <Flex justify="center" align="center" gap={12} style={{ marginTop: 20 }}>
            <Button
              size="small"
              disabled={storyboardPage <= 1}
              onClick={() => void changeStoryboardPage(storyboardPage - 1)}
            >
              上一页
            </Button>
            <Typography.Text type="secondary">
              第 {storyboardPage} / {' '}
              {Math.ceil(storyboardTotal / storyboardPageSize)} 页，共 {' '}
              {storyboardTotal} 个镜头
            </Typography.Text>
            <Button
              size="small"
              disabled={storyboardPage >= Math.ceil(storyboardTotal / storyboardPageSize)}
              onClick={() => void changeStoryboardPage(storyboardPage + 1)}
            >
              下一页
            </Button>
          </Flex>
        ) : null}
        <AssetVariantGenerationModal
          open={Boolean(referenceGenerationTarget)}
          projectId={projectId}
          onImageSelected={async () => {
            const target = referenceGenerationTarget;
            if (!target) return;
            const response = await queryAssetVisualWorkspace(projectId, target.assetType, target.assetId);
            if (visualProjectId.current !== projectId) return;
            setAssetVisuals((previous) => ({
              ...previous,
              [`${target.assetType}:${target.assetId}`]: response.data,
            }));
            await refreshAfterMutation();
          }}
          variant={referenceGenerationTarget?.variant}
          assetType={referenceGenerationTarget?.assetType}
          primaryImageUrl={referenceGenerationTarget?.primaryImageThumbnailUrl}
          referenceImageUrl={referenceGenerationTarget?.primaryImageUrl}
          imageModels={imageModels}
          values={referenceGenerationValues}
          submitting={referenceGenerationSubmitting}
          onChange={(values) => setReferenceGenerationValues((previous) => ({
            ...previous,
            ...values,
          }))}
          onCancel={() => {
            referenceGenerationRequestId.current += 1;
            setReferenceGenerationTarget(undefined);
            setReferenceGenerationSubmitting(false);
          }}
          onSubmit={() => void submitReferenceVariantGeneration()}
        />
      </div>
    </div>
    </StoryboardCardProvider>
  );
};

export default ProductionWorkbenchStoryboard;
