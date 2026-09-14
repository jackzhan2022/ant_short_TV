import { useCallback, useEffect, useRef, useState } from 'react';
import { aiExecutionTaskService } from '@/services/ai-execution/task';

export function conflictingExecutionId(error: unknown): number | undefined {
  const failure = error as {
    info?: { errorCode?: string; data?: { executionId?: number } };
    response?: { data?: { errorCode?: string; data?: { executionId?: number } } };
  } | null;
  const body = failure?.info ?? failure?.response?.data;
  const id = body?.data?.executionId;
  return body?.errorCode === 'ASSET_EXTRACTION_CONFLICT' &&
    typeof id === 'number' && Number.isSafeInteger(id) && id > 0
    ? id : undefined;
}

export function useAssetExtractionTracking(
  projectId: number,
  onComplete: (task: API.AiExecutionResponse) => Promise<void>,
  onError: (error: unknown) => void,
) {
  const tenantId = Number(localStorage.getItem('currentTenantId'));
  const key = `asset-extraction:${tenantId}:${projectId}`;
  const callbacks = useRef({ onComplete, onError });
  callbacks.current = { onComplete, onError };
  const [version, setVersion] = useState(0);
  const [task, setTask] = useState<API.AiExecutionResponse>();
  const [busy, setBusy] = useState(false);

  const follow = useCallback((executionId: number) => {
    sessionStorage.setItem(key, String(executionId));
    setTask({ id: executionId, status: 'PENDING' });
    setBusy(true);
    setVersion((value) => value + 1);
  }, [key]);

  useEffect(() => {
    setBusy(false);
    setTask(undefined);
    const id = Number(sessionStorage.getItem(key));
    if (!Number.isSafeInteger(id) || id <= 0) return;
    const controller = new AbortController();
    setTask({ id, status: 'PENDING' });
    setBusy(true);
    aiExecutionTaskService.poll(tenantId, id, setTask, 1500, controller.signal)
      .then(async (terminal) => {
        if (controller.signal.aborted) return;
        setTask(terminal);
        sessionStorage.removeItem(key);
        await callbacks.current.onComplete(terminal);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) callbacks.current.onError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [key, tenantId, version]);

  return { task, busy, follow };
}
