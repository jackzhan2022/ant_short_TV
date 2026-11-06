import type { RequestOptions } from '@@/plugin-request/request';
import { request } from '@umijs/max';
import { App } from 'antd';
import { type ReactNode, useLayoutEffect } from 'react';
import PointEstimateContent from '@/components/PointEstimateContent';
import styles from '@/components/PointEstimateContent/index.module.css';
import { formatPoints } from '@/utils/points';

export { formatPoints } from '@/utils/points';

type EstimateModal = Pick<ReturnType<typeof App.useApp>['modal'], 'confirm'>;
let estimateModal: EstimateModal | undefined;

export function registerPointEstimateModal(modal: EstimateModal) {
  estimateModal = modal;
  return () => {
    if (estimateModal === modal) estimateModal = undefined;
  };
}

export function PointEstimateProvider({ children }: { children: ReactNode }) {
  const { modal } = App.useApp();
  useLayoutEffect(() => registerPointEstimateModal(modal), [modal]);
  return children;
}

export type PointEstimateOperation = { url: string; data?: unknown };
export type PointEstimate = {
  estimatedPoints: number;
  availablePoints: number;
  paidAvailablePoints: number;
  sufficient: boolean;
  lines: {
    label: string;
    points: number;
    estimated: boolean;
    trialEligible: boolean;
  }[];
  notice: string;
};

export class PointEstimateCancelledError extends Error {
  constructor() {
    super('已取消本次操作');
    this.name = 'PointEstimateCancelledError';
  }
}

export const isPointEstimateCancelled = (error: unknown) =>
  error instanceof PointEstimateCancelledError;

/** Read-only quote; never reserves points, submits work or opens a confirmation. */
export async function fetchPointEstimate(
  operations: PointEstimateOperation[],
  tenantId: number,
  signal?: AbortSignal,
): Promise<PointEstimate> {
  if (!operations.length) throw new Error('没有可预估的任务');
  if (!tenantId) throw new Error('请先选择当前团队');
  const response = await request<{ success: boolean; data: PointEstimate; errorMessage?: string }>(
    `/api/tenants/${tenantId}/points/estimate`,
    { method: 'POST', data: { operations }, skipErrorHandler: true, signal, timeout: 15000 },
  );
  if (!response.success || !response.data) throw new Error(response.errorMessage || '积分预估失败');
  if (typeof response.data.estimatedPoints !== 'number' || !Number.isFinite(response.data.estimatedPoints) || response.data.estimatedPoints < 0
    || !Array.isArray(response.data.lines) || typeof response.data.sufficient !== 'boolean') {
    throw new Error('积分预估结果不完整');
  }
  return response.data;
}

type Options = RequestOptions & { pointEstimateConfirmed?: boolean };

export function requiresPointEstimate(url: string, options: Options = {}) {
  if ((options.method || 'GET').toUpperCase() !== 'POST') return false;
  if (/^\/api\/tenants\/\d+\/production-tasks\/(?:VIRTUAL_AVATAR|SUBTITLE_ERASE)(?::|%3A)\d+\/retry$/i.test(url)) return false;
  if (url === '/api/projects') {
    return Boolean(
      (
        options.data as { initialScriptContent?: string } | undefined
      )?.initialScriptContent?.trim(),
    );
  }
  return (
    url === '/api/free-creations' ||
    url === '/api/script-review/submissions' ||
    /^\/api\/projects\/\d+\/character-prompt-similarity$/.test(url) ||
    /^\/api\/projects\/\d+\/prop-simplification$/.test(url) ||
    /^\/api\/projects\/\d+\/(?:ai-(?:image|video)-tasks(?:\/\d+\/regenerate)?|asset-image-batches(?:\/\d+\/retry-failed)?|asset-(?:reextraction|normalization)|scripts\/ai-(?:generate|rewrite)|prompts\/ai-generate|storyboards\/ai-breakdown|storyboard-batches|script-analysis\/(?:current\/(?:reanalyze|regenerate-episodes|retry\/[^/]+)|versions\/\d+\/reanalyze)|episodes\/\d+\/(?:summary|assets)\/regenerate)$/.test(
      url,
    ) ||
    /^\/api\/script-review\/(?:projects\/\d+\/tasks|tasks\/\d+\/retry)$/.test(
      url,
    ) ||
    /^\/api\/video-script-decomposition\/(?:batches|episodes\/\d+\/retry)$/.test(
      url,
    ) ||
    /^\/api\/tenants\/\d+\/(?:ai-executions\/\d+|production-tasks\/[^/]+)\/(?:retry|regenerate)$/.test(
      url,
    )
  );
}

function PointIcon() {
  return (
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24"
      fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="m12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7zM20 2v4m-2-2h4" />
    </svg>
  );
}

/** A preview is read-only. Only an explicit confirmation permits the caller to submit. */
export function confirmPointEstimates(
  operations: PointEstimateOperation[],
  tenantId?: number,
): Promise<PointEstimate> {
  if (!operations.length) return Promise.reject(new Error('没有可预估的任务'));
  const tenant = tenantId || Number(localStorage.getItem('currentTenantId'));
  if (!tenant) return Promise.reject(new Error('请先选择当前团队'));
  const modal = estimateModal;
  if (!modal)
    return Promise.reject(new Error('积分预估界面尚未就绪，请稍后重试'));
  const creation = operations.every(({ url }) => url === '/api/projects' || url === '/api/free-creations');
  const buttonClass = `${styles.actionButton} ${styles.confirmButton}`;
  return new Promise((resolve, reject) => {
    let estimate: PointEstimate | undefined;
    let closed = false;
    const dialog = modal.confirm({
      title: creation ? '确认本次创作' : '确认积分消耗',
      icon: null,
      width: 520,
      centered: true,
      closable: true,
      className: styles.dialog,
      styles: { container: { padding: 0, borderRadius: 18, overflow: 'hidden' } },
      focusable: { autoFocusButton: 'cancel' },
      content: <div className={styles.loading} role="status">正在计算本次消耗的积分…</div>,
      okText: '—',
      cancelText: '取消',
      okButtonProps: { disabled: true, loading: true, icon: <PointIcon />, className: buttonClass, 'aria-label': '正在计算积分' },
      cancelButtonProps: { className: styles.actionButton },
      footer: (_, { OkBtn, CancelBtn }) => (
        <div className={styles.footer}>
          <span className={styles.footerNote}>{creation ? '确认后开始创作' : '确认后继续操作'}</span>
          <div className={styles.actions}><CancelBtn /><OkBtn /></div>
        </div>
      ),
      onOk: () => {
        if (!estimate?.sufficient)
          return Promise.reject(new Error('积分预估尚未就绪'));
        closed = true;
        resolve(estimate);
        return undefined;
      },
      onCancel: () => {
        closed = true;
        reject(new PointEstimateCancelledError());
      },
    });
    void fetchPointEstimate(operations, tenant)
      .then((response) => {
        if (closed) return;
        estimate = response;
        dialog.update({
          content: <PointEstimateContent estimate={estimate} />,
          okText: formatPoints(Math.ceil(estimate.estimatedPoints)),
          okButtonProps: {
            disabled: !estimate.sufficient,
            loading: false,
            icon: <PointIcon />,
            className: buttonClass,
            'aria-label': `确认消耗 ${formatPoints(Math.ceil(estimate.estimatedPoints))} 积分并继续`,
          },
        });
      })
      .catch((error: unknown) => {
        if (closed) return;
        closed = true;
        dialog.destroy();
        const failure = error as {
          response?: { data?: { errorMessage?: string } };
          info?: { errorMessage?: string };
        };
        const reason =
          failure?.response?.data?.errorMessage ||
          failure?.info?.errorMessage ||
          (error instanceof Error ? error.message : '请稍后重试');
        reject(new Error(`积分预估失败，未提交任务：${reason}`));
      });
  });
}

export async function pointEstimatedRequest<T>(
  url: string,
  options?: Options,
): Promise<T> {
  if (!options) return request<T>(url);
  const { pointEstimateConfirmed, ...requestOptions } = options;
  if (!pointEstimateConfirmed && requiresPointEstimate(url, options)) {
    const tenantMatch = url.match(/^\/api\/tenants\/(\d+)\//);
    await confirmPointEstimates(
      [{ url, data: options.data }],
      tenantMatch ? Number(tenantMatch[1]) : undefined,
    );
  }
  if (requestOptions.signal?.aborted) throw new DOMException('操作已取消', 'AbortError');
  const response = await request<T>(url, requestOptions);
  if (requiresPointEstimate(url, options)) window.dispatchEvent(new Event('point-estimate-invalidated'));
  return response;
}
