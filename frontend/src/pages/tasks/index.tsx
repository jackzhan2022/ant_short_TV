import { history, useModel } from '@umijs/max';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
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
  taskContentItems,
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
const resultLabels: Record<string, string> = {
  asset_type: '资产类型', episode_key: '分集标识', episode_no: '集数', execution_id: '执行来源',
  result_type: '结果类型', status: '状态', storyboard_no: '分镜编号', visual_description: '画面描述',
  dialogue: '对白', video_prompt: '视频提示词', source_file_name: '源文件', mime_type: '文件类型',
  duration_seconds: '时长（秒）', file_size: '文件大小', name: '名称', id: '编号',
};
function ContentSectionView({ section, tenant, taskKey }: { section: ContentSection; tenant: number; taskKey: string }) {
  const media = section.kind === 'IMAGE' || section.kind === 'VIDEO';
  const [copying, setCopying] = useState(false);
  const [items, setItems] = useState(section.items);
  const [itemsPage, setItemsPage] = useState(1);
  const [itemsMore, setItemsMore] = useState(section.hasMore);
  const [loadingItems, setLoadingItems] = useState(false);
  const [displayText, setDisplayText] = useState(section.preview ?? '');
  const [textMore, setTextMore] = useState(section.kind === 'TEXT' && section.hasMore);
  const [expanding, setExpanding] = useState(false);
  const [sectionError, setSectionError] = useState('');
  const copyAbort = useRef<AbortController | undefined>(undefined);
  useEffect(() => () => copyAbort.current?.abort(), []);
  useEffect(() => {
    setItems(section.items); setItemsPage(1); setItemsMore(section.hasMore);
    setDisplayText(section.preview ?? ''); setTextMore(section.kind === 'TEXT' && section.hasMore);
  }, [section]);
  const copyFullText = async () => {
    copyAbort.current?.abort();
    const abort = new AbortController(); copyAbort.current = abort;
    setCopying(true);
    setSectionError('');
    try {
      let offset = 0;
      let hasMore = true;
      const chunks: string[] = [];
      do {
        const page = await taskContentSection(tenant, taskKey, section.key, offset, abort.signal);
        chunks.push(page.text);
        if (page.hasMore && page.nextOffset === undefined) throw new Error('全文读取不完整');
        offset = page.nextOffset ?? 0;
        hasMore = page.hasMore;
      } while (hasMore);
      await navigator.clipboard?.writeText(chunks.join(''));
    } catch (error) {
      if (!abort.signal.aborted) setSectionError(errorText(error));
    } finally {
      if (!abort.signal.aborted) setCopying(false);
    }
  };
  const loadMoreItems = async () => {
    setLoadingItems(true);
    try {
      const next = await taskContentItems(tenant, taskKey, section.key, itemsPage + 1, 20);
      setItems((current) => [...current, ...next.items]);
      setItemsPage(next.page); setItemsMore(next.hasMore);
    } finally {
      setLoadingItems(false);
    }
  };
  const expandText = async () => {
    copyAbort.current?.abort(); const abort = new AbortController(); copyAbort.current = abort;
    setExpanding(true); setSectionError('');
    try {
      const next = await taskContentSection(tenant, taskKey, section.key, displayText.length, abort.signal);
      if (!abort.signal.aborted) { setDisplayText((current) => current + next.text); setTextMore(next.hasMore); }
    } catch (error) {
      if (!abort.signal.aborted) setSectionError(errorText(error));
    } finally {
      if (!abort.signal.aborted) setExpanding(false);
    }
  };
  return <section style={{ marginTop: 20 }}>
    <Typography.Title level={5} style={{ marginBottom: 8 }}>
      {section.title} <Tag>{availability[section.availability]}</Tag>
    </Typography.Title>
    {section.sourceVersion && <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>来源版本：{section.sourceVersion}</Typography.Paragraph>}
    {sectionError && <Alert type="warning" title={sectionError} />}
    {displayText && <Typography.Paragraph copyable={textMore ? false : { text: displayText }} style={{ whiteSpace: 'pre-wrap' }}>{displayText}</Typography.Paragraph>}
    {section.kind === 'TEXT' && (textMore || section.hasMore) && <div><Typography.Text type="secondary">内容较长，按需读取后续内容。</Typography.Text>{textMore && <Button type="link" size="small" loading={expanding} onClick={expandText}>继续展开</Button>}<Button type="link" size="small" loading={copying} onClick={copyFullText}>复制全文</Button></div>}
    {section.hasMore && !textMore && <Typography.Text type="secondary">仅展示前 20 项结果。</Typography.Text>}
    {section.fields.length > 0 && <Descriptions size="small" column={1} items={section.fields.map((field) => ({ key: field.label, label: field.label, children: field.value }))} />}
    {media && items.length > 0 && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 12 }}>
      {items.map((item, index) => section.kind === 'IMAGE' ? <div key={String(item.id ?? index)}><Image width={160} src={String(item.thumbnailUrl ?? item.url)} alt={`${section.title} ${index + 1}`} />{typeof item.role === 'string' && <Tag>{item.role}</Tag>}{item.selected && <Tag color="success">当前采用</Tag>}<a href={String(item.url ?? '')} download>下载</a></div> : <div key={String(item.id ?? index)}><video controls preload="none" poster={String(item.thumbnailUrl ?? '')} style={{ width: 320, maxWidth: '100%' }}><source src={String(item.url ?? '')} /><track kind="captions" srcLang="zh-CN" label="暂无字幕" /></video><a href={String(item.url ?? '')} download>下载</a></div>)}
    </div>}
    {itemsMore && !textMore && <Button type="link" loading={loadingItems} onClick={loadMoreItems}>加载更多结果</Button>}
    {!media && items.length > 0 && <List size="small" bordered dataSource={items} renderItem={(item) => <List.Item><Typography.Text>{Object.entries(item).filter(([key]) => !['id', 'url', 'thumbnailUrl'].includes(key)).map(([key, value]) => `${resultLabels[key] ?? key}: ${String(value)}`).join(' · ')}</Typography.Text></List.Item>} />}
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
  const [parentTask, setParentTask] = useState<string | null>(() =>
    new URLSearchParams(window.location.search).get('parent'),
  );
  const [page, setPage] = useState<TaskPage>();
  const [summary, setSummary] = useState<Summary>();
  const [detail, setDetail] = useState<Task>();
  const [content, setContent] = useState<TaskContent>();
  const [children, setChildren] = useState<TaskPage>();
  const [childPage, setChildPage] = useState(() =>
    Math.max(1, Number(new URLSearchParams(window.location.search).get('childPage')) || 1),
  );
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [contentError, setContentError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const mounted = useRef(true);
  const actionKeys = useRef(new Map<string, string>());
  const contentAbort = useRef<AbortController | undefined>(undefined);
  const detailStatus = useRef<string | undefined>(undefined);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => { detailStatus.current = undefined; }, [tenant, selected]);
  useEffect(() => {
    const p = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== '') p.set(k, String(v));
    });
    if (selected) p.set('task', selected);
    if (parentTask) p.set('parent', parentTask);
    if (parentTask) p.set('childPage', String(childPage));
    window.history.replaceState({}, '', `${window.location.pathname}?${p}`);
  }, [query, selected, parentTask, childPage]);
  useEffect(() => {
    const onPop = () => {
      const next = queryFromUrl();
      setQuery(next);
      setDraftQuery(next);
      setSelected(new URLSearchParams(window.location.search).get('task'));
      setParentTask(new URLSearchParams(window.location.search).get('parent'));
      setChildPage(Math.max(1, Number(new URLSearchParams(window.location.search).get('childPage')) || 1));
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
        const previousStatus = detailStatus.current;
        detailStatus.current = next.statusGroup;
        setDetail(next);
        setChildren(childRows);
        if (next.restricted) {
          contentAbort.current?.abort();
          setContent(undefined);
          setContentError('无权查看此任务内容');
        }
        setDetailError('');
        if (previousStatus && ['QUEUED', 'RUNNING'].includes(previousStatus) && !active(next)) setRevision((value) => value + 1);
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
  const select = (task: Task, fromChild = false) => {
    setParentTask(fromChild ? detail?.taskKey ?? null : null);
    setSelected(task.taskKey);
    if (!fromChild) setChildPage(1);
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
  const columns: ProColumns<Task>[] = [
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
      render: (_: unknown, record) => types[record.type] || '分集任务',
    },
    {
      title: '项目',
      dataIndex: 'projectName',
      render: (_: unknown, record) => record.projectName || '—',
    },
    { title: '创建人', dataIndex: 'creatorName' },
    {
      title: '状态',
      dataIndex: 'statusGroup',
      render: (_: unknown, record) => statuses[record.statusGroup] || record.statusGroup,
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
  const childColumns = [
    { title: '任务', key: 'title', render: (_: unknown, task: Task) => <Button type="link" onClick={() => select(task, true)}>{task.title}</Button> },
    { title: '类型', render: (_: unknown, task: Task) => types[task.type] || '分集任务' },
    { title: '状态', render: (_: unknown, task: Task) => statuses[task.statusGroup] || task.statusGroup },
    { title: '进度', render: (_: unknown, task: Task) => task.progress != null ? <Progress percent={Number(task.progress)} size="small" /> : task.phase || '—' },
    { title: '创建时间', dataIndex: 'createdAt' },
  ];
  return (
    <PageContainer title="任务中心" content="查看后台生产进度、提交内容与实际产出。">
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
      <ProTable<Task>
        rowKey="taskKey"
        columns={columns}
        dataSource={page?.items || []}
        loading={loading}
        search={false}
        options={false}
        toolBarRender={() => [<Button key="refresh" onClick={() => setRevision((n) => n + 1)}>刷新</Button>]}
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
        onClose={() => { setSelected(null); setParentTask(null); }}
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
              {parentTask && <Button onClick={() => { setSelected(parentTask); setParentTask(null); }}>返回批次</Button>}
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
                  columns={childColumns}
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
    </PageContainer>
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
