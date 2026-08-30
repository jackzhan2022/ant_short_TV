import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  HistoryOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PlatformModel } from '../platform-service';
import { queryPlatformModels } from '../platform-service';
import type {
  WorkflowAgent,
  WorkflowAgentPayload,
  WorkflowRunDetail,
  WorkflowRunInput,
  WorkflowRunSummary,
  WorkflowSkill,
  WorkflowTool,
} from '../workflow-service';
import {
  createWorkflowAgent,
  deleteWorkflowAgent,
  queryWorkflowAgentRun,
  queryWorkflowAgentRuns,
  queryWorkflowAgents,
  queryWorkflowSkills,
  queryWorkflowTools,
  runTemporaryWorkflowAgent,
  setWorkflowAgentEnabled,
  updateWorkflowAgent,
} from '../workflow-service';

type EditorMode = 'create' | 'edit' | 'copy';
type AgentForm = WorkflowAgentPayload;

const emptyAgent: AgentForm = {
  code: '',
  name: '',
  description: '',
  systemPrompt: '',
  modelId: 0,
  temperature: 0.7,
  maxTokens: 4096,
  maxSteps: 10,
  status: 'ENABLED',
  skillCodes: [],
  toolCodes: [],
};

const toAgentForm = (record: WorkflowAgent): AgentForm => ({
  code: record.code,
  name: record.name,
  description: record.description,
  systemPrompt: record.systemPrompt,
  modelId: record.modelId,
  temperature: record.temperature,
  maxTokens: record.maxTokens,
  maxSteps: record.maxSteps,
  status: record.status,
  skillCodes: record.skillCodes,
  toolCodes: record.toolCodes,
});

const errorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error && 'data' in error) {
    const data = (error as { data?: { errorMessage?: string } }).data;
    if (data?.errorMessage) return data.errorMessage;
  }
  return error instanceof Error && error.message ? error.message : fallback;
};

const RunDrawer = ({
  config,
  open,
  onClose,
}: {
  config?: AgentForm;
  open: boolean;
  onClose: () => void;
}) => {
  const [form] = Form.useForm<WorkflowRunInput>();
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<WorkflowRunDetail>();
  const [error, setError] = useState<string>();

  const run = async () => {
    if (!config) return;
    const scope = await form.validateFields();
    setLoading(true);
    setError(undefined);
    try {
      const response = await runTemporaryWorkflowAgent({ ...config, ...scope });
      const audit = await queryWorkflowAgentRun(response.data.runId);
      setDetail(audit.data);
    } catch (caught) {
      setError(errorMessage(caught, 'Agent 测试失败'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Drawer
      title="测试 Agent 配置"
      open={open}
      size="large"
      onClose={onClose}
      destroyOnHidden
      extra={
        <Button onClick={run} loading={loading} type="primary">
          开始测试
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        title="测试使用当前表单配置，不会覆盖已保存的 Agent。"
        style={{ marginBottom: 16 }}
      />
      <Form
        form={form}
        layout="vertical"
        initialValues={{ input: '' }}
      >
        <Form.Item
          name="input"
          label="输入内容"
          rules={[{ required: true, message: '请输入测试内容' }]}
        >
          <Input.TextArea autoSize={{ minRows: 4, maxRows: 10 }} />
        </Form.Item>
        <Space wrap>
          <Form.Item name="projectId" label="项目 ID">
            <InputNumber min={1} />
          </Form.Item>
          <Form.Item name="episodeId" label="剧集 ID">
            <InputNumber min={1} />
          </Form.Item>
          <Form.Item name="taskId" label="任务 ID">
            <InputNumber min={1} />
          </Form.Item>
        </Space>
      </Form>
      {error && (
        <Alert type="error" title={error} style={{ marginBottom: 16 }} />
      )}
      {detail && (
        <Space orientation="vertical" style={{ width: '100%' }} size="large">
          <Descriptions
            bordered
            size="small"
            column={2}
            items={[
              { key: 'status', label: '状态', children: detail.status },
              { key: 'run', label: '运行 ID', children: detail.id },
              {
                key: 'output',
                label: '最终输出',
                children: detail.finalOutput || '-',
                span: 2,
              },
            ]}
          />
          <Table
            rowKey="stepNo"
            pagination={false}
            dataSource={detail.steps}
            columns={[
              { title: '步骤', dataIndex: 'stepNo', width: 70 },
              { title: '类型', dataIndex: 'stepType', width: 90 },
              { title: '工具', dataIndex: 'toolCode' },
              { title: '状态', dataIndex: 'status', width: 90 },
              {
                title: '结果/错误',
                render: (_, step) =>
                  step.errorMessage || step.outputJson || '-',
              },
            ]}
          />
        </Space>
      )}
    </Drawer>
  );
};

const WorkflowAgentsPage = () => {
  const access = useAccess();
  const editable = Boolean(access.canEditWorkflowAgents);
  const { message } = App.useApp();
  const [form] = Form.useForm<AgentForm>();
  const [agents, setAgents] = useState<WorkflowAgent[]>([]);
  const [skills, setSkills] = useState<WorkflowSkill[]>([]);
  const [tools, setTools] = useState<WorkflowTool[]>([]);
  const [models, setModels] = useState<PlatformModel[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<EditorMode>();
  const [source, setSource] = useState<WorkflowAgent>();
  const [saving, setSaving] = useState(false);
  const [editorError, setEditorError] = useState<string>();
  const [runConfig, setRunConfig] = useState<AgentForm>();
  const [runOpen, setRunOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [history, setHistory] = useState<WorkflowRunSummary[]>([]);
  const [historyDetail, setHistoryDetail] = useState<WorkflowRunDetail>();
  const promptCursor = useRef(0);
  const selectedToolCodes = Form.useWatch('toolCodes', form) ?? [];

  const load = useCallback(
    async (search?: string) => {
      setLoading(true);
      try {
        const [agentResponse, skillResponse, toolResponse, modelResponse] =
          await Promise.all([
            queryWorkflowAgents(search || undefined),
            access.canViewWorkflowSkills
              ? queryWorkflowSkills()
              : Promise.resolve({ data: [] as WorkflowSkill[] }),
            queryWorkflowTools(),
            access.canViewPlatformAiModels
              ? queryPlatformModels()
              : Promise.resolve({ data: [] as PlatformModel[] }),
          ]);
        setAgents(agentResponse.data ?? []);
        setSkills(skillResponse.data ?? []);
        setTools(toolResponse.data ?? []);
        setModels(
          (modelResponse.data ?? []).filter(
            (model) =>
              model.serviceType === 'TEXT' &&
              model.status === 'ENABLED' &&
              model.capabilities?.includes('TOOL_CALLING'),
          ),
        );
      } catch (caught) {
        message.error(errorMessage(caught, '加载 Agent 数据失败'));
      } finally {
        setLoading(false);
      }
    },
    [access.canViewPlatformAiModels, access.canViewWorkflowSkills, message],
  );

  useEffect(() => {
    void load('');
  }, [load]);

  const openEditor = (editorMode: EditorMode, record?: WorkflowAgent) => {
    setMode(editorMode);
    setSource(record);
    setEditorError(undefined);
    form.setFieldsValue(
      record
        ? {
            code: editorMode === 'copy' ? `${record.code}-copy` : record.code,
            name:
              editorMode === 'copy' ? `${record.name}（副本）` : record.name,
            description: record.description,
            systemPrompt: record.systemPrompt,
            modelId: record.modelId,
            temperature: record.temperature,
            maxTokens: record.maxTokens,
            maxSteps: record.maxSteps,
            status: record.status,
            skillCodes: record.skillCodes,
            toolCodes: record.toolCodes,
          }
        : emptyAgent,
    );
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    setEditorError(undefined);
    try {
      if (mode === 'edit' && source) {
        await updateWorkflowAgent(source.code, {
          ...values,
          code: source.code,
          expectedRevision: source.revision,
        });
      } else if (mode === 'copy' && source) {
        await createWorkflowAgent(values);
      } else {
        await createWorkflowAgent(values);
      }
      message.success('Agent 已保存并立即生效');
      setMode(undefined);
      await load('');
    } catch (caught) {
      setEditorError(errorMessage(caught, 'Agent 保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const insertTool = (tool: WorkflowTool) => {
    const current = form.getFieldValue('systemPrompt') ?? '';
    const position = Math.max(
      0,
      Math.min(promptCursor.current, current.length),
    );
    const insertion = `先调用 ${tool.code}，再根据工具结果继续处理。`;
    form.setFieldValue(
      'systemPrompt',
      `${current.slice(0, position)}${insertion}${current.slice(position)}`,
    );
    const codes: string[] = form.getFieldValue('toolCodes') ?? [];
    if (!codes.includes(tool.code))
      form.setFieldValue('toolCodes', [...codes, tool.code]);
    promptCursor.current = position + insertion.length;
  };

  const startTest = async () => {
    const values = await form.validateFields();
    setRunConfig(values);
    setRunOpen(true);
  };

  const openHistory = async (record: WorkflowAgent) => {
    const response = await queryWorkflowAgentRuns(record.code, 50);
    setHistory(response.data ?? []);
    setHistoryDetail(undefined);
    setHistoryOpen(true);
  };

  const columns = useMemo<ColumnsType<WorkflowAgent>>(
    () => [
      {
        title: 'Agent',
        dataIndex: 'name',
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <Typography.Text type="secondary">{record.code}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '模型',
        dataIndex: 'modelId',
        render: (id) => models.find((model) => model.id === id)?.name ?? id,
      },
      {
        title: 'Skills',
        dataIndex: 'skillCodes',
        render: (codes: string[]) => (
          <Space wrap>
            {codes.map((code) => (
              <Tag key={code}>{code}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: '工具',
        dataIndex: 'toolCodes',
        render: (codes: string[]) => (
          <Space wrap>
            {codes.map((code) => (
              <Tag color="blue" key={code}>
                {code}
              </Tag>
            ))}
          </Space>
        ),
      },
      { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (_, record) =>
          editable ? (
            <Switch
              checked={record.status === 'ENABLED'}
              checkedChildren="启用"
              unCheckedChildren="停用"
              onChange={async (checked) => {
                await setWorkflowAgentEnabled(record.code, checked);
                await load('');
              }}
            />
          ) : (
            <Tag color={record.status === 'ENABLED' ? 'success' : 'default'}>
              {record.status}
            </Tag>
          ),
      },
      {
        title: '操作',
        key: 'actions',
        width: 320,
        render: (_, record) => (
          <Space wrap>
            {editable && (
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={() => openEditor('edit', record)}
              >
                编辑
              </Button>
            )}
            {editable && (
              <Button
                type="link"
                icon={<CopyOutlined />}
                onClick={() => openEditor('copy', record)}
              >
                复制
              </Button>
            )}
            {editable && (
              <Button
                type="link"
                icon={<ThunderboltOutlined />}
                onClick={() => {
                  setRunConfig(toAgentForm(record));
                  setRunOpen(true);
                }}
              >
                测试
              </Button>
            )}
            <Button
              type="link"
              icon={<HistoryOutlined />}
              onClick={() => void openHistory(record)}
            >
              运行记录
            </Button>
            {editable && (
              <Popconfirm
                title={
                  record.toolCodes.length
                    ? '确认删除这个 Agent？'
                    : '确认删除？'
                }
                onConfirm={async () => {
                  await deleteWorkflowAgent(record.code);
                  await load('');
                }}
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            )}
          </Space>
        ),
      },
    ],
    [editable, load, models],
  );

  return (
    <>
      <Space orientation="vertical" style={{ width: '100%' }} size="middle">
        <Alert
          type="info"
          showIcon
          title="Agent（新）是独立的纯文本工具工作流；保存后立即生效，当前不保留配置版本。"
        />
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="搜索 Agent 名称或 code"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onSearch={(value) => void load(value)}
            style={{ width: 320 }}
          />
          {editable && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openEditor('create')}
            >
              新增 Agent
            </Button>
          )}
        </Space>
        <Table
          rowKey="code"
          loading={loading}
          columns={columns}
          dataSource={agents}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Drawer
        title={
          mode === 'create'
            ? '新增 Agent'
            : mode === 'copy'
              ? '复制 Agent'
              : `编辑 Agent：${source?.name ?? ''}`
        }
        open={Boolean(mode)}
        size="large"
        onClose={() => setMode(undefined)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={startTest}>测试当前配置</Button>
            <Button type="primary" loading={saving} onClick={save}>
              保存并立即生效
            </Button>
          </Space>
        }
      >
        <Alert
          type="warning"
          showIcon
          title="保存后立即生效；正式运行将读取当前配置。"
          style={{ marginBottom: 16 }}
        />
        {editorError && (
          <Alert
            type="error"
            title={editorError}
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={form} layout="vertical" initialValues={emptyAgent}>
          <Form.Item
            name="code"
            label="Agent code"
            rules={[
              { required: true },
              {
                pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
                message: '请输入小写 kebab-case',
              },
            ]}
          >
            <Input disabled={mode === 'edit'} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea autoSize />
          </Form.Item>
          <Form.Item
            name="modelId"
            label="文本模型"
            rules={[{ required: true }]}
          >
            <Select
              options={models.map((model) => ({
                label: model.name,
                value: model.id,
              }))}
            />
          </Form.Item>
          <Space wrap>
            <Form.Item
              name="temperature"
              label="Temperature"
              rules={[{ required: true }]}
            >
              <InputNumber min={0} max={2} step={0.1} />
            </Form.Item>
            <Form.Item
              name="maxTokens"
              label="最大 Tokens"
              rules={[{ required: true }]}
            >
              <InputNumber min={1} max={1000000} />
            </Form.Item>
            <Form.Item
              name="maxSteps"
              label="最大步骤"
              rules={[{ required: true }]}
            >
              <InputNumber min={1} max={100} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="skillCodes" label="Skills（按选择顺序加载）">
            <Select
              mode="multiple"
              options={skills.map((skill) => ({
                label: `${skill.name} · ${skill.code}`,
                value: skill.code,
              }))}
            />
          </Form.Item>
          <Form.Item name="toolCodes" label="允许调用的工具">
            <Select
              mode="multiple"
              options={tools.map((tool) => ({
                label: `${tool.name} · ${tool.code}`,
                value: tool.code,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="systemPrompt"
            label="系统提示词 / 纯文本工作流"
            rules={[{ required: true }]}
          >
            <Input.TextArea
              autoSize={{ minRows: 10, maxRows: 22 }}
              onSelect={(event) => {
                promptCursor.current = event.currentTarget.selectionStart;
              }}
            />
          </Form.Item>
          <Typography.Title level={5}>插入工具调用说明</Typography.Title>
          <Space wrap>
            {tools.map((tool) => (
              <Button key={tool.code} onClick={() => insertTool(tool)}>
                {tool.name}
              </Button>
            ))}
          </Space>
          <Space
            orientation="vertical"
            style={{ width: '100%', marginTop: 16 }}
          >
            {tools
              .filter((tool) => selectedToolCodes.includes(tool.code))
              .map((tool) => (
                <Descriptions
                  key={tool.code}
                  bordered
                  size="small"
                  column={2}
                  title={`${tool.name} · ${tool.code}`}
                  items={[
                    {
                      key: 'risk',
                      label: '风险级别',
                      children: tool.riskLevel,
                    },
                    {
                      key: 'policy',
                      label: '失败策略',
                      children: tool.failurePolicy,
                    },
                    {
                      key: 'input',
                      label: '输入 Schema',
                      children: (
                        <Typography.Text code>
                          {JSON.stringify(tool.inputSchema)}
                        </Typography.Text>
                      ),
                      span: 2,
                    },
                    {
                      key: 'output',
                      label: '输出 Schema',
                      children: (
                        <Typography.Text code>
                          {JSON.stringify(tool.outputSchema)}
                        </Typography.Text>
                      ),
                      span: 2,
                    },
                  ]}
                />
              ))}
          </Space>
        </Form>
      </Drawer>

      <RunDrawer
        config={runConfig}
        open={runOpen}
        onClose={() => setRunOpen(false)}
      />
      <Drawer
        title="Agent 运行记录"
        open={historyOpen}
        size="large"
        onClose={() => setHistoryOpen(false)}
        destroyOnHidden
      >
        <Table
          rowKey="id"
          dataSource={history}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'id' },
            { title: '类型', dataIndex: 'runType' },
            { title: '状态', dataIndex: 'status' },
            { title: '开始时间', dataIndex: 'startedAt' },
            {
              title: '操作',
              render: (_, run) => (
                <Button
                  type="link"
                  onClick={async () =>
                    setHistoryDetail((await queryWorkflowAgentRun(run.id)).data)
                  }
                >
                  详情
                </Button>
              ),
            },
          ]}
        />
        {historyDetail && (
          <Typography.Paragraph copyable style={{ whiteSpace: 'pre-wrap' }}>
            {historyDetail.promptSnapshot}
          </Typography.Paragraph>
        )}
      </Drawer>
    </>
  );
};

export default WorkflowAgentsPage;
