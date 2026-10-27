import PointEstimateButton from '@/components/PointEstimateButton';
import type { PointEstimateOperation } from '@/services/point-estimate';
import {
  BarsOutlined,
  CloseOutlined,
  CopyOutlined,
  HolderOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useLocation, useOutletContext, useParams } from '@umijs/max';
import {
  App,
  Button,
  Empty,
  Flex,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Popover,
  Select,
  Spin,
  Switch,
  Tag,
  Typography,
} from 'antd';
import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import type { ProductionWorkbenchOutletContext } from './index';
import ViewportMount from './ViewportMount';
import { queryProjectMetadata } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import type { MediaPage } from '@/services/media/paging';
import {
  confirmPointEstimates,
  isPointEstimateCancelled,
  PointEstimateCancelledError,
} from '@/services/point-estimate';
import { readStoryboardTarget, STORYBOARD_PAGE_SIZE } from './video-workbench-state';
import './storyboard.css';
import ClickToPlayVideo, { stopActiveVideo } from '@/components/ClickToPlayVideo';
import VideoFrameCaptureModal from './VideoFrameCaptureModal';
import StoryboardVideoTools from './StoryboardVideoTools';
import StoryboardAvatarRecovery from './StoryboardAvatarRecovery';
import { queryRecoveredVideoTask } from './avatar-recovery-service';
import LazyMediaImage from '@/components/LazyMediaImage';
import AssetVariantGenerationModal, {
  type AssetVariantGenerationValues,
} from './AssetVariantGenerationModal';
import {
  type ProjectModelOption,
  queryProjectAiConfig,
  queryProjectAiModels,
} from './ai-config/service';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import { useStoryboardVideoSettings, type VideoGenerationSettings, type VideoSettingsSaveStatus } from './useStoryboardVideoSettings';
import StoryboardPromptReferencePicker from './StoryboardPromptReferencePicker';
import StoryboardImageUpload from './StoryboardImageUpload';
import BatchVideoGenerationModal from './BatchVideoGenerationModal';
import StoryboardVideoStrip from './StoryboardVideoStrip';
import StoryboardPositionRail from './StoryboardPositionRail';
import StoryboardVideoEmptyState from './StoryboardVideoEmptyState';
import StoryboardVideoFailure, { videoGenerationError } from './StoryboardVideoFailure';
import StoryboardVideoGenerationStatus, { isVideoGenerationActive } from './StoryboardVideoGenerationStatus';
import type {
  AiImageResult,
  AiImageTask,
  AiVideoResult,
  AiVideoTask,
  AiVoiceTask,
  AssetVisualWorkspace,
  CharacterAsset,
  CreateAiVideoTaskValues,
  ProductionWorkspaceState,
  PropAsset,
  SaveStoryboardValues,
  SceneAsset,
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
  bindAiVideoResultToStoryboard,
  breakdownStoryboards,
  cancelAiImageTask,
  cancelAiVideoTask,
  createAiImageTask,
  createAiVideoTask,
  createAiVoiceTask,
  createStoryboard,
  createStoryboardBatch,
  deleteStoryboard,
  queryAiImageResults,
  queryAiImageTask,
  queryAiVideoResults,
  queryAssetSettingsSummary,
  queryAssetVisualWorkspace,
  queryLatestStoryboardBatch,
  queryStoryboardBatch,
  queryStoryboardMedia,
  queryStoryboardEditorPage,
  queryStoryboardEpisodeDetails,
  regenerateAiImageTask,
  regenerateAiVideoTask,
  replaceStoryboardAssetReferences,
  selectAiImageResult,
  updateStoryboard,
  updateVisualVariant,
} from './service';
import { hasImageReference, hydratePromptDocument,
  type NamedImageReference,
  promptText, withFirstFrameReference,
} from './storyboard-references';

type StoryboardDraft = Record<
  number,
  {
    scriptText: string;
    videoPrompt: string;
    promptDocument?: StoryboardPromptDocument | null;
  }
>;
type StoryboardWithProps = StoryboardShot & { props?: string };
type PromptReferenceOption = Extract<
  StoryboardPromptNode,
  { type: 'mention' }
>;
type VideoSubmission = {
  storyboard: StoryboardShot;
  promptDocument: StoryboardPromptDocument;
  values: CreateAiVideoTaskValues;
};
type VoiceGenerationValues = {
  voiceType: string;
  speakerName: string;
  voiceId: string;
  textContent: string;
  speed: number;
  pitch: number;
  volume: number;
};
type StoryboardReferenceDisplayFields = Pick<StoryboardShot, 'characters' | 'scene'> & {
  props: string;
};
type StoryboardReferenceSaveState = {
  tail: Promise<void>;
  latestVersion: number;
  pendingCount: number;
  confirmedReferences: StoryboardAssetReference[];
  confirmedFields: StoryboardReferenceDisplayFields;
};
type ReferenceGenerationTarget = {
  assetType: StoryboardAssetReference['assetType'];
  assetId: number;
  variant: VisualVariant;
  primaryImageUrl?: string | null;
  primaryImageThumbnailUrl?: string | null;
  generationKey: string;
};
type ClientKeyedStoryboardAssetReference = StoryboardAssetReference & {
  clientKey?: string;
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

const splitNames = (value?: string | null) =>
  String(value || '')
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean);

const getStoryboardPropNames = (storyboard: StoryboardShot) =>
  splitNames((storyboard as StoryboardWithProps).props);

const getStoryboardProps = (storyboard: StoryboardShot) =>
  (storyboard as StoryboardWithProps).props || '';

const getReferenceDisplayFields = (
  references: StoryboardAssetReference[],
): StoryboardReferenceDisplayFields => ({
  characters: references
    .filter((reference) => reference.assetType === 'CHARACTER')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
  scene: references
    .filter((reference) => reference.assetType === 'SCENE')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
  props: references
    .filter((reference) => reference.assetType === 'PROP')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
});

const referencePersistenceIdentity = (reference: StoryboardAssetReference) => JSON.stringify([
  reference.assetType,
  reference.sortOrder,
]);

export const preserveReferenceClientKeys = (
  submitted: StoryboardAssetReference[],
  saved: StoryboardAssetReference[],
) => {
  const clientKeys = new Map<string, string[]>();
  for (const reference of submitted) {
    const clientKey = (reference as ClientKeyedStoryboardAssetReference).clientKey;
    if (!clientKey) continue;
    const identity = referencePersistenceIdentity(reference);
    clientKeys.set(identity, [...(clientKeys.get(identity) || []), clientKey]);
  }
  return saved.map((reference) => {
    const keys = clientKeys.get(referencePersistenceIdentity(reference));
    const clientKey = keys?.shift();
    return clientKey ? { ...reference, clientKey } : reference;
  });
};

const firstByName = <T extends { name: string }>(items: T[], names: string[]) =>
  items.find((item) => names.includes(item.name));

const suggestVisibleCharacter = (storyboard: StoryboardShot, assets: CharacterAsset[]) => {
  const descriptions = [
    storyboard.visualDescription,
    ...(storyboard.shotPlan?.shots || []).flatMap((shot) => [shot.positioning, shot.action]),
  ].filter(Boolean).join(' ');
  const matches = assets.filter((asset) => {
    const name = asset.name.trim();
    if (!name) return false;
    if (!/^[a-zA-Z\s'-]+$/.test(name)) return descriptions.includes(name);
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`(^|[^a-zA-Z])${escaped}(?=$|[^a-zA-Z])`, 'i').test(descriptions);
  });
  return matches.length === 1 ? matches[0] : undefined;
};

const getStoryboardScriptText = (storyboard: StoryboardShot) =>
  [
    storyboard.scene ? `▲${storyboard.scene}` : '',
    storyboard.visualDescription,
    storyboard.dialogue,
  ]
    .filter(Boolean)
    .join('\n');

const parseStoryboardScriptText = (
  storyboard: StoryboardShot,
  scriptText: string,
) => {
  const lines = scriptText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  let scene = storyboard.scene;
  if (lines[0]?.startsWith('▲')) {
    scene =
      lines
        .shift()
        ?.replace(/^▲+\s*/, '')
        .trim() || storyboard.scene;
  }

  let dialogue = storyboard.dialogue;
  const dialogueIndex = lines.findLastIndex((line) =>
    /(?:VO|旁白)?[:：]|[“"].+[”"]/.test(line),
  );
  if (dialogueIndex >= 0) {
    dialogue = lines.splice(dialogueIndex, 1)[0];
  }

  return {
    scene,
    dialogue,
    visualDescription: lines.join('\n') || storyboard.visualDescription,
  };
};

const getStoryboardPrompt = (storyboard: StoryboardShot) => {
  if (storyboard.videoPrompt?.trim()) {
    return storyboard.videoPrompt;
  }
  return [
    '画风：写实都市',
    '视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。',
    '### 素材引用',
    storyboard.characters ? `【人物】${storyboard.characters}` : '',
    storyboard.scene ? `【场景】${storyboard.scene}` : '',
    getStoryboardPropNames(storyboard).length
      ? `【道具】${getStoryboardPropNames(storyboard).join('、')}`
      : '',
    '### 画面描写',
    `镜头${storyboard.shotNo} ${storyboard.durationSeconds || 5}s`,
    storyboard.visualDescription,
    storyboard.dialogue,
  ]
    .filter(Boolean)
    .join('\n');
};

const getStoryboardSavePayload = (
  storyboard: StoryboardShot,
  overrides: Partial<SaveStoryboardValues> = {},
): SaveStoryboardValues => ({
  visualDescription: storyboard.visualDescription,
  characters: storyboard.characters,
  dialogue: storyboard.dialogue,
  scene: storyboard.scene,
  props: getStoryboardProps(storyboard),
  durationSeconds: storyboard.durationSeconds,
  imagePrompt: storyboard.imagePrompt,
  videoPrompt: storyboard.videoPrompt,
  promptDocument: storyboard.promptDocument || undefined,
  ...overrides,
});

const plainTextFromDocument = (document?: StoryboardPromptDocument | null) =>
  document?.nodes
    .map((node) => (node.type === 'text' ? node.text : node.displayName))
    .join('') || '';

const createPromptMention = (node: PromptReferenceOption) => {
  const mention = window.document.createElement('span');
  mention.contentEditable = 'false';
  mention.dataset.mediaType = node.mediaType;
  mention.dataset.sourceType = node.sourceType;
  mention.dataset.sourceId = String(node.sourceId);
  if (node.assetType) mention.dataset.assetType = node.assetType;
  if (node.assetId) mention.dataset.assetId = String(node.assetId);
  if (node.variantId) mention.dataset.variantId = String(node.variantId);
  mention.dataset.displayName = node.displayName;
  mention.className = 'storyboard-prompt-reference';
  mention.title = node.displayName;
  const name = window.document.createElement('span');
  name.className = 'storyboard-prompt-reference-name';
  name.textContent = node.displayName;
  mention.append(name);
  return mention;
};

const MaterialPromptEditor = ({
  label,
  value,
  document,
  referenceThumbnails,
  renderReferencePicker,
  onChange,
  onCommit,
  onBlur,
}: {
  label: string;
  value: string;
  document?: StoryboardPromptDocument | null;
  referenceThumbnails: ReadonlyMap<string, string>;
  renderReferencePicker: (onSelect: (reference: PromptReferenceOption) => void) => ReactNode;
  onChange: (plainText: string, nextDocument: StoryboardPromptDocument) => void;
  onCommit: (plainText: string, nextDocument: StoryboardPromptDocument) => void;
  onBlur: () => void;
}) => {
  const editorRef = useRef<HTMLDivElement>(null);
  const initializedKey = useRef('');
  const composing = useRef(false);
  const [mentionTrigger, setMentionTrigger] = useState<{ range: Range; left: number; top: number }>();
  const renderedNodes = useMemo<StoryboardPromptNode[]>(
    () =>
      document?.nodes?.length
        ? document.nodes
        : [{ type: 'text', text: value }],
    [document?.nodes, value],
  );
  const renderedKey = JSON.stringify(renderedNodes);

  useLayoutEffect(() => {
    const editor = editorRef.current;
    if (!editor || initializedKey.current === renderedKey) return;
    const fragment = window.document.createDocumentFragment();
    const fullText = renderedNodes.map((node) => node.type === 'text' ? node.text : node.displayName).join('');
    const referenceStart = fullText.indexOf('### 素材引用');
    const referenceEnd = fullText.indexOf('### 画面描写');
    let offset = 0;
    renderedNodes.forEach((node) => {
      if (node.type === 'text') {
        let cursor = 0;
        for (const match of node.text.matchAll(/<([^<>\n]+)>/g)) {
          const position = offset + match.index;
          if (referenceStart >= 0 && (position < referenceStart ||
            (referenceEnd >= 0 && position >= referenceEnd))) continue;
          if (match.index > cursor) fragment.append(window.document.createTextNode(node.text.slice(cursor, match.index)));
          const unbound = window.document.createElement('span');
          unbound.contentEditable = 'false';
          unbound.dataset.unboundReference = match[1].trim();
          unbound.title = '尚未绑定可用的参考图片';
          unbound.textContent = match[0];
          unbound.style.cssText = 'display:inline-block;padding:0 4px;border-radius:4px;background:#fff3db;color:#9a5800;border:1px solid #f2ce89';
          fragment.append(unbound);
          cursor = match.index + match[0].length;
        }
        if (cursor < node.text.length) fragment.append(window.document.createTextNode(node.text.slice(cursor)));
        offset += node.text.length;
        return;
      }
      fragment.append(createPromptMention(node));
      offset += node.displayName.length;
    });
    editor.replaceChildren(fragment);
    initializedKey.current = renderedKey;
  }, [renderedKey, renderedNodes]);

  useLayoutEffect(() => {
    // Update images separately so arriving metadata does not reset the editing caret.
    editorRef.current?.querySelectorAll<HTMLElement>('[data-media-type="IMAGE"]').forEach((mention) => {
      const thumbnail = referenceThumbnails.get(`${mention.dataset.sourceType}:${mention.dataset.sourceId}`);
      let image = mention.querySelector('img');
      if (!thumbnail) {
        image?.remove();
        return;
      }
      if (image?.getAttribute('src') === thumbnail) return;
      if (!image) {
        image = window.document.createElement('img');
        image.className = 'storyboard-prompt-reference-image';
        image.alt = `${mention.dataset.displayName}参考图`;
        image.width = 22;
        image.height = 22;
        image.draggable = false;
        image.loading = 'lazy';
        image.decoding = 'async';
        image.onerror = () => image?.remove();
        mention.prepend(image);
      }
      image.src = thumbnail;
    });
  }, [renderedKey, referenceThumbnails]);

  const readDocument = () => {
    const nodes: StoryboardPromptNode[] = [];
    const appendText = (text: string) => {
      if (!text) return;
      const previous = nodes.at(-1);
      if (previous?.type === 'text') previous.text += text;
      else nodes.push({ type: 'text', text });
    };
    const walk = (node: Node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        appendText(node.textContent || '');
        return;
      }
      if (!(node instanceof HTMLElement)) return;
      if (node.dataset.mediaType && node.dataset.sourceType && node.dataset.sourceId) {
        nodes.push({
          type: 'mention',
          mediaType: node.dataset.mediaType as 'IMAGE' | 'VIDEO' | 'AUDIO',
          sourceType: node.dataset.sourceType,
          sourceId: Number(node.dataset.sourceId),
          assetType: node.dataset.assetType as
            | 'CHARACTER'
            | 'SCENE'
            | 'PROP'
            | undefined,
          assetId: node.dataset.assetId
            ? Number(node.dataset.assetId)
            : undefined,
          variantId: node.dataset.variantId
            ? Number(node.dataset.variantId)
            : undefined,
          displayName: node.dataset.displayName || node.textContent || '',
        });
        return;
      }
      Array.from(node.childNodes).forEach(walk);
      if (node.tagName === 'DIV') appendText('\n');
      if (node.tagName === 'BR') appendText('\n');
    };
    Array.from(editorRef.current?.childNodes || []).forEach(walk);
    const nextDocument: StoryboardPromptDocument = { version: 2, nodes };
    initializedKey.current = JSON.stringify(nextDocument.nodes);
    onChange(plainTextFromDocument(nextDocument), nextDocument);
    return nextDocument;
  };

  const detectMentionTrigger = () => {
    const selection = window.getSelection();
    const editor = editorRef.current;
    if (!editor || !selection?.isCollapsed || !selection.rangeCount) {
      setMentionTrigger(undefined);
      return;
    }
    const caret = selection.getRangeAt(0);
    const text = caret.startContainer;
    const offset = caret.startOffset;
    if (text.nodeType !== Node.TEXT_NODE || !editor.contains(text)
      || text.parentElement?.closest('[contenteditable="false"]')
      || !['@', '＠'].includes(text.textContent?.[offset - 1] || '')) {
      setMentionTrigger(undefined);
      return;
    }
    const range = caret.cloneRange();
    range.setStart(text, offset - 1);
    const rect = range.getBoundingClientRect();
    setMentionTrigger({ range, left: rect.left, top: rect.bottom });
  };
  const insertMentionAtTrigger = (reference: PromptReferenceOption) => {
    const editor = editorRef.current;
    const range = mentionTrigger?.range;
    if (!editor || !range || !editor.contains(range.commonAncestorContainer)
      || !['@', '＠'].includes(range.toString())) {
      setMentionTrigger(undefined);
      return;
    }
    range.deleteContents();
    const mention = createPromptMention(reference);
    range.insertNode(mention);
    range.setStartAfter(mention);
    range.collapse(true);
    setMentionTrigger(undefined);
    editor.focus();
    window.getSelection()?.removeAllRanges();
    window.getSelection()?.addRange(range);
    const nextDocument = readDocument();
    onCommit(plainTextFromDocument(nextDocument), nextDocument);
  };
  const dismissMentionPicker = () => {
    setMentionTrigger(undefined);
    onBlur();
  };

  return (
    <>
      {/* biome-ignore lint/a11y/useSemanticElements: Rich-text material mentions require a contenteditable container. */}
      <div
        ref={editorRef}
        role="textbox"
        tabIndex={0}
        aria-label={label}
        aria-multiline="true"
        data-storyboard-scroll-region
        contentEditable
        suppressContentEditableWarning
        onInput={(event) => {
          readDocument();
          if (!composing.current && !(event.nativeEvent as InputEvent).isComposing) detectMentionTrigger();
        }}
        onCompositionStart={() => { composing.current = true; }}
        onCompositionEnd={() => {
          composing.current = false;
          readDocument();
          detectMentionTrigger();
        }}
        onKeyDown={(event) => {
          if (mentionTrigger && ['Escape', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
            setMentionTrigger(undefined);
          }
        }}
        onClick={() => setMentionTrigger(undefined)}
        onScroll={() => setMentionTrigger(undefined)}
        onBlur={() => { if (!mentionTrigger) onBlur(); }}
        className="storyboard-prompt-editor"
        style={{
          whiteSpace: 'pre-wrap',
          outline: 'none',
          fontSize: 14,
          fontWeight: 400,
          lineHeight: 1.7,
        }}
      />
      {mentionTrigger ? (
        <Popover
          open
          trigger="click"
          placement="bottomLeft"
          onOpenChange={(open) => { if (!open) dismissMentionPicker(); }}
          content={(
            <div
              role="dialog"
              aria-label={`${label}引用素材`}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  event.stopPropagation();
                  dismissMentionPicker();
                  editorRef.current?.focus();
                }
              }}
            >
              {renderReferencePicker(insertMentionAtTrigger)}
            </div>
          )}
        >
          <span aria-hidden="true" style={{ position: 'fixed', left: mentionTrigger.left, top: mentionTrigger.top, width: 1, height: 1, pointerEvents: 'none' }} />
        </Popover>
      ) : null}
    </>
  );
};

const normalizeVideoDuration = (
  durationSeconds: number | undefined,
  constraints?: ProjectModelOption['constraints'],
) => {
  const min = constraints?.duration?.min ?? 4;
  const max = constraints?.duration?.max ?? 15;
  if (durationSeconds === -1) return -1;
  return Math.min(max, Math.max(min, Math.round(durationSeconds || min)));
};

const getStoryboardVideo = (
  storyboard: StoryboardShot,
  videoTasks: AiVideoTask[],
) => {
  const boundTask = videoTasks.find((task) =>
    task.storyboardId === storyboard.id && task.results?.some((result) =>
      result.id === storyboard.currentVideoResultId,
    ),
  );
  const selectedTask = videoTasks.find(
    (task) =>
      task.storyboardId === storyboard.id &&
      task.results?.some((result) => result.isSelected),
  );
  const task =
    boundTask || selectedTask ||
    videoTasks.find(
      (item) => item.storyboardId === storyboard.id && item.results?.length,
    );
  const result =
    task?.results?.find((item) => item.id === storyboard.currentVideoResultId)
    || task?.results?.find((item) => item.isSelected) || task?.results?.[0];
  return {
    resultId: result?.id || (storyboard.currentVideoUrl ? storyboard.currentVideoResultId || undefined : undefined),
    selected: Boolean(result?.isSelected || result?.id === storyboard.currentVideoResultId),
    coverUrl: result?.coverUrl || storyboard.firstFrameThumbnailUrl || undefined,
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

const avatarRail = (names: string[], assets: CharacterAsset[], imageTasks: AiImageTask[]) => (
  <div style={{ display: 'flex', width: 58, overflow: 'hidden' }}>
    {names.slice(0, 4).map((name, index) => {
      const asset = assets.find((item) => item.name === name);
      const image = asset?.visual?.primaryVariant?.currentImageThumbnailUrl
        || asset?.mainImageThumbnailUrl || thumbnailFor(imageTasks, 'CHARACTER', asset?.id);
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
          {image ? <LazyMediaImage src={image} alt={`${name}参考图`} width="100%" height="100%" preview={false} style={{ objectFit: 'cover' }} /> : name.slice(0, 1)}
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

const StoryboardCard = ({
  onImageUploaded,
  canCaptureVideo,
  onCaptureVideo,
  item,
  projectId,
  videoEstimateOperations,
  index,
  characters,
  scenes,
  props,
  episodes,
  imageTasks,
  videoTasks,
  referenceOptions,
  referenceThumbnails,
  draft,
  videoHistory,
  model,
  modelConstraints,
  generationSettings,
  generationSettingsSaveStatus,
  onRetryGenerationSettings,
  onDraftChange,
  onGenerationSettingsChange,
  onGenerate,
  onBindVideoResult,
  bindingVideoId,
  onCancelVideo,
  onRetryVideo,
  onRecoveredVideo,
  onGenerateImage,
  onSelectFirstFrame,
  selectingFirstFrameId,
  onRegenerateImage,
  onCancelImage,
  onGenerateVoice,
  onReplaceReferences,
  onGenerateReferenceImage,
  onLoadVariants,
  onLoadImageCandidates,
  onLoadVideoCandidates,
  generatingReferenceKeys,
  onSaveScript,
  onUpdateStoryboard,
  onAddStoryboard,
  onCopyStoryboard,
  onDelete,
}: {
  onImageUploaded: (reference: PromptReferenceOption) => void;
  canCaptureVideo: boolean;
  onCaptureVideo: (resultId: number, storyboardNo: number) => void;
  item: StoryboardShot;
  projectId: number;
  videoEstimateOperations?: PointEstimateOperation[];
  index: number;
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  episodes: ScriptEpisode[];
  imageTasks: AiImageTask[];
  videoTasks: AiVideoTask[];
  referenceOptions: Record<'IMAGE' | 'VIDEO' | 'AUDIO', PromptReferenceOption[]>;
  referenceThumbnails: ReadonlyMap<string, string>;
  draft: StoryboardDraft[number];
  videoHistory?: MediaPage<AiVideoResult>;
  model: string;
  modelConstraints?: ProjectModelOption['constraints'];
  generationSettings: VideoGenerationSettings;
  generationSettingsSaveStatus?: VideoSettingsSaveStatus;
  onRetryGenerationSettings: () => void;
  onDraftChange: (
    storyboardId: number,
    values: Partial<StoryboardDraft[number]>,
  ) => void;
  onGenerationSettingsChange: (values: Partial<VideoGenerationSettings>) => void;
  onGenerate: (storyboard: StoryboardShot) => void;
  onBindVideoResult: (resultId: number) => void;
  bindingVideoId?: number;
  onCancelVideo: (task: AiVideoTask) => void;
  onRetryVideo: (task: AiVideoTask) => void;
  onRecoveredVideo: (taskId: number) => Promise<void>;
  onGenerateImage: (storyboard: StoryboardShot) => void;
  onSelectFirstFrame: (resultId: number) => void;
  selectingFirstFrameId?: number;
  onRegenerateImage: (task: AiImageTask) => void;
  onCancelImage: (task: AiImageTask) => void;
  onGenerateVoice: (
    storyboard: StoryboardShot,
    values: VoiceGenerationValues,
  ) => Promise<void>;
  onReplaceReferences: (
    storyboard: StoryboardShot,
    references: StoryboardAssetReference[],
  ) => Promise<void>;
  onGenerateReferenceImage: (
    reference: StoryboardAssetReference,
    generationKey: string,
  ) => Promise<void>;
  generatingReferenceKeys: ReadonlySet<string>;
  onLoadVariants: (
    assetType: StoryboardAssetReference['assetType'], assetId: number,
    selectedVariantId?: number | null, current?: number, pageSize?: number,
  ) => Promise<void>;
  onLoadImageCandidates: (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => Promise<MediaPage<AiImageResult>>;
  onLoadVideoCandidates: (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => Promise<MediaPage<AiVideoResult>>;
  onSaveScript: (storyboard: StoryboardShot) => void;
  onUpdateStoryboard: (
    storyboard: StoryboardShot,
    values: Partial<SaveStoryboardValues>,
  ) => void;
  onAddStoryboard: (storyboard: StoryboardShot) => void;
  onCopyStoryboard: (storyboard: StoryboardShot) => void;
  onDelete: (storyboard: StoryboardShot) => void;
}) => {
  const { message } = App.useApp();
  const warnings = item.shotPlan?.warnings ?? [];
  const characterNames = splitNames(item.characters);
  const suggestedCharacter = characterNames.length ? undefined : suggestVisibleCharacter(item, characters);
  const shownCharacterNames = characterNames.length ? characterNames : suggestedCharacter ? [suggestedCharacter.name] : [];
  const dialogueSpeaker = String(item.dialogue || '').split(/[:：]/, 1)[0]
    .replace(/VO$/i, '').trim();
  const [voiceModalOpen, setVoiceModalOpen] = useState(false);
  const [voiceSubmitting, setVoiceSubmitting] = useState(false);
  const [previewKey, setPreviewKey] = useState<'VIDEO' | 'FIRST_FRAME' | 'SCENE'>('VIDEO');
  const [previewVersion, setPreviewVersion] = useState<AiVideoResult>();
  useEffect(() => {
    setPreviewVersion(undefined);
    setPreviewKey('VIDEO');
  }, [item.currentVideoResultId]);
  const [candidateType, setCandidateType] = useState<'IMAGE' | 'VIDEO'>();
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [imageCandidates, setImageCandidates] = useState<MediaPage<AiImageResult>>();
  const [videoCandidates, setVideoCandidates] = useState<MediaPage<AiVideoResult>>();
  const candidateRequest = useRef<{ id: number; controller?: AbortController }>({ id: 0 });
  useEffect(() => () => candidateRequest.current.controller?.abort(), []);
  const [voiceValues, setVoiceValues] = useState<VoiceGenerationValues>({
    voiceType: 'DIALOGUE',
    speakerName: dialogueSpeaker || shownCharacterNames[0] || '',
    voiceId: 'default-cn-voice',
    textContent: item.dialogue || item.visualDescription || '',
    speed: 1,
    pitch: 1,
    volume: 1,
  });
  const sceneNames = splitNames(item.scene);
  const propNames = getStoryboardPropNames(item);
  const scene = firstByName(scenes, sceneNames);
  const prop = firstByName(props, propNames);
  const boundVideo = getStoryboardVideo(item, videoTasks);
  const version = previewVersion || videoHistory?.data.find((result) => result.id === item.currentVideoResultId);
  const video = version ? {
    resultId: version.id,
    selected: item.currentVideoResultId != null ? version.id === item.currentVideoResultId : Boolean(version.isSelected),
    coverUrl: version.coverUrl || undefined,
    videoUrl: version.videoUrl,
  } : boundVideo;
  const videoVersions = videoTasks.filter((task) => task.storyboardId === item.id).flatMap((task) => task.results || []);
  const loadCandidates = async (
    type: 'IMAGE' | 'VIDEO', current = 1, pageSize = 20,
  ) => {
    candidateRequest.current.controller?.abort();
    const controller = new AbortController();
    const requestId = candidateRequest.current.id + 1;
    candidateRequest.current = { id: requestId, controller };
    setCandidateLoading(true);
    try {
      if (type === 'IMAGE') {
        const page = await onLoadImageCandidates(item.id, current, pageSize, controller.signal);
        if (!controller.signal.aborted && candidateRequest.current.id === requestId) setImageCandidates(page);
      } else {
        const page = await onLoadVideoCandidates(item.id, current, pageSize, controller.signal);
        if (!controller.signal.aborted && candidateRequest.current.id === requestId) setVideoCandidates(page);
      }
    } catch {
      if (!controller.signal.aborted && candidateRequest.current.id === requestId) {
        message.error('历史媒体加载失败');
      }
    } finally {
      if (!controller.signal.aborted && candidateRequest.current.id === requestId) setCandidateLoading(false);
    }
  };
  const openCandidates = (type: 'IMAGE' | 'VIDEO') => {
    setCandidateType(type);
    void loadCandidates(type);
  };
  const closeCandidates = () => {
    candidateRequest.current.controller?.abort();
    candidateRequest.current = { id: candidateRequest.current.id + 1 };
    setCandidateLoading(false);
    setCandidateType(undefined);
  };
  const candidatePage = candidateType === 'IMAGE' ? imageCandidates : videoCandidates;
  const insertReference = (reference: PromptReferenceOption) => {
    const currentNodes = draft.promptDocument?.nodes?.length
      ? draft.promptDocument.nodes
      : [{ type: 'text' as const, text: draft.videoPrompt }];
    const separator = currentNodes.length ? [{ type: 'text' as const, text: ' ' }] : [];
    const promptDocument: StoryboardPromptDocument = {
      version: 2,
      nodes: [...currentNodes, ...separator, reference],
    };
    const videoPrompt = plainTextFromDocument(promptDocument);
    onDraftChange(item.id, { videoPrompt, promptDocument });
    onUpdateStoryboard(item, { videoPrompt, promptDocument });
  };
  const copyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(draft.videoPrompt);
      message.success('提示词已复制');
    } catch {
      message.error('提示词复制失败');
    }
  };
  const renderReferencePicker = (onSelect: (reference: PromptReferenceOption) => void) => (
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
            min={modelConstraints?.duration?.intelligent ? -1 : modelConstraints?.duration?.min}
            max={modelConstraints?.duration?.max}
            value={generationSettings.durationSeconds}
            onChange={(value) =>
              onGenerationSettingsChange({
                durationSeconds: typeof value === 'number' ? value : generationSettings.durationSeconds,
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
          onChange={(generateAudio) => onGenerationSettingsChange({ generateAudio })}
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
      url: visual?.primaryVariant?.currentImageThumbnailUrl || asset?.mainImageThumbnailUrl,
      source: visual?.resolvedImageSource || (asset?.mainImageThumbnailUrl ? 'LEGACY_FALLBACK' : undefined),
    };
  };
  const resolvedSceneVisual = resolveEpisodeVisual(scene);
  const resolvedPropVisual = resolveEpisodeVisual(prop);
  const sceneImage =
    resolvedSceneVisual.url ||
    thumbnailFor(imageTasks, 'SCENE', scene?.id);
  const propImage =
    resolvedPropVisual.url ||
    thumbnailFor(imageTasks, 'PROP', prop?.id);
  const previewSources = [
    { key: 'FIRST_FRAME' as const, label: '首帧', url: item.firstFrameThumbnailUrl || thumbnailFor(imageTasks, 'STORYBOARD', item.id) },
    { key: 'VIDEO' as const, label: '视频', url: video.coverUrl },
    { key: 'SCENE' as const, label: '场景', url: sceneImage },
  ].filter((source) => source.url);
  const selectedPreview = previewSources.find((source) => source.key === previewKey);
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
    .filter((task) => task.targetType === 'STORYBOARD' && task.targetId === item.id)
    .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
    .flatMap((task) => task.results || [])
    .find((result) => !result.selected && result.status === 'ACTIVE' && result.thumbnailUrl);
  const videoTask = videoTasks
    .filter((task) => task.storyboardId === item.id)
    .sort((a, b) => b.id - a.id)[0];
  const videoGenerating = isVideoGenerationActive(videoTask);
  const [avatarRecoveryBusy, setAvatarRecoveryBusy] = useState(false);
  const [dismissedVideoErrorTaskId, setDismissedVideoErrorTaskId] = useState<number>();
  const videoError = videoGenerationError(videoTask);
  const visibleVideoError = videoTask?.id === dismissedVideoErrorTaskId ? undefined : videoError;
  useEffect(() => {
    if (videoGenerating) {
      setPreviewKey('VIDEO');
      setPreviewVersion(undefined);
    }
  }, [videoGenerating, videoTask?.id]);
  return (
    <article
      className="storyboard-card"
      style={{
        border: '1px solid #e4e9f2',
        borderRadius: 12,
        background: '#fff',
        overflow: 'hidden',
        boxShadow: '0 2px 10px rgba(27, 39, 74, 0.04)',
      }}
    >
      <div
        className="storyboard-card-header"
        style={{
          minHeight: 52,
          borderBottom: '1px solid #edf0f6',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Flex align="center" gap={10}>
          <HolderOutlined style={{ color: '#8a93a8', fontSize: 15 }} />
          <Typography.Text strong>分镜{index + 1}</Typography.Text>
          <span
            style={{
              height: 16,
              borderRadius: 4,
              background: '#eef1ff',
              color: '#5454ff',
              fontSize: 10,
              padding: '0 4px',
              lineHeight: '16px',
            }}
          >
            ID
          </span>
          <Tag color="blue">全能参考生视频</Tag>
        </Flex>
        <Flex gap={4} style={{ color: '#7f88a6', fontSize: 16 }}>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            aria-label="新增分镜"
            onClick={() => onAddStoryboard(item)}
          />
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            aria-label="复制分镜"
            onClick={() => onCopyStoryboard(item)}
          />
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label={`删除分镜${index + 1}`}
            onClick={() => onDelete(item)}
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

      <div
        className="storyboard-card-content"
        style={{
          display: 'grid',
          gap: 20,
          padding: '18px 20px 22px',
        }}
      >
        <section data-storyboard-scroll-region>
          <Typography.Text strong style={{ fontSize: 15 }}>
            分镜信息
          </Typography.Text>
          <Flex align="center" gap={8} style={{ marginTop: 14 }}>
            <PointEstimateButton operations={(item.imagePrompt || item.visualDescription)?.trim() ? [{ url: `/api/projects/${projectId}/ai-image-tasks`, data: { taskType: 'STORYBOARD_FIRST_FRAME', targetType: 'STORYBOARD', targetId: item.id, prompt: item.imagePrompt || item.visualDescription, aspectRatio: '9:16', imageCount: 1, quality: 'STANDARD' } }] : undefined}
              size="small"
              onClick={() => onGenerateImage(item)}
              disabled={['PENDING', 'RUNNING'].includes(
                imageTask?.execution?.status || '',
              )}
            >
              生成首帧
            </PointEstimateButton>
            <Button size="small" onClick={() => openCandidates('IMAGE')}>
              选择历史首帧
            </Button>
            {imageTask?.execution ? (
              <AiExecutionStatus
                task={imageTask.execution}
                onCancel={() => onCancelImage(imageTask)}
                onRetry={() => onRegenerateImage(imageTask)}
                retryOperations={[{ url: `/api/projects/${projectId}/ai-image-tasks/${imageTask.id}/regenerate` }]}
              />
            ) : null}
          </Flex>
          {unselectedFirstFrame ? (
            <Flex align="center" gap={8} style={{ marginTop: 10 }}>
              <div style={{ width: 42, height: 42, flex: 'none', overflow: 'hidden' }}>
                <PreviewPoster src={unselectedFirstFrame.thumbnailUrl ?? undefined} title="待选首帧" />
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
          <div style={{ marginTop: 22, color: '#1f2937', fontSize: 14 }}>
            剧本原文
          </div>
          <Input.TextArea
            className="storyboard-script-editor"
            aria-label={`分镜${index + 1}剧本原文`}
            value={draft.scriptText}
            onChange={(event) =>
              onDraftChange(item.id, { scriptText: event.target.value })
            }
            onBlur={() => onSaveScript(item)}
            autoSize={{ minRows: 4, maxRows: 5 }}
            style={{
              marginTop: 9,
              borderColor: '#d8deee',
              borderRadius: 8,
              fontSize: 13,
              fontWeight: 400,
              lineHeight: 1.7,
            }}
          />

          {item.assetReferences !== undefined ? (
            <StoryboardAssetReferenceEditor
              projectId={projectId}
              storyboardId={item.id}
              storyboardNo={index + 1}
              references={item.assetReferences}
              characters={characters}
              scenes={scenes}
              props={props}
              onChange={(references) => void onReplaceReferences(item, references)}
              onLoadVariants={onLoadVariants}
              onVoice={(speakerName) => {
                setVoiceValues((previous) => ({ ...previous, speakerName }));
                setVoiceModalOpen(true);
              }}
              onGenerateImage={(reference, generationKey) =>
                void onGenerateReferenceImage(reference, generationKey)}
              generatingReferenceKeys={generatingReferenceKeys}
            />
          ) : (<>
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
                  onUpdateStoryboard(item, { characters: value || '' })
                }
              />
              {shownCharacterNames.length ? (
                <Button
                  size="small"
                  aria-label={`清空分镜${index + 1}出镜角色`}
                  onClick={() => onUpdateStoryboard(item, { characters: '' })}
                >
                  清空
                </Button>
              ) : null}
              <Button
                icon={<PlayCircleOutlined />}
                aria-label={`分镜${index + 1}自定义音色`}
                onClick={() => setVoiceModalOpen(true)}
              >
                自定义音色
              </Button>
            </Flex>
            {suggestedCharacter ? (
              <Flex align="center" gap={6} style={{ marginTop: 6 }}>
                <Tag color="warning">按画面推断，待确认</Tag>
                <Button size="small" type="link" onClick={() => onUpdateStoryboard(item, { characters: suggestedCharacter.name })}>
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
                <PreviewPoster src={sceneImage} title={`${item.scene || '场景'}参考图`} />
              ) : (
                <div style={{ height: '100%', display: 'grid', placeItems: 'center', color: '#8793a7', fontSize: 12 }}>
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
                onChange={(value) =>
                  onUpdateStoryboard(item, { scene: value || '' })
                }
              />
              {sceneNames.length ? (
                <Button
                  size="small"
                  aria-label={`清空分镜${index + 1}场景`}
                  onClick={() => onUpdateStoryboard(item, { scene: '' })}
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
                onChange={(value) =>
                  onUpdateStoryboard(item, { props: value || '' })
                }
              />
              {propNames.length ? (
                <Button
                  size="small"
                  aria-label={`清空分镜${index + 1}场景道具`}
                  onClick={() => onUpdateStoryboard(item, { props: '' })}
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
          </>)}
        </section>

        <section className="storyboard-prompt-section">
          <Flex align="center" gap={12}>
            <Typography.Text strong style={{ fontSize: 15 }}>
              分镜视频生成
            </Typography.Text>
            <span
              style={{
                height: 32,
                borderRadius: 8,
                background: '#f0f2fb',
                color: '#20283a',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: '0 14px',
                fontWeight: 600,
              }}
            >
              <ThunderboltOutlined />
              <span>3D导演台</span>
              <span style={{ color: '#6956ff', fontSize: 12 }}>Beta</span>
            </span>
          </Flex>
          <Flex gap={6} wrap style={{ marginTop: 8 }}>
            {modelConstraints?.image?.maxCount !== undefined ? (
              <Tag>图片上限 {modelConstraints.image.maxCount}</Tag>
            ) : null}
            {modelConstraints?.video?.maxCount !== undefined ? (
              <Tag>视频上限 {modelConstraints.video.maxCount}</Tag>
            ) : null}
            {modelConstraints?.audio?.maxCount !== undefined ? (
              <Tag>音频上限 {modelConstraints.audio.maxCount}</Tag>
            ) : null}
            {(videoTask?.referenceDiagnostics?.omitted || []).map((omitted) => (
              <Tag color="warning" key={`${omitted.sourceType}-${omitted.sourceId}-${omitted.variantId}-${omitted.reason}`}>
                已省略 {' '}
                {omitted.displayName || `${omitted.assetType || '素材'} ${omitted.assetId || ''}`}
                {omitted.reason ? `：${omitted.reason === 'IMAGE_UNAVAILABLE' ? '参考图片不可用' : omitted.reason}` : ''}
              </Tag>
            ))}
          </Flex>
          <div
            className="storyboard-prompt-panel"
            style={{
              border: '1px solid #dfe5f2',
              borderRadius: 14,
              padding: '16px 18px 12px',
            }}
          >
            <Flex
              align="center"
              gap={10}
              style={{ color: '#b5bdcc', marginBottom: 12 }}
            >
              <StoryboardImageUpload
                key={`${projectId}:${item.id}`}
                projectId={projectId}
                storyboardId={item.id}
                storyboardNo={index + 1}
                disabled={!canCaptureVideo}
                onUploaded={(image) => {
                  const reference: PromptReferenceOption = {
                    type: 'mention', mediaType: 'IMAGE', sourceType: 'IMAGE_MATERIAL',
                    sourceId: image.materialId, displayName: image.name,
                  };
                  onImageUploaded(reference);
                  insertReference(reference);
                }}
              />
              <Popover content={referencePicker} trigger="click" placement="bottomLeft">
                <Button aria-label="添加提示词引用" title="引用资产" icon={<span aria-hidden="true">@</span>} />
              </Popover>
              <Button
                aria-label={`复制分镜${index + 1}提示词`}
                title="复制提示词"
                icon={<CopyOutlined />}
                onClick={() => void copyPrompt()}
              />
              <span>
                使用 @
                引用角色、场景、道具、音色及参考素材，编辑更灵活，分镜更精准
              </span>
            </Flex>
            <MaterialPromptEditor
              label={`分镜${index + 1}视频提示词`}
              value={draft.videoPrompt}
              document={draft.promptDocument}
              referenceThumbnails={referenceThumbnails}
              renderReferencePicker={renderReferencePicker}
              onCommit={(videoPrompt, promptDocument) => onUpdateStoryboard(item, { videoPrompt, promptDocument })}
              onChange={(videoPrompt, promptDocument) =>
                onDraftChange(item.id, { videoPrompt, promptDocument })
              }
              onBlur={() =>
                onUpdateStoryboard(item, {
                  videoPrompt: draft.videoPrompt,
                  promptDocument: draft.promptDocument || undefined,
                })
              }
            />
            <Flex
              justify="space-between"
              align="center"
              wrap
              gap={8}
              style={{ marginTop: 9 }}
            >
              <Flex gap={6} wrap>
                <Tag icon={<ThunderboltOutlined />}>
                  {model}
                </Tag>
                <Popover content={parameterEditor} trigger="click" placement="bottomLeft">
                  <Button
                    size="small"
                    aria-label={`分镜${index + 1}视频参数`}
                  >
                    ◷ {' '}
                    {generationSettings.durationSeconds === -1
                      ? '智能'
                      : `${generationSettings.durationSeconds}s`}{' '} | 1个 | {generationSettings.resolution} | mp4
                  </Button>
                </Popover>
                <Popover content={referencePicker} trigger="click" placement="bottomLeft">
                  <Button size="small" aria-label="选择提示词素材">@</Button>
                </Popover>
              </Flex>
              <PointEstimateButton operations={videoEstimateOperations}
                type="primary"
                onClick={() => onGenerate(item)}
                aria-label={`生成分镜${index + 1}视频`}
                disabled={videoGenerating}
                style={{
                  minWidth: 92,
                  background: '#5b50ff',
                  borderColor: '#5b50ff',
                  fontWeight: 700,
                }}
              >
                <ThunderboltOutlined /> 生成视频
              </PointEstimateButton>
            </Flex>
            {generationSettingsSaveStatus ? <Flex aria-label={`分镜${index + 1}参数保存状态`} style={{ marginTop: 4 }}>
              {generationSettingsSaveStatus === 'error' ? <Button type="link" size="small" danger
                aria-label={`重试保存分镜${index + 1}视频参数`} onClick={onRetryGenerationSettings}>
                视频参数保存失败，重试
              </Button> : <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {generationSettingsSaveStatus === 'saving' ? '正在保存视频参数' : '视频参数已保存'}
              </Typography.Text>}
            </Flex> : null}
          </div>
        </section>

        <section
          className="storyboard-preview-section"
          aria-label={`分镜${index + 1}视频预览区`}
          style={{
            position: 'relative',
            background: '#f7f9fd',
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          <div
            className="storyboard-preview-stage"
            style={{
              background: videoGenerating || visibleVideoError || (previewKey === 'VIDEO' && !video.videoUrl) ? '#f7f8fc' : '#2c333f',
              display: 'flex',
              justifyContent: 'center',
            }}
          >
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
                  onRetry={!avatarRecoveryBusy && videoTask.execution?.retryable ? () => onRetryVideo(videoTask) : undefined}
                  retryOperations={[{ url: `/api/projects/${projectId}/ai-video-tasks/${videoTask.id}/regenerate` }]}
                >
                  {/may\s+contain\s+real\s*(?:person|people|persons)\b/i.test(videoTask.errorMessage || videoTask.execution?.errorMessage || '') ? (
                    <StoryboardAvatarRecovery projectId={projectId} taskId={videoTask.id}
                      onReady={onRecoveredVideo} onBusyChange={setAvatarRecoveryBusy} />
                  ) : null}
                  {!!videoTask.referenceDiagnostics?.failedImages?.length && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 8 }}>
                      {videoTask.referenceDiagnostics.failedImages.map((image) => (
                        <figure key={image.contentIndex} style={{ margin: 0, width: 120 }}>
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
                            {image.label}{image.displayName ? `：${image.displayName}` : ''}
                          </figcaption>
                        </figure>
                      ))}
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
                    objectFit: 'cover',
                    background: '#000',
                  }}
                />
              ) : previewKey === 'VIDEO' ? <StoryboardVideoEmptyState /> : (
                <PreviewPoster
                  src={selectedPreview?.url || video.coverUrl}
                  title={`分镜${index + 1}主预览`}
                />
              )}
            </div>
            {previewKey === 'VIDEO' && video.videoUrl && !videoGenerating && !visibleVideoError ? (
              <StoryboardVideoTools enabled={canCaptureVideo} resultId={video.resultId}
                onCapture={() => { if (video.resultId) onCaptureVideo(video.resultId, item.storyboardNo || item.shotNo); }} />
            ) : null}
          </div>
          <StoryboardVideoStrip
            storyboardNo={item.storyboardNo || item.shotNo}
            results={videoVersions}
            history={videoHistory}
            currentResultId={item.currentVideoResultId || (boundVideo.selected ? boundVideo.resultId : undefined)}
            previewResultId={previewKey === 'VIDEO' ? video.resultId : undefined}
            onPreview={(result) => { setPreviewVersion(result); setPreviewKey('VIDEO'); }}
            onLoad={(current, pageSize, signal) => onLoadVideoCandidates(item.id, current, pageSize, signal)}
          />
          <Flex
            justify="center"
            gap={10}
            style={{ height: 54, paddingTop: 12 }}
          >
            {previewSources.filter((source) => source.key !== 'VIDEO' || (!videoVersions.length && !videoHistory?.data.length)).map((source) => (
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
          {video.resultId ? (
            <Flex className="storyboard-preview-binding" justify="center">
              {video.selected ? <Tag color="success">当前分镜视频</Tag> : (
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
          <Flex justify="center" style={{ marginTop: 8 }}>
            <Button size="small" onClick={() => openCandidates('VIDEO')}>
              选择历史视频
            </Button>
          </Flex>
        </section>
      </div>
      <Modal
        title={candidateType === 'IMAGE' ? `分镜${index + 1}历史首帧` : `分镜${index + 1}历史视频`}
        open={Boolean(candidateType)}
        footer={null}
        width={760}
        destroyOnHidden
        onCancel={closeCandidates}
      >
        <Spin spinning={candidateLoading}>
          {candidatePage?.data.length ? (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12 }}>
              {candidateType === 'IMAGE'
                ? imageCandidates?.data.map((candidate) => (
                  <div key={candidate.id} style={{ minWidth: 0 }}>
                    <LazyMediaImage
                      native
                      active={candidateType === 'IMAGE'}
                      src={candidate.thumbnailUrl || undefined}
                      alt={`首帧候选${candidate.id}`}
                      height={112}
                      preview={false}
                    />
                    <Button
                      block
                      size="small"
                      loading={selectingFirstFrameId === candidate.id}
                      disabled={candidate.selected}
                      onClick={() => onSelectFirstFrame(candidate.id)}
                      style={{ marginTop: 6 }}
                    >
                      {candidate.selected ? '当前首帧' : '设为首帧'}
                    </Button>
                  </div>
                ))
                : videoCandidates?.data.map((candidate) => (
                  <div key={candidate.id} style={{ minWidth: 0 }}>
                    <LazyMediaImage
                      native
                      active={candidateType === 'VIDEO'}
                      src={candidate.coverUrl || undefined}
                      alt={`视频候选${candidate.id}`}
                      height={112}
                      preview={false}
                    />
                    <Button
                      block
                      size="small"
                      loading={bindingVideoId === candidate.id}
                      disabled={candidate.isSelected}
                      onClick={() => onBindVideoResult(candidate.id)}
                      style={{ marginTop: 6 }}
                    >
                      {candidate.isSelected ? '当前视频' : '设为当前视频'}
                    </Button>
                  </div>
                ))}
            </div>
          ) : (
            <Empty description={candidateLoading ? '加载中' : '暂无历史结果'} />
          )}
          {(candidatePage?.total || 0) > (candidatePage?.pageSize || 20) ? (
            <Pagination
              current={candidatePage?.current || 1}
              pageSize={candidatePage?.pageSize || 20}
              total={candidatePage?.total || 0}
              showSizeChanger
              onChange={(page, size) => candidateType && void loadCandidates(candidateType, page, size)}
              style={{ marginTop: 16 }}
            />
          ) : null}
        </Spin>
      </Modal>
      <Modal
        title={`分镜${index + 1}自定义音色`}
        open={voiceModalOpen}
        okText="生成语音"
        confirmLoading={voiceSubmitting}
        onCancel={() => setVoiceModalOpen(false)}
        onOk={async () => {
          if (!voiceValues.voiceId.trim() || !voiceValues.textContent.trim()) {
            message.warning('请填写音色ID和合成文本');
            return;
          }
          setVoiceSubmitting(true);
          try {
            await onGenerateVoice(item, voiceValues);
            setVoiceModalOpen(false);
          } finally {
            setVoiceSubmitting(false);
          }
        }}
      >
        <Flex vertical gap={12}>
          <div>
            <Typography.Text>语音类型</Typography.Text>
            <Select
              aria-label={`分镜${index + 1}语音类型`}
              value={voiceValues.voiceType}
              options={[
                { label: '对白', value: 'DIALOGUE' },
                { label: '旁白', value: 'NARRATION' },
                { label: '内心独白', value: 'MONOLOGUE' },
              ]}
              onChange={(voiceType) => setVoiceValues((previous) => ({ ...previous, voiceType }))}
              style={{ width: '100%' }}
            />
          </div>
          <div>
            <Typography.Text>说话人</Typography.Text>
            <input
              aria-label={`分镜${index + 1}说话人`}
              value={voiceValues.speakerName}
              onChange={(event) => setVoiceValues((previous) => ({ ...previous, speakerName: event.target.value }))}
            />
          </div>
          <div>
            <Typography.Text>音色ID</Typography.Text>
            <input
              aria-label={`分镜${index + 1}音色ID`}
              value={voiceValues.voiceId}
              onChange={(event) => setVoiceValues((previous) => ({ ...previous, voiceId: event.target.value }))}
            />
          </div>
          <div>
            <Typography.Text>合成文本</Typography.Text>
            <Input.TextArea
              aria-label={`分镜${index + 1}合成文本`}
              value={voiceValues.textContent}
              onChange={(event) => setVoiceValues((previous) => ({ ...previous, textContent: event.target.value }))}
              autoSize={{ minRows: 4, maxRows: 8 }}
            />
          </div>
        </Flex>
      </Modal>
    </article>
  );
};

const ProductionWorkbenchStoryboard = () => {
  const location = useLocation();
  const navigationTarget = readStoryboardTarget(location.search);
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const [frameCapture, setFrameCapture] = useState<{ resultId: number; storyboardNo: number }>();
  useEffect(() => setFrameCapture(undefined), [projectId]);
  const { message, modal } = App.useApp();
  const [imageTasks, setImageTasks] = useState<AiImageTask[]>([]);
  const [videoTasks, setVideoTasks] = useState<AiVideoTask[]>([]);
  const [voiceTasks, setVoiceTasks] = useState<AiVoiceTask[]>([]);
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
  const [bindingVideoId, setBindingVideoId] = useState<number>();
  const [selectingFirstFrameId, setSelectingFirstFrameId] = useState<number>();
  const storyboardSaves = useRef<Record<number, Promise<unknown>>>({});
  const referenceSaveStates = useRef(new Map<number, StoryboardReferenceSaveState>());
  const [drafts, setDrafts] = useState<StoryboardDraft>({});
  const draftSources = useRef<StoryboardDraft>({});
  const loadDrafts = (shots: StoryboardShot[]) => {
    const sources = draftSources.current;
    const incoming = Object.fromEntries(shots.map((item) => [item.id, {
      scriptText: getStoryboardScriptText(item),
      videoPrompt: getStoryboardPrompt(item),
      promptDocument: item.promptDocument,
    }]));
    draftSources.current = { ...sources, ...incoming };
    setDrafts((previous) => ({
      ...previous,
      ...Object.fromEntries(shots.map((item) => {
        const draft = previous[item.id];
        const source = sources[item.id];
        const edited = draft && source && (
          draft.scriptText !== source.scriptText || draft.videoPrompt !== source.videoPrompt ||
          (draft.promptDocument !== source.promptDocument &&
            JSON.stringify(draft.promptDocument ?? null) !== JSON.stringify(source.promptDocument ?? null))
        );
        return [item.id, edited ? draft : incoming[item.id]];
      })),
    }));
  };
  const [storyboardExecution, setStoryboardExecution] =
    useState<API.AiExecutionResponse>();
  const [storyboardExecutionEpisodeId, setStoryboardExecutionEpisodeId] = useState<number>();
  const [storyboardBusy, setStoryboardBusy] = useState(false);
  const [storyboardBatch, setStoryboardBatch] = useState<StoryboardBatch>();
  const [storyboardBatchBusy, setStoryboardBatchBusy] = useState(false);
  const [videoBatchRetryBusy, setVideoBatchRetryBusy] = useState(false);
  const [storyboardNavigation, setStoryboardNavigation] = useState<StoryboardNavigationItem[]>([]);
  const [activeStoryboardId, setActiveStoryboardId] = useState<number>();
  const [videoHistories, setVideoHistories] = useState<Record<number, MediaPage<AiVideoResult>>>({});
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
    return () => { videoBatchRequestId.current += 1; };
  }, [projectId, activeEpisode]);
  const reservedShotNos = useRef<Record<number, number>>({});
  const storyboardRequestId = useRef(0);
  const [workspace, setWorkspace] = useState<ProductionWorkspaceState>({
    projectId: projectId || 0,
    script: null,
    versions: [],
    characters: [],
    scenes: [],
    props: [],
    storyboards: [],
  });
  const videoSettings = useStoryboardVideoSettings(projectId, (storyboardId, settings) => {
    setWorkspace((previous) => ({ ...previous, storyboards: previous.storyboards.map((item) =>
      item.id === storyboardId ? { ...item, videoGenerationSettings: settings } : item) }));
  }, () => message.error('视频参数保存失败，请重试'));
  const mediaAbort = useRef<AbortController | undefined>(undefined);
  const mediaScopeKey = workspace.storyboards.map((item) => item.id).join(',');
  const currentMediaScope = useRef('');
  currentMediaScope.current = `${projectId}:${mediaScopeKey}`;

  useEffect(() => {
    const controller = new AbortController();
    mediaAbort.current = controller;
    setImageTasks([]);
    setVideoTasks([]);
    setVoiceTasks([]);
    setVideoHistories({});
    const ids = mediaScopeKey ? mediaScopeKey.split(',').map(Number) : [];
    if (ids.length) {
      void queryStoryboardMedia(projectId, ids, controller.signal).then((response) => {
        if (controller.signal.aborted || !response.data) return;
        setImageTasks(response.data.imageTasks || []);
        setVideoTasks(response.data.videoTasks || []);
        setVoiceTasks(response.data.voiceTasks || []);
        setVideoHistories(Object.fromEntries((response.data.videoHistory || []).map((page) => [page.storyboardId, page])));
        if (response.data.assetVisuals) {
          const visuals = response.data.assetVisuals;
          setAssetVisuals((previous) => ({
            ...previous,
            ...Object.fromEntries(visuals.map(({ key, visual }) => [key, visual])),
          }));
        }
        const knownVariantIds = new Set((response.data.assetVisuals || [])
          .flatMap(({ visual }) => visual.variants.map((variant) => variant.id)));
        for (const shot of workspace.storyboards) {
          for (const node of shot.promptDocument?.nodes || []) {
            if (node.type !== 'mention' || node.mediaType !== 'IMAGE'
              || node.sourceType !== 'ASSET_VISUAL_VARIANT' || !node.assetType || !node.assetId
              || knownVariantIds.has(node.sourceId)) continue;
            knownVariantIds.add(node.sourceId);
            // Restore only the referenced look, including looks outside the current shot's asset bindings.
            void queryAssetVisualWorkspace(projectId, node.assetType, node.assetId, {
              current: 1, pageSize: 1, selectedVariantId: node.variantId || node.sourceId,
            }).then(({ data }) => {
              if (controller.signal.aborted || !data) return;
              const key = `${node.assetType}:${node.assetId}`;
              setAssetVisuals((previous) => ({
                ...previous,
                [key]: {
                  ...data, ...previous[key],
                  variants: [...new Map([
                    ...(previous[key]?.variants || []), ...data.variants, data.primaryVariant, data.selectedVariant,
                  ].filter((variant): variant is VisualVariant => Boolean(variant))
                    .map((variant) => [variant.id, variant])).values()],
                },
              }));
            }).catch(() => undefined);
          }
        }
        for (const task of response.data.videoTasks.filter((item) => item.executionId &&
          !successStatuses.includes(item.status) && !['FAILED', 'CANCELED'].includes(item.status))) {
          void followVideoExecution(task).catch(() => {
            if (!controller.signal.aborted) message.error('视频任务状态刷新失败');
          });
        }
      }).catch(() => {
        if (!controller.signal.aborted) message.error('分镜媒体加载失败');
      });
    }
    return () => controller.abort();
  }, [projectId, mediaScopeKey]);

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
        setWorkspace((previous) => ({ ...previous, projectId, storyboards: shots, episodes }));
        setStoryboardPage(storyboardResponse.data?.current || 1);
        setStoryboardTotal(storyboardResponse.data?.total ?? shots.length);
        setStoryboardNavigation(navigationFromPage(storyboardResponse.data));
        setActiveEpisode(storyboardResponse.data?.episodeNo || episodes[0]?.episodeNo || shots[0]?.episodeNo || 1);
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
      storyboardBatchBusy ||
      terminalStoryboardBatchStatuses.has(storyboardBatch.status)
    ) {
      return;
    }
    let active = true;
    const follow = async () => {
      while (active) {
        const response = await queryStoryboardBatch(projectId, storyboardBatch.id);
        if (!active || !response.data) return;
        setStoryboardBatch(response.data);
        if (terminalStoryboardBatchStatuses.has(response.data.status)) {
          await reloadWorkspace();
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }
    };
    void follow().catch(() => {
      if (active) message.error('分镜批次状态刷新失败');
    });
    return () => {
      active = false;
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
    let active = true;
    let timer: number | undefined;
    const refreshGeneratingVisuals = async () => {
      const results = await Promise.allSettled(visualPollTargets.map(async (target) => {
        const response = await queryAssetVisualWorkspace(
          projectId,
          target.assetType,
          target.assetId,
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
      ...workspace.storyboards.flatMap((shot) => (drafts[shot.id]?.promptDocument || shot.promptDocument)?.nodes || [])
        .filter((node): node is PromptReferenceOption => node.type === 'mention' && node.sourceType === 'IMAGE_MATERIAL'),
      ...uploadedImageReferences,
    ].map((reference) => [reference.sourceId, reference])).values()];
    return { IMAGE: [...images, ...firstFrames, ...uploadedImages], VIDEO: videos, AUDIO: audio };
  }, [characters, scenes, props, imageTasks, videoTasks, voiceTasks, workspace.storyboards, drafts, uploadedImageReferences]);

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
    const draft = drafts[storyboard.id];
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

  const updateDraft = (
    storyboardId: number,
    values: Partial<StoryboardDraft[number]>,
  ) => {
    setDrafts((previous) => ({
      ...previous,
      [storyboardId]: {
        scriptText: previous[storyboardId]?.scriptText || '',
        videoPrompt: previous[storyboardId]?.videoPrompt || '',
        promptDocument: previous[storyboardId]?.promptDocument,
        ...values,
      },
    }));
  };

  const reloadMediaTasks = async (storyboardId?: number) => {
    const scope = currentMediaScope.current;
    const ids = storyboardId ? [storyboardId] : workspace.storyboards.map((item) => item.id);
    if (!ids.length) return;
    const response = await queryStoryboardMedia(projectId, ids, mediaAbort.current?.signal);
    if (currentMediaScope.current !== scope || mediaAbort.current?.signal.aborted || !response.data) return;
    const appliesTo = (targetId: number) => ids.includes(targetId);
    setImageTasks((previous) => [
      ...previous.filter((task) => task.targetType !== 'STORYBOARD' || !appliesTo(task.targetId)),
      ...response.data.imageTasks,
    ]);
    setVideoTasks((previous) => [
      ...previous.filter((task) => !appliesTo(task.storyboardId)),
      ...response.data.videoTasks.map((task) => ({ ...task, execution: previous.find((item) => item.id === task.id)?.execution })),
    ]);
    setVoiceTasks((previous) => [
      ...previous.filter((task) => !appliesTo(task.storyboardId)), ...response.data.voiceTasks,
    ]);
    setVideoHistories((previous) => ({ ...previous,
      ...Object.fromEntries((response.data.videoHistory || []).map((page) => [page.storyboardId, page])),
    }));
  };
  const reloadVideoTasks = (storyboardId?: number) => reloadMediaTasks(storyboardId);
  const reloadImageTasks = (storyboardId?: number) => reloadMediaTasks(storyboardId);
  const loadImageCandidates = async (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => {
    const response = await queryAiImageResults(projectId, {
      targetType: 'STORYBOARD', targetId: storyboardId, current, pageSize,
    }, signal);
    return response.data;
  };
  const loadVideoCandidates = async (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => {
    const response = await queryAiVideoResults(projectId, { storyboardId, current, pageSize }, signal);
    return response.data;
  };

  const persistStoryboard = (storyboardId: number, values: SaveStoryboardValues) => {
    const pending = storyboardSaves.current[storyboardId] || Promise.resolve();
    const next = pending.catch(() => undefined).then(() =>
      updateStoryboard(projectId, storyboardId, values),
    );
    storyboardSaves.current[storyboardId] = next;
    return next;
  };

  const reloadWorkspace = async (
    episodeNo = activeEpisode,
    current = storyboardPage,
  ) => {
    const requestId = storyboardRequestId.current + 1;
    storyboardRequestId.current = requestId;
    void queryAssetSettingsSummary(projectId).then((response) => {
      if (storyboardRequestId.current !== requestId) return;
      setWorkspace((previous) => ({
        ...previous,
        characters: response.data?.characters || [],
        scenes: response.data?.scenes || [],
        props: response.data?.props || [],
      }));
    }).catch(() => {
      if (storyboardRequestId.current === requestId) message.error('分镜素材加载失败');
    });
    const storyboardResponse = await queryStoryboardEditorPage(projectId, {
      episodeNo, current, pageSize: storyboardPageSize,
    });
    if (storyboardRequestId.current === requestId && storyboardResponse.data) {
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
    }
  };

  const reloadStoryboardPage = async (episodeNo = activeEpisode, current = storyboardPage) => {
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
    } catch (error) {
      if (requestId === storyboardRequestId.current) setStoryboardLoadFailed(true);
      throw error;
    } finally {
      if (requestId === storyboardRequestId.current) setStoryboardLoading(false);
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
      if (!response.data) throw new Error('missing execution');
      setStoryboardExecutionEpisodeId(episode.episodeId);
      setStoryboardExecution(response.data);
      const tenantId = currentTenantId();
      if (!tenantId) throw new Error('missing tenant');
      const terminal = await aiExecutionTaskService.poll(
        tenantId,
        response.data.id || 0,
        setStoryboardExecution,
      );
      setStoryboardExecution(terminal);
      if (terminal.status === 'SUCCEEDED') {
        await reloadWorkspace();
        message.success('本集分镜已生成');
      }
    } catch (error) {
      if (isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '本集分镜生成失败');
    } finally {
      setStoryboardBusy(false);
    }
  };

  const pollStoryboardBatch = async (batchId: number) => {
    while (true) {
      const response = await queryStoryboardBatch(projectId, batchId);
      if (!response.data) throw new Error('missing storyboard batch');
      setStoryboardBatch(response.data);
      if (terminalStoryboardBatchStatuses.has(response.data.status)) {
        return response.data;
      }
      await new Promise((resolve) => setTimeout(resolve, 1500));
    }
  };

  const retryFailedStoryboards = async () => {
    const episodeIds = (storyboardBatch?.items || []).filter((item) => item.status === 'FAILED')
      .map((item) => item.episodeId);
    if (!episodeIds.length) {
      message.warning('当前项目没有可生成分镜的有效剧集');
      return;
    }
    setStoryboardBatchBusy(true);
    try {
      const response = await createStoryboardBatch(projectId, { episodeIds });
      if (!response.data) throw new Error('missing storyboard batch');
      setStoryboardBatch(response.data);
      const terminal = terminalStoryboardBatchStatuses.has(response.data.status)
        ? response.data
        : await pollStoryboardBatch(response.data.id);
      setStoryboardBatch(terminal);
      await reloadWorkspace();
      message.success('失败分镜已重试');
    } catch (error) {
      if (isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '批量分镜生成失败');
    } finally {
      setStoryboardBatchBusy(false);
    }
  };

  const currentTenantId = () => Number(localStorage.getItem('currentTenantId'));

  const followImageExecution = async (task: AiImageTask) => {
    const scope = currentMediaScope.current;
    const signal = mediaAbort.current?.signal;
    if (!task.executionId || !currentTenantId()) {
      await reloadImageTasks(task.targetType === 'STORYBOARD' ? task.targetId : undefined);
      return;
    }
    await aiExecutionTaskService.poll(
      currentTenantId(),
      task.executionId,
      async () => {
        const response = await queryAiImageTask(projectId, task.id);
        if (response.data && currentMediaScope.current === scope && !signal?.aborted) {
          setImageTasks((previous) => [
            ...previous.filter((item) => item.id !== response.data?.id),
            response.data,
          ]);
        }
      },
      1500,
      signal,
    );
    await reloadImageTasks(task.targetType === 'STORYBOARD' ? task.targetId : undefined);
  };

  const followVideoExecution = async (task: AiVideoTask) => {
    const scope = currentMediaScope.current;
    const signal = mediaAbort.current?.signal;
    const tenantId = currentTenantId();
    if (!task.executionId || !tenantId) {
      await reloadVideoTasks(task.storyboardId);
      return;
    }
    await aiExecutionTaskService.poll(
      tenantId,
      task.executionId,
      (execution) => {
        if (currentMediaScope.current !== scope || signal?.aborted) return;
        setVideoTasks((previous) =>
          previous.map((item) =>
            item.id === task.id ? { ...item, execution } : item,
          ),
        );
      },
      1500,
      signal,
    );
    await reloadVideoTasks(task.storyboardId);
  };

  const generateImage = async (storyboard: StoryboardShot) => {
    try {
      const response = await createAiImageTask(projectId, {
        taskType: 'STORYBOARD_FIRST_FRAME',
        targetType: 'STORYBOARD',
        targetId: storyboard.id,
        prompt: storyboard.imagePrompt || storyboard.visualDescription,
        aspectRatio: '9:16',
        imageCount: 1,
        quality: 'STANDARD',
      });
      if (response.data) {
        setImageTasks((previous) => [
          ...previous,
          response.data as AiImageTask,
        ]);
        void followImageExecution(response.data as AiImageTask).catch(() =>
          message.error('首帧任务状态刷新失败'),
        );
      }
      message.success('首帧任务已创建');
    } catch (error) {
      if (isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '首帧任务创建失败');
    }
  };

  const regenerateImage = async (task: AiImageTask) => {
    try {
      const response = await regenerateAiImageTask(projectId, task.id);
      if (response.data) {
        setImageTasks((previous) => [
          ...previous,
          response.data as AiImageTask,
        ]);
        void followImageExecution(response.data as AiImageTask).catch(() =>
          message.error('再生成任务状态刷新失败'),
        );
      }
      message.success('再生成任务已创建');
    } catch (error) {
      if (isPointEstimateCancelled(error)) return;
      message.error(
        error instanceof Error ? error.message : '再生成任务创建失败');
    }
  };

  const cancelImage = async (task: AiImageTask) => {
    try {
      const response = await cancelAiImageTask(projectId, task.id);
      if (response.data) {
        setImageTasks((previous) => [
          ...previous.filter((item) => item.id !== response.data?.id),
          response.data,
        ]);
      }
    } catch {
      message.error('首帧任务取消失败');
    }
  };

  const selectFirstFrame = async (resultId: number) => {
    setSelectingFirstFrameId(resultId);
    try {
      const response = await selectAiImageResult(projectId, resultId);
      if (!response.data) throw new Error('missing image result');
      const result = response.data;
      setImageTasks((previous) => previous.map((task) => ({
        ...task, results: task.results?.map((item) => ({
          ...item, selected: item.targetId === result.targetId
            ? item.id === result.id : item.selected,
        })) || [],
      })));
      setWorkspace((previous) => ({ ...previous, storyboards: previous.storyboards.map((item) =>
        item.id === result.targetId ? { ...item, firstFrameUrl: result.imageUrl } : item,
      ) }));
      message.success('已设为分镜首帧');
    } catch {
      message.error('设置分镜首帧失败');
    } finally {
      setSelectingFirstFrameId(undefined);
    }
  };


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

  const previewVideoOperations = (storyboards: StoryboardShot[]): PointEstimateOperation[] | undefined => {
    if (!storyboards.length) return undefined;
    try {
      const submissions = storyboards.map((storyboard) => {
        const promptDocument = promptFor(storyboard);
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
    await videoSettings.wait(storyboards.map((item) => item.id));
    const submissions = storyboards.map((storyboard) => {
      const promptDocument = promptFor(storyboard);
      return { storyboard, promptDocument, values: videoTaskValues(storyboard, promptDocument) };
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
    if (!confirmed) throw new PointEstimateCancelledError();
    const skipMissingIds = new Set(missing.map(({ storyboard }) => storyboard.id));
    return submissions.map((submission) => skipMissingIds.has(submission.storyboard.id)
      ? { ...submission, values: { ...submission.values, skipUnavailableImages: true } }
      : submission);
  };

  const submitVideoTask = async ({ storyboard, values, promptDocument }: VideoSubmission) => {
    const prompt = values.prompt;
    const scriptText = drafts[storyboard.id]?.scriptText;
    const latestStoryboard = scriptText && scriptText !== getStoryboardScriptText(storyboard)
      ? { ...storyboard, ...parseStoryboardScriptText(storyboard, scriptText) }
      : storyboard;
    await persistStoryboard(storyboard.id, getStoryboardSavePayload(latestStoryboard, {
      videoPrompt: prompt, promptDocument,
    }));
    setWorkspace((previous) => ({ ...previous, storyboards: previous.storyboards.map((item) =>
      item.id === storyboard.id ? { ...item, videoPrompt: prompt, promptDocument } : item,
    ) }));
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
      if (task) {
        setVideoTasks((previous) => [...previous, task]);
        void followVideoExecution(task).catch(() =>
          message.error('视频任务状态刷新失败'),
        );
      }
      message.success('视频任务已创建');
    } catch (error) {
      if (!isPointEstimateCancelled(error))
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
          message.error('视频任务状态刷新失败'),
        );
      });
      if (created > 0) message.success(`已创建 ${created} 个视频任务`);
      setVideoBatchSelectedIds(failedIds);
      if (!failedIds.length) setVideoBatchOpen(false);
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(
          error instanceof Error ? error.message : '批量视频提交失败',
        );
    } finally {
      videoBatchSubmission.current = false;
      setVideoBatchSubmitting(false);
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
      const outcomes = await Promise.allSettled(
        failedVisibleVideoTasks.map((task) =>
          regenerateAiVideoTask(projectId, task.id, true),
        ),
      );
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
        void followVideoExecution(task).catch(() => message.error('视频任务状态刷新失败'));
      });
      if (created) message.success(`已重试 ${created} 条失败视频`);
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(
          error instanceof Error ? error.message : '批量视频重试失败',
        );
    } finally {
      setVideoBatchRetryBusy(false);
    }
  };

  const cancelVideo = async (task: AiVideoTask) => {
    try {
      await cancelAiVideoTask(projectId, task.id);
      await reloadVideoTasks();
    } catch {
      message.error('视频任务取消失败');
    }
  };

  const followRecoveredVideo = async (taskId: number) => {
    const scope = currentMediaScope.current;
    const signal = mediaAbort.current?.signal;
    const task = await queryRecoveredVideoTask(projectId, taskId, signal);
    if (signal?.aborted || currentMediaScope.current !== scope || task.projectId !== projectId) return;
    setVideoTasks((previous) => [...previous.filter((item) => item.id !== task.id), task]);
    void followVideoExecution(task).catch(() => message.error('重试视频状态刷新失败'));
  };

  const retryVideo = async (task: AiVideoTask) => {
    try {
      const response = await regenerateAiVideoTask(projectId, task.id);
      if (response.data) {
        const nextTask = response.data as AiVideoTask;
        setVideoTasks((previous) => [...previous, nextTask]);
        void followVideoExecution(nextTask).catch(() =>
          message.error('视频任务状态刷新失败'),
        );
      }
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(error instanceof Error ? error.message : '视频任务重试失败');
    }
  };

  const bindVideoResult = async (resultId: number) => {
    setBindingVideoId(resultId);
    try {
      const response = await bindAiVideoResultToStoryboard(projectId, resultId);
      if (!response.data) throw new Error('missing video result');
      const result = response.data;
      setVideoHistories((previous) => {
        const page = previous[result.storyboardId];
        return { ...previous, [result.storyboardId]: {
          current: page?.current || 1, pageSize: page?.pageSize || 8, total: Math.max(1, page?.total || 0),
          data: [...(page?.data || []).filter((candidate) => candidate.id !== result.id)
            .map((candidate) => ({ ...candidate, isSelected: false })), result],
        } };
      });
      setWorkspace((previous) => ({ ...previous, storyboards: previous.storyboards.map((item) =>
        item.id === result.storyboardId
          ? { ...item, currentVideoResultId: result.id, currentVideoUrl: result.videoUrl }
          : item,
      ) }));
      setVideoTasks((previous) => previous.map((task) => ({
        ...task, results: task.results?.map((item) => ({
          ...item, isSelected: item.storyboardId === result.storyboardId
            ? item.id === result.id : item.isSelected,
        })) || [],
      })));
      message.success('已设为当前分镜视频');
    } catch {
      message.error('设置当前分镜视频失败');
    } finally {
      setBindingVideoId(undefined);
    }
  };

  const generateVoice = async (
    storyboard: StoryboardShot,
    values: VoiceGenerationValues,
  ) => {
    try {
      const response = await createAiVoiceTask(projectId, {
        storyboardId: storyboard.id,
        ...values,
      });
      if (response.data) {
        setVoiceTasks((previous) => [
          ...previous.filter((task) => task.id !== response.data?.id),
          response.data as AiVoiceTask,
        ]);
      }
      message.success('语音任务已创建');
    } catch (error) {
      message.error('语音任务创建失败');
      throw error;
    }
  };

  const saveStoryboardScript = async (storyboard: StoryboardShot) => {
    const scriptText = drafts[storyboard.id]?.scriptText;
    if (!scriptText || scriptText === getStoryboardScriptText(storyboard)) {
      return;
    }
    const parsedScript = parseStoryboardScriptText(storyboard, scriptText);
    const nextStoryboard = {
      ...storyboard,
      ...parsedScript,
    };
    const videoPrompt =
      drafts[storyboard.id]?.videoPrompt || getStoryboardPrompt(nextStoryboard);
    try {
      await persistStoryboard(storyboard.id, {
        visualDescription: parsedScript.visualDescription,
        scene: parsedScript.scene,
        dialogue: parsedScript.dialogue,
        characters: storyboard.characters,
        props: getStoryboardProps(storyboard),
        durationSeconds: storyboard.durationSeconds,
        imagePrompt: storyboard.imagePrompt,
        videoPrompt,
      });
      draftSources.current[storyboard.id] = {
        ...draftSources.current[storyboard.id], scriptText, videoPrompt,
      };
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.map((item) =>
          item.id === storyboard.id ? nextStoryboard : item,
        ),
      }));
      if (await refreshAfterMutation()) message.success('分镜已保存');
    } catch {
      message.error('分镜保存失败');
    }
  };

  const saveStoryboardFields = async (
    storyboard: StoryboardShot,
    values: Partial<SaveStoryboardValues>,
  ) => {
    const previousDraft = drafts[storyboard.id];
    const nextStoryboard = {
      ...storyboard,
      ...values,
    };
    const nextVideoPrompt = values.videoPrompt || getStoryboardPrompt(nextStoryboard);
    setWorkspace((previous) => ({
      ...previous,
      storyboards: previous.storyboards.map((item) =>
        item.id === storyboard.id ? nextStoryboard : item,
      ),
    }));
    const savedDraft = {
      scriptText: getStoryboardScriptText(nextStoryboard),
      videoPrompt: nextVideoPrompt,
      promptDocument: values.promptDocument || storyboard.promptDocument || undefined,
    };
    setDrafts((previous) => ({ ...previous, [storyboard.id]: savedDraft }));

    try {
      await persistStoryboard(
        storyboard.id,
        getStoryboardSavePayload(nextStoryboard, {
          videoPrompt: nextVideoPrompt,
        }),
      );
      draftSources.current[storyboard.id] = savedDraft;
      message.success('分镜已保存');
    } catch {
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.map((item) =>
          item.id === storyboard.id ? storyboard : item,
        ),
      }));
      setDrafts((previous) => ({
        ...previous,
        [storyboard.id]: previousDraft || {
          scriptText: getStoryboardScriptText(storyboard),
          videoPrompt: getStoryboardPrompt(storyboard),
          promptDocument: storyboard.promptDocument,
        },
      }));
      message.error('分镜保存失败');
    }
  };

  const replaceStoryboardReferences = async (
    storyboard: StoryboardShot,
    references: StoryboardAssetReference[],
  ) => {
    const previousReferences = storyboard.assetReferences || [];
    const displayFields = getReferenceDisplayFields(references);
    const applyReferences = (next: StoryboardAssetReference[], fields = displayFields) => {
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.map((item) => item.id === storyboard.id
          ? { ...item, ...fields, assetReferences: next }
          : item),
      }));
    };
    applyReferences(references);
    let saveState = referenceSaveStates.current.get(storyboard.id);
    if (!saveState) {
      saveState = {
        tail: Promise.resolve(),
        latestVersion: 0,
        pendingCount: 0,
        confirmedReferences: previousReferences,
        confirmedFields: {
          characters: storyboard.characters,
          scene: storyboard.scene,
          props: getStoryboardProps(storyboard),
        },
      };
      referenceSaveStates.current.set(storyboard.id, saveState);
    }
    const currentSaveState = saveState;
    const version = currentSaveState.latestVersion + 1;
    currentSaveState.latestVersion = version;
    currentSaveState.pendingCount += 1;
    currentSaveState.tail = currentSaveState.tail.then(async () => {
      try {
        const response = await replaceStoryboardAssetReferences(
          projectId,
          storyboard.id,
          references.map((reference) => ({
            assetType: reference.assetType,
            assetId: reference.assetId,
            variantId: reference.variantId,
            referenceRole: reference.referenceRole,
            sortOrder: reference.sortOrder,
            sourceName: reference.sourceName,
          })),
        );
        const savedReferences = preserveReferenceClientKeys(
          references,
          response.data || references,
        );
        currentSaveState.confirmedReferences = savedReferences;
        currentSaveState.confirmedFields = getReferenceDisplayFields(savedReferences);
        if (version === currentSaveState.latestVersion) {
          applyReferences(savedReferences, currentSaveState.confirmedFields);
          message.success('素材引用已保存');
        }
      } catch {
        if (version === currentSaveState.latestVersion) {
          applyReferences(
            currentSaveState.confirmedReferences,
            currentSaveState.confirmedFields,
          );
          message.error('素材引用保存失败');
        }
      } finally {
        currentSaveState.pendingCount -= 1;
        if (
          currentSaveState.pendingCount === 0
          && referenceSaveStates.current.get(storyboard.id) === currentSaveState
        ) {
          referenceSaveStates.current.delete(storyboard.id);
        }
      }
    });
    await currentSaveState.tail;
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
  ) => {
    try {
      const tenantId = currentTenantId();
      if (task.executionId && tenantId) {
        await aiExecutionTaskService.poll(tenantId, task.executionId);
      } else {
        let currentTask = task;
        while (!terminalImageTaskStatuses.has(currentTask.status)) {
          await new Promise((resolve) => setTimeout(resolve, 1500));
          if (visualProjectId.current !== projectId) return;
          const response = await queryAiImageTask(projectId, currentTask.id);
          if (!response.data) throw new Error('missing image task');
          currentTask = response.data;
        }
      }
      if (visualProjectId.current !== projectId) return;
      const response = await queryAssetVisualWorkspace(
        projectId,
        target.assetType,
        target.assetId,
      );
      if (visualProjectId.current !== projectId) return;
      setAssetVisuals((previous) => ({
        ...previous,
        [`${target.assetType}:${target.assetId}`]: response.data,
      }));
    } catch {
      if (visualProjectId.current === projectId) {
        message.error('资产图状态刷新失败');
      }
    } finally {
      if (visualProjectId.current === projectId) {
        setGeneratingReferenceKeys((previous) => {
          const next = new Set(previous);
          next.delete(target.generationKey);
          return next;
        });
      }
    }
  };

  const submitReferenceVariantGeneration = async () => {
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
      if (visualProjectId.current !== requestedProjectId) return;
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
      void followReferenceVariantGeneration(target, response.data);
    } catch (error) {
      if (visualProjectId.current === requestedProjectId) {
        if (!isPointEstimateCancelled(error))
          message.error(
            error instanceof Error ? error.message : '提交生成失败');
      }
    } finally {
      if (
        visualProjectId.current === requestedProjectId
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
    } catch {
      message.error('删除分镜失败');
    }
  };

  if (!projectId) {
    return <Empty description="项目不存在" />;
  }

  return (
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
                <StoryboardCard
                  canCaptureVideo={project?.capabilities?.canEdit === true}
                  onImageUploaded={(reference) => setUploadedImageReferences((previous) => [...previous, reference])}
                  onCaptureVideo={(resultId, storyboardNo) => { stopActiveVideo(); setFrameCapture({ resultId, storyboardNo }); }}
                  projectId={projectId}
                  videoEstimateOperations={previewVideoOperations([item])}
                  item={item}
                  index={(item.storyboardNo || item.shotNo || index + 1) - 1}
                  characters={characters}
                  scenes={scenes}
                  props={props}
                  episodes={workspace?.episodes ?? []}
                  imageTasks={imageTasks}
                  videoTasks={videoTasks}
                  videoHistory={videoHistories[item.id]}
                  referenceOptions={referenceOptions}
                  referenceThumbnails={referenceThumbnails}
                  draft={
                    {
                      ...(drafts[item.id] || {
                        scriptText: getStoryboardScriptText(item),
                        videoPrompt: getStoryboardPrompt(item),
                      }),
                      promptDocument: promptFor(item),
                    }
                  }
                  model={selectedVideoModel?.name || '未配置视频模型'}
                  modelConstraints={selectedVideoModel?.constraints}
                  generationSettings={settingsFor(item)}
                  onDraftChange={updateDraft}
                  onGenerationSettingsChange={(values) => videoSettings.save(item.id, { ...settingsFor(item), ...values })}
                  generationSettingsSaveStatus={videoSettings.status[item.id]}
                  onRetryGenerationSettings={() => videoSettings.retry(item.id)}
                  onGenerate={generateVideo}
                  onBindVideoResult={bindVideoResult}
                  bindingVideoId={bindingVideoId}
                  onCancelVideo={cancelVideo}
                  onRetryVideo={retryVideo}
                  onRecoveredVideo={followRecoveredVideo}
                  onGenerateImage={generateImage}
                  onSelectFirstFrame={selectFirstFrame}
                  selectingFirstFrameId={selectingFirstFrameId}
                  onRegenerateImage={regenerateImage}
                  onCancelImage={cancelImage}
                  onGenerateVoice={generateVoice}
                  onReplaceReferences={replaceStoryboardReferences}
                  onGenerateReferenceImage={generateReferenceImage}
                  onLoadVariants={loadAssetVariants}
                  onLoadImageCandidates={loadImageCandidates}
                  onLoadVideoCandidates={loadVideoCandidates}
                  generatingReferenceKeys={generatingReferenceKeys}
                  onSaveScript={saveStoryboardScript}
                  onUpdateStoryboard={saveStoryboardFields}
                  onAddStoryboard={addStoryboardAfter}
                  onCopyStoryboard={copyStoryboard}
                  onDelete={removeStoryboard}
                />
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
  );
};

export default ProductionWorkbenchStoryboard;
