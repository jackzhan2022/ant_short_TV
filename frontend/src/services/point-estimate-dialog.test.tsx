import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  isPointEstimateCancelled,
  PointEstimateProvider,
  pointEstimatedRequest,
} from './point-estimate';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  submitted: vi.fn(),
  error: vi.fn(),
  cancelled: vi.fn(),
}));
vi.mock('@umijs/max', () => ({ request: mocks.request }));

function Harness() {
  return (
    <App>
      <PointEstimateProvider>
        <button
          type="button"
          onClick={() => {
            void pointEstimatedRequest('/api/projects/3/ai-image-tasks', {
              method: 'POST',
              data: { imageCount: 2 },
            })
              .then(mocks.submitted)
              .catch((error: unknown) => {
                  if (isPointEstimateCancelled(error)) mocks.cancelled();
                  else mocks.error(error);
              });
          }}
        >
          生成两张图片
        </button>
      </PointEstimateProvider>
    </App>
  );
}

describe('point estimate confirmation dialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('currentTenantId', '7');
    mocks.request
      .mockResolvedValueOnce({
        success: true,
        data: {
          estimatedPoints: 24.5,
          availablePoints: 100,
          paidAvailablePoints: 100,
          sufficient: true,
          lines: [
            {
              label: '角色形象图',
              points: 24.5,
              estimated: false,
              trialEligible: true,
            },
          ],
          notice: '实际按模型用量结算，未使用的预占积分会释放。',
        },
      })
      .mockResolvedValue({ success: true, data: { id: 9 } });
  });

  it('shows the server estimate and balance before creating any task', async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成两张图片' }));
    expect(await screen.findByRole('table', { name: '积分费用明细' })).toBeInTheDocument();
    expect(within(screen.getByLabelText('预计消耗')).getByText('25')).toBeInTheDocument();
    expect(within(screen.getByLabelText('当前可用积分')).getByText('100')).toBeInTheDocument();
    expect(screen.getByText('余额充足')).toBeInTheDocument();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '确认消耗并继续' }));
    await waitFor(() => expect(mocks.submitted).toHaveBeenCalled());
    expect(mocks.request).toHaveBeenLastCalledWith(
      '/api/projects/3/ai-image-tasks',
      expect.objectContaining({ data: { imageCount: 2 } }),
    );
  });

  it('leaves points and tasks untouched when the user cancels', async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成两张图片' }));
    await screen.findByRole('table', { name: '积分费用明细' });
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(mocks.cancelled).toHaveBeenCalled());
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.submitted).not.toHaveBeenCalled();
    expect(mocks.error).not.toHaveBeenCalled();
  });

  it('keeps confirmation disabled when the server reports insufficient points', async () => {
    mocks.request.mockReset().mockResolvedValueOnce({
      success: true,
      data: {
        estimatedPoints: 588,
        availablePoints: 100,
        paidAvailablePoints: 0,
        sufficient: false,
        lines: [{ label: '生成视频', points: 588, estimated: true, trialEligible: false }],
        notice: '实际按模型用量结算。',
      },
    });
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成两张图片' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('可用积分不足');
    expect(screen.getByText(/试用积分不可用于视频生成/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认消耗并继续' })).toBeDisabled();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(mocks.cancelled).toHaveBeenCalled());
    expect(mocks.submitted).not.toHaveBeenCalled();
  });
});
