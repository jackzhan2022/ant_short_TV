import {
  CloseOutlined,
  CopyOutlined,
  DownOutlined,
  ExpandOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Flex,
  Input,
  InputNumber,
  Modal,
  Popover,
  Select,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import PointEstimateButton from '@/components/PointEstimateButton';
import './storyboard.css';

import { memo } from 'react';
import ClickToPlayVideo, {
  stopActiveVideo,
} from '@/components/ClickToPlayVideo';
import LazyMediaImage from '@/components/LazyMediaImage';
import MaterialPromptEditor from './MaterialPromptEditor';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import StoryboardHistoryPicker from './StoryboardHistoryPicker';
import StoryboardImageUpload from './StoryboardImageUpload';
import StoryboardPromptReferencePicker from './StoryboardPromptReferencePicker';
import StoryboardVideoEmptyState from './StoryboardVideoEmptyState';
import StoryboardVideoFailure, {
  videoGenerationError,
} from './StoryboardVideoFailure';
import StoryboardVideoGenerationStatus, {
  isVideoGenerationActive,
} from './StoryboardVideoGenerationStatus';
import StoryboardVideoStrip from './StoryboardVideoStrip';
import StoryboardVideoTools from './StoryboardVideoTools';
import StoryboardVoiceGenerator from './StoryboardVoiceGenerator';
import SubtitleEraseStatus from './SubtitleEraseStatus';
import type {
  AiImageTask,
  AiVideoResult,
  AiVideoTask,
  CharacterAsset,
  PropAsset,
  SceneAsset,
  StoryboardPromptDocument,
  StoryboardPromptNode,
  StoryboardShot,
} from './service';
import {
  useStoryboardCardActions,
  useStoryboardEditor,
  useStoryboardMedia,
  useStoryboardResources,
  useStoryboardRow,
} from './storyboard-card-context';
import { useStoryboardDraft } from './storyboard-drafts';
import {
  getStoryboardPrompt,
  getStoryboardPropNames,
  getStoryboardScriptText,
  splitNames,
} from './storyboard-model';
import { isEraseActive } from './subtitle-erase-service';
import useDelayedPromptSave from './useDelayedPromptSave';

type PromptReferenceOption = Extract<StoryboardPromptNode, { type: 'mention' }>;
const successStatuses = ['SUCCESS', 'SUCCEEDED'];
const getTaskImage = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId: number,
) => {
  const tasks = imageTasks.filter(
    (item) =>
      item.targetType === targetType &&
      item.targetId === targetId &&
      successStatuses.includes(item.status) &&
      item.results?.length,
  );
  const selectedResult =
    tasks
      .flatMap((task) => task.results)
      .find(
        (result) => result.selected && successStatuses.includes(result.status),
      ) ||
    tasks[0]?.results.find((result) =>
      successStatuses.includes(result.status),
    ) ||
    tasks[0]?.results[0];
  return selectedResult?.thumbnailUrl || undefined;
};

const getPlaceholderBackground = (key: string) => {
  if (key.startsWith('scene')) {
    return [
      'linear-gradient(135deg, #d8e9f8 0%, #f9fbff 52%, #b8c6d6 100%)',
      'linear-gradient(135deg, #20242d 0%, #3d4148 50%, #11141a 100%)',
      'linear-gradient(135deg, #d4ead8 0%, #eef6ff 48%, #a6b6ca 100%)',
      'linear-gradient(135deg, #101720 0%, #2d3542 52%, #111827 100%)',
    ][Number(key.at(-1)) || 0];
  }
  if (key.startsWith('prop')) {
    return [
      'radial-gradient(circle at 50% 38%, #d98a1f 0 17%, transparent 18%), linear-gradient(#fff, #fbfcff)',
      'linear-gradient(145deg, #2d3034 0%, #777b82 46%, #f5f7fb 47%, #ffffff 100%)',
      'radial-gradient(circle at 52% 44%, #35a2ff 0 18%, transparent 19%), linear-gradient(#fff, #fbfcff)',
      'radial-gradient(ellipse at 50% 52%, #edf0f5 0 32%, transparent 33%), linear-gradient(#fff, #fbfcff)',
    ][Number(key.at(-1)) || 0];
  }
  return [
    'linear-gradient(90deg, #fff 0%, #fff5d8 32%, #f8fbff 33%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #2f343b 34%, #f7f9ff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #e8f2ff 34%, #fbfdff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #8b1f2c 34%, #fff 35%, #fff 100%)',
  ][Number(key.at(-1)) || 0];
};

const firstByName = <T extends { name: string }>(items: T[], names: string[]) =>
  items.find((item) => names.includes(item.name));

const suggestVisibleCharacter = (
  storyboard: StoryboardShot,
  assets: CharacterAsset[],
) => {
  const descriptions = [
    storyboard.visualDescription,
    ...(storyboard.shotPlan?.shots || []).flatMap((shot) => [
      shot.positioning,
      shot.action,
    ]),
  ]
    .filter(Boolean)
    .join(' ');
  const matches = assets.filter((asset) => {
    const name = asset.name.trim();
    if (!name) return false;
    if (!/^[a-zA-Z\s'-]+$/.test(name)) return descriptions.includes(name);
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`(^|[^a-zA-Z])${escaped}(?=$|[^a-zA-Z])`, 'i').test(
      descriptions,
    );
  });
  return matches.length === 1 ? matches[0] : undefined;
};

const getStoryboardVideo = (
  storyboard: StoryboardShot,
  videoTasks: AiVideoTask[],
) => {
  const boundTask = videoTasks.find(
    (task) =>
      task.storyboardId === storyboard.id &&
      task.results?.some(
        (result) => result.id === storyboard.currentVideoResultId,
      ),
  );
  const selectedTask = videoTasks.find(
    (task) =>
      task.storyboardId === storyboard.id &&
      task.results?.some((result) => result.isSelected),
  );
  const task =
    boundTask ||
    selectedTask ||
    videoTasks.find(
      (item) => item.storyboardId === storyboard.id && item.results?.length,
    );
  const result =
    task?.results?.find(
      (item) => item.id === storyboard.currentVideoResultId,
    ) ||
    task?.results?.find((item) => item.isSelected) ||
    task?.results?.[0];
  return {
    resultId:
      result?.id ||
      (storyboard.currentVideoUrl
        ? storyboard.currentVideoResultId || undefined
        : undefined),
    selected: Boolean(
      result?.isSelected || result?.id === storyboard.currentVideoResultId,
    ),
    coverUrl:
      result?.coverUrl || storyboard.firstFrameThumbnailUrl || undefined,
    videoUrl:
      result?.videoUrl ||
      storyboard.currentVideoUrl ||
      storyboard.currentShotVideoUrl ||
      undefined,
  };
};

const thumbnailFor = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId?: number,
  fallback?: string | null,
) => {
  if (targetId) {
    return (
      getTaskImage(imageTasks, targetType, targetId) || fallback || undefined
    );
  }
  return fallback || undefined;
};

const avatarRail = (
  names: string[],
  assets: CharacterAsset[],
  imageTasks: AiImageTask[],
) => (
  <div style={{ display: 'flex', width: 58, overflow: 'hidden' }}>
    {names.slice(0, 4).map((name, index) => {
      const asset = assets.find((item) => item.name === name);
      const image =
        asset?.visual?.primaryVariant?.currentImageThumbnailUrl ||
        asset?.mainImageThumbnailUrl ||
        thumbnailFor(imageTasks, 'CHARACTER', asset?.id);
      return (
        <span
          key={name}
          title={name}
          style={{
            width: 18,
            height: 36,
            flex: 'none',
            marginLeft: index ? -5 : 0,
            borderRadius: 9,
            border: '1px solid #fff',
            background: '#e8eef7',
            display: 'grid',
            placeItems: 'center',
            fontSize: 11,
            overflow: 'hidden',
          }}
        >
          {image ? (
            <LazyMediaImage
              src={image}
              alt={`${name}参考图`}
              width="100%"
              height="100%"
              preview={false}
              style={{ objectFit: 'cover' }}
            />
          ) : (
            name.slice(0, 1)
          )}
        </span>
      );
    })}
  </div>
);

const PreviewPoster = ({ src, title }: { src?: string; title: string }) =>
  src ? (
    <LazyMediaImage
      src={src}
      alt={title}
      width="100%"
      height="100%"
      preview={false}
      style={{ objectFit: 'cover' }}
    />
  ) : (
    <div
      role="img"
      aria-label={title}
      style={{
        width: '100%',
        height: '100%',
        background:
          'linear-gradient(180deg, #9eb8d2 0%, #dce7f2 38%, #b9c3cc 39%, #6f7d8c 100%)',
      }}
    />
  );

const plainTextFromDocument = (document?: StoryboardPromptDocument | null) =>
  document?.nodes
    .map((node) => (node.type === 'text' ? node.text : node.displayName))
    .join('') || '';

const StoryboardCardView = ({
  row,
}: {
  row: NonNullable<ReturnType<typeof useStoryboardRow>>;
}) => {
  const {
    item,
    generationSettings,
    generationSettingsSaveStatus,
    eraseTasks,
    erasedPreviewResult,
  } = row;
  const {
    projectId,
    canCaptureVideo,
    canExtractAudio,
    characters,
    scenes,
    props,
    episodes,
    referenceOptions,
    referenceThumbnails,
    model,
    modelId,
    aspectRatio,
    modelConstraints,
    generatingReferenceKeys,
  } = useStoryboardResources();
  const {
    draftStore,
    updateDraft,
    saveFields,
    saveScript,
    replaceReferences,
    retrySave,
    saveStatus,
    promptFor,
    previewVideoOperations,
    saveGenerationSettings: onGenerationSettingsChange,
    retryGenerationSettings: onRetryGenerationSettings,
  } = useStoryboardEditor(item.id);
  const {
    imageTasks = [],
    videoTasks = [],
    videoHistory,
    bindingVideoId,
    selectingFirstFrameId,
    generateVideo,
    generateImage,
    bindVideoResult: onBindVideoResult,
    selectFirstFrame: onSelectFirstFrame,
    regenerateImage: onRegenerateImage,
    cancelImage: onCancelImage,
    cancelVideo: onCancelVideo,
    retryVideo: onRetryVideo,
    loadVideoCandidates,
    retryErase: onRetryErase,
  } = useStoryboardMedia(item.id);
  const {
    add,
    copy,
    remove,
    capture: onCaptureVideo,
    extractAudio: onExtractAudio,
    erase: onEraseVideo,
    uploaded: onImageUploaded,
    generateReferenceImage: onGenerateReferenceImage,
    loadVariants: onLoadVariants,
  } = useStoryboardCardActions(item.id);
  const index = (item.storyboardNo || item.shotNo) - 1;

  const { message } = App.useApp();
  const localDraft = useStoryboardDraft(draftStore, item.id);
  const promptDocument = useMemo(
    () => promptFor(item),
    [
      item,
      localDraft,
      promptFor,
      characters,
      scenes,
      props,
      episodes,
      imageTasks,
    ],
  );
  const draft = {
    ...(localDraft || {
      scriptText: getStoryboardScriptText(item),
      videoPrompt: getStoryboardPrompt(item),
    }),
    promptDocument,
  };
  const videoEstimateOperations = useMemo(
    () => previewVideoOperations([item], () => promptDocument),
    [
      item,
      promptDocument,
      previewVideoOperations,
      generationSettings,
      modelId,
      aspectRatio,
      referenceOptions,
    ],
  );
  const promptSave = useDelayedPromptSave(() =>
    saveFields({
      videoPrompt: draft.videoPrompt,
      promptDocument: draft.promptDocument || undefined,
    }),
  );
  const warnings = item.shotPlan?.warnings ?? [];
  const characterNames = splitNames(item.characters);
  const suggestedCharacter = characterNames.length
    ? undefined
    : suggestVisibleCharacter(item, characters);
  const shownCharacterNames = characterNames.length
    ? characterNames
    : suggestedCharacter
      ? [suggestedCharacter.name]
      : [];
  const dialogueSpeaker = String(item.dialogue || '')
    .split(/[:：]/, 1)[0]
    .replace(/VO$/i, '')
    .trim();
  const [previewKey, setPreviewKey] = useState<'VIDEO' | 'FIRST_FRAME'>(
    'VIDEO',
  );
  const [previewVersion, setPreviewVersion] = useState<AiVideoResult>();
  const [largeVideoOpen, setLargeVideoOpen] = useState(false);
  useEffect(() => {
    setPreviewVersion(undefined);
    setPreviewKey('VIDEO');
    setLargeVideoOpen(false);
  }, [item.currentVideoResultId]);
  useEffect(() => {
    if (erasedPreviewResult) {
      setPreviewVersion(erasedPreviewResult);
      setPreviewKey('VIDEO');
    }
  }, [erasedPreviewResult?.id]);
  const sceneNames = splitNames(item.scene);
  const propNames = getStoryboardPropNames(item);
  const scene = firstByName(scenes, sceneNames);
  const prop = firstByName(props, propNames);
  const boundVideo = getStoryboardVideo(item, videoTasks);
  const version =
    previewVersion ||
    videoHistory?.data.find(
      (result) => result.id === item.currentVideoResultId,
    );
  const video = version
    ? {
        resultId: version.id,
        selected:
          item.currentVideoResultId != null
            ? version.id === item.currentVideoResultId
            : Boolean(version.isSelected),
        coverUrl: version.coverUrl || undefined,
        videoUrl: version.videoUrl,
      }
    : boundVideo;
  const videoVersions = videoTasks
    .filter((task) => task.storyboardId === item.id)
    .flatMap((task) => task.results || []);
  const insertReference = (reference: PromptReferenceOption) => {
    const currentNodes = draft.promptDocument?.nodes?.length
      ? draft.promptDocument.nodes
      : [{ type: 'text' as const, text: draft.videoPrompt }];
    const separator = currentNodes.length
      ? [{ type: 'text' as const, text: ' ' }]
      : [];
    const promptDocument: StoryboardPromptDocument = {
      version: 2,
      nodes: [...currentNodes, ...separator, reference],
    };
    const videoPrompt = plainTextFromDocument(promptDocument);
    promptSave.cancel();
    updateDraft({ videoPrompt, promptDocument });
    saveFields({ videoPrompt, promptDocument });
  };
  const copyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(draft.videoPrompt);
      message.success('提示词已复制');
    } catch {
      message.error('提示词复制失败');
    }
  };
  const renderReferencePicker = (
    onSelect: (reference: PromptReferenceOption) => void,
  ) => (
    <StoryboardPromptReferencePicker
      storyboardNo={index + 1}
      characters={characters}
      scenes={scenes}
      props={props}
      references={referenceOptions}
      thumbnails={referenceThumbnails}
      onSelect={onSelect}
      onLoadVariants={onLoadVariants}
    />
  );
  const referencePicker = renderReferencePicker(insertReference);
  const parameterEditor = (
    <div style={{ width: 260, display: 'grid', gap: 12 }}>
      <Flex justify="space-between" align="center" gap={12}>
        <Typography.Text>时长</Typography.Text>
        <Flex align="center" gap={6}>
          <InputNumber
            aria-label={`分镜${index + 1}视频时长`}
            min={
              modelConstraints?.duration?.intelligent
                ? -1
                : modelConstraints?.duration?.min
            }
            max={modelConstraints?.duration?.max}
            value={generationSettings.durationSeconds}
            onChange={(value) =>
              onGenerationSettingsChange({
                durationSeconds:
                  typeof value === 'number'
                    ? value
                    : generationSettings.durationSeconds,
              })
            }
            style={{ width: 108 }}
          />
          <Typography.Text>秒</Typography.Text>
        </Flex>
      </Flex>
      <Flex justify="space-between" align="center" gap={12}>
        <Typography.Text>分辨率</Typography.Text>
        <Select
          aria-label={`分镜${index + 1}视频分辨率`}
          value={generationSettings.resolution}
          options={(modelConstraints?.resolutions || ['720p']).map((value) => ({
            label: value,
            value,
          }))}
          onChange={(resolution) => onGenerationSettingsChange({ resolution })}
          style={{ width: 138 }}
        />
      </Flex>
      <Flex justify="space-between" align="center">
        <Typography.Text>生成音频</Typography.Text>
        <Switch
          aria-label={`分镜${index + 1}生成音频`}
          checked={generationSettings.generateAudio}
          onChange={(generateAudio) =>
            onGenerationSettingsChange({ generateAudio })
          }
        />
      </Flex>
      <Flex justify="space-between" align="center">
        <Typography.Text>视频水印</Typography.Text>
        <Switch
          aria-label={`分镜${index + 1}视频水印`}
          checked={generationSettings.watermark}
          onChange={(watermark) => onGenerationSettingsChange({ watermark })}
        />
      </Flex>
    </div>
  );
  const resolveEpisodeVisual = (asset?: SceneAsset | PropAsset) => {
    const visual = asset?.visual;
    const episodeId = episodes.find(
      (episode) => episode.episodeNo === item.episodeNo,
    )?.episodeId;
    const preferredBinding = episodeId
      ? visual?.episodeBindings.find(
          (binding) =>
            binding.episodeId === episodeId &&
            binding.preferred &&
            binding.status === 'ACTIVE',
        )
      : undefined;
    const preferredVariant = preferredBinding
      ? visual?.variants.find(
          (variant) => variant.id === preferredBinding.variantId,
        )
      : undefined;
    if (preferredVariant?.usable && preferredVariant.currentImageThumbnailUrl) {
      return {
        url: preferredVariant.currentImageThumbnailUrl,
        source: 'EPISODE_PREFERRED',
      };
    }
    return {
      url:
        visual?.primaryVariant?.currentImageThumbnailUrl ||
        asset?.mainImageThumbnailUrl,
      source:
        visual?.resolvedImageSource ||
        (asset?.mainImageThumbnailUrl ? 'LEGACY_FALLBACK' : undefined),
    };
  };
  const resolvedSceneVisual = resolveEpisodeVisual(scene);
  const resolvedPropVisual = resolveEpisodeVisual(prop);
  const sceneImage =
    resolvedSceneVisual.url || thumbnailFor(imageTasks, 'SCENE', scene?.id);
  const propImage =
    resolvedPropVisual.url || thumbnailFor(imageTasks, 'PROP', prop?.id);
  const previewSources = [
    {
      key: 'FIRST_FRAME' as const,
      label: '首帧',
      url:
        item.firstFrameThumbnailUrl ||
        thumbnailFor(imageTasks, 'STORYBOARD', item.id),
    },
    { key: 'VIDEO' as const, label: '视频', url: video.coverUrl },
  ].filter((source) => source.url);
  const visiblePreviewSources = previewSources.filter(
    (source) =>
      source.key !== 'VIDEO' ||
      (!videoVersions.length && !videoHistory?.data.length),
  );
  const selectedPreview = previewSources.find(
    (source) => source.key === previewKey,
  );
  const visualSourceLabel = (source?: string | null) => {
    if (source === 'EPISODE_PREFERRED') return '剧集首选形象';
    if (source === 'PRIMARY_VARIANT') return '主形象';
    if (source === 'LEGACY_FALLBACK') return '旧图片回退';
    return '未解析参考图';
  };
  const imageTask = imageTasks
    .filter(
      (task) => task.targetType === 'STORYBOARD' && task.targetId === item.id,
    )
    .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))[0];
  const unselectedFirstFrame = imageTasks
    .filter(
      (task) => task.targetType === 'STORYBOARD' && task.targetId === item.id,
    )
    .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
    .flatMap((task) => task.results || [])
    .find(
      (result) =>
        !result.selected && result.status === 'ACTIVE' && result.thumbnailUrl,
    );
  const videoTask = videoTasks
    .filter((task) => task.storyboardId === item.id)
    .sort((a, b) => b.id - a.id)[0];
  const videoGenerating = isVideoGenerationActive(videoTask);
  const [dismissedVideoErrorTaskId, setDismissedVideoErrorTaskId] =
    useState<number>();
  const videoError = videoGenerationError(videoTask);
  const visibleVideoError =
    videoTask?.id === dismissedVideoErrorTaskId ? undefined : videoError;
  useEffect(() => {
    if (videoGenerating) {
      setPreviewKey('VIDEO');
      setPreviewVersion(undefined);
    }
  }, [videoGenerating, videoTask?.id]);
  return (
    <article className="storyboard-card">
      <div className="storyboard-card-header">
        <Flex align="center" gap={10}>
          <span className="storyboard-shot-number">
            {String(index + 1).padStart(2, '0')}
          </span>
          <Typography.Text>分镜{index + 1}</Typography.Text>
          <span className="storyboard-shot-mode">全能参考生视频</span>
        </Flex>
        <Flex gap={4} style={{ color: '#7f88a6', fontSize: 16 }}>
          <span
            className={`storyboard-shot-status${videoGenerating ? ' is-generating' : visibleVideoError ? ' is-failed' : video.videoUrl ? ' is-ready' : ''}`}
          >
            {videoGenerating
              ? '生成中'
              : visibleVideoError
                ? '生成失败'
                : video.videoUrl
                  ? '已生成'
                  : '待生成'}
          </span>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            aria-label="新增分镜"
            onClick={() => add()}
          />
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            aria-label="复制分镜"
            onClick={() => copy()}
          />
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label={`删除分镜${index + 1}`}
            onClick={async () => {
              const pending = promptSave.cancel();
              if (!(await remove()) && pending) {
                promptSave.markDirty();
                promptSave.schedule();
              }
            }}
          />
        </Flex>
      </div>

      {warnings.length ? (
        <div
          style={{
            padding: '10px 18px',
            background: '#fffbe6',
            borderBottom: '1px solid #ffe58f',
          }}
        >
          <Flex gap={8} wrap>
            {warnings.map((warning) => (
              <Tag
                color="warning"
                key={`${warning.code}-${warning.storyboardNo}-${warning.shotNo}-${warning.message}`}
              >
                {warning.shotNo ? `镜头${warning.shotNo}：` : ''}
                {warning.message}
              </Tag>
            ))}
          </Flex>
        </div>
      ) : null}

      <div className="storyboard-card-content">
        <section data-storyboard-scroll-region>
          <div className="storyboard-column-heading">
            <Typography.Text>分镜信息</Typography.Text>
            <Flex align="center" gap={6}>
              <PointEstimateButton
                compact
                operations={
                  (item.imagePrompt || item.visualDescription)?.trim()
                    ? [
                        {
                          url: `/api/projects/${projectId}/ai-image-tasks`,
                          data: {
                            taskType: 'STORYBOARD_FIRST_FRAME',
                            targetType: 'STORYBOARD',
                            targetId: item.id,
                            prompt: item.imagePrompt || item.visualDescription,
                            aspectRatio: '9:16',
                            imageCount: 1,
                            quality: 'STANDARD',
                          },
                        },
                      ]
                    : undefined
                }
                size="small"
                onClick={() => generateImage()}
                disabled={['PENDING', 'RUNNING'].includes(
                  imageTask?.execution?.status || '',
                )}
              >
                生成首帧
              </PointEstimateButton>
              <StoryboardHistoryPicker
                storyboardId={item.id}
                storyboardNo={index + 1}
                type="IMAGE"
              />
              {imageTask?.execution ? (
                <AiExecutionStatus
                  task={imageTask.execution}
                  onCancel={() => onCancelImage(imageTask)}
                  onRetry={() => onRegenerateImage(imageTask)}
                  retryOperations={[
                    {
                      url: `/api/projects/${projectId}/ai-image-tasks/${imageTask.id}/regenerate`,
                    },
                  ]}
                />
              ) : null}
            </Flex>
          </div>
          {unselectedFirstFrame ? (
            <Flex align="center" gap={8} style={{ marginTop: 10 }}>
              <div
                style={{
                  width: 42,
                  height: 42,
                  flex: 'none',
                  overflow: 'hidden',
                }}
              >
                <PreviewPoster
                  src={unselectedFirstFrame.thumbnailUrl ?? undefined}
                  title="待选首帧"
                />
              </div>
              <Button
                size="small"
                loading={selectingFirstFrameId === unselectedFirstFrame.id}
                onClick={() => onSelectFirstFrame(unselectedFirstFrame.id)}
              >
                设为分镜{index + 1}首帧
              </Button>
            </Flex>
          ) : null}
          <div className="storyboard-script-heading">
            剧本原文
            <span>可编辑</span>
          </div>
          <Input.TextArea
            className="storyboard-script-editor"
            aria-label={`分镜${index + 1}剧本原文`}
            value={draft.scriptText}
            onChange={(event) =>
              updateDraft({ scriptText: event.target.value })
            }
            onBlur={() => saveScript()}
            autoSize={{ minRows: 4, maxRows: 5 }}
          />

          {item.assetReferences !== undefined ? (
            <StoryboardVoiceGenerator
              storyboardId={item.id}
              storyboardNo={index + 1}
              defaultSpeaker={dialogueSpeaker || shownCharacterNames[0] || ''}
              textContent={item.dialogue || item.visualDescription || ''}
            >
              {(openVoice) => (
                <StoryboardAssetReferenceEditor
                  projectId={projectId}
                  storyboardId={item.id}
                  storyboardNo={index + 1}
                  references={item.assetReferences || []}
                  characters={characters}
                  scenes={scenes}
                  props={props}
                  onChange={(references) => void replaceReferences(references)}
                  onLoadVariants={onLoadVariants}
                  onVoice={openVoice}
                  onGenerateImage={(reference, generationKey) =>
                    void onGenerateReferenceImage(reference, generationKey)
                  }
                  generatingReferenceKeys={generatingReferenceKeys}
                />
              )}
            </StoryboardVoiceGenerator>
          ) : (
            <>
              <div style={{ marginTop: 22 }}>
                <Flex justify="space-between" align="center">
                  <span>出镜角色</span>
                </Flex>
                <Flex align="center" gap={9} style={{ marginTop: 13 }}>
                  {avatarRail(shownCharacterNames, characters, imageTasks)}
                  <Select
                    aria-label={`分镜${index + 1}出镜角色`}
                    value={shownCharacterNames[0] || undefined}
                    options={characters.map((asset) => ({
                      label: `${asset.name} - ${asset.name}`,
                      value: asset.name,
                    }))}
                    style={{ flex: 1 }}
                    onChange={(value) =>
                      saveFields({ characters: value || '' })
                    }
                  />
                  {shownCharacterNames.length ? (
                    <Button
                      size="small"
                      aria-label={`清空分镜${index + 1}出镜角色`}
                      onClick={() => saveFields({ characters: '' })}
                    >
                      清空
                    </Button>
                  ) : null}
                  <StoryboardVoiceGenerator
                    storyboardId={item.id}
                    storyboardNo={index + 1}
                    defaultSpeaker={
                      dialogueSpeaker || shownCharacterNames[0] || ''
                    }
                    textContent={item.dialogue || item.visualDescription || ''}
                  />
                </Flex>
                {suggestedCharacter ? (
                  <Flex align="center" gap={6} style={{ marginTop: 6 }}>
                    <Tag color="warning">按画面推断，待确认</Tag>
                    <Button
                      size="small"
                      type="link"
                      onClick={() =>
                        saveFields({ characters: suggestedCharacter.name })
                      }
                    >
                      确认分镜{index + 1}出镜角色
                    </Button>
                  </Flex>
                ) : null}
              </div>

              <div style={{ marginTop: 28 }}>
                <Flex justify="space-between" align="center">
                  <span>分镜场景</span>
                </Flex>
                <div
                  style={{
                    width: 190,
                    height: 102,
                    marginTop: 12,
                    borderRadius: 8,
                    overflow: 'hidden',
                    background: '#eef2f7',
                  }}
                >
                  {sceneImage ? (
                    <PreviewPoster
                      src={sceneImage}
                      title={`${item.scene || '场景'}参考图`}
                    />
                  ) : (
                    <div
                      style={{
                        height: '100%',
                        display: 'grid',
                        placeItems: 'center',
                        color: '#8793a7',
                        fontSize: 12,
                      }}
                    >
                      未生成场景参考图
                    </div>
                  )}
                </div>
                {scene ? (
                  <Tag
                    color={
                      resolvedSceneVisual.source === 'LEGACY_FALLBACK'
                        ? 'orange'
                        : 'blue'
                    }
                  >
                    场景参考来源：
                    {visualSourceLabel(resolvedSceneVisual.source)}
                  </Tag>
                ) : null}
                <Flex gap={8} style={{ marginTop: 10 }}>
                  <Select
                    aria-label={`分镜${index + 1}场景`}
                    value={sceneNames[0] || undefined}
                    options={scenes.map((asset) => ({
                      label: `${asset.name} - ${asset.name}`,
                      value: asset.name,
                    }))}
                    style={{ flex: 1 }}
                    onChange={(value) => saveFields({ scene: value || '' })}
                  />
                  {sceneNames.length ? (
                    <Button
                      size="small"
                      aria-label={`清空分镜${index + 1}场景`}
                      onClick={() => saveFields({ scene: '' })}
                    >
                      清空
                    </Button>
                  ) : null}
                </Flex>
              </div>

              <div style={{ marginTop: 20 }}>
                <Flex justify="space-between" align="center">
                  <span>场景道具</span>
                </Flex>
                <Flex align="center" gap={9} style={{ marginTop: 13 }}>
                  <div
                    style={{
                      width: 48,
                      height: 38,
                      borderRadius: 7,
                      overflow: 'hidden',
                      background: getPlaceholderBackground('prop-1'),
                    }}
                  >
                    {propImage && (
                      <LazyMediaImage
                        src={propImage}
                        alt={prop?.name}
                        width="100%"
                        height="100%"
                        preview={false}
                        style={{ objectFit: 'cover' }}
                      />
                    )}
                  </div>
                  <Select
                    aria-label={`分镜${index + 1}场景道具`}
                    value={propNames[0] || undefined}
                    options={props.map((asset) => ({
                      label: `${asset.name} - ${asset.name}`,
                      value: asset.name,
                    }))}
                    style={{ flex: 1 }}
                    onChange={(value) => saveFields({ props: value || '' })}
                  />
                  {propNames.length ? (
                    <Button
                      size="small"
                      aria-label={`清空分镜${index + 1}场景道具`}
                      onClick={() => saveFields({ props: '' })}
                    >
                      清空
                    </Button>
                  ) : null}
                </Flex>
                {prop ? (
                  <Tag
                    color={
                      resolvedPropVisual.source === 'LEGACY_FALLBACK'
                        ? 'orange'
                        : 'blue'
                    }
                  >
                    道具参考来源：
                    {visualSourceLabel(resolvedPropVisual.source)}
                  </Tag>
                ) : null}
              </div>
            </>
          )}
        </section>

        <section className="storyboard-prompt-section">
          <div className="storyboard-column-heading">
            <Typography.Text>分镜视频生成</Typography.Text>
            <span className="storyboard-director-label">
              <ThunderboltOutlined />
              <span>3D导演台</span>
              <small>Beta</small>
            </span>
          </div>
          {videoTask?.referenceDiagnostics?.omitted?.length ? (
            <Flex gap={6} wrap className="storyboard-prompt-diagnostics">
              {(videoTask?.referenceDiagnostics?.omitted || []).map(
                (omitted) => (
                  <Tag
                    color="warning"
                    key={`${omitted.sourceType}-${omitted.sourceId}-${omitted.variantId}-${omitted.reason}`}
                  >
                    已省略{' '}
                    {omitted.displayName ||
                      `${omitted.assetType || '素材'} ${omitted.assetId || ''}`}
                    {omitted.reason
                      ? `：${omitted.reason === 'IMAGE_UNAVAILABLE' ? '参考图片不可用' : omitted.reason}`
                      : ''}
                  </Tag>
                ),
              )}
            </Flex>
          ) : null}
          <div className="storyboard-prompt-panel">
            <Flex
              align="center"
              gap={10}
              className="storyboard-composer-toolbar"
            >
              <span className="storyboard-composer-title">提示词</span>
              <span className="storyboard-material-limits" title="素材引用上限">
                {modelConstraints?.image?.maxCount !== undefined
                  ? `${modelConstraints.image.maxCount} 图`
                  : ''}
                {modelConstraints?.video?.maxCount !== undefined
                  ? ` · ${modelConstraints.video.maxCount} 视频`
                  : ''}
                {modelConstraints?.audio?.maxCount !== undefined
                  ? ` · ${modelConstraints.audio.maxCount} 音频`
                  : ''}
              </span>
              <StoryboardImageUpload
                key={`${projectId}:${item.id}`}
                projectId={projectId}
                storyboardId={item.id}
                storyboardNo={index + 1}
                disabled={!canCaptureVideo}
                onUploaded={(image) => {
                  const reference: PromptReferenceOption = {
                    type: 'mention',
                    mediaType: 'IMAGE',
                    sourceType: 'IMAGE_MATERIAL',
                    sourceId: image.materialId,
                    displayName: image.name,
                  };
                  onImageUploaded(reference);
                  insertReference(reference);
                }}
              />
              <Popover
                content={referencePicker}
                trigger="click"
                placement="bottomLeft"
              >
                <Button
                  type="text"
                  size="small"
                  aria-label="添加提示词引用"
                  title="引用资产"
                  icon={<span aria-hidden="true">@</span>}
                />
              </Popover>
              <Button
                type="text"
                size="small"
                aria-label={`复制分镜${index + 1}提示词`}
                title="复制提示词"
                icon={<CopyOutlined />}
                onClick={() => void copyPrompt()}
              />
            </Flex>
            {saveStatus === 'error' ? (
              <Button
                type="link"
                danger
                size="small"
                aria-label={`重试保存分镜${index + 1}`}
                onClick={() => void retrySave()}
              >
                保存失败，点击重试
              </Button>
            ) : null}
            <MaterialPromptEditor
              label={`分镜${index + 1}视频提示词`}
              value={draft.videoPrompt}
              document={draft.promptDocument}
              referenceThumbnails={referenceThumbnails}
              renderReferencePicker={renderReferencePicker}
              onCommit={(videoPrompt, promptDocument) => {
                promptSave.cancel();
                saveFields({ videoPrompt, promptDocument });
              }}
              onChange={(videoPrompt, promptDocument) => {
                promptSave.markDirty();
                updateDraft({ videoPrompt, promptDocument });
              }}
              onFocus={promptSave.pause}
              onBlur={promptSave.schedule}
            />
            <div className="storyboard-composer-hint">
              <span>@ 引用角色、场景与音色</span>
              <span>
                {saveStatus === 'saving'
                  ? '保存中…'
                  : saveStatus === 'saved'
                    ? '已保存'
                    : ''}
              </span>
            </div>
            <div className="storyboard-composer-footer">
              <span className="storyboard-composer-model" title={model}>
                <ThunderboltOutlined />
                {model}
              </span>
              <div className="storyboard-composer-actions">
                <Popover
                  content={parameterEditor}
                  trigger="click"
                  placement="bottomLeft"
                >
                  <Button
                    type="text"
                    size="small"
                    aria-label={`分镜${index + 1}视频参数`}
                  >
                    {generationSettings.durationSeconds === -1
                      ? '智能'
                      : `${generationSettings.durationSeconds}s`}{' '}
                    · {generationSettings.resolution} <DownOutlined />
                  </Button>
                </Popover>
                <PointEstimateButton
                  compact
                  operations={videoEstimateOperations}
                  type="primary"
                  onClick={() => {
                    promptSave.flush();
                    generateVideo();
                  }}
                  aria-label={`生成分镜${index + 1}视频`}
                  disabled={videoGenerating}
                >
                  <ThunderboltOutlined /> 生成视频
                </PointEstimateButton>
              </div>
            </div>
            {generationSettingsSaveStatus ? (
              <Flex
                aria-label={`分镜${index + 1}参数保存状态`}
                style={{ marginTop: 4 }}
              >
                {generationSettingsSaveStatus === 'error' ? (
                  <Button
                    type="link"
                    size="small"
                    danger
                    aria-label={`重试保存分镜${index + 1}视频参数`}
                    onClick={onRetryGenerationSettings}
                  >
                    视频参数保存失败，重试
                  </Button>
                ) : (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {generationSettingsSaveStatus === 'saving'
                      ? '正在保存视频参数'
                      : '视频参数已保存'}
                  </Typography.Text>
                )}
              </Flex>
            ) : null}
          </div>
        </section>

        <section
          className="storyboard-preview-section"
          aria-label={`分镜${index + 1}视频预览区`}
        >
          <div className="storyboard-column-heading">
            <Typography.Text>视频预览</Typography.Text>
            {previewKey === 'VIDEO' &&
            video.videoUrl &&
            !videoGenerating &&
            !visibleVideoError ? (
              <Button
                type="text"
                size="small"
                icon={<ExpandOutlined />}
                aria-label={`大屏播放分镜${index + 1}视频`}
                onClick={() => {
                  stopActiveVideo();
                  setLargeVideoOpen(true);
                }}
              />
            ) : null}
          </div>
          <div className="storyboard-preview-stage">
            <div
              className="storyboard-preview-media"
              style={{ position: 'relative' }}
            >
              {videoGenerating && videoTask ? (
                <StoryboardVideoGenerationStatus
                  storyboardNo={index + 1}
                  task={videoTask}
                  onCancel={() => onCancelVideo(videoTask)}
                />
              ) : visibleVideoError && videoTask ? (
                <StoryboardVideoFailure
                  storyboardNo={index + 1}
                  message={visibleVideoError}
                  timedOut={videoTask.execution?.status === 'TIMED_OUT'}
                  onClose={() => setDismissedVideoErrorTaskId(videoTask.id)}
                  onRetry={
                    videoTask.retryable || videoTask.execution?.retryable
                      ? () => onRetryVideo(videoTask)
                      : undefined
                  }
                  retryOperations={[
                    {
                      url: `/api/projects/${projectId}/ai-video-tasks/${videoTask.id}/regenerate`,
                    },
                  ]}
                >
                  {!!videoTask.referenceDiagnostics?.failedImages?.length && (
                    <div
                      style={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 12,
                        marginTop: 8,
                      }}
                    >
                      {videoTask.referenceDiagnostics.failedImages.map(
                        (image) => (
                          <figure
                            key={image.contentIndex}
                            style={{ margin: 0, width: 120 }}
                          >
                            {image.imageUrl ? (
                              <LazyMediaImage
                                src={image.imageUrl}
                                alt={`${image.label}：${image.displayName || '参考图片'}`}
                                width={120}
                                height={90}
                                imageStyle={{ objectFit: 'contain' }}
                                placeholderFallback="图片无法加载"
                              />
                            ) : null}
                            <figcaption>
                              {image.label}
                              {image.displayName
                                ? `：${image.displayName}`
                                : ''}
                            </figcaption>
                          </figure>
                        ),
                      )}
                    </div>
                  )}
                </StoryboardVideoFailure>
              ) : previewKey === 'VIDEO' && video.videoUrl ? (
                <ClickToPlayVideo
                  alt={`分镜${index + 1}成片预览`}
                  src={video.videoUrl}
                  poster={video.coverUrl || undefined}
                  style={{
                    width: '100%',
                    height: '100%',
                    aspectRatio: 'auto',
                    background: '#171922',
                  }}
                  active={!largeVideoOpen}
                />
              ) : previewKey === 'VIDEO' ? (
                <StoryboardVideoEmptyState />
              ) : (
                <PreviewPoster
                  src={selectedPreview?.url || video.coverUrl}
                  title={`分镜${index + 1}主预览`}
                />
              )}
            </div>
            {previewKey === 'VIDEO' &&
            video.videoUrl &&
            !videoGenerating &&
            !visibleVideoError ? (
              <StoryboardVideoTools
                enabled={canCaptureVideo}
                resultId={video.resultId}
                canErase={canCaptureVideo}
                canExtractAudio={canExtractAudio}
                onExtractAudio={() => {
                  if (video.resultId)
                    onExtractAudio(video.resultId, item.storyboardNo || item.shotNo);
                }}
                eraseRunning={eraseTasks.some(
                  (task) =>
                    task.sourceResultId === video.resultId &&
                    isEraseActive(task),
                )}
                onErase={() => {
                  if (video.resultId)
                    onEraseVideo(
                      video.resultId,
                      item.storyboardNo || item.shotNo,
                    );
                }}
                onCapture={() => {
                  if (video.resultId)
                    onCaptureVideo(
                      video.resultId,
                      item.storyboardNo || item.shotNo,
                    );
                }}
              />
            ) : null}
          </div>
          <div className="storyboard-media-format">
            <span>{aspectRatio || '9:16'}</span>
            <span>
              {generationSettings.resolution} ·{' '}
              {generationSettings.durationSeconds === -1
                ? '智能时长'
                : `${generationSettings.durationSeconds}s`}
            </span>
          </div>
          <SubtitleEraseStatus tasks={eraseTasks} onRetry={onRetryErase} />
          <StoryboardVideoStrip
            storyboardNo={item.storyboardNo || item.shotNo}
            results={videoVersions}
            history={videoHistory}
            currentResultId={
              item.currentVideoResultId ||
              (boundVideo.selected ? boundVideo.resultId : undefined)
            }
            previewResultId={
              previewKey === 'VIDEO' ? video.resultId : undefined
            }
            onPreview={(result) => {
              setPreviewVersion(result);
              setPreviewKey('VIDEO');
            }}
            onLoad={(current, pageSize, signal) =>
              loadVideoCandidates(current, pageSize, signal)
            }
          />
          {visiblePreviewSources.length > 0 && (
            <Flex
              justify="center"
              gap={10}
              style={{ height: 54, paddingTop: 12 }}
            >
              {visiblePreviewSources.map((source) => (
                <Button
                  key={source.key}
                  type="text"
                  aria-label={`查看分镜${index + 1}${source.label}`}
                  onClick={() => setPreviewKey(source.key)}
                  style={{
                    position: 'relative',
                    width: 42,
                    height: 40,
                    padding: 0,
                    borderRadius: 6,
                    border:
                      previewKey === source.key
                        ? '2px solid #5454ff'
                        : '1px solid #dfe5f2',
                    overflow: 'hidden',
                  }}
                >
                  <PreviewPoster
                    src={source.url || undefined}
                    title={`分镜${index + 1}${source.label}缩略图`}
                  />
                  {previewKey === source.key && (
                    <span
                      style={{
                        position: 'absolute',
                        left: 0,
                        right: 0,
                        bottom: 0,
                        height: 16,
                        background: '#5454ff',
                        color: '#fff',
                        fontSize: 10,
                        textAlign: 'center',
                        lineHeight: '16px',
                      }}
                    >
                      当前预览
                    </span>
                  )}
                </Button>
              ))}
            </Flex>
          )}
          {video.resultId ? (
            <Flex className="storyboard-preview-binding" justify="center">
              {video.selected ? (
                <Tag color="success">当前分镜视频</Tag>
              ) : (
                <Button
                  size="small"
                  loading={bindingVideoId === video.resultId}
                  onClick={() => onBindVideoResult(video.resultId as number)}
                >
                  设为当前分镜视频
                </Button>
              )}
            </Flex>
          ) : null}
          <Flex
            justify="space-between"
            align="center"
            className="storyboard-history-row"
          >
            <span>历史视频</span>
            <StoryboardHistoryPicker
              storyboardId={item.id}
              storyboardNo={index + 1}
              type="VIDEO"
            />
          </Flex>
          {largeVideoOpen && video.videoUrl ? (
            <Modal
              open
              title={`分镜${index + 1}视频预览`}
              width={1040}
              centered
              footer={null}
              destroyOnHidden
              onCancel={() => {
                stopActiveVideo();
                setLargeVideoOpen(false);
              }}
            >
              <ClickToPlayVideo
                src={video.videoUrl}
                poster={video.coverUrl}
                alt={`分镜${index + 1}大屏成片预览`}
                style={{
                  height: 'min(68vh, 660px)',
                  aspectRatio: 'auto',
                  borderRadius: 8,
                }}
              />
            </Modal>
          ) : null}
        </section>
      </div>
    </article>
  );
};

const StoryboardCard = memo(({ storyboardId }: { storyboardId: number }) => {
  const row = useStoryboardRow(storyboardId);
  return row ? <StoryboardCardView row={row} /> : null;
});
export default StoryboardCard;
