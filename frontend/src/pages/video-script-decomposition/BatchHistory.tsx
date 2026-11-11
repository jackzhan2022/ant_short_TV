import { FileTextOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Empty, Pagination, Progress, Skeleton } from 'antd';
import { statusText } from '@/utils/fieldDictionary';
import styles from './index.module.css';
import type { VideoDecompositionBatch } from './service';

function formatDate(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? '—'
    : date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
}

export default function BatchHistory({
  batches,
  total,
  current,
  pageSize,
  pending,
  error,
  onRetry,
  onPageChange,
  onOpen,
}: {
  batches: VideoDecompositionBatch[];
  total: number;
  current: number;
  pageSize: number;
  pending: boolean;
  error: boolean;
  onRetry: () => void;
  onPageChange: (page: number, pageSize: number) => void;
  onOpen: (id: number) => void;
}) {
  return (
    <section className={styles.historyView} aria-label="生成记录">
      <div className={styles.historyHeading}>
        <h2>生成记录</h2>
        <span>共 {total} 个批次</span>
      </div>
      {error && (
        <div role="alert" className={styles.historyError}>
          记录加载失败<Button onClick={onRetry}>重试加载批次</Button>
        </div>
      )}
      {pending ? (
        <div className={styles.historyGrid}>
          {[0, 1, 2, 3].map((key) => (
            <div className={styles.historyCard} key={key}>
              <Skeleton active />
            </div>
          ))}
        </div>
      ) : !batches.length && !error ? (
        <div className={styles.emptyHistory}>
          <Empty description="暂无生成记录，选择视频开始拆剧" />
        </div>
      ) : (
        <div className={styles.historyGrid}>
          {batches.map((batch) => {
            const failed =
              batch.status === 'FAILED' || batch.status === 'PARTIAL_FAILED';
            return (
              <article className={styles.historyCard} key={batch.id}>
                <header>
                  <span className={styles.batchIcon}>
                    <FileTextOutlined />
                  </span>
                  <div>
                    <h3>{batch.name}</h3>
                    <small>
                      {formatDate(batch.createdAt)} · {batch.totalEpisodes ?? 0}{' '}
                      集
                    </small>
                  </div>
                  <span className={styles.batchBadge} data-failed={failed}>
                    {statusText(batch.status)}
                  </span>
                </header>
                <Progress
                  percent={batch.percentage ?? 0}
                  showInfo={false}
                  size="small"
                  strokeColor={
                    failed
                      ? 'var(--app-color-warning, #b78640)'
                      : batch.succeededEpisodes === batch.totalEpisodes
                        ? 'var(--app-color-success, #27866b)'
                        : 'var(--app-color-primary, #5252ff)'
                  }
                  railColor="var(--app-color-bg-layout, #f7f8fc)"
                />
                <footer>
                  <span>
                    {batch.succeededEpisodes ?? 0} / {batch.totalEpisodes ?? 0}{' '}
                    集已完成
                    {batch.failedEpisodes > 0 && (
                      <em> · {batch.failedEpisodes} 集失败</em>
                    )}
                  </span>
                  <Button
                    className={styles.quietButton}
                    onClick={() => onOpen(batch.id)}
                  >
                    {failed ? '查看并处理' : '查看剧本'}
                    <RightOutlined />
                  </Button>
                </footer>
              </article>
            );
          })}
        </div>
      )}
      {total > 0 && (
        <div className={styles.historyPagination}>
          <Pagination
            current={current}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            pageSizeOptions={[20, 50, 100]}
            onChange={onPageChange}
          />
        </div>
      )}
    </section>
  );
}
