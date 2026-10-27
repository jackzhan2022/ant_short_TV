import { Button } from 'antd';
import { history } from '@umijs/max';
import { useState } from 'react';
import {
  isEraseActive,
  type SubtitleEraseTask,
} from './subtitle-erase-service';
import './SubtitleEraseModal.css';

export default function SubtitleEraseStatus({
  tasks,
  onRetry,
}: {
  tasks: SubtitleEraseTask[];
  onRetry: (id: number) => Promise<void>;
}) {
  const [retrying, setRetrying] = useState<number>(),
    [error, setError] = useState('');
  const active = tasks.filter(isEraseActive);
  const shown = active.length
    ? active
    : tasks
        .slice(0, 1)
        .filter((task) => ['FAILED', 'TIMED_OUT'].includes(task.status));
  return (
    <>
      {shown.map((task) => {
        const failed = ['FAILED', 'TIMED_OUT'].includes(task.status);
        return (
          <div
            key={task.id}
            className={`subtitle-erase-task${failed ? ' is-failed' : ''}`}
            role="status"
          >
            <div>
              <strong>
                {failed
                  ? '字幕擦除失败'
                  : task.status === 'PENDING'
                    ? '字幕擦除排队中'
                    : task.phase === 'ERASE_PUBLISH'
                      ? '擦除结果转存中'
                      : '字幕擦除中'}
              </strong>
              <p>
                {failed
                  ? task.errorMessage || '处理失败，可在任务中心重试'
                  : `视频版本 ${task.sourceResultId} · 完成后新增视频版本`}
              </p>
              {error && retrying === undefined ? (
                <p role="alert">{error}</p>
              ) : null}
            </div>
            <Button
              type="link"
              size="small"
              onClick={() => history.push('/tasks')}
            >
              任务中心
            </Button>
            {failed && task.retryable ? (
              <Button
                size="small"
                loading={retrying === task.id}
                disabled={retrying !== undefined}
                onClick={() => {
                  setRetrying(task.id);
                  setError('');
                  void onRetry(task.id)
                    .catch((failure) =>
                      setError(
                        failure instanceof Error ? failure.message : '重试失败',
                      ),
                    )
                    .finally(() => setRetrying(undefined));
                }}
              >
                重试擦除
              </Button>
            ) : null}
          </div>
        );
      })}
    </>
  );
}
