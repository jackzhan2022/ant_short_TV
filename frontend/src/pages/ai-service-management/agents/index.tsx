import { EditOutlined, EyeOutlined, LockOutlined, PlayCircleOutlined, UploadOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  App,
  Popconfirm,
} from 'antd';
import { ModalForm, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { useEffect, useMemo, useState } from 'react';
import type {
  BuiltInAgent,
  BuiltInAgentPreview,
  BuiltInSkill,
} from '../platform-service';
import {
  previewBuiltInAgent,
  queryBuiltInAgents,
  queryBuiltInSkills,
  queryEditableAgents, queryEditableSkills, updateEditableAgent, updateEditableSkill, publishEditableAgent, publishEditableSkill, setEditableAgentStatus, setEditableSkillStatus, rollbackEditableAgent, rollbackEditableSkill,
} from '../platform-service';
import type { EditableAgent, EditableSkill } from '../platform-service';

const AgentEditor = ({ record, onDone }: { record: EditableAgent; onDone: () => void }) => {
  const { message } = App.useApp();
  return <ModalForm<Pick<EditableAgent, 'name' | 'description' | 'promptTemplate' | 'outputSchema'>> title={`编辑 Agent：${record.name}`} trigger={<Button type="link" icon={<EditOutlined />}>编辑</Button>} initialValues={record} onFinish={async (values) => { await updateEditableAgent(record.code, values); message.success('Agent 草稿已保存'); onDone(); return true; }}><ProFormText name="name" label="名称" rules={[{ required: true }]} /><ProFormTextArea name="description" label="描述" /><ProFormTextArea name="promptTemplate" label="提示词模板" rules={[{ required: true }]} fieldProps={{ autoSize: { minRows: 8 } }} /><ProFormTextArea name="outputSchema" label="输出 JSON Schema" fieldProps={{ autoSize: { minRows: 4 } }} /></ModalForm>;
};

const SkillEditor = ({ record, onDone }: { record: EditableSkill; onDone: () => void }) => {
  const { message } = App.useApp();
  return <ModalForm<Pick<EditableSkill, 'name' | 'category' | 'content'>> title={`编辑 Skill：${record.name}`} trigger={<Button type="link" icon={<EditOutlined />}>编辑</Button>} initialValues={record} onFinish={async (values) => { await updateEditableSkill(record.code, values); message.success('Skill 草稿已保存'); onDone(); return true; }}><ProFormText name="name" label="名称" rules={[{ required: true }]} /><ProFormText name="category" label="分类" /><ProFormTextArea name="content" label="Skill 内容" rules={[{ required: true }]} fieldProps={{ autoSize: { minRows: 10 } }} /></ModalForm>;
};

const capabilityText: Record<string, string> = {
  TEXT: '文本',
  VIDEO_UNDERSTANDING: '视频理解',
};

const AgentDetail = ({
  agent,
  onClose,
}: {
  agent: BuiltInAgent;
  onClose: () => void;
}) => {
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<BuiltInAgentPreview>();
  const [previewLoading, setPreviewLoading] = useState(false);
  const [error, setError] = useState<string>();

  const renderPreview = async () => {
    setPreviewLoading(true);
    setError(undefined);
    try {
      const response = await previewBuiltInAgent(agent.code, variables);
      if (!response.success) {
        setError(response.errorMessage || 'Prompt 预览失败');
        return;
      }
      setPreview(response.data);
    } catch {
      setError('Prompt 预览失败');
    } finally {
      setPreviewLoading(false);
    }
  };

  return (
    <Drawer
      title={
        <Space>
          <span>{agent.name}</span>
          <Tag icon={<LockOutlined />}>系统内置</Tag>
        </Space>
      }
      open
      size="large"
      onClose={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="智能体编码">{agent.code}</Descriptions.Item>
          <Descriptions.Item label="业务场景">
            {agent.businessSceneName}
          </Descriptions.Item>
          <Descriptions.Item label="能力">
            {capabilityText[agent.capability] ?? agent.capability}
          </Descriptions.Item>
          <Descriptions.Item label="模型">
            平台默认路由
          </Descriptions.Item>
          <Descriptions.Item label="说明" span={2}>
            {agent.description}
          </Descriptions.Item>
        </Descriptions>

        <section>
          <Typography.Title level={5}>引用 Skills</Typography.Title>
          <Space wrap>
            {agent.skills.map((skill) => (
              <Tag key={skill.code} color="blue">
                {skill.name}
              </Tag>
            ))}
          </Space>
        </section>

        <section>
          <Typography.Title level={5}>输入变量</Typography.Title>
          <Space orientation="vertical" style={{ width: '100%' }}>
            {agent.variables.map((variable) => (
              <div key={variable.name}>
                <Typography.Text strong>
                  {variable.label} · {variable.name}
                </Typography.Text>
                <Input.TextArea
                  autoSize={{ minRows: 2, maxRows: 6 }}
                  placeholder={variable.required ? '必填' : '可选'}
                  value={variables[variable.name] ?? ''}
                  onChange={(event) =>
                    setVariables((current) => ({
                      ...current,
                      [variable.name]: event.target.value,
                    }))
                  }
                />
              </div>
            ))}
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={previewLoading}
              onClick={renderPreview}
            >
              预览最终 Prompt
            </Button>
          </Space>
        </section>

        {error && <Alert type="error" title={error} />}

        {preview && (
          <section>
            <Typography.Title level={5}>最终 Prompt</Typography.Title>
            <Typography.Paragraph
              copyable
              style={{
                whiteSpace: 'pre-wrap',
                background: 'var(--app-color-fill-secondary)',
                padding: 12,
                maxHeight: 320,
                overflow: 'auto',
              }}
            >
              {preview.prompt}
            </Typography.Paragraph>
            <Typography.Title level={5}>输出结构</Typography.Title>
            <Typography.Paragraph
              copyable
              style={{ whiteSpace: 'pre-wrap', background: 'var(--app-color-fill-secondary)', padding: 12 }}
            >
              {preview.outputSchema}
            </Typography.Paragraph>
          </section>
        )}
      </Space>
    </Drawer>
  );
};

export const AgentTabContent = () => {
  const [selectedAgent, setSelectedAgent] = useState<BuiltInAgent>();
  const [editableAgents, setEditableAgents] = useState<EditableAgent[]>([]);
  const reloadEditable = () => { queryEditableAgents().then((r) => setEditableAgents(r.data ?? [])).catch(() => undefined); };
  useEffect(() => { reloadEditable(); }, []);
  const agentColumns = useMemo<ProColumns<BuiltInAgent>[]>(
    () => [
      {
        title: 'Agent',
        dataIndex: 'name',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <Typography.Text type="secondary">{record.code}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '业务场景',
        dataIndex: 'businessSceneName',
        width: 160,
      },
      {
        title: '能力',
        dataIndex: 'capability',
        width: 120,
        renderText: (value) => capabilityText[value] ?? value,
      },
      {
        title: '模型路由',
        dataIndex: 'modelRouting',
        width: 150,
        renderText: () => '平台默认路由',
      },
      {
        title: 'Skills',
        dataIndex: 'skills',
        search: false,
        render: (_, record) => (
          <Space wrap>
            {record.skills.map((skill) => (
              <Tag key={skill.code}>{skill.name}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 210,
        render: (_, record) => (
          <Space><Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => setSelectedAgent(record)}
          >
            详情
          </Button>{(() => { const versions = editableAgents.filter((item) => item.code === record.code); const editable = versions[0]; return editable ? <><AgentEditor record={editable} onDone={reloadEditable} /><Button type="link" icon={<UploadOutlined />} onClick={async () => { await publishEditableAgent(record.code); reloadEditable(); }}>发布</Button><Switch size="small" checked={editable.status === 'ENABLED'} checkedChildren="启用" unCheckedChildren="停用" onChange={async (checked) => { await setEditableAgentStatus(record.code, checked); reloadEditable(); }} />{versions.length > 1 && <Dropdown menu={{ items: versions.slice(1).map((version) => ({ key: String(version.versionNo), label: <Popconfirm title={`回滚到 v${version.versionNo}？`} onConfirm={async () => { await rollbackEditableAgent(record.code, version.versionNo); reloadEditable(); }}><span>回滚到 v{version.versionNo}</span></Popconfirm> })) }}><Button type="link">历史版本</Button></Dropdown>}</> : null; })()}</Space>
        ),
      },
    ],
    [],
  );

  return <>
    <ProTable<BuiltInAgent>
      rowKey="code"
      headerTitle="系统内置 Agent"
      columns={agentColumns}
      search={false}
      request={async () => {
        const response = await queryBuiltInAgents();
        return { data: response.data ?? [], success: response.success };
      }}
      pagination={false}
    />
    {selectedAgent && <AgentDetail agent={selectedAgent} onClose={() => setSelectedAgent(undefined)} />}
  </>;
};

export const SkillTabContent = () => {
  const [selectedSkill, setSelectedSkill] = useState<BuiltInSkill>();
  const [editableSkills, setEditableSkills] = useState<EditableSkill[]>([]);
  const reloadEditable = () => { queryEditableSkills().then((r) => setEditableSkills(r.data ?? [])).catch(() => undefined); };
  useEffect(() => { reloadEditable(); }, []);
  const skillColumns = useMemo<ProColumns<BuiltInSkill>[]>(
    () => [
      {
        title: 'Skill',
        dataIndex: 'name',
        width: 240,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <Typography.Text type="secondary">{record.code}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '分类',
        dataIndex: 'category',
        width: 120,
        render: (_, record) => <Tag color="blue">{record.category}</Tag>,
      },
      {
        title: '说明',
        dataIndex: 'description',
        search: false,
        ellipsis: true,
      },
      {
        title: '引用 Agent',
        dataIndex: 'agents',
        search: false,
        render: (_, record) => (
          <Space wrap>
            {record.agents.map((agent) => (
              <Tag key={agent.code}>{agent.name}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 210,
        render: (_, record) => (
          <Space><Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => setSelectedSkill(record)}
          >
            详情
          </Button>{(() => { const versions = editableSkills.filter((item) => item.code === record.code); const editable = versions[0]; return editable ? <><SkillEditor record={editable} onDone={reloadEditable} /><Button type="link" icon={<UploadOutlined />} onClick={async () => { await publishEditableSkill(record.code); reloadEditable(); }}>发布</Button><Switch size="small" checked={editable.status === 'ENABLED'} checkedChildren="启用" unCheckedChildren="停用" onChange={async (checked) => { await setEditableSkillStatus(record.code, checked); reloadEditable(); }} />{versions.length > 1 && <Dropdown menu={{ items: versions.slice(1).map((version) => ({ key: String(version.versionNo), label: <Popconfirm title={`回滚到 v${version.versionNo}？`} onConfirm={async () => { await rollbackEditableSkill(record.code, version.versionNo); reloadEditable(); }}><span>回滚到 v{version.versionNo}</span></Popconfirm> })) }}><Button type="link">历史版本</Button></Dropdown>}</> : null; })()}</Space>
        ),
      },
    ],
    [],
  );

  return <>
    <ProTable<BuiltInSkill>
      rowKey="code"
      headerTitle="系统预置 Skill"
      columns={skillColumns}
      search={false}
      request={async () => {
        const response = await queryBuiltInSkills();
        return { data: response.data ?? [], success: response.success };
      }}
      pagination={false}
    />
      <Drawer
        title={
          <Space>
            <span>{selectedSkill?.name}</span>
            <Tag icon={<LockOutlined />}>系统内置</Tag>
          </Space>
        }
        open={Boolean(selectedSkill)}
        size="large"
        onClose={() => setSelectedSkill(undefined)}
        destroyOnHidden
      >
        {selectedSkill ? (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="技能编码">
                {selectedSkill.code}
              </Descriptions.Item>
              <Descriptions.Item label="分类">
                {selectedSkill.category}
              </Descriptions.Item>
              <Descriptions.Item label="说明">
                {selectedSkill.description}
              </Descriptions.Item>
              <Descriptions.Item label="引用 Agent">
                <Space wrap>
                  {selectedSkill.agents.map((agent) => (
                    <Tag key={agent.code}>{agent.name}</Tag>
                  ))}
                </Space>
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5}>Skill 内容</Typography.Title>
            <Typography.Paragraph
              copyable
              style={{ whiteSpace: 'pre-wrap', background: 'var(--app-color-fill-secondary)', padding: 12 }}
            >
              {selectedSkill.content}
            </Typography.Paragraph>
          </Space>
        ) : (
          <Empty />
        )}
      </Drawer>
  </>;
};

const BuiltInAgentsPage = () => (
  <PageContainer>
    <Tabs
      items={[
        { key: 'agents', label: 'Agent 管理', children: <AgentTabContent /> },
        { key: 'skills', label: 'Skill 管理', children: <SkillTabContent /> },
      ]}
    />
  </PageContainer>
);

export default BuiltInAgentsPage;
