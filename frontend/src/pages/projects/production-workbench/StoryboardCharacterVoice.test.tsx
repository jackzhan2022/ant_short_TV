import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import type { StoryboardAssetReference } from './service';

vi.mock('@/pages/asset-library/audio-service', () => ({
  queryStoryboardCharacterVoice: vi.fn().mockResolvedValue({ success: true, data: { configured: false, source: 'NONE' } }),
}));

const mocks = vi.hoisted(() => ({ props: vi.fn() }));
vi.mock('./CharacterVoiceModal', () => ({ default: (props: any) => {
  mocks.props(props);
  return <section aria-label="分镜角色音色弹窗"><button type="button" onClick={() => props.onSaved({})}>保存分镜音色</button></section>;
} }));
vi.mock('@/components/LazyMediaImage', () => ({ default: ({ alt }: { alt: string }) => <span>{alt}</span> }));

it('opens voice configuration for the bound character shape without replacing visual bindings', async () => {
  const onChange = vi.fn();
  const onVoice = vi.fn();
  const reference: StoryboardAssetReference = { id: 1, assetType: 'CHARACTER', assetId: 10, assetName: '陈志国', variantId: 100,
    variantName: '老年形态', referenceRole: 'MAIN', sortOrder: 0, resolutionStatus: 'RESOLVED', sourceType: 'MANUAL', lockedByUser: true };
  render(<StoryboardAssetReferenceEditor projectId={2} storyboardId={301} storyboardNo={1} references={[reference]}
    characters={[]} scenes={[]} props={[]} onChange={onChange} onVoice={onVoice} onGenerateImage={vi.fn()} generatingReferenceKeys={new Set()} />);
  fireEvent.click(screen.getByRole('button', { name: '分镜1陈志国 老年形态配置音色' }));
  await screen.findByRole('region', { name: '分镜角色音色弹窗' });
  expect(mocks.props).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 2, characterId: 10,
    storyboard: { id: 301, variantId: 100, variantName: '老年形态' } }));
  fireEvent.click(screen.getByRole('button', { name: '保存分镜音色' }));
  await waitFor(() => expect(screen.queryByRole('region', { name: '分镜角色音色弹窗' })).not.toBeInTheDocument());
  expect(onChange).not.toHaveBeenCalled();
  expect(onVoice).not.toHaveBeenCalled();
});
