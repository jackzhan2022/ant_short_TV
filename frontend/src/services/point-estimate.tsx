import type { RequestOptions } from '@@/plugin-request/request';
import { request } from '@umijs/max';
import { App } from 'antd';
import { type ReactNode, useLayoutEffect } from 'react';
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

type Options = RequestOptions & { pointEstimateConfirmed?: boolean };

export function requiresPointEstimate(url: string, options: Options = {}) {
  if ((options.method || 'GET').toUpperCase() !== 'POST') return false;
  if (url === '/api/projects') {
    return Boolean(
      (
        options.data as { initialScriptContent?: string } | undefined
      )?.initialScriptContent?.trim(),
    );
  }
  return (
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

function EstimateContent({ estimate }: { estimate: PointEstimate }) {
  const grouped = new Map<string, { points: number; count: number }>();
  for (const line of estimate.lines) {
    const previous = grouped.get(line.label) || { points: 0, count: 0 };
    grouped.set(line.label, {
      points: previous.points + Number(line.points),
      count: previous.count + 1,
    });
  }
  return (
    <div aria-label="积分预估详情">
      <p>
        预计消耗 <strong>{formatPoints(estimate.estimatedPoints)}</strong> 积分
      </p>
      <p>当前可用：{formatPoints(estimate.availablePoints)} 积分</p>
      <ul>
        {[...grouped].map(([label, item]) => (
          <li key={label}>
            {label}
            {item.count > 1 ? ` × ${item.count}` : ''}：
            {formatPoints(item.points)} 积分
          </li>
        ))}
      </ul>
      {estimate.lines.some((line) => !line.trialEligible) ? (
        <p>
          视频生成可用：{formatPoints(estimate.paidAvailablePoints)}{' '}
          积分（试用积分不可用于视频生成）
        </p>
      ) : null}
      {!estimate.sufficient ? (
        <p role="alert">可用积分不足，请充值后重试。</p>
      ) : null}
      <p style={{ color: 'var(--app-color-text-secondary)' }}>
        {estimate.notice}
      </p>
    </div>
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
  return new Promise((resolve, reject) => {
    let estimate: PointEstimate | undefined;
    let closed = false;
    const dialog = modal.confirm({
      title: '积分消耗预估',
      content: <div role="status">正在计算本次操作预计消耗的积分…</div>,
      okText: '确认消耗并继续',
      cancelText: '取消',
      okButtonProps: { disabled: true },
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
    void request<{
      success: boolean;
      data: PointEstimate;
      errorMessage?: string;
    }>(`/api/tenants/${tenant}/points/estimate`, {
      method: 'POST',
      data: { operations },
      skipErrorHandler: true,
    })
      .then((response) => {
        if (closed) return;
        if (!response.success || !response.data)
          throw new Error(response.errorMessage || '积分预估失败');
        if (
          !Number.isFinite(Number(response.data.estimatedPoints)) ||
          Number(response.data.estimatedPoints) < 0 ||
          !Array.isArray(response.data.lines) ||
          typeof response.data.sufficient !== 'boolean'
        ) {
          throw new Error('积分预估结果不完整');
        }
        estimate = response.data;
        dialog.update({
          content: <EstimateContent estimate={estimate} />,
          okButtonProps: { disabled: !estimate.sufficient },
        });
      })
      .catch((error: unknown) => {
        if (closed) return;
        closed = true;
        dialog.destroy();
        reject(
          new Error(
            `积分预估失败，未提交任务：${error instanceof Error ? error.message : '请稍后重试'}`,
          ),
        );
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
  return request<T>(url, requestOptions);
}
