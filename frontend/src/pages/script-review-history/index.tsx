import { PageContainer } from '@ant-design/pro-components';
import { App, Button, Card, Checkbox, Empty, List, Modal, Progress, Select, Space, Tag, Typography } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import { cancelReviewTask, createReviewTask, queryReviewProjectHistory, retryReviewTask, type ReviewProjectHistory } from '../script-review/service';

const DEFAULT_DIMENSIONS = ['台词合理性', '人物关系一致性', '人物认知一致性'];

const statusColor = (status: string) => {
  if (status === 'COMPLETED') return 'green';
  if (status === 'FAILED') return 'red';
  if (['PENDING', 'RUNNING'].includes(status)) return 'blue';
  return 'default';
};

const projectIdFromPath = () => {
  const match = window.location.pathname.match(/^\/script-review\/projects\/(\d+)\/reviews$/);
  return match ? Number(match[1]) : undefined;
};

const ScriptReviewHistoryPage = () => {
  const { message } = App.useApp();
  const projectId = projectIdFromPath();
  const [data, setData] = useState<ReviewProjectHistory>();
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [versionId, setVersionId] = useState<number>();
  const [reviewMode, setReviewMode] = useState('QUICK');
  const [dimensions, setDimensions] = useState(DEFAULT_DIMENSIONS);
  const [statusFilter, setStatusFilter] = useState<string>();

  const load = async (page = 1) => {
    if (!projectId) return;
    setLoading(true);
    try {
      const response = await queryReviewProjectHistory(projectId, page);
      setData(response.data);
    } catch {
      message.error('加载审核历史失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [projectId]);
  useEffect(() => { if (!versionId && data?.versions[0]) setVersionId(data.versions[0].id); }, [data, versionId]);

  const createReview = async () => {
    if (!projectId || !versionId || dimensions.length === 0) return;
    const response = await createReviewTask(projectId, { versionId, reviewMode, selectedDimensions: dimensions, reviewScopeType: 'ALL', reviewScope: {} });
    setCreateOpen(false);
    if (response.data?.businessId) history.push(`/script-review/tasks/${response.data.businessId}`);
  };

  const runTaskAction = async (taskId: number, action: 'cancel' | 'retry') => {
    if (action === 'cancel') await cancelReviewTask(taskId);
    else await retryReviewTask(taskId);
    await load(data?.page);
  };

  if (!projectId) return <Empty description="无效的审核项目" />;
  const hasPrevious = (data?.page ?? 1) > 1;
  const hasNext = (data?.page ?? 1) * (data?.pageSize ?? 20) < (data?.total ?? 0);
  const items = (data?.items ?? []).filter((task) => !statusFilter || task.status === statusFilter);
  return (
    <PageContainer
      title={data?.project.name ?? '审核历史'}
      onBack={() => history.push('/script-review-library')}
      extra={<Button type="primary" onClick={() => setCreateOpen(true)}>发起审核</Button>}
    >
      <Card title="审核历史" loading={loading}>
        <Select
          allowClear
          placeholder="筛选审核状态"
          style={{ marginBottom: 12, width: 180 }}
          value={statusFilter}
          options={['PENDING', 'RUNNING', 'FAILED', 'CANCELED', 'COMPLETED'].map((value) => ({ value, label: value }))}
          onChange={setStatusFilter}
        />
        <List
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无审核记录" /> }}
          renderItem={(task) => {
            const version = data?.versions.find((item) => item.id === task.scriptVersionId);
            return <List.Item
              actions={[
                <Button key="open" type="link" onClick={() => history.push(`/script-review/tasks/${task.id}`)}>查看详情</Button>,
                ...(['PENDING', 'RUNNING'].includes(task.status) ? [<Button key="cancel" type="link" onClick={() => runTaskAction(task.id, 'cancel')}>取消</Button>] : []),
                ...(task.status === 'FAILED' ? [<Button key="retry" type="link" onClick={() => runTaskAction(task.id, 'retry')}>重试</Button>] : []),
              ]}
            >
              <List.Item.Meta
                title={<Space><Typography.Text strong>第 {task.roundNo} 轮审核</Typography.Text><Tag color={statusColor(task.status)}>{task.status}</Tag></Space>}
                description={<Space direction="vertical" size={2}>
                  <span>V{version?.versionNo ?? '-'} · {version?.fileName || '直接录入'} · {task.reviewMode} · {task.reviewScopeType}</span>
                  <span>{task.selectedDimensions.join('、') || '未选择维度'} · 问题 {task.issueCount} · 待处理 {task.outstandingIssueCount}</span>
                  <span>创建人 #{task.createdBy ?? '-'} · {task.errorMessage || task.completedAt || task.canceledAt || task.createdAt || ''}</span>
                </Space>}
              />
              <Progress percent={task.overallProgress ?? 0} size="small" style={{ width: 120 }} />
            </List.Item>;
          }}
        />
        {(hasPrevious || hasNext) && <Space style={{ marginTop: 16 }}>
          <Button disabled={!hasPrevious} onClick={() => load((data?.page ?? 1) - 1)}>上一页</Button>
          <Typography.Text>第 {data?.page ?? 1} 页</Typography.Text>
          <Button disabled={!hasNext} onClick={() => load((data?.page ?? 1) + 1)}>下一页</Button>
        </Space>}
      </Card>
      <Modal open={createOpen} title="新建审核任务" onCancel={() => setCreateOpen(false)} onOk={() => createReview()}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select value={versionId} options={(data?.versions ?? []).map((version) => ({ value: version.id, label: `V${version.versionNo} · ${version.fileName || '直接录入'}` }))} onChange={setVersionId} />
          <Select value={reviewMode} options={[{ value: 'QUICK', label: '快速审核' }, { value: 'DEEP', label: '深度审核' }]} onChange={setReviewMode} />
          <Checkbox.Group value={dimensions} options={DEFAULT_DIMENSIONS} onChange={(values) => setDimensions(values as string[])} />
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default ScriptReviewHistoryPage;
