import { act, renderHook } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import type { AiImageTask, AiVideoTask, AiVoiceTask, StoryboardShot } from './service';
import useStoryboardMediaController from './useStoryboardMediaController';

const mocks = vi.hoisted(() => ({
  media: vi.fn(), createImage: vi.fn(), bindVideo: vi.fn(), selectImage: vi.fn(), cancelImage: vi.fn(), cancelVideo: vi.fn(),
  pollVideo: vi.fn(), success: vi.fn(), error: vi.fn(),
}));
vi.mock('antd', () => ({ App: { useApp: () => ({ message: { success: mocks.success, error: mocks.error } }) } }));
vi.mock('@/services/point-estimate', () => ({ isPointEstimateCancelled: () => false }));
vi.mock('@/services/ai-execution/task', () => ({ aiExecutionTaskService: { poll: vi.fn() } }));
vi.mock('./avatar-recovery-service', () => ({ queryRecoveredVideoTask: vi.fn() }));
vi.mock('./video-task-polling', () => ({ pollVideoTask: mocks.pollVideo }));
vi.mock('./service', () => ({
  queryStoryboardMedia: mocks.media, createAiImageTask: mocks.createImage,
  bindAiVideoResultToStoryboard: mocks.bindVideo, cancelAiImageTask: mocks.cancelImage,
  cancelAiVideoTask: mocks.cancelVideo, createAiVoiceTask: vi.fn(), queryAiImageResults: vi.fn(),
  queryAiImageTask: vi.fn(), queryAiVideoResults: vi.fn(), queryAssetVisualWorkspace: vi.fn(),
  regenerateAiImageTask: vi.fn(), regenerateAiVideoTask: vi.fn(), selectAiImageResult: mocks.selectImage,
}));

const shot = { id: 10, visualDescription: '街道' } as StoryboardShot;
const emptyMedia = { data: { imageTasks: [], videoTasks: [], voiceTasks: [], videoHistory: [] } };
const callbacks = () => ({ onStoryboardPatch: vi.fn(), onAssetVisuals: vi.fn() });
const flush = async () => { await act(async () => { await Promise.resolve(); }); };

beforeEach(() => {
  vi.resetAllMocks();
  mocks.media.mockResolvedValue(emptyMedia);
});

it('does not reload media for new callback identities and applies results to the latest callback', async () => {
  let resolveBind!: (value: unknown) => void;
  mocks.bindVideo.mockImplementation(() => new Promise((resolve) => { resolveBind = resolve; }));
  const first = callbacks();
  const view = renderHook(({ handlers }) => useStoryboardMediaController(1, [shot], handlers), {
    initialProps: { handlers: first },
  });
  await flush();
  let binding!: Promise<void>;
  act(() => { binding = view.result.current.bindVideoResult(80); });
  const second = callbacks();
  view.rerender({ handlers: second });
  await act(async () => {
    resolveBind({ data: { id: 80, storyboardId: 10, videoUrl: 'video.mp4', isSelected: true } });
    await binding;
  });
  expect(mocks.media).toHaveBeenCalledTimes(1);
  expect(first.onStoryboardPatch).not.toHaveBeenCalled();
  expect(second.onStoryboardPatch).toHaveBeenCalledExactlyOnceWith(10, {
    currentVideoResultId: 80, currentVideoUrl: 'video.mp4',
  });
  expect(view.result.current.videoHistories[10].data[0].id).toBe(80);
  expect(view.result.current.bindingVideoId).toBeUndefined();
  view.unmount();
});

it('ignores late generation responses and retained actions after leaving and returning to a project', async () => {
  let resolveCreate!: (value: unknown) => void;
  mocks.createImage.mockImplementation(() => new Promise((resolve) => { resolveCreate = resolve; }));
  const handlers = callbacks();
  const view = renderHook(({ project }) => useStoryboardMediaController(project, [shot], handlers), {
    initialProps: { project: 1 },
  });
  await flush();
  const oldGenerate = view.result.current.generateImage;
  const generating = oldGenerate(shot);
  const oldSignal = mocks.media.mock.calls[0][2] as AbortSignal;
  view.rerender({ project: 2 });
  view.rerender({ project: 1 });
  await act(async () => {
    resolveCreate({ data: { id: 99, targetType: 'STORYBOARD', targetId: 10, status: 'PENDING' } });
    await generating;
    await oldGenerate(shot);
  });
  expect(oldSignal.aborted).toBe(true);
  expect(mocks.createImage).toHaveBeenCalledTimes(1);
  expect(view.result.current.imageTasks).toEqual([]);
  expect(mocks.success).not.toHaveBeenCalled();
  expect(mocks.cancelImage).not.toHaveBeenCalled();
  expect(mocks.cancelVideo).not.toHaveBeenCalled();
  view.unmount();
});

it('discards an old video binding without clearing a new project binding', async () => {
  const resolvers: Array<(value: unknown) => void> = [];
  mocks.bindVideo.mockImplementation(() => new Promise((resolve) => { resolvers.push(resolve); }));
  const handlers = callbacks();
  const view = renderHook(({ project }) => useStoryboardMediaController(project, [shot], handlers), {
    initialProps: { project: 1 },
  });
  await flush();
  let oldBinding!: Promise<void>;
  act(() => { oldBinding = view.result.current.bindVideoResult(80, 10); });
  expect(view.result.current.bindingStoryboardId).toBe(10);
  view.rerender({ project: 2 });
  await flush();
  let newBinding!: Promise<void>;
  act(() => { newBinding = view.result.current.bindVideoResult(90, 20); });
  await act(async () => {
    resolvers[0]({ data: { id: 80, storyboardId: 10, videoUrl: 'old.mp4' } });
    await oldBinding;
  });
  expect(view.result.current.bindingVideoId).toBe(90);
  expect(view.result.current.bindingStoryboardId).toBe(20);
  expect(view.result.current.videoHistories).toEqual({});
  expect(handlers.onStoryboardPatch).not.toHaveBeenCalled();
  await act(async () => {
    resolvers[1]({ data: { id: 90, storyboardId: 10, videoUrl: 'new.mp4' } });
    await newBinding;
  });
  expect(handlers.onStoryboardPatch).toHaveBeenCalledExactlyOnceWith(10, {
    currentVideoResultId: 90, currentVideoUrl: 'new.mp4',
  });
  expect(view.result.current.bindingStoryboardId).toBeUndefined();
  view.unmount();
});

it('loads media with a fresh signal after StrictMode effect replay', async () => {
  const view = renderHook(() => useStoryboardMediaController(1, [shot], callbacks()), {
    reactStrictMode: true,
  });
  await flush();
  expect(mocks.media).toHaveBeenCalledTimes(2);
  const first = mocks.media.mock.calls[0][2] as AbortSignal;
  const current = mocks.media.mock.calls[1][2] as AbortSignal;
  expect(first.aborted).toBe(true);
  expect(current.aborted).toBe(false);
  expect(view.result.current.mediaAbort.current?.signal).toBe(current);
  view.unmount();
  expect(current.aborted).toBe(true);
  expect(mocks.cancelVideo).not.toHaveBeenCalled();
});

it('infers pending owners from loaded image tasks and video history for legacy callers', async () => {
  const imageResult = { id: 70, targetType: 'STORYBOARD', targetId: 10, imageUrl: 'frame.png' };
  const videoResult = { id: 80, storyboardId: 20, videoUrl: 'video.mp4' };
  mocks.media.mockResolvedValue({ data: {
    imageTasks: [{ id: 7, targetType: 'STORYBOARD', targetId: 10, results: [imageResult] }],
    videoTasks: [], voiceTasks: [],
    videoHistory: [{ storyboardId: 20, current: 1, pageSize: 8, total: 1, data: [videoResult] }],
  } });
  let resolveImage!: (value: unknown) => void;
  let resolveVideo!: (value: unknown) => void;
  mocks.selectImage.mockImplementation(() => new Promise((resolve) => { resolveImage = resolve; }));
  mocks.bindVideo.mockImplementation(() => new Promise((resolve) => { resolveVideo = resolve; }));
  const view = renderHook(() => useStoryboardMediaController(1, [shot], callbacks()));
  await flush();
  let image!: Promise<void>;
  let video!: Promise<void>;
  act(() => {
    image = view.result.current.selectFirstFrame(70);
    video = view.result.current.bindVideoResult(80);
  });
  expect(view.result.current.selectingStoryboardId).toBe(10);
  expect(view.result.current.bindingStoryboardId).toBe(20);
  await act(async () => {
    resolveImage({ data: imageResult });
    resolveVideo({ data: videoResult });
    await Promise.all([image, video]);
  });
  expect(view.result.current.selectingStoryboardId).toBeUndefined();
  expect(view.result.current.bindingStoryboardId).toBeUndefined();
  view.unmount();
});

it('keeps the latest first-frame pending owner when an earlier selection completes', async () => {
  const resolvers: Array<(value: unknown) => void> = [];
  mocks.selectImage.mockImplementation(() => new Promise((resolve) => { resolvers.push(resolve); }));
  const view = renderHook(() => useStoryboardMediaController(1, [shot], callbacks()));
  await flush();
  let first!: Promise<void>;
  let second!: Promise<void>;
  act(() => {
    first = view.result.current.selectFirstFrame(70, 10);
    second = view.result.current.selectFirstFrame(90, 20);
  });
  await act(async () => {
    resolvers[0]({ data: { id: 70, targetId: 10, imageUrl: 'first.png' } });
    await first;
  });
  expect(view.result.current.selectingFirstFrameId).toBe(90);
  expect(view.result.current.selectingStoryboardId).toBe(20);
  await act(async () => {
    resolvers[1]({ data: { id: 90, targetId: 20, imageUrl: 'second.png' } });
    await second;
  });
  expect(view.result.current.selectingFirstFrameId).toBeUndefined();
  expect(view.result.current.selectingStoryboardId).toBeUndefined();
  view.unmount();
});

it.each([
  { label: 'project', project: 2, shotId: 10 },
  { label: 'storyboard page', project: 1, shotId: 20 },
])('ignores retained setters from an old $label scope', async ({ project, shotId }) => {
  const view = renderHook(({ project, shotId }) => useStoryboardMediaController(project,
    [{ ...shot, id: shotId }], callbacks()), { initialProps: { project: 1, shotId: 10 } });
  await flush();
  const previous = view.result.current;
  view.rerender({ project, shotId });
  await flush();
  const oldUpdate = vi.fn(() => [{ id: 90 } as AiVideoTask]);
  act(() => {
    previous.setImageTasks([{ id: 70 } as AiImageTask]);
    previous.setVideoTasks(oldUpdate);
    previous.setVoiceTasks([{ id: 80 } as AiVoiceTask]);
    previous.setVideoHistories({ 10: { current: 1, pageSize: 8, total: 1, data: [] } });
    previous.setBindingVideoId(90);
    previous.setSelectingFirstFrameId(70);
  });
  expect(view.result.current.imageTasks).toEqual([]);
  expect(view.result.current.videoTasks).toEqual([]);
  expect(view.result.current.voiceTasks).toEqual([]);
  expect(view.result.current.videoHistories).toEqual({});
  expect(view.result.current.bindingVideoId).toBeUndefined();
  expect(view.result.current.selectingFirstFrameId).toBeUndefined();
  expect(oldUpdate).not.toHaveBeenCalled();
  act(() => { view.result.current.setVideoTasks((tasks) => [...tasks, { id: 91 } as AiVideoTask]); });
  expect(view.result.current.videoTasks.map((task) => task.id)).toEqual([91]);
  view.unmount();
});
