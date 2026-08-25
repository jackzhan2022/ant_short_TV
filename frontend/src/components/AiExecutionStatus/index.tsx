import { ReloadOutlined, StopOutlined } from '@ant-design/icons';
import { Alert, Button, Progress } from 'antd';

export interface AiExecutionStatusProps {
  task: API.AiExecutionResponse;
  busy?: boolean;
  onCancel?: () => void;
  onRetry?: () => void;
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: '等待处理',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '生成失败',
  CANCELED: '已取消',
  TIMED_OUT: '处理超时',
};

export default function AiExecutionStatus({
  task,
  busy = false,
  onCancel,
  onRetry,
}: AiExecutionStatusProps) {
  const status = task.status ?? 'PENDING';
  const canCancel = (status === 'PENDING' || status === 'RUNNING') && onCancel;
  const canRetry =
    (status === 'FAILED' || status === 'TIMED_OUT') && task.retryable && onRetry;

  if (status === 'FAILED' || status === 'TIMED_OUT') {
    return (
      <Alert
        showIcon
        type="error"
        title={STATUS_LABELS[status]}
        description={task.errorMessage || '本次任务未完成'}
        action={
          canRetry ? (
            <Button
              aria-label="重试"
              icon={<ReloadOutlined />}
              loading={busy}
              onClick={onRetry}
              size="small"
            >
              重试
            </Button>
          ) : undefined
        }
      />
    );
  }

  return (
    <div className="grid min-h-20 grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
      <div className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm">
          <span className="font-medium">{STATUS_LABELS[status] ?? status}</span>
          {task.phase ? <span className="text-gray-500">{task.phase}</span> : null}
        </div>
        <Progress
          percent={Math.min(100, Math.max(0, task.progress ?? 0))}
          status={status === 'SUCCEEDED' ? 'success' : 'active'}
          strokeLinecap="square"
        />
      </div>
      {canCancel ? (
        <Button
          aria-label="取消"
          danger
          icon={<StopOutlined />}
          loading={busy}
          onClick={onCancel}
          size="small"
        >
          取消
        </Button>
      ) : null}
    </div>
  );
}
