import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    expect(await screen.findByText('25')).toBeInTheDocument();
    expect(screen.getByText('当前可用：100 积分')).toBeInTheDocument();
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
    await screen.findByText('25');
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(mocks.cancelled).toHaveBeenCalled());
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.submitted).not.toHaveBeenCalled();
    expect(mocks.error).not.toHaveBeenCalled();
  });
});
