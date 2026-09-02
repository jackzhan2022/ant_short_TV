import {
  AuditOutlined,
  CheckCircleOutlined,
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
  Col,
  Empty,
  Input,
  List,
  Modal,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import { statusText } from '@/utils/fieldDictionary';
import type {
  ReviewIssue,
  ReviewProjectDetail,
  ReviewTask,
} from './service';
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

const DIMENSIONS = [
  '台词合理性',
  '人物关系一致性',
  '人物认知一致性',
  '人物动机',
  '时间线连续性',
  '场景连续性',
  '道具连续性',
  '视觉连续性',
  '剧情逻辑与因果',
  '分镜可执行性',
  '情绪递进',
  '悬念与反转铺垫',
  '伏笔回收',
];

const statusColor = (status: string) => {
  if (['COMPLETED', 'fixed'].includes(status)) return 'green';
  if (['FAILED', 'P2', 'P1'].includes(status)) return 'red';
  if (['RUNNING', 'persists'].includes(status)) return 'blue';
  if (['CANCELED', 'processed'].includes(status)) return 'default';
  return 'gold';
};

const ScriptReviewPage = () => {
  const { message, modal } = App.useApp();
  const [detail, setDetail] = useState<ReviewProjectDetail>();
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(() => {
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
  const [dimensions, setDimensions] = useState<string[]>(
    DIMENSIONS.slice(0, 3),
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

  useEffect(() => {
    loadProjects().catch(() => message.error('加载审核项目失败'));
  }, []);

  useEffect(() => {
    if (selectedProjectId) {
      loadProject(selectedProjectId).catch(() =>
        message.error('加载剧本审核工作台失败'),
      );
    }
  }, [selectedProjectId]);

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
      message.warning(scopeType === 'EPISODES' ? '请输入要审核的集数' : '请输入要审核的场次编号');
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
    if (!selectedProjectId || !selectedVersionId) return;
    const response = await exportReviewReport(
      selectedProjectId,
      selectedVersionId,
      'MARKDOWN',
    );
    message.success(`导出记录已创建：${response.data?.fileName ?? '审核报告'}`);
    if (response.data?.downloadUrl) {
      window.open(response.data.downloadUrl, '_blank', 'noopener,noreferrer');
    }
  };

  const selectIssueFilter = (filter: 'PENDING' | 'PROCESSED') => {
    setIssueFilter(filter);
    const issues = filter === 'PENDING' ? visibleIssues : processedIssues;
    setSelectedIssueId(issues[0]?.id);
  };

  return (
    <PageContainer
      title="剧本审核工作台"
      extra={[
        <Button
          key="create-review"
          icon={<AuditOutlined />}
          type="primary"
          onClick={() => setReviewModalOpen(true)}
        >
          发起审核
        </Button>,
        <Button key="refresh" icon={<ReloadOutlined />} onClick={refresh}>
          刷新
        </Button>,
      ]}
    >
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          {!detail ? (
            <Card>
              <Empty description="请选择或导入一个独立剧本" />
            </Card>
          ) : (
            <Row gutter={[16, 16]}>
              <Col xs={24} xl={5}>
                <Card title="问题队列" loading={loading}>
                  {!selectedTask ? (
                    <Empty description="创建审核任务后显示问题队列" />
                  ) : (
                    <Space vertical style={{ width: '100%' }}>
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
                      <List
                        size="small"
                        dataSource={queueIssues}
                        locale={{ emptyText: '当前筛选下没有问题' }}
                        renderItem={(issue) => (
                          <List.Item
                            style={{
                              cursor: 'pointer',
                              background:
                                issue.id === selectedIssueId
                                  ? 'var(--app-color-primary-bg)'
                                  : undefined,
                            }}
                            onClick={() => setSelectedIssueId(issue.id)}
                          >
                            <Space vertical size={0}>
                              <Space wrap>
                                <Tag color={statusColor(issue.severity)}>
                                  {issue.issueNo}
                                </Tag>
                                <Tag>{issue.dimension}</Tag>
                              </Space>
                              <Typography.Text strong>{issue.title}</Typography.Text>
                              <Typography.Text type="secondary">
                                {issue.problem}
                              </Typography.Text>
                            </Space>
                          </List.Item>
                        )}
                      />
                    </Space>
                  )}
                </Card>
              </Col>
              <Col xs={24} xl={12}>
              <Card
                title={detail.project.name}
                extra={
                  <Space>
                    <Select
                      value={selectedVersionId}
                      style={{ width: 150 }}
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
                    <Button icon={<DownloadOutlined />} onClick={exportReport}>
                      导出
                    </Button>
                  </Space>
                }
              >
                <Row gutter={[16, 16]}>
                  <Col xs={24} xl={15}>
                    <Input.TextArea
                      ref={editorRef}
                      value={content}
                      onChange={(event) => setContent(event.target.value)}
                      autoSize={{ minRows: 20, maxRows: 36 }}
                      disabled={taskLocked}
                    />
                    <Space style={{ marginTop: 12 }}>
                      <Button
                        type="primary"
                        icon={taskLocked ? <LockOutlined /> : <SaveOutlined />}
                        disabled={taskLocked}
                        loading={saving}
                        onClick={saveVersion}
                      >
                        保存为新版本
                      </Button>
                      <Button
                        icon={<SwapOutlined />}
                        disabled={!selectedVersionId}
                        onClick={() =>
                          modal.confirm({
                            title: '还原此版本？',
                            content:
                              '还原会创建一个新的版本，不会删除现有历史。',
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
                    </Space>
                  </Col>
                  <Col xs={24} xl={9}>
                    {activeExecution ? (
                      <div style={{ marginTop: 12 }}>
                        <AiExecutionStatus
                          task={activeExecution}
                          busy={saving}
                        />
                      </div>
                    ) : null}
                    {detail.tasks.map((task) => (
                      <Card
                        key={task.id}
                        size="small"
                        style={{ marginTop: 12, cursor: 'pointer' }}
                        onClick={() => {
                          setSelectedTaskId(task.id);
                          setIssueFilter('PENDING');
                          setSelectedIssueId(
                            task.issues.find((issue) => !issue.manuallyResolved)
                              ?.id ?? task.issues[0]?.id,
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
                              {task.retryKind ? <Tag>重试：{task.retryKind}</Tag> : null}
                              {task.stale ? <Tag color="orange">输入已变化</Tag> : null}
                            </Space>
                          </div>
                        ) : null}
                        {task.fanout ? (
                          <div style={{ marginTop: 8 }}>
                            <Typography.Text type="secondary">
                              深度单元 {task.fanout.completedUnits}/{task.fanout.totalUnits}
                              {task.fanout.failedUnits > 0
                                ? ` · 失败 ${task.fanout.failedUnits}`
                                : ''}
                              {task.fanout.aggregationStatus
                                ? ` · 聚合 ${task.fanout.aggregationStatus}`
                                : ''}
                              {task.fanout.currentUnitId
                                ? ` · 当前单元 ${
                                    task.fanout.units.find(
                                      (unit) => unit.id === task.fanout?.currentUnitId,
                                    )?.unitNo ?? task.fanout.currentUnitId
                                  }`
                                : ''}
                            </Typography.Text>
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
                                    ? '重试失败单元'
                                    : '重试'}
                              </Button>
                            )}
                          </Space>
                        </div>
                      </Card>
                    ))}
                  </Col>
                </Row>
              </Card>
              </Col>

              <Col xs={24} xl={7}>
              <Card
                title={
                  <Space>
                    <span>审核问题</span>
                    {selectedTask && (
                      <Tag color={statusColor(selectedTask.status)}>
                        {selectedTask.summary?.overallConclusion ??
                          selectedTask.status}
                      </Tag>
                    )}
                  </Space>
                }
                loading={loading}
              >
                {!selectedTask ? (
                  <Empty description="创建审核任务后，这里会显示问题卡" />
                ) : (
                  <Space vertical style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary">
                      {selectedTask.summary?.summary ||
                        '问题会按维度聚合，支持多命中片段和人工处理。'}
                    </Typography.Paragraph>
                    {selectedIssue ? (
                      <Card size="small" title="问题详情">
                        <Space vertical style={{ width: '100%' }}>
                          <Space wrap>
                            <Tag color={statusColor(selectedIssue.severity)}>
                              {selectedIssue.issueNo}
                            </Tag>
                            <Tag>{selectedIssue.dimension}</Tag>
                            <Typography.Text strong>
                              {selectedIssue.title}
                            </Typography.Text>
                          </Space>
                          <Typography.Text>{selectedIssue.problem}</Typography.Text>
                          <Typography.Text type="secondary">
                            原文：{selectedIssue.excerpt || '未提供片段'}
                          </Typography.Text>
                          {selectedIssue.suggestion ? (
                            <Typography.Text type="success">
                              建议：{selectedIssue.suggestion}
                            </Typography.Text>
                          ) : null}
                          {!selectedIssue.manuallyResolved ? (
                            <Space>
                              <Button
                                icon={<CheckCircleOutlined />}
                                onClick={() => confirmResolve(selectedIssue)}
                              >
                                已处理
                              </Button>
                              <Button
                                type="primary"
                                onClick={() => applyRepair(selectedIssue)}
                              >
                                预览修订
                              </Button>
                            </Space>
                          ) : (
                            <Typography.Text type="success">
                              已于 {selectedIssue.manuallyResolvedAt ?? '当前轮次'} 人工处理
                            </Typography.Text>
                          )}
                        </Space>
                      </Card>
                    ) : null}
                    <List
                      loading={taskDetailLoading}
                      dataSource={queueIssues}
                      locale={{
                        emptyText: taskDetailError
                          ? '审核问题加载失败，请刷新重试'
                          : issueFilter === 'PENDING'
                            ? '当前没有未处理问题'
                            : '暂无人工处理记录',
                      }}
                      renderItem={(issue) => (
                        <List.Item
                          actions={
                            issue.manuallyResolved
                              ? []
                              : [
                                  <Button
                                    key="repair"
                                    size="small"
                                    type="primary"
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      applyRepair(issue);
                                    }}
                                  >
                                    批量修复
                                  </Button>,
                                ]
                          }
                          style={{
                            cursor: 'pointer',
                            background:
                              issue.id === selectedIssueId
                                ? 'var(--app-color-primary-bg)'
                                : undefined,
                          }}
                          onClick={() => setSelectedIssueId(issue.id)}
                        >
                          <List.Item.Meta
                            title={
                              <Space wrap>
                                <Tag color={statusColor(issue.severity)}>
                                  {issue.issueNo}
                                </Tag>
                                <Tag>{issue.dimension}</Tag>
                                <Tag color={statusColor(issue.status)}>
                                  {statusText(issue.status)}
                                </Tag>
                                <Typography.Text strong>
                                  {issue.title}
                                </Typography.Text>
                              </Space>
                            }
                            description={
                              <Space vertical size={4}>
                                <Typography.Text>
                                  {issue.problem}
                                </Typography.Text>
                                <Typography.Text type="secondary">
                                  原文：{issue.excerpt || '未提供片段'} · 命中{' '}
                                  {issue.hits.length} 处
                                </Typography.Text>
                                {issue.hits.length > 0 && (
                                  <Space vertical style={{ width: '100%' }}>
                                    <Checkbox.Group
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
                                          onClick={() =>
                                            openIssueHit(issue, hit)
                                          }
                                        >
                                          定位命中 {hit.hitNo}
                                        </Button>
                                      ))}
                                    </Space>
                                  </Space>
                                )}
                                {issue.suggestion && (
                                  <Typography.Text type="success">
                                    建议：{issue.suggestion}
                                  </Typography.Text>
                                )}
                              </Space>
                            }
                          />
                        </List.Item>
                      )}
                    />
                    {versionHistory && (
                      <Card size="small" title="版本历史">
                        <Space vertical style={{ width: '100%' }}>
                          <Typography.Text>
                            当前版本 V
                            {versionHistory.selectedVersion?.versionNo}
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
                                  <Typography.Text type="secondary">
                                    问题 {item.issueCount} · 已处理{' '}
                                    {item.processedIssueCount}
                                  </Typography.Text>
                                </Space>
                              </List.Item>
                            )}
                          />
                        </Space>
                      </Card>
                    )}
                  </Space>
                )}
              </Card>
              </Col>
            </Row>
          )}
        </Col>
      </Row>
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
            options={DIMENSIONS}
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
