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
    const confirm = await screen.findByRole('button', { name: '确认消耗 25 积分并继续' });
    expect(confirm.textContent).toBe('25');
    expect(confirm.querySelector('svg path')).toHaveAttribute('d', 'm12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7zM20 2v4m-2-2h4');
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByLabelText('当前可用积分')).toHaveTextContent('100');
    expect(within(dialog).getByText('余额充足')).toBeInTheDocument();
    expect(within(dialog).queryByLabelText('exclamation-circle')).not.toBeInTheDocument();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    fireEvent.click(confirm);
    await waitFor(() => expect(mocks.submitted).toHaveBeenCalled());
    expect(mocks.request).toHaveBeenLastCalledWith(
      '/api/projects/3/ai-image-tasks',
      expect.objectContaining({ data: { imageCount: 2 } }),
    );
  });

  it('leaves points and tasks untouched when the user cancels', async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成两张图片' }));
    await screen.findByRole('button', { name: '确认消耗 25 积分并继续' });
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(mocks.cancelled).toHaveBeenCalled());
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.submitted).not.toHaveBeenCalled();
    expect(mocks.error).not.toHaveBeenCalled();
  });

  it('keeps confirmation disabled when the server says the balance is insufficient', async () => {
    mocks.request.mockReset().mockResolvedValue({
      success: true,
      data: { estimatedPoints: 24.1, availablePoints: 10, paidAvailablePoints: 5,
        sufficient: false, lines: [], notice: '请核对可用余额' },
    });
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成两张图片' }));
    const confirm = await screen.findByRole('button', { name: '确认消耗 25 积分并继续' });
    expect(confirm).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('可用积分不足');
    fireEvent.click(confirm);
    expect(mocks.request).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(mocks.cancelled).toHaveBeenCalled());
  });
});
