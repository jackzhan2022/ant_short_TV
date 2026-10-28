import { createDownloadTask } from '@/pages/downloads/service';
import {
  AuditOutlined,
  CopyOutlined,
  DownloadOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { skipToken, useQuery, useQueryClient } from '@tanstack/react-query';
import { history, useLocation } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Pagination,
  Progress,
  Space,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { statusText } from '@/utils/fieldDictionary';
import {
  DEFAULT_REVIEW_DIMENSIONS,
  REVIEW_DIMENSIONS,
} from '../script-review/dimensions';
import ReportIssueList from '../script-review/ReportIssueList';
import {
  cancelSimpleReview,
  getReviewContent,
  getReviewHistory,
  getReviewProgress,
  getReviewReport,
  retrySimpleReview,
  submitReview,
} from '../script-review/simpleService';
import ScriptContentImport from '../short-drama-creation/ScriptContentImport';
import styles from './index.module.css';
import { ReviewQueries, reviewPollingDelay } from './queries';

const MAX_CONTENT_LENGTH = 200000;
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
  const [more, setMore] = useState(false);
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
    try { await createDownloadTask(`审核报告_${taskId}`, [{ kind: 'REVIEW', id: taskId }]); }
    catch (error) { message.error(errorText(error)); }
  };

  if (!tenantId) return <div className={styles.empty}>请先选择当前团队</div>;
  const running =
    progress.data && ['PENDING', 'RUNNING'].includes(progress.data.status);
  const shownDimensions = more
    ? REVIEW_DIMENSIONS
    : REVIEW_DIMENSIONS.slice(0, 6);
  return (
    <div className={styles.page}>
      <header className={styles.toolbar}>
        <span className={styles.breadcrumb}>创作 / 剧本审核</span>
        <Button
          icon={<HistoryOutlined />}
          disabled={busy}
          onClick={() => setHistoryOpen(true)}
        >
          历史报告
        </Button>
      </header>
      {actionError && <Alert type="error" title={actionError} showIcon />}
      {!taskId ? (
        <section className={styles.composer}>
          <h1 className={styles.hero}>剧本审核</h1>
          <p className={styles.intro}>输入剧本，选择关注的维度，获取审核报告</p>
          <div className={styles.inputPanel}>
            <label
              className={styles.inputHeading}
              htmlFor="review-script-content"
            >
              剧本内容
            </label>
            <Input.TextArea
              id="review-script-content"
              aria-label="剧本内容"
              className={styles.textarea}
              value={content}
              maxLength={MAX_CONTENT_LENGTH}
              disabled={busy}
              placeholder="复制粘贴剧本，或导入文件（支持 txt、md、docx）"
              onChange={(event) => setContent(event.target.value)}
            />
            <section className={styles.dimensions} aria-label="审核维度">
              <div className={styles.dimensionHeader}>
                <div>
                  审核维度<span>已选 {dimensions.length} 项</span>
                </div>
                <Button
                  type="link"
                  size="small"
                  onClick={() => setMore(!more)}
                  aria-expanded={more}
                >
                  {more ? '收起' : '更多维度'}
                </Button>
              </div>
              <div className={styles.dimensionOptions}>
                {shownDimensions.map((dimension) => (
                  <div className={styles.chip} key={dimension}>
                    <Checkbox
                      checked={dimensions.includes(dimension)}
                      disabled={busy}
                      onChange={(event) =>
                        setDimensions((current) =>
                          event.target.checked
                            ? [...current, dimension]
                            : current.filter((value) => value !== dimension),
                        )
                      }
                    >
                      {dimension}
                    </Checkbox>
                  </div>
                ))}
              </div>
            </section>
            <div className={styles.footer}>
              <Space>
                <ScriptContentImport
                  className={styles.upload}
                  disabled={busy}
                  localOnly
                  currentContent={content}
                  onImport={(value) => {
                    if (value.length > MAX_CONTENT_LENGTH) {
                      message.error('剧本不能超过 200,000 字，请拆分后审核');
                      return;
                    }
                    setContent(value);
                  }}
                />
                <span className={styles.meta}>
                  {content.length.toLocaleString()} / 200,000 字
                </span>
              </Space>
              <Button
                className={styles.submit}
                type="primary"
                shape="round"
                icon={<AuditOutlined />}
                loading={busy}
                disabled={!content.trim() || !dimensions.length}
                onClick={submit}
              >
                开始审核
              </Button>
            </div>
          </div>
          <p className={styles.hint}>
            按所选维度审核全部输入内容，提交前确认预估积分
          </p>
        </section>
      ) : (
        <section className={styles.report}>
          <div className={styles.reportHeader}>
            <div>
              <h1>
                {report.data?.task.name
                  ? `${report.data.task.name} · 审核报告`
                  : '审核报告'}
              </h1>
              {report.data && (
                <div className={styles.meta}>
                  {dateText(report.data.task.createdAt)} ·{' '}
                  {report.data.task.selectedDimensions.join('、')}
                </div>
              )}
            </div>
            <Space wrap>
              <Button disabled={busy} onClick={returnToInput}>
                返回输入
              </Button>
              {report.data?.reportMarkdown && (
                <>
                  <Button icon={<CopyOutlined />} onClick={copyReport}>
                    复制报告
                  </Button>
                  <Button icon={<DownloadOutlined />} onClick={downloadReport}>
                    下载报告
                  </Button>
                </>
              )}
            </Space>
          </div>
          {progress.isPending && (
            <div className={styles.progress} role="status">
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
              <h2>{statusText(progress.data.status)}</h2>
              <Progress percent={progress.data.overallProgress} />
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
              <details
                className={styles.original}
                open={originalOpen}
                onToggle={(event) => setOriginalOpen(event.currentTarget.open)}
              >
                <summary>查看本次提交的剧本原文</summary>
                {original.isPending && <p>正在加载原文…</p>}
                {original.isError && (
                  <Button onClick={() => void original.refetch()}>
                    重新加载原文
                  </Button>
                )}
                {original.data && <pre>{original.data.content}</pre>}
                <Button disabled={original.isPending} onClick={reuseOriginal}>
                  基于原文再次审核
                </Button>
              </details>
              <div className={styles.body}>
                {report.isPending ? (
                  <p role="status">正在加载审核报告…</p>
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
                  <ReportIssueList markdown={report.data.reportMarkdown} />
                ) : (
                  <p>审核已完成，但报告为空，请联系管理员检查。</p>
                )}
              </div>
            </>
          )}
        </section>
      )}
      <Drawer
        title="历史审核报告"
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        size={640}
      >
        {records.isPending ? (
          <p role="status">正在加载历史报告…</p>
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
            {!records.data?.items.length && (
              <p className={styles.empty}>暂无审核记录</p>
            )}
            {records.data?.items.map((item) => (
              <article className={styles.historyItem} key={item.id}>
                <h3>{item.name}</h3>
                <p className={styles.meta}>
                  {dateText(item.createdAt)} · {statusText(item.status)}
                </p>
                <div className={styles.historyActions}>
                  <p className={styles.meta}>
                    {item.selectedDimensions.join('、')}
                  </p>
                  <Button onClick={() => openTask(item.id)}>
                    {item.status === 'COMPLETED' ? '查看报告' : '查看进度'}
                  </Button>
                </div>
              </article>
            ))}
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
