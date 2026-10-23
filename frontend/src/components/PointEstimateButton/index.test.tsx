import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearCurrentTenantId,
  setCurrentTenantId,
} from '@/services/account-team/auth';
import {
  pointEstimatedRequest,
  type PointEstimate,
} from '@/services/point-estimate';
import PointEstimateButton from '.';
import PointEstimateModal from './Modal';

const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: mocks.request }));
const quote = (points = 10, sufficient = true): PointEstimate => ({
  estimatedPoints: points,
  availablePoints: 100,
  paidAvailablePoints: 80,
  sufficient,
  lines: [{ label: '生成图片', points, estimated: false, trialEligible: true }],
  notice: '实际按模型用量结算',
});
const operations = (count = 1) => [
  { url: '/api/free-creations', data: { parameters: { count } } },
];
const response = (points = 10, sufficient = true) => ({
  success: true,
  data: quote(points, sufficient),
});
function deferred() {
  let resolve!: (value: ReturnType<typeof response>) => void;
  const promise = new Promise<ReturnType<typeof response>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const advance = (ms = 400) =>
  act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
const settle = (work: () => void) =>
  act(async () => {
    work();
    await Promise.resolve();
  });

beforeEach(() => {
  vi.useFakeTimers();
  mocks.request.mockReset().mockResolvedValue(response());
  localStorage.clear();
  setCurrentTenantId(7);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('live button point estimates', () => {
  it('debounces parameters and blocks clicks until the latest read-only quote succeeds', async () => {
    const pending = deferred();
    mocks.request.mockReturnValue(pending.promise);
    const submit = vi.fn();
    const view = render(
      <PointEstimateButton operations={operations()} onClick={submit}>
        生成图片
      </PointEstimateButton>,
    );
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
    expect(screen.getByText(/预估中/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /生成图片/ }));
    await advance(200);
    view.rerender(
      <PointEstimateButton operations={operations(4)} onClick={submit}>
        生成图片
      </PointEstimateButton>,
    );
    await advance(399);
    expect(mocks.request).not.toHaveBeenCalled();
    await advance(1);
    expect(mocks.request).toHaveBeenCalledWith(
      '/api/tenants/7/points/estimate',
      expect.objectContaining({
        method: 'POST',
        data: { operations: operations(4) },
        signal: expect.any(AbortSignal),
      }),
    );
    expect(submit).not.toHaveBeenCalled();
    await settle(() => pending.resolve(response(40)));
    expect(
      screen.getByRole('button', { name: '生成图片' }),
    ).toHaveAccessibleDescription(/预计 40 积分/);
    expect(screen.getByRole('button', { name: '生成图片' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: /生成图片/ }));
    expect(submit).toHaveBeenCalledOnce();
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });

  it('invalidates a ready quote immediately and ignores a stale response even when transport ignores abort', async () => {
    const first = deferred(),
      second = deferred();
    mocks.request
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const view = render(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    const firstSignal = mocks.request.mock.calls[0][1].signal;
    view.rerender(
      <PointEstimateButton operations={operations(2)}>
        生成图片
      </PointEstimateButton>,
    );
    expect(firstSignal.aborted).toBe(true);
    await advance();
    await settle(() => second.resolve(response(20)));
    await settle(() => first.resolve(response(10)));
    expect(
      screen.getByRole('button', { name: '生成图片' }),
    ).toHaveAccessibleDescription(/预计 20 积分/);
    expect(screen.getByRole('button', { name: '生成图片' })).toBeEnabled();
    view.rerender(
      <PointEstimateButton operations={operations(4)}>
        生成图片
      </PointEstimateButton>,
    );
    expect(screen.queryByText(/预计 20/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
  });

  it('discards the old team quote and blocks stale explicit team props', async () => {
    const view = render(
      <PointEstimateButton tenantId={7} operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeEnabled();
    await settle(() => setCurrentTenantId(8));
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
    await advance();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    view.rerender(
      <PointEstimateButton tenantId={8} operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(mocks.request.mock.calls[1][0]).toBe(
      '/api/tenants/8/points/estimate',
    );
    view.rerender(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await settle(clearCurrentTenantId);
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
    await advance();
    expect(mocks.request).toHaveBeenCalledTimes(2);
  });

  it('keeps failures disabled and provides an explicit quote retry', async () => {
    mocks.request
      .mockRejectedValueOnce(new Error('模型价格未配置'))
      .mockResolvedValue(response(15));
    render(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button', { name: '生成图片' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '重新预估积分' }));
    expect(screen.getByRole('button', { name: '生成图片' })).toBeDisabled();
    await advance();
    expect(screen.getByRole('button', { name: '生成图片' })).toBeEnabled();
  });

  it.each([
    null,
    undefined,
    Number.NaN,
    -1,
  ])('rejects a malformed amount %s instead of enabling payment', async (amount) => {
    mocks.request.mockResolvedValue({
      success: true,
      data: { ...quote(), estimatedPoints: amount },
    });
    render(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
    expect(screen.getByText(/预估失败/)).toBeInTheDocument();
  });

  it('blocks insufficient points, but permits a genuinely free action without estimating', async () => {
    mocks.request.mockResolvedValue(response(20, false));
    const view = render(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button', { name: '生成图片' })).toBeDisabled();
    mocks.request.mockResolvedValue(response(20, true));
    fireEvent.click(screen.getByRole('button', { name: '重新预估积分' }));
    await advance();
    expect(screen.getByRole('button', { name: '生成图片' })).toBeEnabled();
    view.rerender(
      <PointEstimateButton operations={[]}>取消任务</PointEstimateButton>,
    );
    expect(screen.getByRole('button', { name: '取消任务' })).toBeEnabled();
    await advance();
    expect(mocks.request).toHaveBeenCalledTimes(2);
  });

  it('does not quote incomplete, disabled or hidden actions', async () => {
    const view = render(
      <PointEstimateButton operations={undefined}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button')).toBeDisabled();
    view.rerender(
      <PointEstimateButton operations={operations()} disabled>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    view.rerender(
      <PointEstimateButton operations={operations()} estimateEnabled={false}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(mocks.request).not.toHaveBeenCalled();
  });

  it('refreshes other quotes after a paid submission changes available points', async () => {
    mocks.request.mockImplementation(async (url) =>
      url.endsWith('/points/estimate') ? response() : { success: true },
    );
    render(
      <PointEstimateButton operations={operations()}>
        生成图片
      </PointEstimateButton>,
    );
    await advance();
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeEnabled();
    await act(async () => {
      await pointEstimatedRequest('/api/free-creations', {
        method: 'POST',
        pointEstimateConfirmed: true,
      });
    });
    expect(screen.getByRole('button', { name: /生成图片/ })).toBeDisabled();
    await advance();
    expect(
      mocks.request.mock.calls.filter(([url]) =>
        url.endsWith('/points/estimate'),
      ),
    ).toHaveLength(2);
  });

  it('disables modal submission while loading and aborts the quote when closed', async () => {
    const pending = deferred();
    mocks.request.mockReturnValue(pending.promise);
    const submit = vi.fn();
    const view = render(
      <PointEstimateModal
        open
        title="批量生成"
        okText="开始生成"
        operations={operations()}
        onOk={submit}
      />,
    );
    await advance();
    fireEvent.click(screen.getByRole('button', { name: /开始生成/ }));
    expect(submit).not.toHaveBeenCalled();
    view.rerender(
      <PointEstimateModal
        open={false}
        operations={operations()}
        onOk={submit}
      />,
    );
    expect(mocks.request.mock.calls[0][1].signal.aborted).toBe(true);
    await settle(() => pending.resolve(response()));
    expect(submit).not.toHaveBeenCalled();
  });
});
