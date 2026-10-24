import '@root/tests/mockPointEstimate';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AssetNormalizationPanel from './AssetNormalizationPanel';
import {
  queryAssetNormalization,
  submitAssetNormalization,
} from './normalization-service';

vi.mock('./normalization-service', () => ({
  queryAssetNormalization: vi.fn(),
  submitAssetNormalization: vi.fn(),
}));
vi.mock('antd', () => ({
  Tooltip: ({ children }: any) => <>{children}</>,
  App: { useApp: () => ({ message: { error: vi.fn() } }) },
  Button: ({ children, loading: _loading, danger: _danger, ...props }: any) => (
    <button {...props}>{children}</button>
  ),
}));

describe('AssetNormalizationPanel', () => {
  it('places the single normalization action in the workbench footer host', async () => {
    vi.mocked(queryAssetNormalization).mockResolvedValue({
      data: {
        status: 'SUCCEEDED',
        blocked: false,
        result: { mergedAssets: 2, splitGroups: 1 },
      },
    });
    const host = document.createElement('div');
    document.body.append(host);
    const view = render(
      <AssetNormalizationPanel
        projectId={52}
        actionHost={host}
        onTrack={vi.fn()}
        onBlocked={vi.fn()}
        onRefresh={vi.fn()}
      />,
    );
    try {
      const button = await within(host).findByRole('button', {
        name: '资产归一',
      });
      expect(button).toHaveAttribute(
        'title',
        expect.stringContaining('合并 2 条'),
      );
      expect(view.container).toBeEmptyDOMElement();
      expect(screen.queryByText(/归一已完成/)).not.toBeInTheDocument();
    } finally {
      view.unmount();
      host.remove();
    }
  });
  it('submits a durable task directly and follows it without candidate review', async () => {
    vi.mocked(queryAssetNormalization).mockResolvedValue({
      data: { status: 'IDLE', blocked: false },
    });
    vi.mocked(submitAssetNormalization).mockResolvedValue({
      data: { id: 91, status: 'PENDING' },
    });
    const onTrack = vi.fn();
    render(
      <AssetNormalizationPanel
        projectId={52}
        onTrack={onTrack}
        onBlocked={vi.fn()}
        onRefresh={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByRole('button', { name: '资产归一' }));
    await waitFor(() => expect(onTrack).toHaveBeenCalledWith(91));
    expect(submitAssetNormalization).toHaveBeenCalledWith(52);
  });
  it('keeps a failed task blocked and exposes retry', async () => {
    vi.mocked(queryAssetNormalization).mockResolvedValue({
      data: { status: 'FAILED', blocked: true, executionId: 90 },
    });
    const onBlocked = vi.fn();
    render(
      <AssetNormalizationPanel
        projectId={52}
        onTrack={vi.fn()}
        onBlocked={onBlocked}
        onRefresh={vi.fn()}
      />,
    );
    expect(
      await screen.findByRole('button', { name: '资产归一' }),
    ).toBeEnabled();
    expect(onBlocked).toHaveBeenCalledWith(
      true,
      expect.stringContaining('请点击底部“资产归一”重试'),
    );
    expect(screen.getByRole('button', { name: '资产归一' })).toHaveAttribute(
      'title',
      expect.stringContaining('归一尚未完成'),
    );
  });
  it('explains status read failures and unlocks after a successful retry', async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(queryAssetNormalization)
        .mockRejectedValueOnce(new Error('network'))
        .mockResolvedValue({ data: { status: 'SUCCEEDED', blocked: false } });
      const onBlocked = vi.fn();
      await act(async () => {
        render(
          <AssetNormalizationPanel
            projectId={52}
            onTrack={vi.fn()}
            onBlocked={onBlocked}
            onRefresh={vi.fn()}
          />,
        );
      });
      expect(onBlocked).toHaveBeenLastCalledWith(
        true,
        expect.stringContaining('归一状态读取失败'),
      );
      await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
      });
      expect(onBlocked).toHaveBeenLastCalledWith(false, undefined);
    } finally {
      vi.useRealTimers();
    }
  });
});
