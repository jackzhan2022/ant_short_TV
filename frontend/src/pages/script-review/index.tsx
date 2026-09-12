import {
  AuditOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  DownloadOutlined,
  LockOutlined,
  ReloadOutlined,
  SaveOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
  App,
  Button,
  Card,
  Checkbox,
  Drawer,
  Empty,
  Input,
  List,
  Modal,
  Progress,
  Radio,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import { statusText } from '@/utils/fieldDictionary';
import { DEFAULT_REVIEW_DIMENSIONS, REVIEW_DIMENSIONS } from './dimensions';
import styles from './index.module.css';
import { reportFindings } from './reportFindings';
import ReportIssueList from './ReportIssueList';
import type { ReviewIssue, ReviewProjectDetail, ReviewTask } from './service';
import {
  batchRepairReview,
  cancelReviewTask,
  createReviewTask,
  exportReviewReport,
  queryReviewProject,
  queryReviewProjects,
  queryReviewTask,
  queryReviewVersionHistory,
  resolveReviewIssue,
  retryReviewTask,
  rollbackReviewVersion,
  saveReviewVersion,
} from './service';

const taskIdFromPath = () => {
  const match = window.location.pathname.match(
    /^\/script-review\/tasks\/(\d+)$/,
  );
  return match ? Number(match[1]) : undefined;
};

const statusColor = (status: string) => {
  if (['COMPLETED', 'fixed'].includes(status)) return 'green';
  if (['FAILED', 'P2', 'P1'].includes(status)) return 'red';
  if (['RUNNING', 'persists'].includes(status)) return 'blue';
  if (['CANCELED', 'processed'].includes(status)) return 'default';
  return 'gold';
};

const formatReviewCacheUsage = (
  cache: NonNullable<ReviewTask['observability']>['cacheUsage'],
) => {
  const latency = `${Number((cache.latencyMs / 1000).toFixed(1))}s`;
  if (
    !cache.cacheObservable ||
    cache.cachedInputTokens == null ||
    cache.cacheHitRatio == null
  ) {
    return `缓存明细不可观测 · 输入 ${cache.promptTokens} tokens · 输出 ${cache.outputTokens} · 耗时 ${latency}`;
  }
  return `缓存 ${cache.cachedInputTokens} / ${cache.promptTokens} tokens · 命中率 ${(
    cache.cacheHitRatio * 100
  ).toFixed(2)}% · 耗时 ${latency}`;
};

const ScriptReviewPage = () => {
  const { message, modal } = App.useApp();
  const taskRouteId = taskIdFromPath();
  const [detail, setDetail] = useState<ReviewProjectDetail>();
  const [selectedProjectId, setSelectedProjectId] = useState<
    number | undefined
  >(() => {
    const projectId = Number(
      new URLSearchParams(window.location.search).get('projectId'),
    );
    return Number.isFinite(projectId) && projectId > 0 ? projectId : undefined;
  });
  const [selectedVersionId, setSelectedVersionId] = useState<number>();
  const [selectedTaskId, setSelectedTaskId] = useState<number>();
  const [taskDetails, setTaskDetails] = useState<Record<number, ReviewTask>>(
    {},
  );
  const [taskDetailLoading, setTaskDetailLoading] = useState(false);
  const [taskDetailError, setTaskDetailError] = useState(false);
  const [selectedIssueId, setSelectedIssueId] = useState<number>();
  const [content, setContent] = useState('');
  const [editing, setEditing] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [highlight, setHighlight] = useState('');
  const hitRef = useRef<HTMLElement>(null);
  const [dimensions, setDimensions] = useState<string[]>(
    DEFAULT_REVIEW_DIMENSIONS,
  );
  const [reviewMode, setReviewMode] = useState('QUICK');
  const [scopeType, setScopeType] = useState('ALL');
  const [scopeValues, setScopeValues] = useState('');
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [issueFilter, setIssueFilter] = useState<'PENDING' | 'PROCESSED'>(
    'PENDING',
  );
  const [hitSelections, setHitSelections] = useState<Record<number, number[]>>(
    {},
  );
  const [versionHistory, setVersionHistory] = useState<any>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();
  const editorRef = useRef<any>(null);

  const selectedTaskSummary = useMemo(
    () => detail?.tasks.find((task) => task.id === selectedTaskId),
    [detail?.tasks, selectedTaskId],
  );
  const selectedTask = selectedTaskId
    ? (taskDetails[selectedTaskId] ?? selectedTaskSummary)
    : undefined;
  const reportFindingCount = useMemo(
    () => reportFindings(selectedTask?.reportMarkdown ?? '').length,
    [selectedTask?.reportMarkdown],
  );
  const visibleIssues =
    selectedTask?.issues.filter((issue) => !issue.manuallyResolved) ?? [];
  const processedIssues =
    selectedTask?.issues.filter((issue) => issue.manuallyResolved) ?? [];
  const queueIssues =
    issueFilter === 'PENDING' ? visibleIssues : processedIssues;
  const selectedIssue = selectedTask?.issues.find(
    (issue) => issue.id === selectedIssueId,
  );
  const currentVersion = detail?.versions.find(
    (version) => version.id === selectedVersionId,
  );
  const taskLocked = selectedTask?.status === 'RUNNING';
  const versionMismatch = Boolean(
    selectedTask && selectedVersionId !== selectedTask.scriptVersionId,
  );
  const highlightStart = highlight ? content.indexOf(highlight) : -1;

  useEffect(() => {
    if (!queueIssues.some((issue) => issue.id === selectedIssueId)) {
      setSelectedIssueId(queueIssues[0]?.id);
    }
  }, [selectedTask, issueFilter, selectedIssueId]);

  useEffect(() => {
    setHighlight(
      selectedIssue?.hits[0]?.excerpt?.trim() ||
        selectedIssue?.excerpt?.trim() ||
        '',
    );
  }, [selectedIssue?.id, selectedIssue?.hits[0]?.excerpt, selectedIssue?.excerpt]);

  useEffect(() => {
    if (!editing && highlightStart >= 0)
      hitRef.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  }, [highlight, highlightStart, editing]);

  const selectIssue = (issue: ReviewIssue) => {
    setSelectedIssueId(issue.id);
    setHighlight(issue.hits[0]?.excerpt?.trim() || issue.excerpt?.trim() || '');
  };

  const resolveTextarea = () => {
    const current = editorRef.current;
    return (
      current?.resizableTextArea?.textArea ?? current?.input ?? current ?? null
    );
  };

  const openIssueHit = (
    issue: ReviewIssue,
    hit: ReviewIssue['hits'][number],
  ) => {
    const target = hit.excerpt?.trim() || issue.excerpt?.trim();
    if (!target) {
      message.warning('当前问题没有可定位的原文片段');
      return;
    }
    setHighlight(target);
    if (!editing) {
      if (!content.includes(target))
        message.warning('当前版本未找到可高亮的命中片段');
      else
        hitRef.current?.scrollIntoView?.({
          block: 'center',
          behavior: 'smooth',
        });
      return;
    }
    const textarea = resolveTextarea();
    const start = content.indexOf(target);
    if (textarea?.focus) {
      textarea.focus();
    }
    if (textarea?.setSelectionRange && start >= 0) {
      textarea.setSelectionRange(start, start + target.length);
      return;
    }
    message.warning('当前版本未找到可高亮的命中片段');
  };

  const loadProjects = async () => {
    const response = await queryReviewProjects();
    const nextProjects = response.data ?? [];
    if (!selectedProjectId && nextProjects[0]) {
      setSelectedProjectId(nextProjects[0].id);
    }
  };

  const loadProject = async (projectId: number) => {
    setLoading(true);
    try {
      const response = await queryReviewProject(projectId);
      const nextDetail = response.data;
      if (!nextDetail) return;
      setTaskDetails({});
      setDetail(nextDetail);
      const versionId =
        nextDetail.project.currentVersionId ?? nextDetail.versions[0]?.id;
      setSelectedVersionId(versionId);
      const taskId = nextDetail.project.lastTaskId ?? nextDetail.tasks[0]?.id;
      setSelectedTaskId(taskId);
      const task = nextDetail.tasks.find((item) => item.id === taskId);
      setSelectedIssueId(
        task?.issues.find((issue) => !issue.manuallyResolved)?.id ??
          task?.issues[0]?.id,
      );
      setIssueFilter('PENDING');
      setContent(
        nextDetail.versions.find((version) => version.id === versionId)
          ?.content ?? '',
      );
      if (versionId) {
        const historyResponse = await queryReviewVersionHistory(
          projectId,
          versionId,
        );
        setVersionHistory(historyResponse.data);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadTaskRoute = async (taskId: number) => {
    setLoading(true);
    try {
      const response = await queryReviewTask(taskId);
      const task = response.data;
      const version = task?.boundVersion;
      if (!task || !version) return;
      setSelectedProjectId(task.projectId);
      setSelectedTaskId(task.id);
      setSelectedVersionId(version.id);
      setTaskDetails({ [task.id]: task });
      setDetail({
        project: {
          id: task.projectId,
          name: '剧本审核',
          sourceType: version.sourceType,
          currentVersionId: version.id,
          lastTaskId: task.id,
          status: 'ACTIVE',
          versionCount: 1,
          latestRoundNo: task.roundNo,
        },
        versions: [version],
        tasks: [task],
      });
      setContent(version.content);
      setSelectedIssueId(
        task.issues.find((issue) => !issue.manuallyResolved)?.id ??
          task.issues[0]?.id,
      );
      setIssueFilter('PENDING');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (taskRouteId) {
      loadTaskRoute(taskRouteId).catch(() => message.error('加载审核详情失败'));
      return;
    }
    loadProjects().catch(() => message.error('加载审核项目失败'));
  }, [taskRouteId]);

  useEffect(() => {
    if (!taskRouteId && selectedProjectId) {
      loadProject(selectedProjectId).catch(() =>
        message.error('加载剧本审核工作台失败'),
      );
    }
  }, [selectedProjectId, taskRouteId]);

  useEffect(() => {
    if (!selectedTaskId || !selectedTaskSummary) {
      setTaskDetailLoading(false);
      setTaskDetailError(false);
      return;
    }
    if (selectedTaskSummary.issues.length > 0 || taskDetails[selectedTaskId]) {
      setTaskDetailLoading(false);
      setTaskDetailError(false);
      return;
    }

    let active = true;
    setTaskDetailLoading(true);
    setTaskDetailError(false);
    queryReviewTask(selectedTaskId)
      .then((response) => {
        if (!active || !response.data) return;
        setTaskDetails((current) => ({
          ...current,
          [selectedTaskId]: response.data,
        }));
      })
      .catch(() => {
        if (!active) return;
        setTaskDetailError(true);
        message.error('加载审核问题失败');
      })
      .finally(() => {
        if (active) setTaskDetailLoading(false);
      });

    return () => {
      active = false;
    };
  }, [message, selectedTaskId, selectedTaskSummary, taskDetails]);

  const refresh = async () => {
    if (taskRouteId) {
      await loadTaskRoute(taskRouteId);
      return;
    }
    await loadProjects();
    if (selectedProjectId) await loadProject(selectedProjectId);
  };

  const saveVersion = async () => {
    if (!selectedProjectId || !content.trim()) return;
    setSaving(true);
    try {
      const response = await saveReviewVersion(
        selectedProjectId,
        content,
        currentVersion?.fileName ?? undefined,
      );
      if (response.data) {
        message.success(`已保存为 V${response.data.versionNo}`);
        await loadProject(selectedProjectId);
        setEditing(false);
      }
    } finally {
      setSaving(false);
    }
  };

  const startReview = async () => {
    if (!selectedProjectId || !selectedVersionId || dimensions.length === 0) {
      message.warning('请选择版本和至少一个审核维度');
      return;
    }
    const selectedScopeValues = scopeValues
      .split(/[,，\n]/)
      .map((value) => value.trim())
      .filter(Boolean);
    if (scopeType !== 'ALL' && selectedScopeValues.length === 0) {
      message.warning(
        scopeType === 'EPISODES'
          ? '请输入要审核的集数'
          : '请输入要审核的场次编号',
      );
      return;
    }
    const reviewScope =
      scopeType === 'EPISODES'
        ? { episodeNos: selectedScopeValues }
        : scopeType === 'SCENES'
          ? { sceneKeys: selectedScopeValues }
          : {};
    setSaving(true);
    try {
      const response = await createReviewTask(selectedProjectId, {
        versionId: selectedVersionId,
        reviewMode,
        selectedDimensions: dimensions,
        reviewScopeType: scopeType,
        reviewScope,
      });
      setSelectedTaskId(response.data?.businessId);
      setReviewModalOpen(false);
      message.success('审核任务已创建，后台会持续更新进度');
      await followReviewExecution(response.data);
    } finally {
      setSaving(false);
    }
  };

  const followReviewExecution = async (task?: API.AiExecutionResponse) => {
    const tenantId = Number(localStorage.getItem('currentTenantId'));
    if (!task?.id || !tenantId || !selectedProjectId) {
      throw new Error('AI execution identity is missing');
    }
    setActiveExecution(task);
    const terminal = await aiExecutionTaskService.poll(
      tenantId,
      task.id,
      setActiveExecution,
    );
    setActiveExecution(terminal);
    await loadProject(selectedProjectId);
  };

  const confirmResolve = (issue: ReviewIssue) => {
    modal.confirm({
      title: `将 ${issue.issueNo} 标记为已处理？`,
      content: '人工标记会保留在问题历史中，后续复审仍可重新判定为仍存在。',
      okText: '标记已处理',
      cancelText: '取消',
      onOk: async () => {
        await resolveReviewIssue(issue.id);
        message.success('问题已移入已处理区');
        if (selectedProjectId) await loadProject(selectedProjectId);
      },
    });
  };

  const applyRepair = (issue: ReviewIssue) => {
    if (!selectedTask) return;
    const selectedHitIds =
      hitSelections[issue.id] ?? issue.hits.map((hit) => hit.id);
    if (selectedHitIds.length === 0) {
      message.warning('请先勾选至少一个命中片段');
      return;
    }
    modal.confirm({
      title: `修订预览 ${issue.issueNo}`,
      content: (
        <Space vertical style={{ width: '100%' }}>
          <Typography.Text>
            将命中片段中的「{issue.hits[0]?.excerpt ?? issue.excerpt}
            」全部替换为建议文本。
          </Typography.Text>
          <Typography.Text type="secondary">
            当前仅支持基础批量修复，并会自动保存为新版本。
          </Typography.Text>
        </Space>
      ),
      okText: '确认应用修订',
      cancelText: '取消',
      onOk: async () => {
        await batchRepairReview(selectedTask.id, {
          actionType: 'GLOBAL_REPLACE',
          replacementFrom: issue.hits[0]?.excerpt ?? issue.excerpt,
          replacementTo: issue.hits[0]?.replacementText ?? '',
          selectedHitIds,
        });
        message.success('批量修复已应用并生成新版本');
        if (selectedProjectId) {
          await loadProject(selectedProjectId);
        }
      },
    });
  };

  const exportReport = async () => {
    if (!selectedProjectId || !selectedVersionId || !selectedTask?.reportMarkdown) return;
    const response = await exportReviewReport(
      selectedProjectId,
      selectedVersionId,
      'MARKDOWN',
      selectedTask.id,
    );
    message.success(`导出记录已创建：${response.data?.fileName ?? '审核报告'}`);
    const downloadUrl = URL.createObjectURL(
      new Blob([selectedTask.reportMarkdown], { type: 'text/markdown;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = response.data?.fileName ?? '审核报告.md';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(downloadUrl);
  };

  const copyMarkdownReport = async () => {
    if (!selectedTask?.reportMarkdown) return;
    await navigator.clipboard.writeText(selectedTask.reportMarkdown);
    message.success('审核报告已复制');
  };

  const selectIssueFilter = (filter: 'PENDING' | 'PROCESSED') => {
    setIssueFilter(filter);
    const issues = filter === 'PENDING' ? visibleIssues : processedIssues;
    setSelectedIssueId(issues[0]?.id);
  };

  return (
    <PageContainer
      className={styles.page}
      title={detail?.project.name ?? '剧本审核详情'}
      extra={[
        <Button key="history" onClick={() => setHistoryOpen(true)}>
          审核记录
        </Button>,
        <Button
          key="export"
          icon={<DownloadOutlined />}
          disabled={!detail}
          onClick={exportReport}
        >
          导出报告
        </Button>,
        <Button key="refresh" icon={<ReloadOutlined />} onClick={refresh}>
          刷新
        </Button>,
        <Button
          key="create-review"
          icon={<AuditOutlined />}
          type="primary"
          disabled={!detail}
          onClick={() => setReviewModalOpen(true)}
        >
          发起审核
        </Button>,
      ]}
    >
      {!detail ? (
        <Card loading={loading}>
          <Empty description="请选择或导入一个独立剧本" />
        </Card>
      ) : (
        <>
          <div className={styles.overview}>
            <Space wrap>
              {selectedTask && (
                <>
                  <Tag color={statusColor(selectedTask.status)}>
                    {statusText(selectedTask.status)}
                  </Tag>
                  <span>
                    第 {selectedTask.roundNo} 轮 ·{' '}
                    {selectedTask.reviewMode === 'DEEP'
                      ? '深度审核'
                      : '快速审核'}
                  </span>
                </>
              )}
              <span>当前剧本 V{currentVersion?.versionNo ?? '-'}</span>
              {!taskDetailLoading && !taskDetailError && (
                selectedTask?.resultFormat === 'MARKDOWN' ? (
                  reportFindingCount > 0 ? (
                    <output aria-label="报告问题总数">报告共 {reportFindingCount} 项问题</output>
                  ) : null
                ) : (
                  <span>
                    {visibleIssues.length} 项未处理 · {processedIssues.length}{' '}
                    项已处理
                  </span>
                )
              )}
              {versionMismatch && (
                <Tag color="orange">
                  当前正文与审核版本不同，请切回审核版本查看证据
                </Tag>
              )}
            </Space>
            {selectedTask &&
              ['PENDING', 'RUNNING', 'FAILED'].includes(
                selectedTask.status,
              ) && (
                <div className={styles.progress}>
                  <Progress
                    percent={selectedTask.overallProgress}
                    size="small"
                  />
                  <Typography.Text type="secondary">
                    {selectedTask.currentAction ??
                      statusText(selectedTask.status)}
                  </Typography.Text>
                  <Button size="small" onClick={() => setHistoryOpen(true)}>
                    查看执行详情
                  </Button>
                </div>
              )}
            {activeExecution && (
              <AiExecutionStatus task={activeExecution} busy={saving} />
            )}
          </div>
          <div className={styles.workspace}>
            <section className={styles.reader} aria-label="剧本正文">
              <div className={styles.toolbar}>
                <Space wrap>
                  <Select
                    value={selectedVersionId}
                    style={{ width: 150 }}
                    disabled={content !== currentVersion?.content}
                    onChange={async (versionId) => {
                      const nextVersionId = Number(versionId);
                      setSelectedVersionId(nextVersionId);
                      setContent(
                        detail.versions.find(
                          (version) => version.id === nextVersionId,
                        )?.content ?? '',
                      );
                      if (selectedProjectId) {
                        const response = await queryReviewVersionHistory(
                          selectedProjectId,
                          nextVersionId,
                        );
                        setVersionHistory(response.data);
                      }
                    }}
                    options={detail.versions.map((version) => ({
                      value: version.id,
                      label: `版本 V${version.versionNo}`,
                    }))}
                  />

                  <Typography.Text type="secondary">
                    {editing ? '编辑模式' : '阅读模式'}
                  </Typography.Text>
                </Space>
                <Space wrap>
                  <Button onClick={() => setHistoryOpen(true)}>版本历史</Button>
                  <Button
                    disabled={taskLocked}
                    onClick={() => setEditing(!editing)}
                  >
                    {editing ? '返回阅读' : '编辑剧本'}
                  </Button>
                </Space>
              </div>
              <section aria-label="审核维度标签" style={{ marginBottom: 16 }}>
                    <Space wrap size={[4, 8]}>
                      {selectedTask?.selectedDimensions.map((dimension) => (
                        <Tag key={dimension} color="blue">{dimension}</Tag>
                      ))}
                    </Space>
                  </section>
              <div className={styles.document}>
                {editing ? (
                  <Input.TextArea
                    ref={editorRef}
                    value={content}
                    onChange={(event) => setContent(event.target.value)}
                    autoSize={{ minRows: 24 }}
                    disabled={taskLocked}
                    aria-label="编辑剧本正文"
                  />
                ) : (
                  <div className={styles.paper}>
                    {content ? (
                      highlightStart >= 0 ? (
                        <>
                          {content.slice(0, highlightStart)}
                          <mark ref={hitRef} className={styles.highlight}>
                            {content.slice(
                              highlightStart,
                              highlightStart + highlight.length,
                            )}
                          </mark>
                          {content.slice(highlightStart + highlight.length)}
                        </>
                      ) : (
                        content
                      )
                    ) : (
                      <Empty description="当前版本没有正文" />
                    )}
                  </div>
                )}
              </div>
              <div className={styles.readerFooter}>
                {editing || content !== currentVersion?.content ? (
                  <Space wrap>
                    <Button
                      type="primary"
                      icon={taskLocked ? <LockOutlined /> : <SaveOutlined />}
                      disabled={taskLocked}
                      loading={saving}
                      onClick={saveVersion}
                    >
                      保存为新版本
                    </Button>
                  </Space>
                ) : (
                  <Typography.Text type="secondary">
                    选择右侧问题，定位原文证据
                  </Typography.Text>
                )}
                <Typography.Text type="secondary">
                  {content.length.toLocaleString()} 字符
                  {content !== currentVersion?.content ? ' · 有未保存修改' : ''}
                </Typography.Text>
              </div>
            </section>
            <aside className={styles.review} aria-label="审核问题">
              <div className={styles.reviewHeader}>
                <div className={styles.reviewTitle}>
                  审核问题{' '}
                  {selectedTask?.summary?.overallConclusion && (
                    <Tag>{selectedTask.summary.overallConclusion}</Tag>
                  )}
                </div>
                <p className={styles.summary}>
                  {selectedTask?.summary?.summary ??
                    '逐条核对证据，确认修改建议。'}
                </p>
                {selectedTask?.summary?.summary && (
                  <Button
                    type="link"
                    size="small"
                    onClick={() => setHistoryOpen(true)}
                  >
                    完整摘要
                  </Button>
                )}
                {selectedTask?.resultFormat !== 'MARKDOWN' ? (
                <Space wrap>
                  <Button
                    type={issueFilter === 'PENDING' ? 'primary' : 'default'}
                    onClick={() => selectIssueFilter('PENDING')}
                  >
                    未处理 ({visibleIssues.length})
                  </Button>
                  <Button
                    type={issueFilter === 'PROCESSED' ? 'primary' : 'default'}
                    onClick={() => selectIssueFilter('PROCESSED')}
                  >
                    已处理 ({processedIssues.length})
                  </Button>
                </Space>
                ) : null}
              </div>
              <div className={styles.issueList}>
                {!selectedTask ? (
                  <Empty description="创建审核任务后，这里会显示问题卡" />
                ) : selectedTask.resultFormat === 'MARKDOWN' ? (
                  <div className={styles.report}>
                    {selectedTask.status === 'FAILED' ? (
                      <Typography.Paragraph type="danger">
                        {selectedTask.errorMessage ?? '报告生成失败，可从任务卡片重试。'}
                      </Typography.Paragraph>
                    ) : null}
                    <Space>
                      <Button
                        icon={<CopyOutlined />}
                        disabled={!selectedTask.reportMarkdown}
                        onClick={copyMarkdownReport}
                      >
                        复制报告
                      </Button>
                      <Button
                        icon={<DownloadOutlined />}
                        disabled={!selectedTask.reportMarkdown}
                        onClick={exportReport}
                      >
                        下载 .md
                      </Button>
                    </Space>
                    {selectedTask.reportMarkdown ? (
                      <ReportIssueList markdown={selectedTask.reportMarkdown} />
                    ) : (
                      <Empty
                        description={
                          selectedTask.status === 'COMPLETED'
                            ? '报告内容为空'
                            : '报告生成中'
                        }
                      />
                    )}
                  </div>
                ) : (
                  <>
                    {selectedTask.observability?.humanReviewFindings.length ? (
                      <Card
                        size="small"
                        title={`待人工复核 (${selectedTask.observability.humanReviewFindings.length})`}
                      >
                        <List
                          size="small"
                          dataSource={
                            selectedTask.observability.humanReviewFindings
                          }
                          rowKey="candidateId"
                          renderItem={(finding) => (
                            <List.Item>
                              <List.Item.Meta
                                title={
                                  <Space wrap>
                                    <Tag color="gold">{finding.dimension}</Tag>
                                    <Typography.Text strong>
                                      {typeof finding.candidate.title ===
                                      'string'
                                        ? finding.candidate.title
                                        : `候选 ${finding.candidateId}`}
                                    </Typography.Text>
                                  </Space>
                                }
                                description={finding.rationale}
                              />
                            </List.Item>
                          )}
                        />
                      </Card>
                    ) : null}

                    {taskDetailLoading ? (
                      <Typography.Text type="secondary">
                        正在加载审核问题…
                      </Typography.Text>
                    ) : taskDetailError ? (
                      <Empty description="审核问题加载失败，请刷新重试" />
                    ) : queueIssues.length === 0 ? (
                      <Empty
                        description={
                          issueFilter === 'PENDING'
                            ? '当前没有未处理问题'
                            : '暂无人工处理记录'
                        }
                      />
                    ) : (
                      queueIssues.map((issue) => (
                        <section
                          key={issue.id}
                          className={
                            issue.id === selectedIssueId
                              ? styles.activeIssue
                              : styles.issue
                          }
                        >
                          <button
                            type="button"
                            className={styles.issueToggle}
                            aria-expanded={issue.id === selectedIssueId}
                            onClick={() => selectIssue(issue)}
                          >
                            <span className={styles.issueMeta}>
                              <Tag color={statusColor(issue.severity)}>
                                {issue.severity}
                              </Tag>
                              <span>
                                {issue.issueNo} · {issue.dimension}
                              </span>
                            </span>
                            <strong>{issue.title}</strong>
                            <span className={styles.issueMeta}>
                              {issue.hits[0]?.anchorLabel ?? '查看原文证据'} ·
                              命中 {issue.hits.length} 处
                            </span>
                          </button>
                          {issue.id === selectedIssueId && (
                            <div className={styles.issueBody}>
                              <div className={styles.label}>问题详情</div>
                              <Typography.Paragraph>
                                {issue.problem}
                              </Typography.Paragraph>
                              <div className={styles.quote}>
                                原文：{issue.excerpt || '未提供片段'}
                              </div>
                              {issue.suggestion && (
                                <div className={styles.suggestion}>
                                  建议：{issue.suggestion}
                                </div>
                              )}
                              {issue.hits.length > 0 && (
                                <div className={styles.hits}>
                                  <div className={styles.label}>
                                    选择修订片段
                                  </div>
                                  <Checkbox.Group
                                    className={styles.hitOptions}
                                    value={
                                      hitSelections[issue.id] ??
                                      issue.hits.map((hit) => hit.id)
                                    }
                                    onChange={(values) =>
                                      setHitSelections((current) => ({
                                        ...current,
                                        [issue.id]: values as number[],
                                      }))
                                    }
                                    options={issue.hits.map((hit) => ({
                                      label: `${hit.hitNo}. ${hit.anchorLabel ?? '未标注位置'}：${hit.excerpt}`,
                                      value: hit.id,
                                    }))}
                                  />
                                  <Space wrap>
                                    {issue.hits.map((hit) => (
                                      <Button
                                        key={hit.id}
                                        size="small"
                                        type="link"
                                        onClick={() => openIssueHit(issue, hit)}
                                      >
                                        定位命中 {hit.hitNo}
                                      </Button>
                                    ))}
                                  </Space>
                                </div>
                              )}
                              {issue.manuallyResolved ? (
                                <Typography.Text type="success">
                                  已于 {issue.manuallyResolvedAt ?? '当前轮次'}{' '}
                                  人工处理
                                </Typography.Text>
                              ) : (
                                <div className={styles.issueActions}>
                                  <Button
                                    icon={<CheckCircleOutlined />}
                                    onClick={() => confirmResolve(issue)}
                                  >
                                    已处理
                                  </Button>
                                  <Button
                                    type="primary"
                                    onClick={() => applyRepair(issue)}
                                  >
                                    预览修订
                                  </Button>
                                </div>
                              )}
                            </div>
                          )}
                        </section>
                      ))
                    )}
                  </>
                )}
              </div>
              <div className={styles.reviewFooter}>
                AI 建议仅供参考，修改由你决定。
              </div>
            </aside>
          </div>
          <Drawer
            title="审核记录与版本历史"
            open={historyOpen}
            onClose={() => setHistoryOpen(false)}
            size={560}
            destroyOnHidden
          >
            {selectedTask?.summary?.summary && (
              <Card size="small" title="审核摘要">
                <Typography.Paragraph>
                  {selectedTask.summary.summary}
                </Typography.Paragraph>
              </Card>
            )}
            {detail.tasks.map((task) => (
              <Card
                key={task.id}
                size="small"
                style={{ marginTop: 12, cursor: 'pointer' }}
                onClick={() => {
                  setSelectedTaskId(task.id);
                  setIssueFilter('PENDING');
                  setSelectedIssueId(
                    task.issues.find((issue) => !issue.manuallyResolved)?.id ??
                      task.issues[0]?.id,
                  );
                }}
                title={`第 ${task.roundNo} 轮 · ${task.reviewMode}`}
                extra={
                  <Tag color={statusColor(task.status)}>
                    {statusText(task.status)}
                  </Tag>
                }
              >
                <Progress percent={task.overallProgress} size="small" />
                <Typography.Text type="secondary">
                  {task.currentAction ?? '等待任务执行'}
                </Typography.Text>
                {task.workflowAgentCode ? (
                  <div style={{ marginTop: 8 }}>
                    <Space wrap>
                      <Tag color="geekblue">Agent：剧本审核</Tag>
                      <Tag>{task.workflowPhase ?? task.reviewMode}</Tag>
                      {task.selectedDimensions.map((dimension) => (
                        <Tag key={dimension}>Skill：{dimension}</Tag>
                      ))}
                      {task.retryKind ? (
                        <Tag>重试：{task.retryKind}</Tag>
                      ) : null}
                      {task.stale ? <Tag color="orange">输入已变化</Tag> : null}
                    </Space>
                  </div>
                ) : null}
                {task.fanout ? (
                  <div style={{ marginTop: 8 }}>
                    <Typography.Text type="secondary">
                      {task.fanout.units.some((unit) => unit.dimension)
                        ? '审核维度'
                        : '深度单元'}{' '}
                      {task.fanout.completedUnits}/{task.fanout.totalUnits}
                      {task.fanout.failedUnits > 0
                        ? ` · 失败 ${task.fanout.failedUnits}`
                        : ''}
                      {task.fanout.aggregationStatus
                        ? ` · 聚合 ${task.fanout.aggregationStatus}`
                        : ''}
                      {task.fanout.currentUnitId
                        ? ` · 当前 ${
                            task.fanout.units.find(
                              (unit) => unit.id === task.fanout?.currentUnitId,
                            )?.dimension ??
                            `单元 ${
                              task.fanout.units.find(
                                (unit) =>
                                  unit.id === task.fanout?.currentUnitId,
                              )?.unitNo ?? task.fanout.currentUnitId
                            }`
                          }`
                        : ''}
                    </Typography.Text>
                    {task.fanout.units.some((unit) => unit.dimension) ? (
                      <div style={{ marginTop: 6 }}>
                        <Space wrap size={[4, 4]}>
                          {task.fanout.units.map((unit) => (
                            <Tag
                              key={unit.id}
                              color={statusColor(unit.status)}
                              title={unit.errorMessage ?? undefined}
                            >
                              {unit.dimension} · {statusText(unit.status)}
                              {unit.attemptNo
                                ? ` · 第 ${unit.attemptNo} 次`
                                : ''}
                            </Tag>
                          ))}
                        </Space>
                      </div>
                    ) : null}
                  </div>
                ) : null}
                {task.observability ? (
                  <div style={{ marginTop: 8 }}>
                    {task.observability.quality ? (
                      <Typography.Text type="secondary">
                        语义质检：
                        {statusText(task.observability.quality.status)} · 候选{' '}
                        {task.observability.quality.candidateCount ?? 0} / 裁决{' '}
                        {task.observability.quality.decisionCount ?? 0}
                      </Typography.Text>
                    ) : null}
                    <div style={{ marginTop: 6 }}>
                      <Space wrap size={[4, 4]}>
                        <Tag color="green">
                          已确认 {task.observability.decisions.confirmed}
                        </Tag>
                        <Tag color="gold">
                          待人工 {task.observability.decisions.needsHumanReview}
                        </Tag>
                        <Tag>
                          已驳回 {task.observability.decisions.rejected}
                        </Tag>
                        <Tag>
                          证据不足{' '}
                          {task.observability.decisions.insufficientEvidence}
                        </Tag>
                        {task.observability.quality?.anomalyRequired ? (
                          <Tag
                            color={
                              task.observability.quality.anomalyPassed
                                ? 'green'
                                : 'red'
                            }
                          >
                            {task.observability.quality.anomalyPassed
                              ? '异常复核通过'
                              : '异常复核未通过'}
                          </Tag>
                        ) : null}
                      </Space>
                    </div>
                    <div style={{ marginTop: 6 }}>
                      <Typography.Text type="secondary">
                        {formatReviewCacheUsage(task.observability.cacheUsage)}
                      </Typography.Text>
                    </div>
                  </div>
                ) : null}
                <div style={{ marginTop: 8 }}>
                  <Space>
                    {['PENDING', 'RUNNING'].includes(task.status) && (
                      <Button
                        size="small"
                        onClick={() => {
                          cancelReviewTask(task.id).then((response) =>
                            followReviewExecution(response.data),
                          );
                        }}
                      >
                        取消
                      </Button>
                    )}
                    {task.status === 'FAILED' && (
                      <Button
                        size="small"
                        onClick={() => {
                          retryReviewTask(task.id).then((response) =>
                            followReviewExecution(response.data),
                          );
                        }}
                      >
                        {task.retryKind === 'AGGREGATION_ONLY'
                          ? '仅重试聚合'
                          : task.retryKind === 'FAILED_UNITS'
                            ? task.fanout?.units.some((unit) => unit.dimension)
                              ? '重试失败维度'
                              : '重试失败单元'
                            : '重试'}
                      </Button>
                    )}
                  </Space>
                </div>
              </Card>
            ))}

            {versionHistory && (
              <Card size="small" title="版本历史">
                <Space vertical style={{ width: '100%' }}>
                  <Typography.Text>
                    当前版本 V{versionHistory.selectedVersion?.versionNo}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    差异行数：+
                    {versionHistory.diffLines?.[0]?.addedLines ?? 0} / -
                    {versionHistory.diffLines?.[0]?.removedLines ?? 0}
                  </Typography.Text>
                  <List
                    size="small"
                    dataSource={versionHistory.roundHistory ?? []}
                    renderItem={(item: any) => (
                      <List.Item>
                        <Space vertical size={0}>
                          <Typography.Text>
                            第 {item.roundNo} 轮 · {statusText(item.status)}
                          </Typography.Text>
                          {detail.tasks.find((task) => task.id === item.taskId)?.resultFormat !== 'MARKDOWN' && (
                          <Typography.Text type="secondary">
                            问题 {item.issueCount} · 已处理{' '}
                            {item.processedIssueCount}
                          </Typography.Text>
                          )}
                        </Space>
                      </List.Item>
                    )}
                  />
                </Space>
              </Card>
            )}
            <Button
              icon={<SwapOutlined />}
              disabled={!selectedVersionId}
              onClick={() =>
                modal.confirm({
                  title: '还原此版本？',
                  content: '还原会创建一个新的版本，不会删除现有历史。',
                  onOk: async () => {
                    if (!selectedProjectId || !selectedVersionId) {
                      return;
                    }
                    await rollbackReviewVersion(
                      selectedProjectId,
                      selectedVersionId,
                    );
                    message.success('已生成还原版本');
                    await loadProject(selectedProjectId);
                  },
                })
              }
            >
              还原当前版本
            </Button>
          </Drawer>
        </>
      )}
      <Modal
        centered
        confirmLoading={saving}
        okText="开始审核"
        open={reviewModalOpen}
        title="新建审核任务"
        width={680}
        onCancel={() => setReviewModalOpen(false)}
        onOk={startReview}
      >
        <Space vertical size="middle" style={{ width: '100%' }}>
          <Typography.Text>
            审核版本：V{currentVersion?.versionNo ?? '-'}
          </Typography.Text>
          <Checkbox.Group
            value={dimensions}
            onChange={(values) => setDimensions(values as string[])}
            disabled={taskLocked}
            options={REVIEW_DIMENSIONS}
          />
          <Radio.Group
            value={reviewMode}
            onChange={(event) => setReviewMode(event.target.value)}
            disabled={taskLocked}
            optionType="button"
            options={[
              { label: '快速审核', value: 'QUICK' },
              { label: '深度审核', value: 'DEEP' },
            ]}
          />
          <Select
            value={scopeType}
            onChange={setScopeType}
            disabled={taskLocked}
            options={[
              { label: '整本剧本', value: 'ALL' },
              { label: '指定集', value: 'EPISODES' },
              { label: '指定场', value: 'SCENES' },
            ]}
          />
          {scopeType !== 'ALL' ? (
            <Input
              value={scopeValues}
              onChange={(event) => setScopeValues(event.target.value)}
              disabled={taskLocked}
              placeholder={
                scopeType === 'EPISODES'
                  ? '输入集数，用逗号分隔，如 1, 2, 3'
                  : '输入场次编号，用逗号分隔，如 1-2, 2-1'
              }
            />
          ) : null}
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default ScriptReviewPage;
