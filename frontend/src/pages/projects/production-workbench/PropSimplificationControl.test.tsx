import '@root/tests/mockPointEstimate';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PropSimplificationControl from './PropSimplificationControl';
import { applyPropSimplification, propProductionView, queryPropSimplification, submitPropSimplification, type PropProductionPolicy, type PropSimplificationStatus } from './prop-simplification-service';
import type { PropAsset } from './service';

const messages = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('./prop-simplification-service', async (original) => ({
  ...await original<typeof import('./prop-simplification-service')>(),
  queryPropSimplification: vi.fn(), submitPropSimplification: vi.fn(), applyPropSimplification: vi.fn(), undoPropSimplification: vi.fn(),
}));
vi.mock('antd', () => ({
  Tooltip: ({ children }: { children: ReactNode }) => <>{children}</>,
  App: { useApp: () => ({ message: messages }) },
  Button: ({ children, loading: _loading, icon: _icon, type: _type, ...props }: Record<string, unknown> & { children?: ReactNode }) => <button {...props}>{children}</button>,
  Modal: ({ open, children, footer }: { open: boolean; children: ReactNode; footer: ReactNode }) => open ? <section role="dialog">{children}{footer}</section> : null,
  Alert: ({ title }: { title: ReactNode }) => <div role="status">{title}</div>,
  Checkbox: ({ onChange, ...props }: Record<string, unknown> & { onChange: (event: unknown) => void }) => <input type="checkbox" {...props} onChange={onChange} />,
  Select: ({ options, value, onChange, ...props }: { options: { value: string | number; label: string; disabled?: boolean }[]; value: string | number; onChange: (value: string) => void }) => <select {...props} value={value ?? ''} onChange={(event) => onChange(event.target.value)}>{options.map((item) => <option key={item.value} value={item.value} disabled={item.disabled}>{item.label}</option>)}</select>,
  Popconfirm: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

const ready = (): PropSimplificationStatus => ({
  id: 8, status: 'READY', production: { policies: [], needsReview: false },
  inventory: { props: [{ id: 1, name: '王大山的炒勺' }, { id: 2, name: '李明的炒勺' }, { id: 3, name: '面包车' }], scenes: [], variants: [] },
  decision: { groups: [
    { name: '普通炒勺', mode: 'SHARED', targetId: 1, memberIds: [1, 2], sceneIds: [], variants: [], reason: '同款外观共用', evidence: [{ episodeId: 4, quote: '各自拿起炒勺' }] },
    { name: '面包车', mode: 'INDEPENDENT', targetId: 3, memberIds: [3], sceneIds: [], variants: [], reason: '固定车辆外形', evidence: [] },
  ] },
});
const props = () => ({ projectId: 66, visible: true, disabled: false, revision: '', onChange: vi.fn(), onRefresh: vi.fn().mockResolvedValue(undefined), onTrack: vi.fn() });
const openReview = async () => {
  await waitFor(() => expect(screen.getByRole('button', { name: '道具精简' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '道具精简' }));
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(queryPropSimplification).mockResolvedValue({ data: ready() });
  vi.mocked(applyPropSimplification).mockResolvedValue({ data: { ...ready(), status: 'APPLIED', appliedPlanId: 8 } });
});

describe('prop simplification review', () => {
  it('opens an existing plan without changing assets and applies an edited handling mode', async () => {
    const callbacks = props(); render(<PropSimplificationControl {...callbacks} />);
    await openReview();
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(applyPropSimplification).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('处理方式'), { target: { value: 'INDEPENDENT' } });
    fireEvent.click(screen.getByRole('button', { name: '应用方案' }));
    await waitFor(() => expect(callbacks.onRefresh).toHaveBeenCalled());
    expect(applyPropSimplification).toHaveBeenCalledWith(66, 8, expect.arrayContaining([expect.objectContaining({ mode: 'INDEPENDENT', memberIds: [1, 2] })]));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('keeps excluded groups as independent records in a complete submitted plan', async () => {
    render(<PropSimplificationControl {...props()} />);
    await openReview();
    fireEvent.click(await screen.findByLabelText('采用普通炒勺的建议'));
    fireEvent.click(screen.getByRole('button', { name: '应用方案' }));
    await waitFor(() => expect(applyPropSimplification).toHaveBeenCalled());
    expect(vi.mocked(applyPropSimplification).mock.calls[0][2]).toHaveLength(2);
    expect(vi.mocked(applyPropSimplification).mock.calls[0][2][0].mode).toBe('INDEPENDENT');
  });
  it('retains the review when the server rejects a stale plan', async () => {
    vi.mocked(applyPropSimplification).mockRejectedValue(new Error('资产已变化，请重新分析'));
    const callbacks = props(); render(<PropSimplificationControl {...callbacks} />);
    await openReview();
    fireEvent.click(await screen.findByRole('button', { name: '应用方案' }));
    await waitFor(() => expect(messages.error).toHaveBeenCalledWith('资产已变化，请重新分析'));
    expect(callbacks.onRefresh).not.toHaveBeenCalled(); expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
  it('submits only analysis and tracks the existing background task', async () => {
    vi.mocked(queryPropSimplification).mockResolvedValue({ data: { status: 'IDLE', production: { policies: [], needsReview: false } } });
    vi.mocked(submitPropSimplification).mockResolvedValue({ data: { id: 42, status: 'PENDING' } });
    const callbacks = props(); render(<PropSimplificationControl {...callbacks} />);
    const button = await screen.findByRole('button', { name: '道具精简' });
    await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
    await waitFor(() => expect(callbacks.onTrack).toHaveBeenCalledWith(42));
    expect(applyPropSimplification).not.toHaveBeenCalled();
  });
});

describe('production list grouping', () => {
  it('shows one shared card, keeps original names, and separates scene props', () => {
    const raw = [1, 2, 3, 4].map((id) => ({ id, name: `道具${id}` } as PropAsset));
    const policy = (sourceId: number, mode: 'SHARED' | 'SCENE'): PropProductionPolicy => ({ sourceId, sourceName: `道具${sourceId}`, mode, targetId: 1, name: '普通炒勺', sceneIds: [9], variantMap: {}, allowedVariantIds: [], reason: '' });
    const result = propProductionView(raw, [policy(1, 'SHARED'), policy(2, 'SHARED'), policy(3, 'SCENE')]);
    expect(result.items.map((item) => item.id)).toEqual([1, 4]);
    expect(result.items[0].production?.sourceNames).toEqual(['道具1', '道具2']);
    expect(result.sceneItems.map((item) => item.sourceId)).toEqual([3]);
    expect(raw).toHaveLength(4); expect(raw[0].production).toBeUndefined();
  });
});
