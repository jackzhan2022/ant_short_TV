import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { WorkflowSkill } from '../workflow-service';
import {
  copyWorkflowSkill,
  createWorkflowSkill,
  deleteWorkflowSkill,
  queryWorkflowSkill,
  queryWorkflowSkills,
  updateWorkflowSkill,
} from '../workflow-service';

type EditorMode = 'create' | 'edit' | 'copy';
type SkillForm = { code: string; content: string };

const starter = `---
name: my-skill
description: 描述这个 Skill 提供的方法论
---

# 使用说明

在这里编写可被 Agent 按序加载的完整 Markdown 指令。
`;

const errorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error && 'data' in error) {
    const data = (
      error as { data?: { errorCode?: string; errorMessage?: string } }
    ).data;
    if (data?.errorMessage) return data.errorMessage;
  }
  return error instanceof Error && error.message ? error.message : fallback;
};

const WorkflowSkillsPage = () => {
  const access = useAccess();
  const editable = Boolean(access.canEditWorkflowSkills);
  const { message } = App.useApp();
  const [form] = Form.useForm<SkillForm>();
  const [skills, setSkills] = useState<WorkflowSkill[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [mode, setMode] = useState<EditorMode>();
  const [source, setSource] = useState<WorkflowSkill>();
  const [editorError, setEditorError] = useState<string>();
  const [conflict, setConflict] = useState(false);

  const load = useCallback(
    async (search?: string) => {
      setLoading(true);
      try {
        const response = await queryWorkflowSkills(search || undefined);
        setSkills(response.data ?? []);
      } catch (caught) {
        message.error(errorMessage(caught, '加载 Skill 失败'));
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void load('');
  }, [load]);

  const openEditor = (editorMode: EditorMode, record?: WorkflowSkill) => {
    setMode(editorMode);
    setSource(record);
    setEditorError(undefined);
    setConflict(false);
    form.setFieldsValue(
      record
        ? {
            code: editorMode === 'copy' ? `${record.code}-copy` : record.code,
            content: record.content,
          }
        : { code: '', content: starter },
    );
  };

  const validateContent = (_: unknown, value?: string) => {
    if (!value?.trim()) return Promise.reject(new Error('请输入完整 SKILL.md'));
    const match = value.match(/^---\s*\n([\s\S]*?)\n---(?:\s*\n|$)/);
    if (
      !match ||
      !/^name:\s*\S+/m.test(match[1]) ||
      !/^description:\s*\S+/m.test(match[1])
    ) {
      return Promise.reject(
        new Error('frontmatter 必须包含 name 和 description'),
      );
    }
    return Promise.resolve();
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    setEditorError(undefined);
    setConflict(false);
    try {
      if (mode === 'edit' && source) {
        await updateWorkflowSkill(source.code, values.content, source.revision);
      } else if (mode === 'copy' && source) {
        await copyWorkflowSkill(source.code, values.code);
      } else {
        await createWorkflowSkill(values.code, values.content);
      }
      message.success('Skill 已保存并立即生效');
      setMode(undefined);
      await load('');
    } catch (caught) {
      const text = errorMessage(caught, 'Skill 保存失败');
      setEditorError(text);
      setConflict(/冲突|修改|revision|CONFLICT/i.test(text));
    } finally {
      setSaving(false);
    }
  };

  const reloadLatest = async () => {
    if (!source) return;
    const response = await queryWorkflowSkill(source.code);
    setSource(response.data);
    form.setFieldsValue({
      code: response.data.code,
      content: response.data.content,
    });
    setConflict(false);
    setEditorError(undefined);
  };

  const columns = useMemo<ColumnsType<WorkflowSkill>>(
    () => [
      {
        title: 'Skill',
        dataIndex: 'name',
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.name}</Typography.Text>
            <Typography.Text type="secondary">{record.code}</Typography.Text>
          </Space>
        ),
      },
      { title: '描述', dataIndex: 'description' },
      {
        title: '引用 Agent',
        dataIndex: 'referencingAgentCodes',
        render: (codes: string[]) => (
          <Space wrap>
            {codes.map((code) => (
              <Tag key={code}>{code}</Tag>
            ))}
          </Space>
        ),
      },
      { title: '内容 revision', dataIndex: 'revision', ellipsis: true },
      {
        title: '操作',
        width: 240,
        render: (_, record) => (
          <Space>
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
              <Popconfirm
                title={
                  record.referencingAgentCodes.length
                    ? `正在被 ${record.referencingAgentCodes.join(', ')} 引用，不能删除`
                    : '确认删除这个 Skill？'
                }
                disabled={record.referencingAgentCodes.length > 0}
                onConfirm={async () => {
                  await deleteWorkflowSkill(record.code);
                  await load('');
                }}
              >
                <Button
                  type="link"
                  danger
                  disabled={record.referencingAgentCodes.length > 0}
                  icon={<DeleteOutlined />}
                >
                  删除
                </Button>
              </Popconfirm>
            )}
          </Space>
        ),
      },
    ],
    [editable, load],
  );

  return (
    <>
      <Space orientation="vertical" style={{ width: '100%' }} size="middle">
        <Alert
          type="info"
          showIcon
          title="Skill（新）以完整 SKILL.md 文件保存；修改成功后无需发布或重启，后续 Agent 运行立即读取新内容。"
        />
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="搜索 Skill 名称或 code"
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
              新增 Skill
            </Button>
          )}
        </Space>
        <Table
          rowKey="code"
          loading={loading}
          columns={columns}
          dataSource={skills}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Drawer
        title={
          mode === 'create'
            ? '新增 Skill'
            : mode === 'copy'
              ? '复制 Skill'
              : `编辑 Skill：${source?.name ?? ''}`
        }
        open={Boolean(mode)}
        size="large"
        onClose={() => setMode(undefined)}
        destroyOnHidden
        extra={
          <Button type="primary" loading={saving} onClick={save}>
            保存并立即生效
          </Button>
        }
      >
        <Alert
          type="warning"
          showIcon
          title="保存后立即生效；修改会影响所有引用该 Skill 的 Agent。"
          description={
            source?.referencingAgentCodes.length
              ? `当前引用：${source.referencingAgentCodes.join(', ')}`
              : '当前没有 Agent 引用。'
          }
          style={{ marginBottom: 16 }}
        />
        {editorError && (
          <Alert
            type="error"
            title={editorError}
            description={
              conflict
                ? '编辑器中的未保存内容已保留。确认后可显式加载服务器最新文件。'
                : undefined
            }
            action={
              conflict ? (
                <Button icon={<ReloadOutlined />} onClick={reloadLatest}>
                  加载最新文件
                </Button>
              ) : undefined
            }
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="Skill code"
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
          <Form.Item
            name="content"
            label="完整 SKILL.md"
            rules={[{ validator: validateContent }]}
            extra="需要 YAML frontmatter（name、description）和 Markdown 正文。"
          >
            <Input.TextArea
              autoSize={{ minRows: 22, maxRows: 36 }}
              spellCheck={false}
              style={{
                fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace',
              }}
            />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};

export default WorkflowSkillsPage;
