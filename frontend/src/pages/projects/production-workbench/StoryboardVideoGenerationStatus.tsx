import { Button, Progress } from 'antd';
import styles from './StoryboardVideoGenerationStatus.module.css';
import type { AiVideoTask } from './service';

export const isVideoGenerationActive = (task?: AiVideoTask) => {
  if (!task || ['SUCCEEDED', 'FAILED', 'CANCELED'].includes(task.status))
    return false;
  const status = task.execution?.status;
  if (
    status &&
    ['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMED_OUT'].includes(status)
  )
    return false;
  return (
    status === 'PENDING' ||
    status === 'RUNNING' ||
    ['PENDING', 'SUBMITTING', 'GENERATING'].includes(task.status) ||
    (!task.status && Boolean(task.executionId))
  );
};

export default function StoryboardVideoGenerationStatus({
  storyboardNo,
  task,
  onCancel,
}: {
  storyboardNo: number;
  task: AiVideoTask;
  onCancel: () => void;
}) {
  const progress = Math.min(
    100,
    Math.max(0, task.progress ?? task.execution?.progress ?? 0),
  );
  const phase = task.executionPhase || task.execution?.phase || '';
  const virtualizing = phase === 'AVATAR_PREPARING';
  const pending =
    task.execution?.status === 'PENDING' ||
    (!task.execution && (!task.status || task.status === 'PENDING'));
  const submitting = phase
    ? phase === 'VIDEO_SUBMIT'
    : task.status === 'SUBMITTING';
  const description = virtualizing
    ? '正在准备虚拟人像素材，完成后自动继续生成'
    : submitting
      ? '正在准备并提交视频任务'
      : pending
        ? '任务已创建，正在排队'
        : '视频正在生成，请稍候';

  return (
    <section
      className={styles.container}
      aria-label={`分镜${storyboardNo}视频生成进度`}
    >
      <div className={styles.illustration} aria-hidden="true">
        <span className={styles.glow} />
        <span className={styles.ring} />
        <svg
          width="40"
          height="40"
          viewBox="0 0 48 48"
          fill="none"
          aria-hidden="true"
        >
          <rect
            x="6"
            y="9"
            width="36"
            height="30"
            rx="6"
            stroke="currentColor"
            strokeWidth="2.5"
          />
          <path d="m20 17 12 7-12 7V17Z" fill="currentColor" />
        </svg>
      </div>
      <div className={styles.heading}>
        <output aria-live="polite">
          {virtualizing ? '智能虚拟化中' : '正在生成'}
        </output>
        <span className={styles.dots} aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
      </div>
      <p className={styles.description}>{description}</p>
      <div className={styles.progressRow}>
        <Progress
          className={styles.progress}
          percent={progress}
          status="active"
          showInfo={false}
          strokeColor="var(--app-color-primary)"
          aria-label={`分镜${storyboardNo}视频生成百分比`}
        />
        <span className={styles.percent}>{progress}%</span>
      </div>
      {!virtualizing && (
        <Button
          size="small"
          onClick={onCancel}
          aria-label={`取消分镜${storyboardNo}视频生成`}
        >
          取消生成
        </Button>
      )}
    </section>
  );
}
