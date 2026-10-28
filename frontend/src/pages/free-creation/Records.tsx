import AvatarRecoveryNotice from './AvatarRecoveryNotice';
import { avatarRecoveryPending } from './avatar-recovery';
import {
  ClockCircleOutlined,
  DownloadOutlined,
  PictureOutlined,
  ReloadOutlined,
  TeamOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { Button, Image, Modal, Spin } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { controlTask } from '@/pages/tasks/service';
import PointEstimateButton from '@/components/PointEstimateButton';
import VideoPreviewPlayer from '@/pages/projects/production-workbench/VideoPreviewPlayer';
import {
  type CreationParameters,
  type CreationQuery,
  type CreationRecord,
  type CreationStatus,
  getCreation,
  getCreationDownload,
} from './api';
import styles from './index.module.css';
import useCreationRecords from './useCreationRecords';
export const statusLabels: Record<CreationStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
};
const virtualizing = (record: CreationRecord) =>
  record.phase === 'AVATAR_PREPARING' && ['QUEUED', 'RUNNING'].includes(record.status);
const statusLabel = (record: CreationRecord) =>
  virtualizing(record) ? '智能虚拟化中' : statusLabels[record.status] || record.status;

export function parameterLabels(p: CreationParameters) {
  return [
    p.aspectRatio,
    p.resolution?.toUpperCase(),
    p.intelligentDuration
      ? '智能时长'
      : p.durationSeconds
        ? `${p.durationSeconds} 秒`
        : undefined,
    p.count ? `${p.count} 张` : undefined,
    p.quality,
    p.outputFormat?.toUpperCase(),
    p.size,
    p.generateAudio === undefined
      ? undefined
      : p.generateAudio
        ? '生成音频'
        : '无音频',
    p.watermark === undefined ? undefined : p.watermark ? '带水印' : '无水印',
  ].filter(Boolean) as string[];
}
const formatTime = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('zh-CN', { hour12: false });
};
const pending = (record: CreationRecord) =>
  record.status === 'QUEUED' || record.status === 'RUNNING';
const preparingThumbnail = (record: CreationRecord) =>
  record.outputs.some((output) => output.thumbnailStatus === 'PENDING');
export default function Records({
  tenantId,
  createdRecord,
  onReuse,
  teamName,
}: {
  tenantId: number;
  createdRecord?: CreationRecord;
  onReuse: (record: CreationRecord) => Promise<void>;
  teamName: string;
}) {
  const [query, setQuery] = useState<CreationQuery>({
    scope: 'mine',
    current: 1,
    pageSize: 10,
  });
  const [keyword, setKeyword] = useState('');
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const {
    page,
    loading,
    fetching,
    hasMore,
    error: loadError,
    moreError,
    loadMore,
    insert,
  } = useCreationRecords(query, `${tenantId}:${reload}`);
  const scrollRef = useRef<HTMLElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const [detail, setDetail] = useState<CreationRecord>();
  const [detailId, setDetailId] = useState<number | undefined>(() => {
    const value = Number(
      new URLSearchParams(window.location.search).get('creation'),
    );
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  });
  const [detailError, setDetailError] = useState('');
  const [busy, setBusy] = useState(false);
  const lifetime = useRef(new AbortController());
  const controlKeys = useRef(new Map<string, string>());
  useEffect(() => {
    if (lifetime.current.signal.aborted)
      lifetime.current = new AbortController();
    const controller = lifetime.current;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (createdRecord && insert(createdRecord) && scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [createdRecord, insert]);
  useEffect(() => {
    const timer = window.setTimeout(
      () =>
        setQuery((current) =>
          current.keyword === (keyword.trim() || undefined)
            ? current
            : {
                ...current,
                keyword: keyword.trim() || undefined,
                current: 1,
              },
        ),
      350,
    );
    return () => window.clearTimeout(timer);
  }, [keyword]);
  useEffect(() => {
    setError('');
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, [query, reload]);
  useEffect(() => {
    if (
      !hasMore ||
      fetching ||
      moreError ||
      typeof IntersectionObserver === 'undefined'
    )
      return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) loadMore();
      },
      { root: scrollRef.current, rootMargin: '240px 0px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, fetching, moreError, loadMore]);
  useEffect(() => {
    if (!detailId) return;
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    setDetail(undefined);
    setDetailError('');
    const load = async () => {
      try {
        const next = await getCreation(detailId, abort.signal);
        if (!abort.signal.aborted) {
          setDetail(next);
          if (pending(next) || preparingThumbnail(next) || avatarRecoveryPending(next.avatarRecovery)) timer = setTimeout(() => void load(), 5000);
        }
      } catch (failure) {
        if (!abort.signal.aborted)
          setDetailError(
            failure instanceof Error ? failure.message : '详情加载失败',
          );
      }
    };
    void load();
    return () => {
      abort.abort();
      clearTimeout(timer);
    };
  }, [detailId, reload]);
  const action = async (work: (signal: AbortSignal) => Promise<void>) => {
    if (busy) return;
    const signal = lifetime.current.signal;
    setBusy(true);
    setError('');
    try {
      await work(signal);
    } catch (failure) {
      if (isPointEstimateCancelled(failure)) return;
      if (!signal.aborted)
        setError(
          failure instanceof Error ? failure.message : '操作失败，请重试',
        );
    } finally {
      if (!signal.aborted) setBusy(false);
    }
  };
  const control = (record: CreationRecord, operation: string) =>
    void action(async (signal) => {
      const identity = `${record.id}:${operation}`;
      const key = controlKeys.current.get(identity) || crypto.randomUUID();
      controlKeys.current.set(identity, key);
      const result = await controlTask(
        tenantId,
        record.taskKey || `FREE_CREATION:${record.id}`,
        operation,
        key,
        signal,
      );
      if (signal.aborted) return;
      controlKeys.current.delete(identity);
      setDetailId(Number(result.taskKey.split(':')[1]));
      setReload((value) => value + 1);
    });
  const reuse = (id: number) =>
    void action(async (signal) => {
      const latest = await getCreation(id, signal);
      if (signal.aborted) return;
      await onReuse(latest);
      if (!signal.aborted) setDetailId(undefined);
    });
  const download = (recordId: number, outputId: number) =>
    void action(async (signal) => {
      const result = await getCreationDownload(recordId, outputId, signal);
      const link = document.createElement('a');
      try {
        if (signal.aborted) return;
        link.href = result.url;
        link.download = result.fileName;
        document.body.appendChild(link);
        link.click();
      } finally {
        link.remove();
        URL.revokeObjectURL(result.url);
      }
    });
  const filter = (values: Partial<CreationQuery>) =>
    setQuery((current) => ({ ...current, ...values, current: 1 }));
  return (
    <section className={styles.results} aria-label="创作记录">
      <div className={styles.resultToolbar}>
        <fieldset className={styles.resultTabs} aria-label="记录范围">
          <button
            type="button"
            aria-pressed={query.scope === 'mine'}
            onClick={() => filter({ scope: 'mine' })}
          >
            我的创作
          </button>
          <button
            type="button"
            aria-pressed={query.scope === 'team'}
            onClick={() => filter({ scope: 'team' })}
          >
            团队素材
          </button>
        </fieldset>
        <Button
          size="small"
          icon={<ReloadOutlined aria-hidden="true" />}
          onClick={() => setReload((value) => value + 1)}
        >
          刷新记录
        </Button>
      </div>
      <div className={styles.recordFilters}>
        <fieldset className={styles.filters} aria-label="作品类型">
          {[
            [undefined, '全部'],
            ['IMAGE', '图片'],
            ['VIDEO', '视频'],
          ].map(([value, label]) => (
            <button
              key={label}
              type="button"
              aria-label={value ? `只看${label}` : '查看全部类型'}
              aria-pressed={query.mediaType === value}
              onClick={() =>
                filter({ mediaType: value as CreationQuery['mediaType'] })
              }
            >
              {label}
            </button>
          ))}
        </fieldset>
        <select
          aria-label="任务状态"
          value={query.status || ''}
          onChange={(event) =>
            filter({
              status: (event.target.value ||
                undefined) as CreationQuery['status'],
            })
          }
        >
          <option value="">全部状态</option>
          {Object.entries(statusLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <input
          aria-label="搜索提示词或创作人"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="搜索提示词或创作人"
        />
      </div>
      <div className={styles.recordsHeading}>
        <span>
          <ClockCircleOutlined aria-hidden="true" /> 创作记录 · {page.total}
        </span>
        <span>
          <TeamOutlined aria-hidden="true" /> {teamName} ·{' '}
          {query.scope === 'mine' ? '由我创作' : '全部成员'}
        </span>
      </div>
      <section
        ref={scrollRef}
        className={styles.recordsScroll}
        aria-label="创作记录列表"
        // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and scroll this region.
        tabIndex={0}
      >
        {error || loadError ? (
          <div className={styles.recordError} role="alert">
            {error || loadError}
            <Button
              size="small"
              onClick={() => setReload((value) => value + 1)}
            >
              重试
            </Button>
          </div>
        ) : null}
        {loading ? (
          <div className={styles.recordEmpty}>
            <Spin />
            <p>正在加载创作记录…</p>
          </div>
        ) : page.records.length ? (
          <div className={styles.recordList}>
            {page.records.map((record) => (
              <article className={styles.recordCard} key={record.id}>
                <button
                  className={styles.recordThumb}
                  type="button"
                  aria-label={`预览${record.mediaType === 'IMAGE' ? '图片' : '视频'}：${record.prompt}`}
                  onClick={() => setDetailId(record.id)}
                >
                  {pending(record) ? (
                    <span
                      className={styles.generatingPreview}
                      role="status"
                      aria-label={`${statusLabel(record)}：${record.prompt}`}
                    >
                      <Spin />
                      <span>{virtualizing(record) ? '智能虚拟化中' : record.status === 'QUEUED' ? '排队等待中' : `${record.mediaType === 'IMAGE' ? '图片' : '视频'}生成中`}</span>
                      {record.avatarRecovery?.attempt && record.avatarRecovery.maxAttempts ? (
                        <span className={styles.recoveryRound}>自动恢复第 {record.avatarRecovery.attempt} / {record.avatarRecovery.maxAttempts} 轮</span>
                      ) : null}
                    </span>
                  ) : preparingThumbnail(record) && !record.outputs[0]?.thumbnailUrl ? (
                    <span className={styles.generatingPreview} role="status" aria-label={`缩略图准备中：${record.prompt}`}>
                      <Spin />
                      <span>缩略图准备中</span>
                    </span>
                  ) : record.outputs[0]?.thumbnailUrl ? (
                    <img
                      src={record.outputs[0].thumbnailUrl}
                      alt="生成结果缩略图"
                      loading="lazy"
                    />
                  ) : record.mediaType === 'IMAGE' ? (
                    <PictureOutlined aria-hidden="true" />
                  ) : (
                    <VideoCameraOutlined aria-hidden="true" />
                  )}
                  <span>
                    {record.mediaType === 'IMAGE'
                      ? `${record.outputs.length || record.parameters.count || 1} 张图片`
                      : record.outputs[0]?.durationSeconds
                        ? `${record.outputs[0].durationSeconds} 秒视频`
                        : '视频'}
                  </span>
                </button>
                <div className={styles.recordContent}>
                  <div className={styles.recordMeta}>
                    <span>
                      {record.creatorName} · {formatTime(record.createdAt)}
                    </span>
                    <span
                      className={styles.recordStatus}
                      data-status={record.status}
                    >
                      {statusLabel(record)}
                    </span>
                  </div>
                  <p className={styles.recordPrompt}>{record.prompt}</p>
                  <div className={styles.parameterTags}>
                    <span>{record.modelName}</span>
                    {parameterLabels(record.parameters).map((label) => (
                      <span key={label}>{label}</span>
                    ))}
                  </div>
                  <div className={styles.recordReferences}>
                    <span>参考素材</span>
                    {record.references.length ? (
                      record.references.map((ref) =>
                        ref.thumbnailUrl && ref.kind === 'IMAGE' ? (
                          <img
                            key={ref.id}
                            src={ref.thumbnailUrl}
                            alt={ref.fileName}
                            title={ref.fileName}
                            loading="lazy"
                          />
                        ) : (
                          <span key={ref.id} title={ref.fileName}>
                            {ref.kind === 'VIDEO'
                              ? '视频'
                              : ref.kind === 'AUDIO'
                                ? '音频'
                                : '图片'}
                          </span>
                        ),
                      )
                    ) : (
                      <span>无参考素材</span>
                    )}
                  </div>
                  {record.errorMessage ? (
                    <p className={styles.error}>{record.errorMessage}</p>
                  ) : null}
                  <div className={styles.recordActions}>
                    <span>{teamName}</span>
                    <Button size="small" onClick={() => setDetailId(record.id)}>
                      查看详情
                    </Button>
                    <Button
                      size="small"
                      disabled={busy}
                      onClick={() => reuse(record.id)}
                    >
                      复用参数
                    </Button>
                    {record.outputs.length ? (
                      <Button
                        size="small"
                        disabled={busy}
                        icon={<DownloadOutlined aria-hidden="true" />}
                        onClick={() =>
                          record.outputs.length > 1
                            ? setDetailId(record.id)
                            : download(record.id, record.outputs[0].id)
                        }
                      >
                        下载
                      </Button>
                    ) : null}
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className={styles.recordEmpty}>
            <PictureOutlined aria-hidden="true" />
            <h2>
              {error || loadError ? '暂时无法加载记录' : '暂无匹配的创作记录'}
            </h2>
            <p>
              {query.scope === 'team'
                ? '当前团队所有成员的创作会自动出现在这里'
                : '在左侧描述灵感，完成生成确认后即可查看记录'}
            </p>
          </div>
        )}
        {hasMore ? (
          <div ref={sentinelRef} className={styles.loadMore}>
            {moreError ? <span role="alert">{moreError}</span> : null}
            <Button size="small" loading={fetching} onClick={loadMore}>
              {moreError ? '重试加载更多' : '加载更多'}
            </Button>
          </div>
        ) : !loading && page.records.length > 0 ? (
          <p className={styles.listEnd}>已显示全部创作</p>
        ) : null}
      </section>
      <Modal
        title="创作详情"
        open={detailId !== undefined}
        onCancel={() => setDetailId(undefined)}
        footer={null}
        width={960}
        destroyOnHidden
      >
        {detailError ? (
          <p role="alert">{detailError}</p>
        ) : !detail ? (
          <Spin />
        ) : (
          <div className={styles.detail}>
            <div className={styles.detailMedia}>
              {detail.mediaType === 'IMAGE' ? (
                <Image.PreviewGroup>
                  {detail.outputs.map((output, index) => (
                    <div key={output.id}>
                      <Image
                        src={output.previewUrl || output.thumbnailUrl}
                        alt={`生成图片 ${index + 1}`}
                      />
                      <Button
                        icon={<DownloadOutlined aria-hidden="true" />}
                        disabled={busy}
                        onClick={() => download(detail.id, output.id)}
                      >
                        下载图片 {index + 1}
                      </Button>
                    </div>
                  ))}
                </Image.PreviewGroup>
              ) : (
                detail.outputs.map((output) => (
                  <div key={output.id}>
                    <VideoPreviewPlayer
                      src={output.previewUrl}
                      poster={output.thumbnailUrl}
                      active
                      playbackKey={output.id}
                      label="生成视频"
                      sourceLabel="创作结果"
                      empty={<span>视频尚未就绪</span>}
                      leadingControls={null}
                      trailingControls={null}
                      previous={null}
                      next={null}
                    />
                    <Button
                      icon={<DownloadOutlined aria-hidden="true" />}
                      disabled={busy}
                      onClick={() => download(detail.id, output.id)}
                    >
                      下载视频
                    </Button>
                  </div>
                ))
              )}
              {!detail.outputs.length ? (
                <p>
                  {statusLabel(detail)}
                  {detail.errorMessage
                    ? `：${detail.errorMessage}`
                    : virtualizing(detail) ? ' · 正在准备虚拟人像素材，完成后自动继续生成' : ' · 结果生成后将自动展示'}
                </p>
              ) : null}
            </div>
            <div className={styles.detailInfo}>
              <AvatarRecoveryNotice recovery={detail.avatarRecovery} onOpen={setDetailId} />
              <div className={styles.recordMeta}>
                <strong>{detail.creatorName}</strong>
                <span>{statusLabel(detail)}</span>
              </div>
              <p>{formatTime(detail.createdAt)}</p>
              <p>{teamName}</p>
              <h3>完整提示词</h3>
              <p className={styles.fullPrompt}>{detail.prompt}</p>
              <h3>模型与参数</h3>
              <div className={styles.parameterTags}>
                <span>{detail.modelName}</span>
                {parameterLabels(detail.parameters).map((label) => (
                  <span key={label}>{label}</span>
                ))}
              </div>
              {detail.parameters.negativePrompt ? (
                <p>负面提示词：{detail.parameters.negativePrompt}</p>
              ) : null}
              <h3>参考素材</h3>
              <div className={styles.detailReferences}>
                {detail.references.map((ref) => (
                  <div key={ref.id}>
                    {ref.kind === 'IMAGE' ? (
                      <Image
                        width={72}
                        src={ref.previewUrl || ref.thumbnailUrl}
                        alt={ref.fileName}
                      />
                    ) : ref.kind === 'VIDEO' ? (
                      <video controls preload="metadata" src={ref.previewUrl}>
                        <track kind="captions" />
                      </video>
                    ) : (
                      <audio controls preload="metadata" src={ref.previewUrl}>
                        <track kind="captions" />
                      </audio>
                    )}
                    <span>{ref.fileName}</span>
                  </div>
                ))}
                {!detail.references.length ? <span>无参考素材</span> : null}
              </div>
              {error ? (
                <p role="alert" className={styles.error}>
                  {error}
                </p>
              ) : null}
              <Button
                type="primary"
                disabled={busy}
                onClick={() => reuse(detail.id)}
              >
                复用参数
              </Button>
              {(detail.allowedActions || []).map((operation) => (
                <PointEstimateButton
                  key={operation}
                  tenantId={tenantId}
                  operations={
                    operation === 'CANCEL'
                      ? []
                      : [
                          {
                            url: `/api/tenants/${tenantId}/production-tasks/${encodeURIComponent(detail.taskKey || `FREE_CREATION:${detail.id}`)}/${operation.toLowerCase()}`,
                          },
                        ]
                  }
                  disabled={busy}
                  onClick={() => control(detail, operation)}
                >
                  {
                    {
                      CANCEL: '取消任务',
                      RETRY: '重试',
                      REGENERATE: '重新生成',
                    }[operation]
                  }
                </PointEstimateButton>
              ))}
              <Button
                onClick={() =>
                  history.push(
                    `/tasks?task=${encodeURIComponent(detail.taskKey || `FREE_CREATION:${detail.id}`)}`,
                  )
                }
              >
                在任务中心查看
              </Button>
              <p className={styles.help}>
                回填参数后可继续编辑，生成前会再次确认积分。
              </p>
              <p className={styles.help}>
                重试和重新生成会按原参数新建任务并确认积分。取消后停止跟踪结果，供应商已产生的用量仍会结算。
              </p>
            </div>
          </div>
        )}
      </Modal>
    </section>
  );
}
