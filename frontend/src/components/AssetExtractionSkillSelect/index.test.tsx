import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import AssetExtractionSkillSelect from './index';

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: mocks.request }));

beforeEach(() => {
  mocks.request.mockReset();
  mocks.request.mockResolvedValue({ data: [
    { code: 'short-drama-asset-recognition-framework', name: '默认', description: '默认资产规则' },
    { code: 'short-drama-asset-recognition-live-action', name: '仿真人短剧资产提取', description: '完整正负向提示词与4:3道具图' },
  ] });
});

it('loads system choices and submits a stable skill code', async () => {
  const onChange = vi.fn();
  render(<AssetExtractionSkillSelect onChange={onChange} />);
  await screen.findByText('默认资产规则');
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '资产提取方案' }));
  fireEvent.click(await screen.findByText('仿真人短剧资产提取'));
  expect(onChange).toHaveBeenCalledWith('short-drama-asset-recognition-live-action');
  expect(mocks.request).toHaveBeenCalledWith('/api/projects/asset-extraction-skills');
});

it('shows a recoverable failure without replacing the selected code', async () => {
  mocks.request.mockRejectedValueOnce(new Error('offline'));
  render(<AssetExtractionSkillSelect value="short-drama-asset-recognition-live-action" onChange={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: '重新加载' }));
  await screen.findByText('完整正负向提示词与4:3道具图');
  await waitFor(() => expect(mocks.request).toHaveBeenCalledTimes(2));
});
