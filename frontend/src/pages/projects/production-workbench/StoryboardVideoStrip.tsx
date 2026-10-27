import { Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import type { MediaPage } from '@/services/media/paging';
import type { AiVideoResult } from './service';

export const mergeVideoVersions = (...groups: AiVideoResult[][]) => {
  const versions = new Map<number, AiVideoResult>();
  for (const result of groups.flat()) {
    if (!result.videoUrl || (result.status && !['ACTIVE', 'SUCCESS', 'SUCCEEDED'].includes(result.status))) continue;
    const existing = versions.get(result.id);
    versions.set(result.id, { ...existing, ...result,
      createdAt: result.createdAt || existing?.createdAt,
      coverUrl: result.coverUrl || existing?.coverUrl,
    });
  }
  const time = (result: AiVideoResult) => new Date(result.createdAt || 0).getTime() || 0;
  return [...versions.values()].sort((a, b) => time(b) - time(a) || b.id - a.id);
};

export default function StoryboardVideoStrip({
  storyboardNo, results, history, currentResultId, previewResultId, onPreview, onLoad,
}: {
  storyboardNo: number;
  results: AiVideoResult[];
  history?: MediaPage<AiVideoResult>;
  currentResultId?: number | null;
  previewResultId?: number;
  onPreview: (result: AiVideoResult) => void;
  onLoad: (current: number, pageSize: number, signal: AbortSignal) => Promise<MediaPage<AiVideoResult>>;
}) {
  const [older, setOlder] = useState<AiVideoResult[]>([]);
  const [page, setPage] = useState<MediaPage<AiVideoResult>>();
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const request = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => request.current?.abort(), []);
  const versions = mergeVideoVersions(older, history?.data || [], results);
  const latestPage = page || history;
  const hasMore = latestPage
    ? latestPage.current * latestPage.pageSize < latestPage.total
    : versions.length > 0;
  const loadMore = async () => {
    if (loading) return;
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setFailed(false);
    try {
      const response = await onLoad((latestPage?.current || 0) + 1, latestPage?.pageSize || 8, controller.signal);
      if (controller.signal.aborted) return;
      setOlder((previous) => mergeVideoVersions(previous, response.data));
      setPage(response);
    } catch {
      if (!controller.signal.aborted) setFailed(true);
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  };
  if (!versions.length && !history?.total) return null;
  return (
    <section className="storyboard-video-strip" aria-label={`分镜${storyboardNo}视频版本`}>
      {versions.map((result) => (
        <button
          key={result.id}
          type="button"
          className={`storyboard-video-version${result.id === previewResultId ? ' is-previewing' : ''}`}
          aria-label={`预览分镜${storyboardNo}视频版本${result.id}`}
          aria-pressed={result.id === previewResultId}
          title={result.createdAt ? `生成时间：${new Date(result.createdAt).toLocaleString('zh-CN')}` : `视频版本 ${result.id}`}
          onClick={() => onPreview(result)}
        >
          {result.processingType === 'SUBTITLE_ERASE' ? <span className="subtitle-erase-version-tag">字幕已擦除</span> : null}
          <LazyMediaImage src={result.coverUrl || undefined} alt={`分镜${storyboardNo}视频版本${result.id}缩略图`} width="100%" height="100%" preview={false} />
          {result.id === currentResultId ? <span className="storyboard-video-current">当前分镜</span> : null}
        </button>
      ))}
      {hasMore ? (
        <Button size="small" loading={loading} aria-label={`加载更多分镜${storyboardNo}视频`} onClick={() => void loadMore()}>
          {failed ? '加载失败，重试' : '更多视频'}
        </Button>
      ) : null}
    </section>
  );
}
