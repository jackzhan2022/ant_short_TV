import {
  CloudUploadOutlined,
  FileTextOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import type { UploadFile } from 'antd';
import {
  App,
  Badge,
  Button,
  Card,
  Empty,
  Input,
  List,
  Modal,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  deriveLibraryState,
  filterLibraryProjects,
  type LibraryStateKey,
} from '../script-review/library';
import type { ReviewProject, ReviewProjectDetail } from '../script-review/service';
import {
  importReviewProject,
  queryReviewProject,
  queryReviewProjects,
} from '../script-review/service';

type ProjectLibraryItem = {
  project: ReviewProject;
  detail?: ReviewProjectDetail;
};

const stateColor: Record<LibraryStateKey, string> = {
  NOT_REVIEWED: 'default',
  RUNNING: 'processing',
  ACTION_REQUIRED: 'error',
  READY_FOR_REVIEW: 'warning',
  COMPLETED: 'success',
};

const ScriptReviewLibraryPage = () => {
  const { message } = App.useApp();
  const [items, setItems] = useState<ProjectLibraryItem[]>([]);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<LibraryStateKey>();
  const [importOpen, setImportOpen] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [content, setContent] = useState('');
  const [uploadFile, setUploadFile] = useState<UploadFile>();
  const [saving, setSaving] = useState(false);

  const loadProjects = async () => {
    const response = await queryReviewProjects();
    const projects = response.data ?? [];
    const details = await Promise.all(
      projects.map(async (project) => {
        try {
          const detailResponse = await queryReviewProject(project.id);
          return { project, detail: detailResponse.data };
        } catch {
          return { project };
        }
      }),
    );
    setItems(details);
  };

  useEffect(() => {
    loadProjects().catch(() => message.error('加载剧本库失败'));
  }, []);

  const states = useMemo(
    () =>
      new Map(
        items.map(({ project, detail }) => {
          const task = detail?.tasks.find(
            (candidate) => candidate.id === project.lastTaskId,
          ) ?? detail?.tasks[0];
          return [project.id, deriveLibraryState({ project, task })];
        }),
      ),
    [items],
  );
  const projects = useMemo(
    () => filterLibraryProjects(items.map((item) => item.project), states, query, filter),
    [filter, items, query, states],
  );
  const stateFilters = useMemo(
    () => [
      { key: undefined, label: '全部剧本', count: items.length },
      ...([
        ['NOT_REVIEWED', '未审核'],
        ['RUNNING', '审核中'],
        ['ACTION_REQUIRED', '待处理'],
        ['READY_FOR_REVIEW', '待复审'],
        ['COMPLETED', '审核完成'],
      ] as const).map(([key, label]) => ({
        key,
        label,
        count: [...states.values()].filter((state) => state.key === key).length,
      })),
    ],
    [items.length, states],
  );

  const closeImport = () => {
    setImportOpen(false);
  };

  const createProject = async () => {
    if (!projectName.trim()) {
      message.warning('请输入剧本名称');
      return;
    }
    if (!content.trim() && !uploadFile?.originFileObj) {
      message.warning('请上传剧本文件或粘贴剧本内容');
      return;
    }
    setSaving(true);
    try {
      const response = await importReviewProject(
        projectName,
        content,
        uploadFile?.originFileObj,
      );
      const projectId = response.data?.project.id;
      if (!projectId) return;
      closeImport();
      setProjectName('');
      setContent('');
      setUploadFile(undefined);
      await loadProjects();
      message.success('已创建独立剧本审核项目');
      history.push(`/script-review?projectId=${projectId}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer
      title="剧本库"
      extra={[
        <Button
          key="new"
          icon={<PlusOutlined />}
          type="primary"
          onClick={() => setImportOpen(true)}
        >
          新建剧本
        </Button>,
      ]}
    >
      <div style={{ display: 'grid', gridTemplateColumns: '240px minmax(0, 1fr)', gap: 16 }}>
        <Card size="small">
          <Space vertical size="middle" style={{ width: '100%' }}>
            <Typography.Text strong>查找剧本</Typography.Text>
            <Input.Search
              allowClear
              placeholder="搜索剧本名称"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <Typography.Text strong>审核状态</Typography.Text>
            <Space vertical size={4} style={{ width: '100%' }}>
              {stateFilters.map((item) => (
                <Button
                  block
                  key={item.key ?? 'ALL'}
                  style={{ display: 'flex', justifyContent: 'space-between', textAlign: 'left' }}
                  type={filter === item.key ? 'primary' : 'text'}
                  onClick={() => setFilter(item.key)}
                >
                  {item.label}
                  <Badge count={item.count} showZero />
                </Button>
              ))}
            </Space>
          </Space>
        </Card>
        <Card size="small" styles={{ body: { padding: 0 } }}>
          <div
            style={{
              alignItems: 'center',
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              justifyContent: 'space-between',
              padding: '12px 16px',
            }}
          >
            <Typography.Text strong>剧本列表</Typography.Text>
            <Typography.Text type="secondary">按最近操作排序</Typography.Text>
          </div>
          <List
            dataSource={projects}
            locale={{ emptyText: <Empty description="暂无独立剧本" /> }}
            renderItem={(project) => {
              const state = states.get(project.id);
              return (
                <List.Item
                  actions={[
                    <Button
                      key="open"
                      type="link"
                      onClick={() =>
                        history.push(`/script-review?projectId=${project.id}`)
                      }
                    >
                      {state?.actionLabel ?? '进入审核'}
                    </Button>,
                  ]}
                  style={{ padding: '16px' }}
                >
                  <div
                    style={{
                      alignItems: 'center',
                      display: 'grid',
                      flex: 1,
                      gap: 16,
                      gridTemplateColumns: 'minmax(220px, 2fr) minmax(90px, 0.8fr) minmax(90px, 0.8fr) minmax(120px, 1fr)',
                    }}
                  >
                    <List.Item.Meta
                      avatar={<FileTextOutlined style={{ color: '#1677ff' }} />}
                      title={project.name}
                      description={
                        <Typography.Text type="secondary">
                          {project.sourceFileName || '直接录入'} · V{project.versionCount}
                        </Typography.Text>
                      }
                    />
                    <Typography.Text type="secondary">第 {project.latestRoundNo} 轮审核</Typography.Text>
                    <Tag color={state ? stateColor[state.key] : 'default'}>
                      {state?.label ?? '未审核'}
                    </Tag>
                    {state?.outstandingIssueCount ? (
                      <Typography.Text type="danger">
                        待处理 {state.outstandingIssueCount} 项
                      </Typography.Text>
                    ) : (
                      <Typography.Text type="secondary">
                        {state?.key === 'COMPLETED' ? '审核结果已生成' : '暂无待处理问题'}
                      </Typography.Text>
                    )}
                  </div>
                </List.Item>
              );
            }}
          />
        </Card>
      </div>
      <Modal
        centered
        confirmLoading={saving}
        destroyOnHidden
        okText="导入剧本"
        open={importOpen}
        title="新建独立剧本"
        width={680}
        onCancel={closeImport}
        onOk={createProject}
      >
        <Space vertical size="middle" style={{ width: '100%' }}>
          <Input
            prefix={<FileTextOutlined />}
            placeholder="剧本名称"
            value={projectName}
            onChange={(event) => setProjectName(event.target.value)}
          />
          <Upload.Dragger
            accept=".doc,.docx,.txt,.md,.markdown"
            beforeUpload={() => false}
            fileList={uploadFile ? [uploadFile] : []}
            maxCount={1}
            onChange={({ fileList }) => setUploadFile(fileList[0])}
            onRemove={() => setUploadFile(undefined)}
          >
            <CloudUploadOutlined style={{ fontSize: 28 }} />
            <div>上传 Word / TXT / Markdown</div>
          </Upload.Dragger>
          <Input.TextArea
            autoSize={{ minRows: 7, maxRows: 14 }}
            placeholder="或直接粘贴剧本内容"
            value={content}
            onChange={(event) => setContent(event.target.value)}
          />
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default ScriptReviewLibraryPage;
