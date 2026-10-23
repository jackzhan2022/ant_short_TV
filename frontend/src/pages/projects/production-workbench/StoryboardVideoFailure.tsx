import { Button } from 'antd';
import type { AiVideoTask } from './service';
import styles from './StoryboardVideoFailure.module.css';
import PointEstimateButton from '@/components/PointEstimateButton';
import type { PointEstimateOperation } from '@/services/point-estimate';

export const videoGenerationError = (task?: AiVideoTask) => {
  if (!task || ['SUCCEEDED', 'CANCELED'].includes(task.status))
    return undefined;
  if (
    task.status !== 'FAILED' &&
    !['FAILED', 'TIMED_OUT'].includes(task.execution?.status || '')
  )
    return undefined;
  return (
    task.errorMessage?.trim() ||
    task.execution?.errorMessage?.trim() ||
    '本次视频生成未完成，请稍后重试。'
  );
};

export default function StoryboardVideoFailure({
  storyboardNo,
  message,
  timedOut = false,
  onClose,
  onRetry,
  retryOperations,
}: {
  storyboardNo: number;
  message: string;
  timedOut?: boolean;
  onClose: () => void;
  onRetry?: () => void;
  retryOperations?: PointEstimateOperation[];
}) {
  return (
    <section
      className={styles.container}
      aria-label={`分镜${storyboardNo}视频生成错误`}
    >
      <Button
        type="text"
        size="small"
        className={styles.close}
        aria-label={`关闭分镜${storyboardNo}视频错误`}
        onClick={onClose}
      >
        ×
      </Button>
      <svg
        className={styles.icon}
        width="48"
        height="48"
        viewBox="0 0 48 48"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="24" cy="24" r="22" fill="currentColor" opacity="0.12" />
        <circle cx="24" cy="24" r="16" stroke="currentColor" strokeWidth="2" />
        <path
          d="m19 19 10 10m0-10L19 29"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
      <div role="alert" className={styles.message}>
        <h3 className={styles.title}>
          {timedOut ? '视频生成超时' : '视频生成失败'}
        </h3>
        <p className={styles.details}>{message}</p>
      </div>
      {onRetry ? (
        <PointEstimateButton operations={retryOperations}
          className={styles.retry}
          onClick={onRetry}
          aria-label={`重新生成分镜${storyboardNo}视频`}
        >
          重新生成
        </PointEstimateButton>
      ) : null}
    </section>
  );
}
