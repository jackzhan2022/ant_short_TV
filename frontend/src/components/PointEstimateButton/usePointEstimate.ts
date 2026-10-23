import { useEffect, useState, useSyncExternalStore } from 'react';
import {
  fetchPointEstimate,
  type PointEstimate,
  type PointEstimateOperation,
} from '@/services/point-estimate';

function subscribeTenant(listener: () => void) {
  window.addEventListener('storage', listener);
  window.addEventListener('current-tenant-changed', listener);
  return () => {
    window.removeEventListener('storage', listener);
    window.removeEventListener('current-tenant-changed', listener);
  };
}
const currentTenant = () =>
  Number(localStorage.getItem('currentTenantId')) || undefined;

type QuoteState =
  | { key: string; status: 'ready'; estimate: PointEstimate }
  | { key: string; status: 'error'; error: string };

/** Undefined means incomplete inputs; an empty list means this action does not spend points. */
export default function usePointEstimate(
  operations: PointEstimateOperation[] | undefined,
  { tenantId, enabled = true }: { tenantId?: number; enabled?: boolean } = {},
) {
  const selectedTenant = useSyncExternalStore(
    subscribeTenant,
    currentTenant,
    () => undefined,
  );
  const [revision, setRevision] = useState(0);
  const tenant = tenantId ?? selectedTenant;
  const serialized = JSON.stringify(operations);
  const key = JSON.stringify([tenant, selectedTenant, serialized, revision]);
  const free = operations?.length === 0;
  const scopeMismatch = Boolean(
    selectedTenant && tenantId && tenantId !== selectedTenant,
  );
  const canEstimate =
    enabled && !scopeMismatch && Boolean(tenant && operations?.length);
  const [state, setState] = useState<QuoteState>();

  useEffect(() => {
    const invalidate = () => {
      setState(undefined);
      setRevision((value) => value + 1);
    };
    window.addEventListener('point-estimate-invalidated', invalidate);
    return () =>
      window.removeEventListener('point-estimate-invalidated', invalidate);
  }, []);

  useEffect(() => {
    if (!canEstimate || !serialized || !tenant) {
      setState(undefined);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      void fetchPointEstimate(JSON.parse(serialized), tenant, controller.signal)
        .then((estimate) => {
          if (!controller.signal.aborted)
            setState({ key, status: 'ready', estimate });
        })
        .catch((failure: unknown) => {
          if (controller.signal.aborted) return;
          const error = failure as {
            response?: { data?: { errorMessage?: string } };
            info?: { errorMessage?: string };
          };
          setState({
            key,
            status: 'error',
            error:
              error?.response?.data?.errorMessage ||
              error?.info?.errorMessage ||
              (failure instanceof Error
                ? failure.message
                : '积分预估失败，请重试'),
          });
        });
    }, 400);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [canEstimate, key, serialized, tenant]);

  // Invalidate synchronously during render, before effects run, so an old quote never enables a new request.
  const current = canEstimate && state?.key === key ? state : undefined;
  const estimate = current?.status === 'ready' ? current.estimate : undefined;
  const error = current?.status === 'error' ? current.error : undefined;
  const loading = canEstimate && !current;
  return {
    estimate,
    error,
    loading,
    disabled: !free && (!canEstimate || !estimate?.sufficient),
    reason:
      error ||
      (scopeMismatch
        ? '请等待当前团队加载完成'
        : !tenant
          ? '请先选择当前团队'
          : !operations
            ? '请先完善生成参数'
            : estimate && !estimate.sufficient
              ? '可用积分不足，请充值后重试'
              : undefined),
    refresh: () => {
      setState(undefined);
      setRevision((value) => value + 1);
    },
  };
}
