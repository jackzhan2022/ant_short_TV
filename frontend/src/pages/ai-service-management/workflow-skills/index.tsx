import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useAccess, useModel } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { WorkflowSkill } from '../workflow-service';
import {
  copyWorkflowSkill,
  createWorkflowSkill,
  deleteWorkflowSkill,
  queryTenantWorkflowSkill,
  queryTenantWorkflowSkills,
  queryTenantWorkflowSkillTemplate,
  queryWorkflowAgents,
  queryWorkflowSkill,
  queryWorkflowSkills,
  restoreTenantWorkflowSkill,
  updateTenantWorkflowSkill,
  updateWorkflowSkill,
} from '../workflow-service';

type EditorMode = 'create' | 'edit' | 'copy' | 'view';
type SkillForm = { code: string; content: string };

const builtinAgentNames = new Map([
  ['short-drama-global-understanding', '剧情全局理解'],
  ['short-drama-episode-splitting', '剧集智能拆分'],
  ['short-drama-episode-summary', '剧集概要提炼'],
  ['short-drama-asset-recognition', '角色场景道具识别'],
  ['short-drama-storyboard', '分镜规划'],
  ['script-review', '剧本审核'],
]);

const tenantSkillCode = 'short-drama-storyboard-production';

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

const WorkflowSkillsPage = ({ tenantId }: { tenantId?: number }) => {
  const access = useAccess();
  const tenantMode = tenantId !== undefined;
  const editable = Boolean(
    tenantMode
      ? access.canManageTenantWorkflowSkills
      : access.canEditWorkflowSkills,
  );
  const canViewAgents = !tenantMode && Boolean(access.canViewWorkflowAgents);
  const { message } = App.useApp();
  const [form] = Form.useForm<SkillForm>();
  const [skills, setSkills] = useState<WorkflowSkill[]>([]);
  const [agentNames, setAgentNames] = useState(builtinAgentNames);
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [agentFilter, setAgentFilter] = useState<string>();
  const [scopeFilter, setScopeFilter] = useState<'SYSTEM' | 'TENANT'>();
  const [tenantFilter, setTenantFilter] = useState<number>();
  const [tenantNames, setTenantNames] = useState(new Map<number, string>());
  const requestId = useRef(0);
  const templateRequestId = useRef(0);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [mode, setMode] = useState<EditorMode>();
  const [source, setSource] = useState<WorkflowSkill>();
  const [editorError, setEditorError] = useState<string>();
  const [conflict, setConflict] = useState(false);

  const load = useCallback(
    async (search?: string) => {
      const currentRequest = ++requestId.current;
      setLoading(true);
      try {
        const filters = {
          agentCode: agentFilter,
          scope: scopeFilter,
          tenantId: tenantFilter,
        };
        const response =
          tenantId !== undefined
            ? await queryTenantWorkflowSkills(
                tenantId,
                search || undefined,
                filters,
              )
            : await queryWorkflowSkills(search || undefined, filters);
        if (currentRequest !== requestId.current) return;
        setSkills(response.data ?? []);
        setTenantNames(
          (previous) =>
            new Map([
              ...previous,
              ...(response.data ?? [])
                .filter((skill) => skill.tenantId != null)
                .map((skill): [number, string] => [
                  skill.tenantId as number,
                  skill.tenantName || String(skill.tenantId),
                ]),
            ]),
        );
      } catch (caught) {
        if (currentRequest === requestId.current)
          message.error(errorMessage(caught, '加载 Skill 失败'));
      } finally {
        if (currentRequest === requestId.current) setLoading(false);
      }
    },
    [message, tenantId, agentFilter, scopeFilter, tenantFilter],
  );

  useEffect(() => {
    void load(search);
    return () => {
      requestId.current += 1;
    };
  }, [load, search]);

  useEffect(
    () => () => {
      templateRequestId.current += 1;
    },
    [],
  );

  useEffect(() => {
    if (!canViewAgents) {
      setAgentNames(builtinAgentNames);
      return;
    }
    let active = true;
    void queryWorkflowAgents()
      .then((response) => {
        if (active) {
          setAgentNames(
            new Map([
              ...builtinAgentNames,
              ...(response.data ?? []).map((agent): [string, string] => [
                agent.code,
                agent.name,
              ]),
            ]),
          );
        }
      })
      .catch(() => {
        if (active) setAgentNames(builtinAgentNames);
      });
    return () => {
      active = false;
    };
  }, [canViewAgents]);

  const referenceLabel = useCallback(
    (code: string) => {
      const name = agentNames.get(code);
      return name && name !== code ? `${name}（${code}）` : code;
    },
    [agentNames],
  );

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

  const editTenantSkill = async (record: WorkflowSkill) => {
    if (tenantId === undefined) return;
    try {
      const response = await queryTenantWorkflowSkill(tenantId, record.code);
      openEditor('edit', response.data);
    } catch (caught) {
      message.error(errorMessage(caught, '加载团队 Skill 失败'));
    }
  };

  const createTenantSkill = async () => {
    if (tenantId === undefined || templateLoading) return;
    const currentRequest = ++templateRequestId.current;
    setTemplateLoading(true);
    try {
      const response = await queryTenantWorkflowSkillTemplate(
        tenantId,
        tenantSkillCode,
      );
      if (currentRequest !== templateRequestId.current) return;
      if (!response.data) throw new Error('系统 Skill 模板为空，请重试');
      openEditor('create', {
        ...response.data,
        revision: 'NEW',
        scope: 'TENANT',
        tenantId,
        priority: 200,
        tenantEditable: true,
      });
    } catch (caught) {
      if (currentRequest === templateRequestId.current)
        message.error(errorMessage(caught, '加载系统 Skill 模板失败，请重试'));
    } finally {
      if (currentRequest === templateRequestId.current)
        setTemplateLoading(false);
    }
  };

  const canEditRow = (record: WorkflowSkill) =>
    editable &&
    (tenantMode ? record.tenantEditable : record.scope !== 'TENANT');

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
      if (tenantId !== undefined && source) {
        await updateTenantWorkflowSkill(
          tenantId,
          source.code,
          values.content,
          source.revision,
        );
      } else if (mode === 'edit' && source) {
        await updateWorkflowSkill(source.code, values.content, source.revision);
      } else if (mode === 'copy' && source) {
        await copyWorkflowSkill(source.code, values.code);
      } else {
        await createWorkflowSkill(values.code, values.content);
      }
      message.success(
        tenantMode
          ? '团队 Skill 已保存，将与系统规则共同生效，团队规则优先'
          : 'Skill 已保存并立即生效',
      );
      setMode(undefined);
      await load(search);
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
    const response =
      tenantId === undefined
        ? await queryWorkflowSkill(source.code)
        : await queryTenantWorkflowSkill(tenantId, source.code);
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
        title: '系统内置',
        dataIndex: 'scope',
        render: (scope) => (
          <Tag color={scope === 'TENANT' ? 'blue' : 'default'}>
            {scope === 'TENANT' ? '租户级' : '系统内置'}
          </Tag>
        ),
      },
      {
        title: '所属租户',
        dataIndex: 'tenantName',
        render: (_, record) =>
          record.scope === 'TENANT'
            ? `${record.tenantName || record.tenantId}（${record.tenantId}）`
            : '系统共享',
      },
      {
        title: '优先级',
        render: (_, record) =>
          record.priority ?? (record.scope === 'TENANT' ? 200 : 100),
      },
      {
        title: '引用 Agent',
        dataIndex: 'referencingAgentCodes',
        render: (codes: string[]) => (
          <Space wrap>
            {codes.map((code) => (
              <Tag key={code} title={code}>
                {agentNames.get(code) || code}
              </Tag>
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
            {!canEditRow(record) && (
              <Button type="link" onClick={() => openEditor('view', record)}>
                查看
              </Button>
            )}
            {canEditRow(record) && (
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={() =>
                  tenantMode
                    ? void editTenantSkill(record)
                    : openEditor('edit', record)
                }
              >
                {tenantMode && record.scope !== 'TENANT' ? '自定义' : '编辑'}
              </Button>
            )}
            {editable && !tenantMode && record.scope !== 'TENANT' && (
              <Button
                type="link"
                icon={<CopyOutlined />}
                onClick={() => openEditor('copy', record)}
              >
                复制
              </Button>
            )}
            {tenantMode && canEditRow(record) && record.scope === 'TENANT' && (
              <Popconfirm
                title="恢复系统默认？当前团队的定制内容将被移除。"
                okText="恢复默认"
                cancelText="取消"
                onConfirm={async () => {
                  if (tenantId === undefined) return;
                  try {
                    await restoreTenantWorkflowSkill(
                      tenantId,
                      record.code,
                      record.revision,
                    );
                    message.success('已恢复系统默认');
                    await load(search);
                  } catch (caught) {
                    message.error(errorMessage(caught, '恢复默认失败'));
                  }
                }}
              >
                <Button type="link">恢复系统默认</Button>
              </Popconfirm>
            )}
            {editable && !tenantMode && record.scope !== 'TENANT' && (
              <Popconfirm
                title={
                  record.referencingAgentCodes.length
                    ? `正在被 ${record.referencingAgentCodes.map(referenceLabel).join('、')} 引用，不能删除`
                    : '确认删除这个 Skill？'
                }
                disabled={record.referencingAgentCodes.length > 0}
                onConfirm={async () => {
                  await deleteWorkflowSkill(record.code);
                  await load(search);
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
    [agentNames, editable, load, referenceLabel, tenantMode, tenantId, search],
  );

  return (
    <>
      <Space orientation="vertical" style={{ width: '100%' }} size="middle">
        <Alert
          type="info"
          showIcon
          title={
            tenantMode
              ? '仅展示本团队 Skill。生成时系统规则与团队规则并存，团队优先级 200，高于系统优先级 100；未冲突的系统规则继续生效，现有素材标签及占位方式继续保留。'
              : 'Skill 以完整 SKILL.md 文件保存；修改成功后无需发布或重启，后续 Agent 运行立即读取新内容。租户级版本仅由所属团队所有者或管理员维护。'
          }
        />
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="搜索 Skill 名称或 code"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onSearch={setSearch}
            style={{ width: 320 }}
          />
          <Select
            aria-label="按 Agent 筛选"
            placeholder="全部 Agent"
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            value={agentFilter}
            onChange={setAgentFilter}
            style={{ width: 240 }}
            options={[...agentNames]
              .filter(
                ([code]) => !tenantMode || code === 'short-drama-storyboard',
              )
              .map(([value, name]) => ({
                value,
                label: `${name}（${value}）`,
              }))}
          />
          {!tenantMode && (
            <Select
              aria-label="按系统内置筛选"
              placeholder="全部级别"
              allowClear
              value={scopeFilter}
              onChange={setScopeFilter}
              style={{ width: 150 }}
              options={[
                { value: 'SYSTEM', label: '系统内置' },
                { value: 'TENANT', label: '租户级' },
              ]}
            />
          )}
          {!tenantMode && (
            <Select
              aria-label="按所属租户筛选"
              placeholder="全部租户"
              allowClear
              showSearch={{ optionFilterProp: 'label' }}
              value={tenantFilter}
              onChange={setTenantFilter}
              style={{ width: 200 }}
              options={[...tenantNames].map(([value, name]) => ({
                value,
                label: `${name}（${value}）`,
              }))}
            />
          )}
          {editable && !tenantMode && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openEditor('create')}
            >
              新增系统 Skill
            </Button>
          )}
          {editable && tenantMode && (
            <Button
              type="primary"
              aria-label="新增团队 Skill"
              icon={<PlusOutlined />}
              loading={templateLoading}
              onClick={createTenantSkill}
              disabled={skills.some((skill) => skill.code === tenantSkillCode)}
            >
              新增团队 Skill
            </Button>
          )}
        </Space>
        <Table
          rowKey={(record) =>
            `${record.scope || 'SYSTEM'}:${record.tenantId || 'system'}:${record.code}`
          }
          loading={loading}
          columns={columns}
          dataSource={skills}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Drawer
        title={
          mode === 'create'
            ? tenantMode
              ? '新增团队 Skill'
              : '新增系统 Skill'
            : mode === 'copy'
              ? '复制 Skill'
              : `${mode === 'view' ? '查看' : '编辑'} Skill：${source?.name ?? ''}`
        }
        open={Boolean(mode)}
        size="large"
        onClose={() => setMode(undefined)}
        destroyOnHidden
        extra={
          mode !== 'view' && (
            <Button type="primary" loading={saving} onClick={save}>
              保存并立即生效
            </Button>
          )
        }
      >
        <Alert
          type={mode === 'view' ? 'info' : 'warning'}
          showIcon
          title={
            tenantMode
              ? '仅编辑本团队规则，不修改系统 Skill。两者共同生效，创作规则冲突时以本团队规则为准，可恢复系统默认。'
              : '保存后立即生效；修改会影响所有引用该 Skill 的 Agent。'
          }
          description={
            source?.referencingAgentCodes.length
              ? `当前引用：${source.referencingAgentCodes.map(referenceLabel).join('、')}`
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
            <Input
              disabled={mode === 'edit' || mode === 'view' || tenantMode}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label="完整 SKILL.md"
            rules={[{ validator: validateContent }]}
            extra="需要 YAML frontmatter（name、description）和 Markdown 正文。"
          >
            <Input.TextArea
              readOnly={mode === 'view'}
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

export const TenantWorkflowSkillsPage = () => {
  const { initialState } = useModel('@@initialState');
  const tenantId = initialState?.currentTenantId;
  if (!tenantId) return <Alert type="info" title="请先选择创作团队" />;
  return <WorkflowSkillsPage key={tenantId} tenantId={tenantId} />;
};
