import type { ProjectModelOption } from './ai-config/service';
import { createContext, useCallback, useContext, useLayoutEffect, useMemo, useRef, useSyncExternalStore, type ReactNode } from 'react';
import type { PointEstimateOperation } from '@/services/point-estimate';
import type { StoryboardEditorController } from './useStoryboardEditorController';
import type useStoryboardMediaController from './useStoryboardMediaController';
import type { VoiceGenerationValues } from './StoryboardVoiceGenerator';
import type { VideoGenerationSettings, VideoSettingsSaveStatus } from './useStoryboardVideoSettings';
import type { SubtitleEraseTask } from './subtitle-erase-service';
import type { CharacterAsset, SceneAsset, PropAsset, ScriptEpisode, StoryboardShot,
  StoryboardAssetReference, StoryboardPromptDocument, StoryboardPromptNode, AiVideoResult } from './service';

type Reference = Extract<StoryboardPromptNode, { type: 'mention' }>;
type Media = ReturnType<typeof useStoryboardMediaController>;
type Resources = {
  projectId: number; canCaptureVideo: boolean;
  characters: CharacterAsset[]; scenes: SceneAsset[]; props: PropAsset[]; episodes: ScriptEpisode[];
  referenceOptions: Record<'IMAGE' | 'VIDEO' | 'AUDIO', Reference[]>;
  referenceThumbnails: ReadonlyMap<string, string>;
  model: string; modelId?: number; aspectRatio?: string; modelConstraints?: ProjectModelOption['constraints'];
};
type ProviderProps = {
  projectId: number;
  storyboards: StoryboardShot[];
  editor: StoryboardEditorController;
  media: Media;
  resources: Resources;
  video: {
    settingsFor: (shot: StoryboardShot) => VideoGenerationSettings;
    statuses: Record<number, VideoSettingsSaveStatus>;
    saveSettings: (id: number, values: VideoGenerationSettings) => void;
    retrySettings: (id: number) => void;
    promptFor: (shot: StoryboardShot) => StoryboardPromptDocument;
    preview: (shots: StoryboardShot[], resolver?: (shot: StoryboardShot) => StoryboardPromptDocument) => PointEstimateOperation[] | undefined;
    generate: (shot: StoryboardShot) => void;
  };
  assets: {
    uploaded: (reference: Reference) => void;
    generate: (reference: StoryboardAssetReference, key: string) => Promise<void>;
    generating: ReadonlySet<string>;
    loadVariants: (type: StoryboardAssetReference['assetType'], id: number, selected?: number | null, current?: number, pageSize?: number) => Promise<void>;
  };
  actions: {
    add: (shot: StoryboardShot) => void; copy: (shot: StoryboardShot) => void; remove: (shot: StoryboardShot) => Promise<boolean>;
    capture: (resultId: number, storyboardNo: number) => void;
    erase: (resultId: number, storyboardNo: number) => void;
  };
  erasure: { tasks: SubtitleEraseTask[]; previews: Record<number, AiVideoResult>; retry: (id: number) => Promise<void> };
  children: ReactNode;
};

const rowFor = (shot: StoryboardShot, input: ProviderProps) => ({
  item: shot,
  imageTasks: input.media.imageTasks.filter((task) => task.targetType !== 'STORYBOARD' || task.targetId === shot.id),
  videoTasks: input.media.videoTasks.filter((task) => task.storyboardId === shot.id),
  videoHistory: input.media.videoHistories[shot.id],
  bindingVideoId: input.media.bindingStoryboardId === shot.id ? input.media.bindingVideoId : undefined,
  selectingFirstFrameId: input.media.selectingStoryboardId === shot.id ? input.media.selectingFirstFrameId : undefined,
  generationSettings: input.video.settingsFor(shot),
  generationSettingsSaveStatus: input.video.statuses[shot.id],
  eraseTasks: input.erasure.tasks.filter((task) => task.storyboardId === shot.id),
  erasedPreviewResult: input.erasure.previews[shot.id],
});
type Row = ReturnType<typeof rowFor>;
const sameValue = (left: unknown, right: unknown) => {
  if (left === right) return true;
  if (!left || !right || typeof left !== 'object' || typeof right !== 'object') return false;
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length && keys.every((key) =>
    (left as Record<string, unknown>)[key] === (right as Record<string, unknown>)[key]);
};

function createScope(input: ProviderProps) {
  const rows = new Map(input.storyboards.map((shot) => [shot.id, rowFor(shot, input)]));
  const listeners = new Map<number, Set<() => void>>();
  return {
    current: input,
    read: (id: number) => rows.get(id),
    subscribe: (id: number, notify: () => void) => {
      const subscriptions = listeners.get(id) || new Set<() => void>();
      subscriptions.add(notify); listeners.set(id, subscriptions);
      return () => { subscriptions.delete(notify); if (!subscriptions.size) listeners.delete(id); };
    },
    publish: (next: ProviderProps) => {
      const incoming = new Map(next.storyboards.map((shot) => [shot.id, rowFor(shot, next)]));
      for (const id of new Set([...rows.keys(), ...incoming.keys()])) {
        const previous = rows.get(id);
        const value = incoming.get(id);
        if (previous && value && (Object.keys(value) as (keyof Row)[]).every((key) => sameValue(previous[key], value[key]))) continue;
        if (value) rows.set(id, value); else rows.delete(id);
        listeners.get(id)?.forEach((notify) => { notify(); });
      }
    },
  };
}
type Scope = ReturnType<typeof createScope>;
const ScopeContext = createContext<Scope | null>(null);
const ResourceContext = createContext<(Resources & { generatingReferenceKeys: ReadonlySet<string> }) | null>(null);

export function StoryboardCardProvider(input: ProviderProps) {
  const scope = useMemo(() => createScope(input), [input.projectId]);
  scope.current = input;
  useLayoutEffect(() => scope.publish(input));
  const { resources, assets } = input;
  // Polling recreates the derived lists, but unchanged material choices must not
  // invalidate every card's shared resource context.
  const stableOptions = useRef(resources.referenceOptions);
  if (!(['IMAGE', 'VIDEO', 'AUDIO'] as const).every(type => {
    const previous = stableOptions.current[type];
    const next = resources.referenceOptions[type];
    return previous.length === next.length && previous.every((item, index) => sameValue(item, next[index]));
  })) stableOptions.current = resources.referenceOptions;
  const stableThumbnails = useRef(resources.referenceThumbnails);
  if (stableThumbnails.current.size !== resources.referenceThumbnails.size ||
    [...resources.referenceThumbnails].some(([key, value]) => stableThumbnails.current.get(key) !== value)) {
    stableThumbnails.current = resources.referenceThumbnails;
  }
  const referenceOptions = stableOptions.current;
  const referenceThumbnails = stableThumbnails.current;
  const shared = useMemo(() => ({ ...resources, referenceOptions, referenceThumbnails, generatingReferenceKeys: assets.generating }), [
    resources.projectId, resources.canCaptureVideo, resources.characters, resources.scenes, resources.props,
    resources.episodes, referenceOptions, referenceThumbnails, resources.model, resources.modelId, resources.aspectRatio, resources.modelConstraints, assets.generating,
  ]);
  return <ScopeContext.Provider value={scope}><ResourceContext.Provider value={shared}>
    {input.children}
  </ResourceContext.Provider></ScopeContext.Provider>;
}

function useScope() {
  const scope = useContext(ScopeContext);
  if (!scope) throw new Error('StoryboardCardProvider is required');
  return scope;
}
export function useStoryboardRow(id: number) {
  const scope = useScope();
  const subscribe = useCallback((notify: () => void) => scope.subscribe(id, notify), [scope, id]);
  const snapshot = useCallback(() => scope.read(id), [scope, id]);
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
export function useStoryboardResources() {
  const resources = useContext(ResourceContext);
  if (!resources) throw new Error('StoryboardCardProvider is required');
  return resources;
}
function shotFor(scope: Scope, id: number) {
  const shot = scope.current.storyboards.find((item) => item.id === id) || scope.read(id)?.item;
  if (!shot) throw new Error('分镜已移除');
  return shot;
}

export function useStoryboardEditor(id: number) {
  const scope = useScope();
  const editor = scope.current.editor;
  // Keep the last shot for delayed-save cleanup after paging removes its row.
  const lastShot = useRef(shotFor(scope, id));
  lastShot.current = scope.current.storyboards.find(shot => shot.id === id) || lastShot.current;
  const subscribe = useCallback((notify: () => void) => editor.subscribeStatus(id, notify), [editor, id]);
  const snapshot = useCallback(() => editor.getStatus(id), [editor, id]);
  const saveStatus = useSyncExternalStore(subscribe, snapshot, snapshot);
  const actions = useMemo(() => ({
    updateDraft: (values: Parameters<typeof editor.updateDraft>[1]) => editor.updateDraft(id, values),
    saveFields: (values: Parameters<typeof editor.saveFields>[1]) => editor.saveFields(scope.current.storyboards.find(shot => shot.id === id) || lastShot.current, values),
    saveScript: () => editor.saveScript(scope.current.storyboards.find(shot => shot.id === id) || lastShot.current),
    replaceReferences: (references: StoryboardAssetReference[]) => editor.replaceReferences(shotFor(scope, id), references),
    retrySave: () => editor.retry(id),
    promptFor: (shot: StoryboardShot) => scope.current.video.promptFor(shot),
    previewVideoOperations: (shots: StoryboardShot[], resolver?: (shot: StoryboardShot) => StoryboardPromptDocument) => scope.current.video.preview(shots, resolver),
    saveGenerationSettings: (values: Partial<VideoGenerationSettings>) => scope.current.video.saveSettings(id, {
      ...scope.current.video.settingsFor(shotFor(scope, id)), ...values,
    }),
    retryGenerationSettings: () => scope.current.video.retrySettings(id),
  }), [scope, editor, id]);
  return { ...actions, draftStore: editor.drafts, saveStatus };
}

export function useStoryboardMedia(id: number) {
  const scope = useScope();
  const row = useStoryboardRow(id);
  const actions = useMemo(() => ({
    loadImageCandidates: (current: number, pageSize: number, signal?: AbortSignal) => scope.current.media.loadImageCandidates(id, current, pageSize, signal),
    loadVideoCandidates: (current: number, pageSize: number, signal?: AbortSignal) => scope.current.media.loadVideoCandidates(id, current, pageSize, signal),
    selectFirstFrame: (resultId: number) => scope.current.media.selectFirstFrame(resultId, id),
    bindVideoResult: (resultId: number) => scope.current.media.bindVideoResult(resultId, id),
    generateVoice: (values: VoiceGenerationValues) => scope.current.media.generateVoice(shotFor(scope, id), values),
    generateImage: () => scope.current.media.generateImage(shotFor(scope, id)),
    generateVideo: () => scope.current.video.generate(shotFor(scope, id)),
    regenerateImage: (task: Parameters<Media['regenerateImage']>[0]) => scope.current.media.regenerateImage(task),
    cancelImage: (task: Parameters<Media['cancelImage']>[0]) => scope.current.media.cancelImage(task),
    cancelVideo: (task: Parameters<Media['cancelVideo']>[0]) => scope.current.media.cancelVideo(task),
    retryVideo: (task: Parameters<Media['retryVideo']>[0]) => scope.current.media.retryVideo(task),
    retryErase: (taskId: number) => scope.current.erasure.retry(taskId),
  }), [scope, id]);
  return { ...row, ...actions };
}

export function useStoryboardCardActions(id: number) {
  const scope = useScope();
  return useMemo(() => ({
    add: () => scope.current.actions.add(shotFor(scope, id)),
    copy: () => scope.current.actions.copy(shotFor(scope, id)),
    remove: () => scope.current.actions.remove(shotFor(scope, id)),
    capture: (resultId: number, storyboardNo: number) => scope.current.actions.capture(resultId, storyboardNo),
    erase: (resultId: number, storyboardNo: number) => scope.current.actions.erase(resultId, storyboardNo),
    uploaded: (reference: Reference) => scope.current.assets.uploaded(reference),
    generateReferenceImage: (reference: StoryboardAssetReference, key: string) => scope.current.assets.generate(reference, key),
    loadVariants: (type: StoryboardAssetReference['assetType'], assetId: number, selected?: number | null, current?: number, pageSize?: number) => scope.current.assets.loadVariants(type, assetId, selected, current, pageSize),
  }), [scope, id]);
}
