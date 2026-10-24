import '@root/tests/mockPointEstimate';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AssetNormalizationPanel from './AssetNormalizationPanel';
import { queryAssetNormalization, submitAssetNormalization } from './normalization-service';

vi.mock('./normalization-service', () => ({
  queryAssetNormalization: vi.fn(), submitAssetNormalization: vi.fn(),
}));
vi.mock('antd', () => ({
  Tooltip: ({ children }: any) => <>{children}</>,
  App: { useApp: () => ({ message: { error: vi.fn() } }) },
  Button: ({ children, loading: _loading, ...props }: any) => <button {...props}>{children}</button>,
}));

describe('AssetNormalizationPanel', () => {
  it('submits a durable task directly and follows it without candidate review', async () => {
    vi.mocked(queryAssetNormalization).mockResolvedValue({ data: { status: 'IDLE', blocked: false } });
    vi.mocked(submitAssetNormalization).mockResolvedValue({ data: { id: 91, status: 'PENDING' } });
    const onTrack = vi.fn();
    render(<AssetNormalizationPanel projectId={52} onTrack={onTrack} onBlocked={vi.fn()} onRefresh={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '全剧资产归一' }));
    await waitFor(() => expect(onTrack).toHaveBeenCalledWith(91));
    expect(submitAssetNormalization).toHaveBeenCalledWith(52);
  });
  it('keeps a failed task blocked and exposes retry', async () => {
    vi.mocked(queryAssetNormalization).mockResolvedValue({ data: { status: 'FAILED', blocked: true, executionId: 90 } });
    const onBlocked = vi.fn();
    render(<AssetNormalizationPanel projectId={52} onTrack={vi.fn()} onBlocked={onBlocked} onRefresh={vi.fn()} />);
    expect(await screen.findByRole('button', { name: '重试资产归一' })).toBeEnabled();
    expect(onBlocked).toHaveBeenCalledWith(true, expect.stringContaining('请在上方重试资产归一'));
    expect(screen.getByText(/完成后可编辑和生成/)).toBeInTheDocument();
  });
  it('explains status read failures and unlocks after a successful retry', async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(queryAssetNormalization).mockRejectedValueOnce(new Error('network'))
        .mockResolvedValue({ data: { status: 'SUCCEEDED', blocked: false } });
      const onBlocked = vi.fn();
      await act(async () => {
        render(<AssetNormalizationPanel projectId={52} onTrack={vi.fn()} onBlocked={onBlocked} onRefresh={vi.fn()} />);
      });
      expect(onBlocked).toHaveBeenLastCalledWith(true, expect.stringContaining('归一状态读取失败'));
      await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
      expect(onBlocked).toHaveBeenLastCalledWith(false, undefined);
    } finally {
      vi.useRealTimers();
    }
  });
});
