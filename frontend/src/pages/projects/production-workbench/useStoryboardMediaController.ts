import { App } from 'antd';
import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import type { MediaPage } from '@/services/media/paging';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { queryRecoveredVideoTask } from './avatar-recovery-service';
import { pollVideoTask } from './video-task-polling';
import type {
  AiImageTask, AiVideoResult, AiVideoTask, AiVoiceTask, AssetVisualWorkspace,
  StoryboardShot, VisualVariant,
} from './service';
import {
  bindAiVideoResultToStoryboard, cancelAiImageTask, cancelAiVideoTask,
  createAiImageTask, createAiVoiceTask, queryAiImageResults, queryAiImageTask,
  queryAiVideoResults, queryAssetVisualWorkspace, queryStoryboardMedia,
  regenerateAiImageTask, regenerateAiVideoTask, selectAiImageResult,
} from './service';

export type VoiceGenerationValues = {
  voiceType: string;
  speakerName: string;
  voiceId: string;
  textContent: string;
  speed: number;
  pitch: number;
  volume: number;
};

export type StoryboardMediaCallbacks = {
  onStoryboardPatch: (id: number, values: Partial<StoryboardShot>) => void;
  onAssetVisuals: (
    updater: (previous: Record<string, AssetVisualWorkspace>) => Record<string, AssetVisualWorkspace>,
  ) => void;
};

const successStatuses = ['SUCCESS', 'SUCCEEDED'];
const currentTenantId = () => Number(localStorage.getItem('currentTenantId'));

export default function useStoryboardMediaController(
  projectId: number,
  storyboards: StoryboardShot[],
  callbacks: StoryboardMediaCallbacks,
) {
  const { message } = App.useApp();
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;
  const storyboardsRef = useRef(storyboards);
  storyboardsRef.current = storyboards;
  const messageRef = useRef(message);
  messageRef.current = message;
  const [imageTasks, setImageTasks] = useState<AiImageTask[]>([]);
  const [videoTasks, setVideoTasks] = useState<AiVideoTask[]>([]);
  const [voiceTasks, setVoiceTasks] = useState<AiVoiceTask[]>([]);
  const [videoHistories, setVideoHistories] = useState<Record<number, MediaPage<AiVideoResult>>>({});
  const [bindingVideoId, setBindingVideoId] = useState<number>();
  const [selectingFirstFrameId, setSelectingFirstFrameId] = useState<number>();
  const [bindingStoryboardId, setBindingStoryboardId] = useState<number>();
  const [selectingStoryboardId, setSelectingStoryboardId] = useState<number>();
  const bindingOperation = useRef<symbol | undefined>(undefined);
  const selectingOperation = useRef<symbol | undefined>(undefined);
  const mediaScopeKey = storyboards.map((item) => item.id).join(',');
  const scopeKey = `${projectId}:${mediaScopeKey}`;
  // Each render's operations retain their own scope, including callbacks invoked after navigation.
  const scope = useMemo(() => ({ key: scopeKey, controller: new AbortController() }), [scopeKey]);
  const activeScope = useRef(scope);
  activeScope.current = scope;
  const mediaAbort = useRef<AbortController | undefined>(scope.controller);
  mediaAbort.current = scope.controller;
  const currentMediaScope = useRef(scopeKey);
  currentMediaScope.current = scopeKey;
  const isCurrent = () => activeScope.current === scope && !scope.controller.signal.aborted;

  const reloadMediaTasks = async (storyboardId?: number) => {
    if (!isCurrent()) return;
    const ids = storyboardId ? [storyboardId] : mediaScopeKey.split(',').filter(Boolean).map(Number);
    if (!ids.length) return;
    const response = await queryStoryboardMedia(projectId, ids, scope.controller.signal);
    if (!isCurrent() || !response.data) return;
    const appliesTo = (targetId: number) => ids.includes(targetId);
    setImageTasks((previous) => [
      ...previous.filter((task) => task.targetType !== 'STORYBOARD' || !appliesTo(task.targetId)),
      ...response.data.imageTasks,
    ]);
    setVideoTasks((previous) => [
      ...previous.filter((task) => !appliesTo(task.storyboardId)), ...response.data.videoTasks,
    ]);
    setVoiceTasks((previous) => [
      ...previous.filter((task) => !appliesTo(task.storyboardId)), ...response.data.voiceTasks,
    ]);
    setVideoHistories((previous) => ({ ...previous,
      ...Object.fromEntries((response.data.videoHistory || []).map((page) => [page.storyboardId, page])),
    }));
  };
  const reloadVideoTasks = reloadMediaTasks;
  const reloadImageTasks = reloadMediaTasks;

  const loadImageCandidates = async (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => {
    if (!isCurrent()) throw new DOMException('Media scope changed', 'AbortError');
    const response = await queryAiImageResults(projectId, {
      targetType: 'STORYBOARD', targetId: storyboardId, current, pageSize,
    }, signal || scope.controller.signal);
    signal?.throwIfAborted();
    if (!isCurrent()) throw new DOMException('Media scope changed', 'AbortError');
    return response.data;
  };
  const loadVideoCandidates = async (
    storyboardId: number, current: number, pageSize: number, signal?: AbortSignal,
  ) => {
    if (!isCurrent()) throw new DOMException('Media scope changed', 'AbortError');
    const response = await queryAiVideoResults(projectId, { storyboardId, current, pageSize },
      signal || scope.controller.signal);
    signal?.throwIfAborted();
    if (!isCurrent()) throw new DOMException('Media scope changed', 'AbortError');
    return response.data;
  };

  const followImageExecution = async (task: AiImageTask) => {
    if (!isCurrent()) return;
    const signal = scope.controller.signal;
    try {
      if (!task.executionId || !currentTenantId()) {
        await reloadImageTasks(task.targetType === 'STORYBOARD' ? task.targetId : undefined);
        return;
      }
      await aiExecutionTaskService.poll(currentTenantId(), task.executionId, async () => {
        if (!isCurrent()) return;
        const response = await queryAiImageTask(projectId, task.id, signal);
        if (response.data && isCurrent()) {
          setImageTasks((previous) => [
            ...previous.filter((item) => item.id !== response.data?.id), response.data,
          ]);
        }
      }, 1500, signal);
      if (isCurrent()) await reloadImageTasks(task.targetType === 'STORYBOARD' ? task.targetId : undefined);
    } catch (error) {
      if (isCurrent()) throw error;
    }
  };

  const followVideoExecution = async (task: AiVideoTask) => {
    if (!isCurrent()) return;
    try {
      await pollVideoTask(task.id,
        (id, signal) => queryRecoveredVideoTask(projectId, id, signal),
        (next) => {
          if (!isCurrent()) return;
          setVideoTasks((previous) => previous.map((item) => item.id === task.id ? next : item));
        }, scope.controller.signal);
      if (isCurrent()) await reloadVideoTasks(task.storyboardId);
    } catch (error) {
      if (isCurrent()) throw error;
    }
  };

  useEffect(() => {
    // React StrictMode replays effects; give the replay a fresh polling signal.
    if (scope.controller.signal.aborted) scope.controller = new AbortController();
    const controller = scope.controller;
    mediaAbort.current = controller;
    setImageTasks([]);
    setVideoTasks([]);
    setVoiceTasks([]);
    setVideoHistories({});
    setBindingVideoId(undefined);
    setSelectingFirstFrameId(undefined);
    setBindingStoryboardId(undefined);
    setSelectingStoryboardId(undefined);
    bindingOperation.current = undefined;
    selectingOperation.current = undefined;
    const ids = mediaScopeKey ? mediaScopeKey.split(',').map(Number) : [];
    if (ids.length) {
      void queryStoryboardMedia(projectId, ids, controller.signal).then((response) => {
        if (controller.signal.aborted || !isCurrent() || !response.data) return;
        setImageTasks(response.data.imageTasks || []);
        setVideoTasks(response.data.videoTasks || []);
        setVoiceTasks(response.data.voiceTasks || []);
        setVideoHistories(Object.fromEntries((response.data.videoHistory || [])
          .map((page) => [page.storyboardId, page])));
        if (response.data.assetVisuals) {
          const visuals = response.data.assetVisuals;
          callbacksRef.current.onAssetVisuals((previous) => ({ ...previous,
            ...Object.fromEntries(visuals.map(({ key, visual }) => [key, visual])),
          }));
        }
        const knownVariantIds = new Set((response.data.assetVisuals || [])
          .flatMap(({ visual }) => visual.variants.map((variant) => variant.id)));
        for (const shot of storyboardsRef.current) {
          for (const node of shot.promptDocument?.nodes || []) {
            if (node.type !== 'mention' || node.mediaType !== 'IMAGE'
              || node.sourceType !== 'ASSET_VISUAL_VARIANT' || !node.assetType || !node.assetId
              || knownVariantIds.has(node.sourceId)) continue;
            knownVariantIds.add(node.sourceId);
            // Restore referenced looks even when they are outside this shot's asset bindings.
            void queryAssetVisualWorkspace(projectId, node.assetType, node.assetId, {
              current: 1, pageSize: 1, selectedVariantId: node.variantId || node.sourceId,
            }).then(({ data }) => {
              if (controller.signal.aborted || !isCurrent() || !data) return;
              const key = `${node.assetType}:${node.assetId}`;
              callbacksRef.current.onAssetVisuals((previous) => ({ ...previous, [key]: {
                ...data, ...previous[key],
                variants: [...new Map([
                  ...(previous[key]?.variants || []), ...data.variants, data.primaryVariant, data.selectedVariant,
                ].filter((variant): variant is VisualVariant => Boolean(variant))
                  .map((variant) => [variant.id, variant])).values()],
              } }));
            }).catch(() => undefined);
          }
        }
        for (const task of response.data.videoTasks.filter((item) =>
          !successStatuses.includes(item.status) && !['FAILED', 'CANCELED'].includes(item.status))) {
          void followVideoExecution(task).catch(() => {
            if (!controller.signal.aborted && isCurrent()) messageRef.current.error('视频任务状态刷新失败');
          });
        }
      }).catch(() => {
        if (!controller.signal.aborted && isCurrent()) messageRef.current.error('分镜媒体加载失败');
      });
    }
    // Only client requests and polling stop here; server generation continues.
    return () => controller.abort();
  }, [projectId, mediaScopeKey, scope]);

  const generateImage = async (storyboard: StoryboardShot) => {
    if (!isCurrent()) return;
    try {
      const response = await createAiImageTask(projectId, {
        taskType: 'STORYBOARD_FIRST_FRAME', targetType: 'STORYBOARD', targetId: storyboard.id,
        prompt: storyboard.imagePrompt || storyboard.visualDescription,
        aspectRatio: '9:16', imageCount: 1, quality: 'STANDARD',
      });
      if (!isCurrent()) return;
      if (response.data) {
        const task = response.data as AiImageTask;
        setImageTasks((previous) => [...previous, task]);
        void followImageExecution(task).catch(() => {
          if (isCurrent()) messageRef.current.error('首帧任务状态刷新失败');
        });
      }
      messageRef.current.success('首帧任务已创建');
    } catch (error) {
      if (!isCurrent() || isPointEstimateCancelled(error)) return;
      messageRef.current.error(error instanceof Error ? error.message : '首帧任务创建失败');
    }
  };

  const regenerateImage = async (task: AiImageTask) => {
    if (!isCurrent()) return;
    try {
      const response = await regenerateAiImageTask(projectId, task.id);
      if (!isCurrent()) return;
      if (response.data) {
        const next = response.data as AiImageTask;
        setImageTasks((previous) => [...previous, next]);
        void followImageExecution(next).catch(() => {
          if (isCurrent()) messageRef.current.error('再生成任务状态刷新失败');
        });
      }
      messageRef.current.success('再生成任务已创建');
    } catch (error) {
      if (!isCurrent() || isPointEstimateCancelled(error)) return;
      messageRef.current.error(error instanceof Error ? error.message : '再生成任务创建失败');
    }
  };

  const cancelImage = async (task: AiImageTask) => {
    if (!isCurrent()) return;
    try {
      const response = await cancelAiImageTask(projectId, task.id);
      if (!isCurrent()) return;
      if (response.data) setImageTasks((previous) => [
        ...previous.filter((item) => item.id !== response.data?.id), response.data,
      ]);
    } catch {
      if (isCurrent()) messageRef.current.error('首帧任务取消失败');
    }
  };

  const selectFirstFrame = async (resultId: number, storyboardId?: number) => {
    if (!isCurrent()) return;
    const operation = Symbol();
    selectingOperation.current = operation;
    setSelectingFirstFrameId(resultId);
    setSelectingStoryboardId(storyboardId ?? imageTasks
      .flatMap((task) => task.results || [])
      .find((result) => result.id === resultId && result.targetType === 'STORYBOARD')?.targetId);
    try {
      const response = await selectAiImageResult(projectId, resultId);
      if (!isCurrent()) return;
      if (!response.data) throw new Error('missing image result');
      const result = response.data;
      setImageTasks((previous) => previous.map((task) => ({ ...task,
        results: task.results?.map((item) => ({ ...item,
          selected: item.targetId === result.targetId ? item.id === result.id : item.selected,
        })) || [],
      })));
      callbacksRef.current.onStoryboardPatch(result.targetId, { firstFrameUrl: result.imageUrl });
      messageRef.current.success('已设为分镜首帧');
    } catch {
      if (isCurrent()) messageRef.current.error('设置分镜首帧失败');
    } finally {
      if (isCurrent() && selectingOperation.current === operation) {
        selectingOperation.current = undefined;
        setSelectingFirstFrameId(undefined);
        setSelectingStoryboardId(undefined);
      }
    }
  };

  const cancelVideo = async (task: AiVideoTask) => {
    if (!isCurrent()) return;
    try {
      await cancelAiVideoTask(projectId, task.id);
      if (isCurrent()) await reloadVideoTasks();
    } catch {
      if (isCurrent()) messageRef.current.error('视频任务取消失败');
    }
  };

  const retryVideo = async (task: AiVideoTask) => {
    if (!isCurrent()) return;
    try {
      const response = await regenerateAiVideoTask(projectId, task.id);
      if (!isCurrent()) return;
      if (response.data) {
        const nextTask = response.data as AiVideoTask;
        setVideoTasks((previous) => [...previous, nextTask]);
        void followVideoExecution(nextTask).catch(() => {
          if (isCurrent()) messageRef.current.error('视频任务状态刷新失败');
        });
      }
    } catch (error) {
      if (isCurrent() && !isPointEstimateCancelled(error))
        messageRef.current.error(error instanceof Error ? error.message : '视频任务重试失败');
    }
  };

  const bindVideoResult = async (resultId: number, storyboardId?: number) => {
    if (!isCurrent()) return;
    const operation = Symbol();
    bindingOperation.current = operation;
    setBindingVideoId(resultId);
    const historyOwner = Object.entries(videoHistories)
      .find(([, page]) => page.data.some((result) => result.id === resultId))?.[0];
    setBindingStoryboardId(storyboardId ?? videoTasks
      .flatMap((task) => task.results || [])
      .find((result) => result.id === resultId)?.storyboardId
      ?? (historyOwner === undefined ? undefined : Number(historyOwner)));
    try {
      const response = await bindAiVideoResultToStoryboard(projectId, resultId);
      if (!isCurrent()) return;
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
      callbacksRef.current.onStoryboardPatch(result.storyboardId, {
        currentVideoResultId: result.id, currentVideoUrl: result.videoUrl,
      });
      setVideoTasks((previous) => previous.map((task) => ({ ...task,
        results: task.results?.map((item) => ({ ...item,
          isSelected: item.storyboardId === result.storyboardId ? item.id === result.id : item.isSelected,
        })) || [],
      })));
      messageRef.current.success('已设为当前分镜视频');
    } catch {
      if (isCurrent()) messageRef.current.error('设置当前分镜视频失败');
    } finally {
      if (isCurrent() && bindingOperation.current === operation) {
        bindingOperation.current = undefined;
        setBindingVideoId(undefined);
        setBindingStoryboardId(undefined);
      }
    }
  };

  const generateVoice = async (storyboard: StoryboardShot, values: VoiceGenerationValues) => {
    if (!isCurrent()) return;
    try {
      const response = await createAiVoiceTask(projectId, { storyboardId: storyboard.id, ...values });
      if (!isCurrent()) return;
      if (response.data) setVoiceTasks((previous) => [
        ...previous.filter((task) => task.id !== response.data?.id), response.data as AiVoiceTask,
      ]);
      messageRef.current.success('语音任务已创建');
    } catch (error) {
      if (!isCurrent()) return;
      messageRef.current.error('语音任务创建失败');
      throw error;
    }
  };

  const scopedSetters = useMemo(() => {
    const guard = <Value>(setValue: Dispatch<SetStateAction<Value>>): Dispatch<SetStateAction<Value>> =>
      (value) => {
        if (!isCurrent()) return;
        setValue((previous) => {
          // An update accepted before navigation may be evaluated by React afterwards.
          if (!isCurrent()) return previous;
          return typeof value === 'function'
            ? (value as (previous: Value) => Value)(previous) : value;
        });
      };
    return {
      setImageTasks: guard(setImageTasks), setVideoTasks: guard(setVideoTasks),
      setVoiceTasks: guard(setVoiceTasks), setVideoHistories: guard(setVideoHistories),
      setBindingVideoId: guard(setBindingVideoId), setSelectingFirstFrameId: guard(setSelectingFirstFrameId),
    };
  }, [scope]);

  return {
    imageTasks, videoTasks, voiceTasks,
    videoHistories, bindingVideoId,
    bindingStoryboardId, selectingStoryboardId,
    selectingFirstFrameId, mediaAbort, currentMediaScope,
    isCurrent,
    reloadMediaTasks, reloadVideoTasks, reloadImageTasks, loadImageCandidates, loadVideoCandidates,
    followImageExecution, followVideoExecution, generateImage, regenerateImage, cancelImage,
    selectFirstFrame, cancelVideo, retryVideo, bindVideoResult, generateVoice,
    ...scopedSetters,
  };
}
