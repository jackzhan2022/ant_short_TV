import { CopyOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Drawer, Empty, Progress, Skeleton } from 'antd';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import PointEstimateButton from '@/components/PointEstimateButton';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { statusText } from '@/utils/fieldDictionary';
import styles from './index.module.css';
import { pollingDelay, shouldPollVideoDecompositionBatch } from './queries';
import ScreenplayReader, { screenplayTitle } from './ScreenplayReader';
import {
  queryVideoDecompositionBatch,
  queryVideoDecompositionBatchScreenplays,
  queryVideoDecompositionScreenplay,
  retryVideoDecompositionEpisode,
  type VideoDecompositionBatchScreenplays,
  type VideoDecompositionScreenplayEpisode,
} from './service';

export const buildCopyAllScreenplays = (
  batch?: VideoDecompositionBatchScreenplays,
) =>
  [...(batch?.episodes ?? [])]
    .filter((item) => item.screenplayContent)
    .sort((a, b) => a.episode.episodeNo - b.episode.episodeNo)
    .map((item) => item.screenplayContent?.trim())
    .join('\n\n');

const readable = new Set([
  'SUCCEEDED',
  'ANALYSIS_SUCCEEDED',
  'PENDING_REVIEW',
  'CONFIRMED',
]);

export default function ScreenplayDrawer({
  batchId,
  onClose,
}: {
  batchId: number;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [selectedId, setSelectedId] = useState<number>();
  const [retrying, setRetrying] = useState(false);
  const [copying, setCopying] = useState(false);
  const mounted = useRef(true);
  const reader = useRef<HTMLDivElement>(null);
  const positions = useRef(new Map<number, number>());
  const batch = useQuery({
    queryKey: ['video-batch', batchId],
    queryFn: async ({ signal }) =>
      (await queryVideoDecompositionBatch(batchId, signal)).data,
    refetchInterval: (query) =>
      query.state.fetchStatus !== 'idle'
        ? false
        : query.state.status === 'error' ||
            shouldPollVideoDecompositionBatch(query.state.data?.status)
          ? pollingDelay(
              query.state.status === 'error' ? query.state.errorUpdateCount : 0,
            )
          : false,
  });
  const episodes = batch.data?.episodes ?? [];
  const episode =
    episodes.find((item) => item.id === selectedId) ?? episodes[0];
  const index = episodes.findIndex((item) => item.id === episode?.id);
  const contentKey = [
    'video-screenplay',
    episode?.id,
    episode?.analysisVersion ?? 0,
  ];
  const content = useQuery({
    queryKey: contentKey,
    queryFn: async ({ signal }) => {
      if (!episode) throw new Error('请选择剧集');
      return (await queryVideoDecompositionScreenplay(episode.id, signal)).data;
    },
    enabled: Boolean(episode && readable.has(episode.status)),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
  const screenplay = content.data?.screenplayContent;

  useLayoutEffect(() => {
    if (reader.current && episode)
      reader.current.scrollTop = positions.current.get(episode.id) ?? 0;
  }, [episode?.id, screenplay]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      void client.cancelQueries({ queryKey: ['video-copy', batchId] });
    };
  }, [batchId, client]);

  const retryEpisode = async () => {
    if (!episode || retrying) return;
    setRetrying(true);
    try {
      await retryVideoDecompositionEpisode(episode.id);
      if (!mounted.current) return;
      await client.invalidateQueries({ queryKey: ['video-batch', batchId] });
      void client.invalidateQueries({
        queryKey: ['video-batches'],
        refetchType: 'none',
      });
      message.success(`第 ${episode.episodeNo} 集已重新加入生成队列`);
    } catch (error) {
      if (mounted.current && !isPointEstimateCancelled(error))
        message.error(error instanceof Error ? error.message : '重试失败');
    } finally {
      if (mounted.current) setRetrying(false);
    }
  };

  const copyAll = async () => {
    if (copying) return;
    setCopying(true);
    try {
      // Full batch text is fetched only for this explicit action, never for polling.
      const result = await client.fetchQuery({
        queryKey: ['video-copy', batchId],
        staleTime: 0,
        queryFn: async ({ signal }) =>
          (await queryVideoDecompositionBatchScreenplays(batchId, signal)).data,
      });
      if (!mounted.current) return;
      const text = buildCopyAllScreenplays(result);
      if (!text) {
        message.warning('暂无可复制的成功剧本');
        return;
      }
      await navigator.clipboard.writeText(text);
      if (mounted.current) message.success('已复制全部成功剧本');
    } catch {
      if (mounted.current)
        message.error('复制失败，请检查网络或浏览器剪贴板权限');
    } finally {
      if (mounted.current) setCopying(false);
    }
  };

  return (
    <Drawer
      open
      size="min(1200px, 100vw)"
      onClose={onClose}
      title={
        <div className={styles.drawerTitle}>
          {batch.data?.name ?? '全部剧本'}
          <small>全部剧本 · 生成结果只读</small>
        </div>
      }
      classNames={{ body: styles.drawerBody }}
      styles={{ body: { padding: 0, overflow: 'hidden' } }}
      extra={
        <Button
          icon={<CopyOutlined />}
          loading={copying}
          disabled={!batch.data?.succeededEpisodes}
          onClick={() => void copyAll()}
        >
          复制全部剧本
        </Button>
      }
    >
      {batch.isPending ? (
        <div className={styles.loading}>
          <Skeleton active />
        </div>
      ) : !batch.data ? (
        <div role="alert" className={styles.loading}>
          批次加载失败{' '}
          <Button onClick={() => void batch.refetch()}>重试加载批次</Button>
        </div>
      ) : (
        <>
          <div className={styles.batchStatus} aria-live="polite">
            <span>
              成功 {batch.data.succeededEpisodes} · 处理中{' '}
              {batch.data.processingEpisodes} · 待处理{' '}
              {batch.data.pendingEpisodes} · 失败 {batch.data.failedEpisodes}
            </span>
            {batch.isError ? (
              <span role="alert">
                进度更新失败，将自动重试{' '}
                <Button type="link" onClick={() => void batch.refetch()}>
                  立即刷新
                </Button>
              </span>
            ) : (
              <span>总进度 {batch.data.percentage}%</span>
            )}
          </div>
          <div className={styles.resultGrid}>
            <nav className={styles.episodeRail} aria-label="批次内集数">
              <div className={styles.railLabel}>
                剧集目录 <span>{episodes.length} 集</span>
              </div>
              {episodes.map((item) => {
                const cached =
                  client.getQueryData<VideoDecompositionScreenplayEpisode>([
                    'video-screenplay',
                    item.id,
                    item.analysisVersion ?? 0,
                  ]);
                const title = screenplayTitle(cached?.screenplayContent);
                return (
                  <button
                    type="button"
                    key={item.id}
                    className={styles.episodeButton}
                    aria-pressed={item.id === episode?.id}
                    aria-label={`第 ${item.episodeNo} 集`}
                    onClick={() => setSelectedId(item.id)}
                  >
                    <span className={styles.episodeNumber}>
                      第 {String(item.episodeNo).padStart(2, '0')} 集
                    </span>
                    <strong>{title ?? item.sourceFileName}</strong>
                    <span
                      className={
                        item.status === 'FAILED'
                          ? styles.failed
                          : readable.has(item.status)
                            ? styles.succeeded
                            : styles.muted
                      }
                    >
                      {statusText(item.status)}
                    </span>
                  </button>
                );
              })}
            </nav>
            <div
              ref={reader}
              className={styles.reader}
              onScroll={(event) => {
                if (episode)
                  positions.current.set(
                    episode.id,
                    event.currentTarget.scrollTop,
                  );
              }}
            >
              {!episode ? (
                <Empty description="暂无剧集" />
              ) : readable.has(episode.status) ? (
                content.isPending ? (
                  <div className={styles.paper}>
                    <Skeleton active paragraph={{ rows: 8 }} />
                  </div>
                ) : content.isError ? (
                  <div role="alert" className={styles.paper}>
                    本集剧本加载失败{' '}
                    <Button onClick={() => void content.refetch()}>
                      重试加载剧本
                    </Button>
                  </div>
                ) : screenplay ? (
                  <ScreenplayReader
                    content={screenplay}
                    episodeNo={episode.episodeNo}
                    fileName={episode.sourceFileName}
                  />
                ) : (
                  <div className={styles.paper}>
                    <Empty description="当前记录暂无可展示的生成剧本，历史记录仅供只读查看。" />
                  </div>
                )
              ) : (
                <section className={styles.paper}>
                  <div className={styles.paperMeta}>
                    EPISODE {String(episode.episodeNo).padStart(2, '0')}
                  </div>
                  <h2>
                    第 {episode.episodeNo} 集 · {statusText(episode.status)}
                  </h2>
                  <div className={styles.source}>{episode.sourceFileName}</div>
                  {episode.status === 'FAILED' ? (
                    <div className={styles.failure} role="alert">
                      <p>{episode.errorMessage || '生成失败，请稍后重试'}</p>
                      {episode.retryable && (
                        <PointEstimateButton
                          operations={[
                            {
                              url: `/api/video-script-decomposition/episodes/${episode.id}/retry`,
                            },
                          ]}
                          loading={retrying}
                          disabled={retrying}
                          icon={<ReloadOutlined />}
                          onClick={() => void retryEpisode()}
                        >
                          重试本集
                        </PointEstimateButton>
                      )}
                    </div>
                  ) : (
                    <div className={styles.pending}>
                      <Progress percent={episode.percentage} status="active" />
                      <p>结果就绪后自动显示，可以先查看已成功的剧集。</p>
                    </div>
                  )}
                </section>
              )}
              {episode && (
                <div className={styles.readingNav}>
                  <Button
                    type="text"
                    disabled={index <= 0}
                    onClick={() => setSelectedId(episodes[index - 1].id)}
                  >
                    ← 上一集
                  </Button>
                  <span>
                    {index + 1} / {episodes.length}
                  </span>
                  <Button
                    type="text"
                    disabled={index >= episodes.length - 1}
                    onClick={() => setSelectedId(episodes[index + 1].id)}
                  >
                    下一集 →
                  </Button>
                </div>
              )}
            </div>
          </div>
          <div className={styles.resultFooter}>
            <span>
              {shouldPollVideoDecompositionBatch(batch.data.status)
                ? '进度自动更新'
                : '批次处理已结束'}{' '}
              · 按集查看剧本
            </span>
            <span>
              {batch.data.succeededEpisodes} 集生成成功 / 共{' '}
              {batch.data.totalEpisodes} 集
            </span>
          </div>
        </>
      )}
    </Drawer>
  );
}
