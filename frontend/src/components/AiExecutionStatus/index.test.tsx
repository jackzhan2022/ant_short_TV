import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import AiExecutionStatus from '.';

describe('AiExecutionStatus', () => {
  it('shows canonical progress without provider-specific state', () => {
    render(
      <AiExecutionStatus
        task={{
          status: 'RUNNING',
          phase: 'GENERATE',
          progress: 42,
        }}
      />,
    );

    expect(screen.getByText('处理中')).toBeInTheDocument();
    expect(screen.getByText('GENERATE')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '42');
    expect(screen.queryByText(/provider/i)).not.toBeInTheDocument();
  });

  it('presents normalized failures and retry action', () => {
    const onRetry = vi.fn();
    render(
      <AiExecutionStatus
        task={{
          status: 'FAILED',
          retryable: true,
          errorCode: 'AI_PROVIDER_TIMEOUT',
          errorMessage: '生成服务响应超时',
        }}
        onRetry={onRetry}
      />,
    );

    expect(screen.getByText('生成失败')).toBeInTheDocument();
    expect(screen.getByText('生成服务响应超时')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('offers cancellation only while the task can still run', () => {
    const onCancel = vi.fn();
    const { rerender } = render(
      <AiExecutionStatus task={{ status: 'PENDING' }} onCancel={onCancel} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onCancel).toHaveBeenCalledOnce();

    rerender(
      <AiExecutionStatus task={{ status: 'SUCCEEDED' }} onCancel={onCancel} />,
    );
    expect(screen.queryByRole('button', { name: '取消' })).not.toBeInTheDocument();
  });
});
