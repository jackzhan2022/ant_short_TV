import {
  ArrowLeftOutlined,
  CopyOutlined,
  DownloadOutlined,
  FileTextOutlined,
  HistoryOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { skipToken, useQuery, useQueryClient } from '@tanstack/react-query';
import { history, useLocation } from '@umijs/max';
import { Alert, App, Button, Drawer, Pagination, Progress, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { createDownloadTask } from '@/pages/downloads/service';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { statusText } from '@/utils/fieldDictionary';
import { DEFAULT_REVIEW_DIMENSIONS } from '../script-review/dimensions';
import {
  cancelSimpleReview,
  getReviewContent,
  getReviewHistory,
  getReviewProgress,
  getReviewReport,
  retrySimpleReview,
  submitReview,
} from '../script-review/simpleService';
import styles from './index.module.css';
import { ReviewQueries, reviewPollingDelay } from './queries';
import ReviewComposer, { MAX_CONTENT_LENGTH } from './ReviewComposer';
import ReviewReportReader from './ReviewReportReader';

const dateText = (value?: string | null) =>
  value ? new Date(value).toLocaleString('zh-CN') : '';
const errorText = (error: unknown) =>
  error instanceof Error ? error.message : '操作失败，请重试';

function ReviewPage({ tenantId }: { tenantId: number | undefined }) {
  const { message, modal } = App.useApp();
  const location = useLocation();
  const routeTask =
    Number(
      location.pathname.match(/\/script-review\/tasks\/(\d+)$/)?.[1] ??
        new URLSearchParams(location.search).get('taskId'),
    ) || undefined;
  const [taskId, setTaskId] = useState<number | undefined>(routeTask);
  const [content, setContent] = useState('');
  const [dimensions, setDimensions] = useState(DEFAULT_REVIEW_DIMENSIONS);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [originalOpen, setOriginalOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const controllerRef = useRef(new AbortController());
  const requestIdentity = useRef<
    { fingerprint: string; id: string } | undefined
  >(undefined);
  const submitting = useRef(false);
  const client = useQueryClient();
  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    setTaskId(routeTask);
    setOriginalOpen(false);
  }, [routeTask]);

  const progress = useQuery({
    queryKey: ['review-progress', taskId],
    queryFn:
      tenantId && taskId
        ? ({ signal }) => getReviewProgress(tenantId, taskId, signal)
        : skipToken,
    enabled: Boolean(tenantId && taskId && !historyOpen),
    staleTime: 0,
    refetchInterval: (query) =>
      reviewPollingDelay(
        query.state.data?.status,
        query.state.fetchFailureCount,
      ),
  });
  const report = useQuery({
    queryKey: ['review-report', taskId],
    queryFn:
      tenantId && taskId
        ? ({ signal }) => getReviewReport(tenantId, taskId, signal)
        : skipToken,
    enabled: Boolean(
      tenantId &&
        taskId &&
        !historyOpen &&
        progress.data?.status === 'COMPLETED',
    ),
    staleTime: Number.POSITIVE_INFINITY,
  });
  const original = useQuery({
    queryKey: ['review-content', taskId],
    queryFn:
      tenantId && taskId
        ? ({ signal }) => getReviewContent(tenantId, taskId, signal)
        : skipToken,
    enabled: Boolean(tenantId && taskId && originalOpen && !historyOpen),
    staleTime: Number.POSITIVE_INFINITY,
  });
  const records = useQuery({
    queryKey: ['review-history', page],
    queryFn: tenantId
      ? ({ signal }) => getReviewHistory(tenantId, page, signal)
      : skipToken,
    enabled: Boolean(tenantId && historyOpen),
  });

  const openTask = (id: number) => {
    setTaskId(id);
    setOriginalOpen(false);
    setHistoryOpen(false);
    setActionError('');
    history.replace(`/script-review-library?taskId=${id}`);
  };
  const returnToInput = () => {
    setHistoryOpen(false);
    setTaskId(undefined);
    setOriginalOpen(false);
    setActionError('');
    history.replace('/script-review-library');
  };
  const submit = async () => {
    const controller = controllerRef.current;
    if (
      !tenantId ||
      !content.trim() ||
      !dimensions.length ||
      submitting.current
    )
      return;
    const fingerprint = JSON.stringify([
      content.trim(),
      [...dimensions].sort(),
    ]);
    if (requestIdentity.current?.fingerprint !== fingerprint)
      requestIdentity.current = { fingerprint, id: crypto.randomUUID() };
    submitting.current = true;
    setBusy(true);
    setActionError('');
    try {
      const response = await submitReview(
        tenantId,
        {
          content: content.trim(),
          selectedDimensions: dimensions,
          requestId: requestIdentity.current.id,
        },
        controller.signal,
      );
      if (controller.signal.aborted) return;
      const id = response.data?.businessId;
      if (!id) throw new Error('未取得审核任务编号，请重试提交');
      requestIdentity.current = undefined;
      void client.invalidateQueries({ queryKey: ['review-history'] });
      openTask(id);
    } catch (error) {
      if (!controller.signal.aborted && !isPointEstimateCancelled(error))
        setActionError(errorText(error));
    } finally {
      submitting.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  };
  const taskAction = async (action: 'retry' | 'cancel') => {
    const controller = controllerRef.current;
    if (!tenantId || !taskId || submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setActionError('');
    try {
      await (action === 'retry' ? retrySimpleReview : cancelSimpleReview)(
        tenantId,
        taskId,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      await client.invalidateQueries({ queryKey: ['review-progress', taskId] });
      void client.invalidateQueries({ queryKey: ['review-history'] });
    } catch (error) {
      if (!controller.signal.aborted && !isPointEstimateCancelled(error))
        setActionError(errorText(error));
    } finally {
      submitting.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  };
  const reuseOriginal = async () => {
    const controller = controllerRef.current;
    if (!tenantId || !taskId) return;
    try {
      const value = await client.fetchQuery({
        queryKey: ['review-content', taskId],
        queryFn: ({ signal }) => getReviewContent(tenantId, taskId, signal),
        staleTime: Number.POSITIVE_INFINITY,
      });
      if (controller.signal.aborted) return;
      const apply = () => {
        if (controller.signal.aborted) return;
        setContent(value.content);
        setDimensions(
          report.data?.task.selectedDimensions ?? DEFAULT_REVIEW_DIMENSIONS,
        );
        returnToInput();
      };
      if (content.trim() && content !== value.content)
        modal.confirm({
          title: '用本次原文替换输入框内容？',
          content: '当前未提交的输入会被替换，历史报告保持不变。',
          onOk: apply,
        });
      else apply();
    } catch (error) {
      if (!controller.signal.aborted) setActionError(errorText(error));
    }
  };
  const copyReport = async () => {
    try {
      await navigator.clipboard.writeText(report.data?.reportMarkdown ?? '');
      message.success('报告已复制');
    } catch {
      message.error('复制失败，请手动选择报告文字复制');
    }
  };
  const downloadReport = async () => {
    if (!taskId || !report.data?.reportMarkdown) return;
    try {
      await createDownloadTask(`审核报告_${taskId}`, [
        { kind: 'REVIEW', id: taskId },
      ]);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  if (!tenantId) return <div className={styles.empty}>请先选择当前团队</div>;
  const running =
    progress.data && ['PENDING', 'RUNNING'].includes(progress.data.status);
  const percent = Math.max(
    0,
    Math.min(100, progress.data?.overallProgress ?? 0),
  );
  return (
    <div className={styles.page}>
      <header className={styles.toolbar}>
        <div className={styles.pageTitle}>
          <h1>{historyOpen ? '历史报告' : '剧本审核'}</h1>
          <p>
            {historyOpen
              ? '查看已有审核记录与报告'
              : '从人物到叙事，逐项核验剧本细节'}
          </p>
        </div>
        <Space wrap>
          {historyOpen || taskId ? (
            <Button
              icon={<ArrowLeftOutlined />}
              disabled={busy}
              onClick={returnToInput}
            >
              返回输入
            </Button>
          ) : null}
          {!historyOpen && (
            <Button
              icon={<HistoryOutlined />}
              disabled={busy}
              onClick={() => {
                setOriginalOpen(false);
                setHistoryOpen(true);
              }}
            >
              历史报告
            </Button>
          )}
          {historyOpen && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={busy}
              onClick={returnToInput}
            >
              新建审核
            </Button>
          )}
        </Space>
      </header>
      {actionError && (
        <Alert
          className={styles.actionError}
          type="error"
          title={actionError}
          showIcon
        />
      )}
      {historyOpen ? (
        <section aria-label="历史审核报告">
          {records.isPending ? (
            <div className={styles.empty} role="status">
              正在加载历史报告…
            </div>
          ) : records.isError ? (
            <Alert
              type="error"
              title="历史报告加载失败"
              action={
                <Button onClick={() => void records.refetch()}>重新加载</Button>
              }
            />
          ) : (
            <>
              <div className={styles.historyGrid}>
                {!records.data?.items.length && (
                  <div className={styles.empty}>暂无审核记录</div>
                )}
                {records.data?.items.map((item) => (
                  <article className={styles.historyItem} key={item.id}>
                    <div className={styles.historyTop}>
                      <span className={styles.documentIcon}>
                        <FileTextOutlined />
                      </span>
                      <span
                        className={styles.statusBadge}
                        data-status={item.status}
                      >
                        {statusText(item.status)}
                      </span>
                    </div>
                    <h2>{item.name}</h2>
                    <p className={styles.meta}>{dateText(item.createdAt)}</p>
                    <div className={styles.dimensionTags}>
                      {item.selectedDimensions.map((dimension) => (
                        <span className={styles.dimensionTag} key={dimension}>
                          {dimension}
                        </span>
                      ))}
                    </div>
                    <footer>
                      <span className={styles.meta}>
                        {item.selectedDimensions.length} 项审核维度
                      </span>
                      <Button onClick={() => openTask(item.id)}>
                        {item.status === 'COMPLETED' ? '查看报告' : '查看进度'}
                      </Button>
                    </footer>
                  </article>
                ))}
              </div>
              <Pagination
                className={styles.pagination}
                current={page}
                pageSize={20}
                total={records.data?.total ?? 0}
                onChange={setPage}
                showSizeChanger={false}
                hideOnSinglePage
              />
            </>
          )}
        </section>
      ) : !taskId ? (
        <ReviewComposer
          content={content}
          dimensions={dimensions}
          busy={busy}
          tenantId={tenantId}
          onContentChange={setContent}
          onDimensionsChange={setDimensions}
          onSubmit={() => void submit()}
          onImport={(value) => {
            if (value.length > MAX_CONTENT_LENGTH) {
              message.error('剧本不能超过 200,000 字，请拆分后审核');
              return;
            }
            setContent(value);
          }}
        />
      ) : (
        <section className={styles.report}>
          {progress.isPending && (
            <div className={styles.empty} role="status">
              正在读取审核状态…
            </div>
          )}
          {progress.isError && (
            <Alert
              type="error"
              title="审核状态加载失败"
              action={
                <Button onClick={() => void progress.refetch()}>
                  重新加载
                </Button>
              }
            />
          )}
          {running && (
            <div className={styles.progress} aria-live="polite">
              <span className={styles.progressEyebrow}>SCRIPT REVIEW</span>
              <div className={styles.progressOrbit}>
                <Progress
                  type="circle"
                  percent={percent}
                  size={136}
                  strokeWidth={5}
                  strokeColor="var(--app-color-primary, #5252ff)"
                  format={(value) => (
                    <span className={styles.percent}>
                      {Math.round(value ?? 0)}
                      <small>%</small>
                    </span>
                  )}
                />
              </div>
              <h2>
                {progress.data.status === 'PENDING'
                  ? '审核即将开始'
                  : '正在审核剧本'}
              </h2>
              <p>{progress.data.currentAction || '正在生成审核报告…'}</p>
              <p className={styles.meta}>
                审核在后台继续，可从历史报告重新打开。
              </p>
              <Button
                disabled={busy}
                onClick={() =>
                  modal.confirm({
                    title: '取消本次审核？',
                    content: '已产生的模型调用按实际用量结算。',
                    onOk: () => taskAction('cancel'),
                  })
                }
              >
                取消审核
              </Button>
            </div>
          )}
          {progress.data &&
            ['FAILED', 'CANCELED'].includes(progress.data.status) && (
              <Alert
                type={progress.data.status === 'FAILED' ? 'error' : 'info'}
                title={
                  progress.data.errorMessage || statusText(progress.data.status)
                }
                action={
                  progress.data.status === 'FAILED' ? (
                    <Button loading={busy} onClick={() => taskAction('retry')}>
                      {progress.data.retryKind === 'AGGREGATION_ONLY'
                        ? '重试报告汇总'
                        : '重试审核'}
                    </Button>
                  ) : undefined
                }
              />
            )}
          {progress.data?.status === 'COMPLETED' && (
            <>
              <header className={styles.reportHeader}>
                <div>
                  <span className={styles.reportEyebrow}>审核报告</span>
                  <h2>{report.data?.task.name || '剧本审核报告'}</h2>
                  {report.data && (
                    <div className={styles.meta}>
                      {dateText(report.data.task.createdAt)} ·{' '}
                      {report.data.task.selectedDimensions.length} 项审核维度
                    </div>
                  )}
                </div>
                <Space wrap>
                  <Button
                    icon={<FileTextOutlined />}
                    onClick={() => setOriginalOpen(true)}
                  >
                    查看原文
                  </Button>
                  {report.data?.reportMarkdown && (
                    <>
                      <Button icon={<CopyOutlined />} onClick={copyReport}>
                        复制报告
                      </Button>
                      <Button
                        icon={<DownloadOutlined />}
                        onClick={downloadReport}
                      >
                        下载报告
                      </Button>
                    </>
                  )}
                </Space>
              </header>
              {report.isPending ? (
                <div className={styles.empty} role="status">
                  正在加载审核报告…
                </div>
              ) : report.isError ? (
                <Alert
                  type="error"
                  title="报告加载失败"
                  action={
                    <Button onClick={() => void report.refetch()}>
                      重新加载报告
                    </Button>
                  }
                />
              ) : report.data?.reportMarkdown ? (
                <ReviewReportReader
                  key={taskId}
                  markdown={report.data.reportMarkdown}
                />
              ) : (
                <div className={styles.empty}>
                  审核已完成，但报告为空，请联系管理员检查。
                </div>
              )}
            </>
          )}
        </section>
      )}
      <Drawer
        title="本次提交的剧本原文"
        open={originalOpen && !historyOpen}
        onClose={() => setOriginalOpen(false)}
        size={720}
        footer={
          <Button
            type="primary"
            disabled={!original.data || busy}
            onClick={() => void reuseOriginal()}
          >
            基于原文再次审核
          </Button>
        }
      >
        {original.isPending && <p role="status">正在加载原文…</p>}
        {original.isError && (
          <Alert
            type="error"
            title="原文加载失败"
            action={
              <Button onClick={() => void original.refetch()}>
                重新加载原文
              </Button>
            }
          />
        )}
        {original.data && (
          <pre className={styles.originalText}>{original.data.content}</pre>
        )}
      </Drawer>
    </div>
  );
}

export default function ScriptReviewLibraryPage() {
  return (
    <ReviewQueries>
      {(tenantId) => <ReviewPage tenantId={tenantId} />}
    </ReviewQueries>
  );
}
