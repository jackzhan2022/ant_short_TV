import { history, useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Image,
  Input,
  List,
  Progress,
  Select,
  Table,
  Tag,
  Tabs,
  Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  controlTask,
  listTasks,
  type Query,
  type Summary,
  type Task,
  type TaskPage,
  taskChildren,
  taskContent,
  taskContentSection,
  taskDetail,
  taskSummary,
  type ContentSection,
  type TaskContent,
} from './service';

const statuses: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '处理中',
  WAITING_USER: '待处理',
  SUCCEEDED: '已完成',
  PARTIAL: '部分完成',
  FAILED: '已失败',
  CANCELED: '已取消',
};
const types: Record<string, string> = {
  VIDEO_DECOMPOSITION: '视频拆剧',
  STORYBOARD_BATCH: '分镜批次',
  SCRIPT_ANALYSIS: '剧本分析',
  SCRIPT_OPERATION: '剧本与资产处理',
  REVIEW: '剧本审核',
  IMAGE: '图片生成',
  VIDEO: '视频生成',
};
const actions: Record<string, string> = {
  CANCEL: '取消任务',
  RETRY: '重试',
  REGENERATE: '重新生成',
};
const active = (task: Task) => ['QUEUED', 'RUNNING'].includes(task.statusGroup);
const errorText = (error: unknown) =>
  error instanceof Error ? error.message : '任务加载失败，请重试';
const availability: Record<ContentSection['availability'], string> = {
  AVAILABLE: '可查看',
  PENDING: '尚未生成',
  NOT_RECORDED: '此任务未保存此内容',
  DELETED: '内容已删除',
  RESTRICTED: '无权查看此内容',
  UNSUPPORTED: '历史任务暂不支持此内容',
};
function ContentSectionView({ section, tenant, taskKey }: { section: ContentSection; tenant: number; taskKey: string }) {
  const media = section.kind === 'IMAGE' || section.kind === 'VIDEO';
  const [copying, setCopying] = useState(false);
  const copyFullText = async () => {
    setCopying(true);
    try {
      let offset = 0;
      let hasMore = true;
      const chunks: string[] = [];
      do {
        const page = await taskContentSection(tenant, taskKey, section.key, offset);
        chunks.push(page.text);
        if (page.hasMore && page.nextOffset === undefined) throw new Error('全文读取不完整');
        offset = page.nextOffset ?? 0;
        hasMore = page.hasMore;
      } while (hasMore);
      await navigator.clipboard?.writeText(chunks.join(''));
    } finally {
      setCopying(false);
    }
  };
  return <section style={{ marginTop: 20 }}>
    <Typography.Title level={5} style={{ marginBottom: 8 }}>
      {section.title} <Tag>{availability[section.availability]}</Tag>
    </Typography.Title>
    {section.preview && <Typography.Paragraph copyable={section.hasMore ? false : { text: section.preview }} style={{ whiteSpace: 'pre-wrap' }}>{section.preview}</Typography.Paragraph>}
    {section.hasMore && <div><Typography.Text type="secondary">内容较长，当前仅展示前 4,000 个字符。</Typography.Text><Button type="link" size="small" loading={copying} onClick={copyFullText}>复制全文</Button></div>}
    {section.fields.length > 0 && <Descriptions size="small" column={1} items={section.fields.map((field) => ({ key: field.label, label: field.label, children: field.value }))} />}
    {media && section.items.length > 0 && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 12 }}>
      {section.items.map((item, index) => section.kind === 'IMAGE' ? <div key={String(item.id ?? index)}><Image width={160} src={String(item.thumbnailUrl ?? item.url)} alt={`${section.title} ${index + 1}`} />{item.selected && <Tag color="success">当前采用</Tag>}</div> : <video key={String(item.id ?? index)} controls preload="none" poster={String(item.thumbnailUrl ?? '')} style={{ width: 320, maxWidth: '100%' }}><source src={String(item.url ?? '')} /><track kind="captions" srcLang="zh-CN" label="暂无字幕" /></video>)}
    </div>}
    {!media && section.items.length > 0 && <List size="small" bordered dataSource={section.items} renderItem={(item) => <List.Item><Typography.Text>{Object.entries(item).filter(([key]) => !['id', 'url', 'thumbnailUrl'].includes(key)).map(([key, value]) => `${key}: ${String(value)}`).join(' · ')}</Typography.Text></List.Item>} />}
  </section>;
}
function queryFromUrl(): Query {
  const p = new URLSearchParams(window.location.search);
  return {
    scope: p.get('scope') || 'mine',
    page: Math.max(1, Number(p.get('page')) || 1),
    pageSize: Math.min(100, Math.max(1, Number(p.get('pageSize')) || 20)),
    ...Object.fromEntries(
      [
        'type',
        'statusGroup',
        'projectId',
        'creatorId',
        'createdFrom',
        'createdTo',
      ].flatMap((k) => (p.get(k) ? [[k, p.get(k)]] : [])),
    ),
  };
}
function TasksForTeam({ tenant }: { tenant: number }) {
  const [query, setQuery] = useState<Query>(queryFromUrl);
  const [draftQuery, setDraftQuery] = useState<Query>(queryFromUrl);
  const [selected, setSelected] = useState<string | null>(() =>
    new URLSearchParams(window.location.search).get('task'),
  );
  const [page, setPage] = useState<TaskPage>();
  const [summary, setSummary] = useState<Summary>();
  const [detail, setDetail] = useState<Task>();
  const [content, setContent] = useState<TaskContent>();
  const [children, setChildren] = useState<TaskPage>();
  const [childPage, setChildPage] = useState(1);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [contentError, setContentError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const mounted = useRef(true);
  const actionKeys = useRef(new Map<string, string>());
  const contentAbort = useRef<AbortController>();
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    const p = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== '') p.set(k, String(v));
    });
    if (selected) p.set('task', selected);
    window.history.replaceState({}, '', `${window.location.pathname}?${p}`);
  }, [query, selected]);
  useEffect(() => {
    const onPop = () => {
      const next = queryFromUrl();
      setQuery(next);
      setDraftQuery(next);
      setSelected(new URLSearchParams(window.location.search).get('task'));
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    setPage(undefined);
    setSummary(undefined);
    setError('');
    setLoading(true);
    const load = async () => {
      if (document.visibilityState === 'hidden') {
        timer = setTimeout(load, 5000);
        return;
      }
      try {
        const [next, counts] = await Promise.all([
          listTasks(tenant, query, abort.signal),
          taskSummary(tenant, query, abort.signal),
        ]);
        if (abort.signal.aborted) return;
        setPage(next);
        setSummary(counts);
        setError('');
        if (next.items.some(active)) timer = setTimeout(load, 5000);
      } catch (e) {
        if (!abort.signal.aborted) {
          setPage(undefined);
          setSummary(undefined);
          setError(errorText(e));
        }
      } finally {
        if (!abort.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => {
      abort.abort();
      clearTimeout(timer);
    };
  }, [tenant, query, revision]);
  useEffect(() => {
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    setDetail(undefined);
    setChildren(undefined);
    setDetailError('');
    if (!selected) return () => abort.abort();
    const load = async () => {
      if (document.visibilityState === 'hidden') {
        timer = setTimeout(load, 5000);
        return;
      }
      try {
        const next = await taskDetail(tenant, selected, abort.signal);
        if (abort.signal.aborted) return;
        const childRows =
          next.childCounts.total > 0
            ? await taskChildren(
                tenant,
                selected,
                { scope: query.scope, page: childPage, pageSize: 20 },
                abort.signal,
              )
            : undefined;
        if (abort.signal.aborted) return;
        setDetail(next);
        setChildren(childRows);
        if (next.restricted) {
          contentAbort.current?.abort();
          setContent(undefined);
          setContentError('无权查看此任务内容');
        }
        setDetailError('');
        if (active(next)) timer = setTimeout(load, 5000);
      } catch (e) {
        if (!abort.signal.aborted) {
          setDetail(undefined);
          setChildren(undefined);
          setDetailError(errorText(e));
        }
      }
    };
    void load();
    return () => {
      abort.abort();
      clearTimeout(timer);
    };
  }, [tenant, selected, query.scope, childPage, revision]);
  useEffect(() => {
    const abort = new AbortController();
    contentAbort.current = abort;
    setContent(undefined);
    setContentError('');
    if (!selected) return () => abort.abort();
    void taskContent(tenant, selected, abort.signal)
      .then((next) => { if (!abort.signal.aborted) setContent(next); })
      .catch((error) => { if (!abort.signal.aborted) setContentError(errorText(error)); });
    return () => {
      abort.abort();
      if (contentAbort.current === abort) contentAbort.current = undefined;
    };
  }, [tenant, selected, revision]);
  const updateDraft = (patch: Partial<Query>) => {
    setDraftQuery((q) => ({ ...q, ...patch }));
  };
  const submitQuery = () => {
    setQuery((q) => ({ ...q, ...draftQuery, page: 1 }));
    setSelected(null);
  };
  const select = (task: Task) => {
    setSelected(task.taskKey);
    setChildPage(1);
  };
  const run = async (task: Task, action: string) => {
    if (busy) return;
    setBusy(true);
    setDetailError('');
    const identity = `${task.taskKey}:${action}`;
    const key = actionKeys.current.get(identity) || crypto.randomUUID();
    actionKeys.current.set(identity, key);
    try {
      const result = await controlTask(tenant, task.taskKey, action, key);
      if (mounted.current) {
        actionKeys.current.delete(identity);
        setSelected(result.taskKey);
        setRevision((n) => n + 1);
      }
    } catch (e) {
      if (mounted.current) setDetailError(errorText(e));
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const columns = [
    {
      title: '任务',
      key: 'title',
      render: (_: unknown, t: Task) => (
        <Button type="link" onClick={() => select(t)}>
          {t.title}
        </Button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      render: (v: string) => types[v] || '分集任务',
    },
    {
      title: '项目',
      dataIndex: 'projectName',
      render: (v?: string) => v || '—',
    },
    { title: '创建人', dataIndex: 'creatorName' },
    {
      title: '状态',
      dataIndex: 'statusGroup',
      render: (v: string) => statuses[v] || v,
    },
    {
      title: '进度',
      key: 'progress',
      render: (_: unknown, t: Task) =>
        t.progress != null ? (
          <Progress percent={Number(t.progress)} size="small" />
        ) : (
          t.phase || '—'
        ),
    },
    { title: '创建时间', dataIndex: 'createdAt' },
  ];
  return (
    <main style={{ padding: 24 }}>
      <Typography.Title level={2} style={{ marginTop: 0, marginBottom: 4 }}>任务中心</Typography.Title>
      <Typography.Paragraph type="secondary">查看后台生产进度、提交内容与实际产出。</Typography.Paragraph>
      <Tabs
        activeKey={query.scope}
        onChange={(scope) => {
          const next = { ...draftQuery, scope, creatorId: undefined, page: 1 };
          setDraftQuery(next);
          setQuery(next);
          setSelected(null);
        }}
        items={[
          { key: 'mine', label: '我的任务' },
          ...(page?.canViewTeamTasks
            ? [{ key: 'team', label: '团队任务' }]
            : []),
        ]}
      />
      <div
        style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}
      >
        <Select
          aria-label="任务类型"
          placeholder="全部类型"
          allowClear
          value={draftQuery.type}
          style={{ width: 170 }}
          options={Object.entries(types).map(([value, label]) => ({
            value,
            label,
          }))}
          onChange={(type) => updateDraft({ type })}
        />
        <Select
          aria-label="任务状态"
          placeholder="全部状态"
          allowClear
          value={draftQuery.statusGroup}
          style={{ width: 140 }}
          options={Object.entries(statuses).map(([value, label]) => ({
            value,
            label,
          }))}
          onChange={(statusGroup) => updateDraft({ statusGroup })}
        />
        <Input
          aria-label="项目编号"
          placeholder="项目编号"
          type="number"
          min={1}
          value={draftQuery.projectId || ''}
          style={{ width: 130 }}
          onChange={(e) =>
            updateDraft({ projectId: e.target.value || undefined })
          }
        />
        {page?.canViewTeamTasks && query.scope === 'team' && (
          <Input
            aria-label="创建人编号"
            placeholder="创建人编号"
            type="number"
            min={1}
            value={draftQuery.creatorId || ''}
            style={{ width: 130 }}
            onChange={(e) =>
              updateDraft({ creatorId: e.target.value || undefined })
            }
          />
        )}
        <label>
          开始时间{' '}
          <input
            aria-label="开始时间"
            type="datetime-local"
            value={draftQuery.createdFrom || ''}
            onChange={(e) =>
              updateDraft({ createdFrom: e.target.value || undefined })
            }
          />
        </label>
        <label>
          结束时间{' '}
          <input
            aria-label="结束时间"
            type="datetime-local"
            value={draftQuery.createdTo || ''}
            onChange={(e) =>
              updateDraft({ createdTo: e.target.value || undefined })
            }
          />
        </label>
        <Button type="primary" onClick={submitQuery}>查询</Button>
        <Button onClick={() => {
          const reset: Query = { scope: query.scope, page: 1, pageSize: 20 };
          setDraftQuery(reset); setQuery(reset); setSelected(null);
        }}>重置</Button>
        <Button onClick={() => {
          setRevision((n) => n + 1);
        }}>
          刷新
        </Button>
      </div>
      {error && (
        <Alert
          type="error"
          title={error}
          action={
            <Button onClick={() => setRevision((n) => n + 1)}>重试</Button>
          }
        />
      )}
      {summary && (
        <p>
          共 {summary.total} 项 ·{' '}
          {Object.entries(summary.counts)
            .filter(([, count]) => count > 0)
            .map(([state, count]) => `${statuses[state]} ${count}`)
            .join(' · ')}
        </p>
      )}
      <Table<Task>
        rowKey="taskKey"
        columns={columns}
        dataSource={page?.items || []}
        loading={loading}
        scroll={{ x: 900 }}
        pagination={{
          current: query.page,
          pageSize: query.pageSize,
          total: page?.total || 0,
          showSizeChanger: true,
          onChange: (pageNumber, pageSize) =>
            setQuery((current) => ({ ...current, page: pageNumber, pageSize })),
        }}
      />
      <Drawer
        title={detail?.title || '任务详情'}
        open={Boolean(selected)}
        size={720}
        onClose={() => setSelected(null)}
      >
        {detailError && (
          <Alert
            type="error"
            title={detailError}
            action={
              <Button onClick={() => setRevision((n) => n + 1)}>重试</Button>
            }
          />
        )}
        {detail && (
          <>
            <p>
              {statuses[detail.statusGroup]} ·{' '}
              {detail.phase || statuses[detail.statusGroup]}
            </p>
            {detail.progress != null && (
              <Progress percent={Number(detail.progress)} />
            )}
            <p>{detail.resultSummary}</p>
            {detail.warningSummary && (
              <Alert type="warning" title={detail.warningSummary} />
            )}
            {detail.restricted && (
              <p>当前无项目访问权限，仅展示任务状态和时间。</p>
            )}
            <p>
              创建时间：{detail.createdAt} · 完成时间：
              {detail.completedAt || '—'}
            </p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
              {detail.destination && (
                <Button
                  onClick={() => {
                    if (detail.destination) history.push(detail.destination);
                  }}
                >
                  返回业务
                </Button>
              )}
              {detail.allowedActions
                .filter((a) => actions[a])
                .map((action) => (
                  <Button
                    key={action}
                    loading={busy}
                    onClick={() => void run(detail, action)}
                  >
                    {actions[action]}
                  </Button>
                ))}
            </div>
            {contentError && <Alert type="warning" title={contentError} />}
            {content?.sections.map((section) => <ContentSectionView key={section.key} section={section} tenant={tenant} taskKey={selected ?? detail.taskKey} />)}
            {detail.childCounts.total > 0 && (
              <>
                <p>
                  共 {detail.childCounts.total} 项 · 成功{' '}
                  {detail.childCounts.success} · 失败{' '}
                  {detail.childCounts.failed} · 取消{' '}
                  {detail.childCounts.canceled}
                </p>
                <Table<Task>
                  rowKey="taskKey"
                  size="small"
                  columns={columns.filter(
                    (c) =>
                      !['creatorName', 'projectName'].includes(
                        ('dataIndex' in c ? c.dataIndex : '') || '',
                      ),
                  )}
                  dataSource={children?.items || []}
                  scroll={{ x: 650 }}
                  pagination={{
                    current: childPage,
                    pageSize: 20,
                    total: children?.total || 0,
                    onChange: setChildPage,
                  }}
                />
              </>
            )}
          </>
        )}
      </Drawer>
    </main>
  );
}
export default function Tasks() {
  const { initialState } = useModel('@@initialState');
  const tenant = initialState?.currentTenantId;
  const previousTenant = useRef(tenant);
  // The keyed child discards every previous-team request and state before new data renders.
  if (tenant !== previousTenant.current) {
    previousTenant.current = tenant;
    window.history.replaceState({}, '', window.location.pathname);
  }
  return tenant ? (
    <TasksForTeam key={tenant} tenant={tenant} />
  ) : (
    <Alert type="info" title="请先选择创作团队" />
  );
}
