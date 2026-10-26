import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import type { CharacterAsset, StoryboardAssetReference } from './service';
import type { EffectiveStoryboardVoice } from '@/pages/asset-library/audio-service';

const mocks = vi.hoisted(() => ({ current: vi.fn() }));
vi.mock('@/pages/asset-library/audio-service', () => ({ queryStoryboardCharacterVoice: mocks.current }));
vi.mock('@/components/LazyMediaImage', () => ({ default: () => null }));
vi.mock('./CharacterVoiceModal', () => ({
  default: ({ onSaved, onChanged }: { onSaved: () => void; onChanged: () => void }) => <div>
    <button type="button" onClick={() => { onChanged(); onSaved(); }}>保存音色</button>
    <button type="button" onClick={onChanged}>恢复默认</button>
  </div>,
}));
const audio = { sourceProjectId: 9, sourceProjectName: '音频来源项目', resourceType: 'PROJECT_AUDIO_ASSET' as const,
  resourceId: 51, name: '角色默认.wav', previewUrl: '/voice.wav' };
const reference: StoryboardAssetReference = {
  id: 1, assetType: 'CHARACTER', assetId: 21, assetName: '王晓梅', variantId: 33,
  referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'RESOLVED', sourceType: 'MANUAL', lockedByUser: true,
};
const character = { id: 21, name: '王晓梅' } as CharacterAsset;
const editor = (ref = reference, storyboardId = 301, projectId = 12) => <StoryboardAssetReferenceEditor
  projectId={projectId} storyboardId={storyboardId} storyboardNo={6} references={[ref]} characters={[character]}
  scenes={[]} props={[]} onChange={vi.fn()} onVoice={vi.fn()} onGenerateImage={vi.fn()} generatingReferenceKeys={new Set()} />;
const result = (overridden = false, name = audio.name) => ({ success: true, data: { audio: { ...audio, name }, overridden } });
beforeEach(() => { mocks.current.mockReset(); mocks.current.mockResolvedValue(result()); });
afterEach(cleanup);

describe('storyboard character effective voice', () => {
  it('shows the default voice and source from the effective binding API', async () => {
    render(editor());
    expect(await screen.findByText('角色默认.wav')).toBeInTheDocument();
    expect(screen.getByText(/角色默认音色.*音频来源项目/)).toBeInTheDocument();
    expect(mocks.current).toHaveBeenCalledWith(12, 21, { id: 301, variantId: 33 }, expect.any(AbortSignal));
  });

  it('switches scope and ignores a late response for an old shape', async () => {
    let finish: (value: { success: boolean; data: EffectiveStoryboardVoice }) => void = () => {};
    mocks.current.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    const view = render(editor());
    const oldSignal = mocks.current.mock.calls[0][3];
    mocks.current.mockResolvedValue(result(true, '老年形态.wav'));
    view.rerender(editor({ ...reference, assetId: 22, variantId: 34 }, 302, 13));
    expect(await screen.findByText('老年形态.wav')).toBeInTheDocument();
    expect(screen.getByText(/当前分镜形态覆盖.*音频来源项目/)).toBeInTheDocument();
    expect(oldSignal.aborted).toBe(true);
    await act(async () => finish(result(false, '过期音色.wav')));
    await waitFor(() => expect(mocks.current).toHaveBeenLastCalledWith(13, 22, { id: 302, variantId: 34 }, expect.any(AbortSignal)));
    expect(screen.queryByText('过期音色.wav')).not.toBeInTheDocument();
  });

  it('refreshes after saving and resetting, including an empty character default', async () => {
    render(editor());
    await screen.findByText('角色默认.wav');
    fireEvent.click(screen.getByRole('button', { name: '分镜6王晓梅配置音色' }));
    mocks.current.mockResolvedValue(result(true, '分镜覆盖.wav'));
    fireEvent.click(await screen.findByRole('button', { name: '保存音色' }));
    await screen.findByText('分镜覆盖.wav');
    fireEvent.click(screen.getByRole('button', { name: '分镜6王晓梅配置音色' }));
    mocks.current.mockResolvedValue(result());
    fireEvent.click(await screen.findByRole('button', { name: '恢复默认' }));
    await screen.findByText('角色默认.wav');
    expect(screen.getByText(/角色默认音色.*音频来源项目/)).toBeInTheDocument();
    mocks.current.mockResolvedValue({ success: true, data: { audio: null, overridden: false } });
    fireEvent.click(await screen.findByRole('button', { name: '恢复默认' }));
    await screen.findByText('未配置音色');
    expect(screen.queryByText('分镜覆盖.wav')).not.toBeInTheDocument();
  });

  it.each([
    { label: 'project', ref: reference, storyboardId: 301, projectId: 13 },
    { label: 'storyboard', ref: reference, storyboardId: 302, projectId: 12 },
    { label: 'character', ref: { ...reference, assetId: 22 }, storyboardId: 301, projectId: 12 },
    { label: 'shape', ref: { ...reference, variantId: 34 }, storyboardId: 301, projectId: 12 },
  ])('reloads after switching only the $label', async ({ ref, storyboardId, projectId }) => {
    const view = render(editor());
    await screen.findByText('角色默认.wav');
    mocks.current.mockResolvedValue(result(true, '切换后的音色.wav'));
    view.rerender(editor(ref, storyboardId, projectId));
    await screen.findByText('切换后的音色.wav');
    expect(mocks.current).toHaveBeenCalledTimes(2);
    expect(mocks.current).toHaveBeenLastCalledWith(projectId, ref.assetId,
      { id: storyboardId, variantId: ref.variantId }, expect.any(AbortSignal));
  });

  it('reloads when an optimistic reference is confirmed by autosave with the same client key', async () => {
    mocks.current.mockRejectedValueOnce(new Error('binding not persisted yet'));
    const draft = { ...reference, id: undefined, clientKey: 'draft-voice' };
    const view = render(editor(draft));
    await screen.findByText('音色加载失败');
    view.rerender(editor({ ...draft, id: 99 }));
    await screen.findByText('角色默认.wav');
    expect(mocks.current).toHaveBeenCalledTimes(2);
  });

  it('shows a load error and recovers when switching to the default shape', async () => {
    mocks.current.mockRejectedValueOnce(new Error('offline'));
    const view = render(editor());
    await screen.findByText('音色加载失败');
    expect(screen.queryByText('未配置音色')).not.toBeInTheDocument();
    view.rerender(editor({ ...reference, variantId: null }));
    await screen.findByText('角色默认.wav');
    expect(mocks.current).toHaveBeenLastCalledWith(12, 21, { id: 301, variantId: null }, expect.any(AbortSignal));
  });
});
