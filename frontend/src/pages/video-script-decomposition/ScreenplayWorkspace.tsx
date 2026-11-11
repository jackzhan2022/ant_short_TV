import {
  CheckOutlined,
  CopyOutlined,
  FileTextOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Empty, Skeleton } from 'antd';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import PointEstimateButton from '@/components/PointEstimateButton';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { statusText } from '@/utils/fieldDictionary';
import { EpisodeProgress, FlowSteps } from './DecompositionProgress';
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

export default function ScreenplayWorkspace({ batchId }: { batchId: number }) {
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
  const episodes = useMemo(
    () =>
      [...(batch.data?.episodes ?? [])].sort(
        (a, b) => a.episodeNo - b.episodeNo,
      ),
    [batch.data?.episodes],
  );
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

  const copyCurrent = async () => {
    if (!screenplay || copying) return;
    setCopying(true);
    try {
      await navigator.clipboard.writeText(screenplay);
      if (mounted.current) message.success('已复制本集剧本');
    } catch {
      if (mounted.current)
        message.error('复制失败，请检查网络或浏览器剪贴板权限');
    } finally {
      if (mounted.current) setCopying(false);
    }
  };
  const running = shouldPollVideoDecompositionBatch(batch.data?.status);
  const succeeded = batch.data?.succeededEpisodes ?? 0;

  return (
    <section aria-label="剧本工作区" className={styles.batchWorkspace}>
      <FlowSteps current={running ? 1 : 2} />
      <div className={styles.panel}>
        {batch.isPending ? (
          <div className={styles.loading}>
            <Skeleton active />
          </div>
        ) : !batch.data ? (
          <div role="alert" className={styles.loading}>
            批次加载失败
            <Button onClick={() => void batch.refetch()}>重试加载批次</Button>
          </div>
        ) : (
          <>
            <header className={styles.panelHeading}>
              <div>
                <h2>{batch.data.name}</h2>
                <p>{batch.data.totalEpisodes} 集 · 按集查看与复制</p>
              </div>
              <span
                className={styles.statusSummary}
                data-failed={batch.data.failedEpisodes > 0}
                data-complete={!running && !batch.data.failedEpisodes}
              >
                <i />
                {statusText(batch.data.status)} · {succeeded} /{' '}
                {batch.data.totalEpisodes} 集已完成
              </span>
            </header>
            {batch.isError && (
              <div role="alert" className={styles.historyError}>
                进度更新失败，将自动重试
                <Button type="link" onClick={() => void batch.refetch()}>
                  立即刷新
                </Button>
              </div>
            )}
            <div className={styles.studioGrid}>
              <nav className={styles.episodeRail} aria-label="批次内集数">
                <div className={styles.railLabel}>
                  <strong>剧集目录</strong>
                  <span>{episodes.length} 集</span>
                </div>
                <div className={styles.resultEpisodeList}>
                  {episodes.map((item) => {
                    const cached =
                      client.getQueryData<VideoDecompositionScreenplayEpisode>([
                        'video-screenplay',
                        item.id,
                        item.analysisVersion ?? 0,
                      ]);
                    const title = screenplayTitle(cached?.screenplayContent);
                    const done = readable.has(item.status);
                    const failed = item.status === 'FAILED';
                    return (
                      <button
                        type="button"
                        key={item.id}
                        className={styles.episodeButton}
                        aria-pressed={item.id === episode?.id}
                        aria-label={`第 ${item.episodeNo} 集`}
                        onClick={() => setSelectedId(item.id)}
                      >
                        <span className={styles.resultEpisodeIcon}>
                          <FileTextOutlined />
                        </span>
                        <span className={styles.resultEpisodeInfo}>
                          <span className={styles.episodeNumber}>
                            第 {String(item.episodeNo).padStart(2, '0')} 集
                          </span>
                          <strong title={title ?? item.sourceFileName}>
                            {title ?? item.sourceFileName}
                          </strong>
                          <small
                            className={
                              failed
                                ? styles.failed
                                : done
                                  ? styles.succeeded
                                  : styles.muted
                            }
                          >
                            {done ? '已完成 · 可阅读' : statusText(item.status)}
                          </small>
                        </span>
                        {done ? (
                          <span className={styles.completeIcon}>
                            <CheckOutlined />
                          </span>
                        ) : failed ? (
                          <span className={styles.failureIcon}>!</span>
                        ) : (
                          <EpisodeProgress percent={item.percentage} compact />
                        )}
                      </button>
                    );
                  })}
                </div>
              </nav>
              <div
                ref={reader}
                className={
                  episode && !readable.has(episode.status)
                    ? styles.stateArea
                    : styles.reader
                }
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
                      本集剧本加载失败
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
                ) : episode.status === 'FAILED' ? (
                  <section
                    className={styles.failedArea}
                    aria-label={`第 ${episode.episodeNo} 集生成失败`}
                  >
                    <span className={styles.failedStateIcon} aria-hidden="true">
                      !
                    </span>
                    <h3>
                      第 {String(episode.episodeNo).padStart(2, '0')}{' '}
                      集暂未生成成功
                    </h3>
                    <p role="alert">
                      {episode.errorMessage || '生成失败，请稍后重试'}
                    </p>
                    <p>其他已完成的剧本可以继续阅读。</p>
                    {episode.retryable && (
                      <PointEstimateButton
                        compact
                        type="primary"
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
                  </section>
                ) : (
                  <section
                    className={styles.progressArea}
                    aria-label={`第 ${episode.episodeNo} 集生成进度`}
                  >
                    <EpisodeProgress percent={episode.percentage} />
                    <h3>
                      第 {String(episode.episodeNo).padStart(2, '0')} 集 ·{' '}
                      {statusText(episode.status)}
                    </h3>
                    <p>
                      剧本就绪后会自动显示
                      <br />
                      可以先阅读已完成的剧集
                    </p>
                    <span className={styles.progressStage}>
                      <i />
                      进度自动更新
                    </span>
                  </section>
                )}
                {episode && readable.has(episode.status) && (
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
            <footer className={styles.resultFooter}>
              <span>
                {running
                  ? '生成中可离开本页，稍后从生成记录继续查看'
                  : batch.data.failedEpisodes
                    ? `${succeeded} 集已完成 · ${batch.data.failedEpisodes} 集失败`
                    : `${succeeded} 集剧本已完成`}
              </span>
              <div>
                <Button
                  className={styles.quietButton}
                  icon={<CopyOutlined />}
                  loading={copying}
                  disabled={
                    !screenplay ||
                    copying ||
                    !episode ||
                    !readable.has(episode.status)
                  }
                  onClick={() => void copyCurrent()}
                >
                  复制本集
                </Button>
                <Button
                  className={styles.quietButton}
                  icon={<CopyOutlined />}
                  loading={copying}
                  disabled={!succeeded || copying}
                  onClick={() => void copyAll()}
                >
                  复制全部剧本
                </Button>
              </div>
            </footer>
          </>
        )}
      </div>
    </section>
  );
}
