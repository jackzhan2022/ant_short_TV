import { Button, Modal } from 'antd';
import { type ReactNode, useState } from 'react';
import PointEstimateButton from '@/components/PointEstimateButton';
import type { PointEstimateOperation } from '@/services/point-estimate';
import styles from './StoryboardVideoFailure.module.css';
import type { AiVideoTask } from './service';

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
  children,
}: {
  storyboardNo: number;
  message: string;
  timedOut?: boolean;
  onClose: () => void;
  onRetry?: () => void;
  retryOperations?: PointEstimateOperation[];
  children?: ReactNode;
}) {
  const [detailsOpen, setDetailsOpen] = useState(false);
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
      </div>
      <Button
        type="text"
        size="small"
        className={styles.detailsButton}
        aria-label={`查看分镜${storyboardNo}错误详情`}
        onClick={() => setDetailsOpen(true)}
      >
        查看错误详情
      </Button>
      {onRetry ? (
        <PointEstimateButton
          compact
          operations={retryOperations}
          className={styles.retry}
          onClick={onRetry}
          aria-label={`重新生成分镜${storyboardNo}视频`}
        >
          重新生成
        </PointEstimateButton>
      ) : null}
      <Modal
        title={`分镜${storyboardNo} · 错误详情`}
        open={detailsOpen}
        onCancel={() => setDetailsOpen(false)}
        footer={null}
        width={640}
        centered
        destroyOnHidden
      >
        <p className={styles.details}>{message}</p>
        {children}
      </Modal>
    </section>
  );
}
