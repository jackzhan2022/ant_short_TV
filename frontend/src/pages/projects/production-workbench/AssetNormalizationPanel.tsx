import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import PointEstimateButton from '@/components/PointEstimateButton';
import {
  type AssetNormalizationStatus,
  queryAssetNormalization,
  submitAssetNormalization,
} from './normalization-service';

type Props = {
  projectId: number;
  onTrack: (executionId: number) => void;
  onBlocked: (blocked: boolean) => void;
  onRefresh: () => unknown;
};

export default function AssetNormalizationPanel(props: Props) {
  const { projectId } = props;
  const { message } = App.useApp();
  const callbacks = useRef(props);
  callbacks.current = props;
  const [status, setStatus] = useState<AssetNormalizationStatus>();
  const [submitting, setSubmitting] = useState(false);
  const [readFailed, setReadFailed] = useState(false);
  const submittingRef = useRef(false);
  const previous = useRef<AssetNormalizationStatus | undefined>(undefined);
  const busy = submitting || ['EXTRACTING', 'NORMALIZING', 'RUNNING'].includes(status?.status || '');

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    previous.current = undefined;
    const poll = async () => {
      try {
        const response = await queryAssetNormalization(projectId);
        if (!active) return;
        const next = response.data;
        setStatus(next);
        setReadFailed(false);
        callbacks.current.onBlocked(next.blocked);
        if (previous.current?.blocked && !next.blocked) await callbacks.current.onRefresh();
        previous.current = next;
      } catch {
        if (!active) return;
        setReadFailed(true);
        callbacks.current.onBlocked(true);
      }
      if (active) timer = setTimeout(poll, 3000);
    };
    void poll();
    return () => { active = false; clearTimeout(timer); };
  }, [projectId]);

  const submit = async () => {
    if (submittingRef.current || busy) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const response = await submitAssetNormalization(projectId);
      if (!response.data.id) throw new Error('归一任务未返回任务编号');
      setStatus({ status: 'NORMALIZING', blocked: true, executionId: response.data.id });
      callbacks.current.onBlocked(true);
      callbacks.current.onTrack(response.data.id);
    } catch (error) {
      if (!isPointEstimateCancelled(error)) message.error(error instanceof Error ? error.message : '归一任务提交失败');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <section aria-label="全剧资产归一" style={{ marginBottom: 16 }}>
      <PointEstimateButton operations={[{ url: `/api/projects/${projectId}/asset-normalization` }]} loading={submitting} disabled={!status || readFailed || busy} onClick={() => void submit()}>
        {status?.status === 'FAILED' ? '重试资产归一' : '全剧资产归一'}
      </PointEstimateButton>
      <span role="status" style={{ marginLeft: 12 }}>
        {readFailed ? '归一状态读取失败，正在重试' : busy ? '正在提取或归一资产，可继续浏览；完成后可编辑和生成。'
          : status?.status === 'FAILED' ? '归一尚未完成，可重试；完成后可编辑和生成。'
            : status?.result ? `归一已完成：合并 ${status.result.mergedAssets} 条重复资产，拆分 ${status.result.splitGroups} 组混用记录。`
              : '自动统一角色、场景和道具身份，保留图片及视觉形态。'}
      </span>
      {status?.errorMessage ? <div>{status.errorMessage}</div> : null}
      {status?.executionId ? (
        <Button type="link" onClick={() => status.executionId && callbacks.current.onTrack(status.executionId)}>
          查看任务 #{status.executionId}
        </Button>
      ) : null}
    </section>
  );
}
