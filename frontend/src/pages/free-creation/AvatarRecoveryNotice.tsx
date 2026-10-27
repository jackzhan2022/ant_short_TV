import { type AvatarRecovery, avatarRecoveryPending } from './avatar-recovery';
import styles from './index.module.css';

export default function AvatarRecoveryNotice({
  recovery,
  onOpen,
}: {
  recovery?: AvatarRecovery;
  onOpen: (id: number) => void;
}) {
  if (!recovery) return null;
  const retryId = recovery.retryCreationId;
  const labels = {
    QUEUED: '排队中',
    RUNNING: '生成中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
  };
  return (
    <div
      className={styles.avatarRecovery}
      role="status"
      data-pending={avatarRecoveryPending(recovery)}
    >
      <span>
        {recovery.status === 'FAILED'
          ? `虚拟人像自动处理未完成：${recovery.errorMessage || recovery.phase}`
          : `${recovery.phase}${recovery.retryStatus ? ` · ${labels[recovery.retryStatus]}` : ''}`}
      </span>
      {retryId ? (
        <button type="button" onClick={() => onOpen(retryId)}>
          查看关联视频
        </button>
      ) : recovery.status !== 'FAILED' ? (
        <small>正在准备虚拟人像素材，完成后自动继续生成</small>
      ) : null}
    </div>
  );
}
