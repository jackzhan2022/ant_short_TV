import { useEffect, useRef, useState } from 'react';
import { type AvatarRecovery, queryAvatarRecovery } from './avatar-recovery-service';

export default function StoryboardAvatarRecovery({ projectId, taskId, onReady, onBusyChange }: {
  projectId: number;
  taskId: number;
  onReady: (taskId: number) => void | Promise<void>;
  onBusyChange?: (busy: boolean) => void;
}) {
  const [recovery, setRecovery] = useState<AvatarRecovery | null>(null);
  const [queryError, setQueryError] = useState(false);
  const callbacks = useRef({ onReady, onBusyChange });
  callbacks.current = { onReady, onBusyChange };

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const controller = new AbortController();
    setRecovery(null);
    setQueryError(false);
    const refresh = async () => {
      try {
        const job = await queryAvatarRecovery(projectId, taskId, controller.signal);
        if (!active) return;
        setRecovery(job);
        setQueryError(false);
        const busy = job?.status === 'PENDING' || job?.status === 'RUNNING';
        callbacks.current.onBusyChange?.(busy);
        if (job?.retryTaskId && job.status === 'SUCCEEDED') {
          await callbacks.current.onReady(job.retryTaskId);
        } else if (busy) {
          timer = setTimeout(() => void refresh(), 2000);
        }
      } catch {
        if (!active) return;
        setQueryError(true);
        timer = setTimeout(() => void refresh(), 3000);
      }
    };
    void refresh();
    return () => {
      active = false;
      controller.abort();
      if (timer) clearTimeout(timer);
      callbacks.current.onBusyChange?.(false);
    };
  }, [projectId, taskId]);

  if (!recovery && !queryError) return null;
  return (
    <div role="status" aria-live="polite" style={{ marginTop: 12 }}>
      <p style={{ margin: 0 }}>{queryError ? '素材准备状态刷新失败，正在重试…' : recovery?.phase}</p>
      {recovery?.errorMessage ? <p style={{ margin: '6px 0 0' }}>{recovery.errorMessage}</p> : null}
      {recovery && ['PENDING', 'RUNNING'].includes(recovery.status) ? (
        <p style={{ margin: '6px 0 0', fontSize: 12 }}>素材就绪后将自动重新生成一次，重试按正常积分规则结算。</p>
      ) : null}
    </div>
  );
}
