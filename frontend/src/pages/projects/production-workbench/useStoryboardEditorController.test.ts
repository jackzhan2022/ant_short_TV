import { useLayoutEffect } from 'react';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import useStoryboardEditorController, { createStoryboardEditor } from './useStoryboardEditorController';
import type { StoryboardShot } from './service';

const api = vi.hoisted(() => ({ updateStoryboard: vi.fn(), replaceStoryboardAssetReferences: vi.fn() }));
vi.mock('./service', () => api);

const pending = () => {
  let resolve!: (value: unknown) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

const setup = () => {
  api.updateStoryboard.mockReset().mockResolvedValue({ data: {} });
  let shot = { id: 1, episodeNo: 1, shotNo: 1, videoPrompt: '原内容', visualDescription: '画面' } as StoryboardShot;
  const callbacks = {
    readShot: () => shot,
    patchShot: vi.fn((_id: number, values: Partial<StoryboardShot>) => { shot = { ...shot, ...values }; }),
    refresh: vi.fn().mockResolvedValue(true),
    message: { success: vi.fn(), error: vi.fn() },
  };
  const editor = createStoryboardEditor(10, callbacks);
  editor.loadDrafts([shot]);
  return { editor, callbacks, shot };
};

describe('storyboard editor ownership', () => {
  it('serializes writes for one storyboard and preserves a newer edit when the old write fails', async () => {
    const { editor, callbacks, shot } = setup();
    const first = pending();
    api.updateStoryboard.mockReturnValueOnce(first.promise);
    const one = editor.saveFields(shot, { videoPrompt: '第一次' });
    const two = editor.saveFields(shot, { videoPrompt: '第二次' });
    await vi.waitFor(() => expect(api.updateStoryboard).toHaveBeenCalledTimes(1));
    first.reject(new Error('offline'));
    await Promise.all([one, two]);
    expect(editor.drafts.get(1).videoPrompt).toBe('第二次');
    expect(callbacks.readShot().videoPrompt).toBe('第二次');
    expect(editor.getStatus(1)).toBe('saved');
    expect(api.updateStoryboard).toHaveBeenCalledTimes(2);
  });

  it('retains failed input, exposes an error and retries the same draft', async () => {
    const { editor, shot } = setup();
    api.updateStoryboard.mockRejectedValueOnce(new Error('offline'));
    await editor.saveFields(shot, { videoPrompt: '保留输入' });
    expect(editor.drafts.get(1).videoPrompt).toBe('保留输入');
    expect(editor.getStatus(1)).toBe('error');
    await editor.retry(1);
    expect(editor.getStatus(1)).toBe('saved');
    expect(api.updateStoryboard).toHaveBeenLastCalledWith(10, 1, expect.objectContaining({ videoPrompt: '保留输入' }));
  });

  it('keeps the queue alive after card unsubscribe and does not block a different card', async () => {
    const { editor, shot } = setup();
    const first = pending();
    api.updateStoryboard.mockReturnValueOnce(first.promise);
    const notify = vi.fn();
    const unsubscribe = editor.subscribeStatus(1, notify);
    const saving = editor.saveFields(shot, { videoPrompt: '离开卡片前编辑' });
    unsubscribe();
    await editor.persist(2, { visualDescription: '另一张卡片画面', videoPrompt: '另一张卡片' });
    expect(api.updateStoryboard).toHaveBeenCalledWith(10, 2, expect.objectContaining({ videoPrompt: '另一张卡片' }));
    first.resolve({ data: {} });
    await saving;
    expect(editor.getStatus(1)).toBe('saved');
    expect(editor.drafts.get(1).videoPrompt).toBe('离开卡片前编辑');
  });

  it('does not write page state or display completion after the project scope is disposed', async () => {
    const { editor, shot, callbacks } = setup();
    const first = pending();
    api.updateStoryboard.mockReturnValueOnce(first.promise);
    const saving = editor.saveFields(shot, { videoPrompt: '提交内容' });
    await Promise.resolve();
    editor.dispose();
    callbacks.patchShot.mockClear();
    first.resolve({ data: {} });
    await saving;
    expect(callbacks.patchShot).not.toHaveBeenCalled();
    expect(callbacks.message.success).not.toHaveBeenCalled();
    expect(editor.drafts.get(1).videoPrompt).toBe('提交内容');
  });

  it('retries the latest script fields and confirms only the script sent to the server', async () => {
    const { editor, shot } = setup();
    api.updateStoryboard.mockRejectedValueOnce(new Error('offline'));
    await editor.saveFields(shot, { videoPrompt: '保留提示词' });
    editor.updateDraft(1, { scriptText: '▲新场景\n新画面\n旁白：新对白' });
    await editor.retry(1);
    expect(api.updateStoryboard).toHaveBeenLastCalledWith(10, 1, expect.objectContaining({
      scene: '新场景', visualDescription: '新画面', dialogue: '旁白：新对白', videoPrompt: '保留提示词',
    }));
    expect(editor.getStatus(1)).toBe('saved');
    editor.loadDrafts([{ ...shot, scene: '新场景', visualDescription: '新画面', dialogue: '旁白：新对白', videoPrompt: '保留提示词' }]);
    expect(editor.drafts.get(1).scriptText).toBe('▲新场景\n新画面\n旁白：新对白');
  });

  it('does not mark an unsubmitted script draft as saved when a prompt-only save succeeds', async () => {
    const { editor, shot } = setup();
    editor.updateDraft(1, { scriptText: '尚未提交的新画面' });
    await editor.saveFields(shot, { videoPrompt: '已保存提示词' });
    expect(api.updateStoryboard).toHaveBeenLastCalledWith(10, 1, expect.objectContaining({ visualDescription: '画面' }));
    expect(editor.sources.current[1].scriptText).toBe('画面');
    expect(editor.getStatus(1)).toBe('dirty');
    editor.loadDrafts([{ ...shot, videoPrompt: '已保存提示词' }]);
    expect(editor.drafts.get(1).scriptText).toBe('尚未提交的新画面');
  });

  it('keeps a script edited during retry dirty after the older submitted script completes', async () => {
    const { editor, shot } = setup();
    api.updateStoryboard.mockRejectedValueOnce(new Error('offline'));
    await editor.saveFields(shot, { videoPrompt: '重试提示词' });
    editor.updateDraft(1, { scriptText: '重试时画面' });
    const write = pending();
    api.updateStoryboard.mockReturnValueOnce(write.promise);
    const retrying = editor.retry(1);
    await Promise.resolve();
    editor.updateDraft(1, { scriptText: '重试之后的新画面' });
    write.resolve({ data: {} });
    await retrying;
    expect(editor.sources.current[1].scriptText).toBe('重试时画面');
    expect(editor.getStatus(1)).toBe('dirty');
    editor.loadDrafts([{ ...shot, visualDescription: '重试时画面', videoPrompt: '重试提示词' }]);
    expect(editor.drafts.get(1).scriptText).toBe('重试之后的新画面');
  });

  it('isolates a departing editor during layout cleanup while allowing its pending blur save to finish', async () => {
    const { callbacks: oldCallbacks, shot } = setup();
    const newCallbacks = {
      readShot: vi.fn(() => ({ ...shot, visualDescription: '新项目画面' })),
      patchShot: vi.fn(), refresh: vi.fn().mockResolvedValue(true),
      message: { success: vi.fn(), error: vi.fn() },
    };
    let departingSave: Promise<boolean> | undefined;
    const view = renderHook(({ project, callbacks }) => {
      const editor = useStoryboardEditorController(project, callbacks);
      useLayoutEffect(() => {
        editor.loadDrafts([shot]);
        return () => {
          // Cleanup runs after the new render but before the old hook's passive cleanup.
          departingSave = editor.saveFields(shot, { videoPrompt: '旧项目待保存提示词' });
        };
      }, [editor]);
      return editor;
    }, { initialProps: { project: 10, callbacks: oldCallbacks } });
    view.rerender({ project: 20, callbacks: newCallbacks });
    await act(async () => { await departingSave; });
    expect(api.updateStoryboard).toHaveBeenLastCalledWith(10, 1, expect.objectContaining({
      visualDescription: '画面', videoPrompt: '旧项目待保存提示词',
    }));
    expect(newCallbacks.readShot).not.toHaveBeenCalled();
    expect(newCallbacks.patchShot).not.toHaveBeenCalled();
    expect(newCallbacks.refresh).not.toHaveBeenCalled();
    expect(newCallbacks.message.success).not.toHaveBeenCalled();
    view.unmount();
    await act(async () => { await departingSave; });
  });
});
