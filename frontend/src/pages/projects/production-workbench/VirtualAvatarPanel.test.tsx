import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import VirtualAvatarPanel from './VirtualAvatarPanel';
const mocks = vi.hoisted(() => ({ candidates: vi.fn(), create: vi.fn(), retry: vi.fn() }));
vi.mock('./virtual-avatar-service', () => ({ queryAvatarCandidates: mocks.candidates, createAvatarTasks: mocks.create, retryAvatarTask: mocks.retry }));
beforeEach(() => {
  vi.clearAllMocks();
  mocks.candidates.mockResolvedValue([
    { id: 11, characterName: '角色甲', variantName: '日常' },
    { id: 22, characterName: '角色乙', variantName: '礼服' },
    { id: 33, characterName: '角色乙', variantName: '已入库', assetId: 'asset-3', status: 'SUCCEEDED' },
  ]);
  mocks.create.mockResolvedValue([{ id: 1, variantId: 11, status: 'QUEUED' }, { id: 2, variantId: 22, status: 'QUEUED' }]);
});
it('submits selected shapes across characters and excludes ready assets', async () => {
  render(<VirtualAvatarPanel projectId={1} onChanged={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: '批量虚拟人像入库' }));
  fireEvent.click(await screen.findByLabelText('选择角色甲 · 日常'));
  fireEvent.click(screen.getByLabelText('选择角色乙 · 礼服'));
  expect(screen.getByLabelText('选择角色乙 · 已入库')).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: /提交.*2.*张/ }));
  await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(1, [11, 22]));
  expect(await screen.findByText('已提交 2 张，未提交 0 张')).toBeInTheDocument();
});
it('reports partial failures without claiming all images succeeded', async () => {
  mocks.create.mockResolvedValue([{ id: 1, variantId: 11 }, { variantId: 22, errorMessage: '原图已失效' }]);
  render(<VirtualAvatarPanel projectId={1} onChanged={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: '批量虚拟人像入库' }));
  fireEvent.click(await screen.findByLabelText('选择角色甲 · 日常'));
  fireEvent.click(screen.getByLabelText('选择角色乙 · 礼服'));
  fireEvent.click(screen.getByRole('button', { name: /提交.*2.*张/ }));
  expect(await screen.findByText('已提交 1 张，未提交 1 张')).toBeInTheDocument();
  expect(screen.getByText(/原图已失效/)).toBeInTheDocument();
});
