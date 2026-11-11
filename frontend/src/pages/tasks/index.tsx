import DownloadButton from '@/pages/downloads/DownloadButton';
import { type DownloadSource, sourceFromDownloadUrl } from '@/pages/downloads/service';
import { type ProColumns, ProTable } from '@ant-design/pro-components';
import {
  CheckOutlined,
  DownOutlined,
  FileTextOutlined,
  FolderOutlined,
  PictureOutlined,
  ReloadOutlined,
  RightOutlined,
  SoundOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Input,
  List,
  Progress,
  Select,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import styles from './index.module.css';
import { displayEnum, displayField, displaySourceVersion, displayTime } from './presentation';
import ClickToPlayVideo from '@/components/ClickToPlayVideo';
import LazyMediaImage from '@/components/LazyMediaImage';
import PointEstimateButton from '@/components/PointEstimateButton';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import {
  type ContentSection,
  controlTask,
  listTasks,
  type Query,
  type Summary,
  type Task,
  type TaskContent,
  type TaskPage,
  taskChildren,
  taskContent,
  taskContentItems,
  taskContentSection,
  taskDetail,
  taskSummary,
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
  SUBTITLE_ERASE: '字幕擦除',
  FREE_CREATION: '自由创作',
  VIRTUAL_AVATAR: '虚拟人像',
};
const actions: Record<string, string> = {
  CANCEL: '取消任务',
  RETRY: '重试',
  REGENERATE: '重新生成',
};
const active = (task: Task) => ['QUEUED', 'RUNNING'].includes(task.statusGroup);
const taskStatus = (task: Task) => task.type === 'VIDEO' && active(task)
  ? task.phase === '智能虚拟化中' ? '智能虚拟化中' : '生成中'
  : statuses[task.statusGroup] || displayEnum(task.statusGroup);
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
const hasAdvancedFilters = (query: Query) => Boolean(
  query.statusGroup || query.creatorId || query.createdFrom || query.createdTo,
);
function TaskStatusView({ task }: { task: Task }) {
  const percent = task.progress != null && Number.isFinite(Number(task.progress))
    ? Math.min(100, Math.max(0, Math.round(Number(task.progress)))) : undefined;
  const showProgress = active(task) && percent !== undefined;
  return <div className={styles.taskStatus}>
    <div className={styles.statusLine}>
      <Tag className={styles.statusTag} data-state={task.statusGroup} icon={task.statusGroup === 'SUCCEEDED' ? <CheckOutlined /> : <span className={styles.statusDot} />}>
        {taskStatus(task)}
      </Tag>
      {showProgress && <span className={styles.percent}>{percent}%</span>}
    </div>
    {showProgress && <Progress percent={percent} showInfo={false} size={[100, 3]} strokeColor="var(--app-color-primary, #5252ff)" status={task.statusGroup === 'RUNNING' ? 'active' : 'normal'} />}
  </div>;
}
function TaskTypeIcon({ type }: { type: string }) {
  return <span className={styles.typeIcon} data-kind={type} title={types[type] || '分集任务'}>
    {type === 'IMAGE' ? <PictureOutlined /> : ['VIDEO', 'VIDEO_DECOMPOSITION', 'SUBTITLE_ERASE', 'VIRTUAL_AVATAR'].includes(type) ? <VideoCameraOutlined /> : type === 'FREE_CREATION' ? <SoundOutlined /> : <FileTextOutlined />}
  </span>;
}
function TaskTime({ value }: { value?: string }) {
  const [date, time] = displayTime(value).split(' ');
  return <time className={styles.taskTime} dateTime={value}><span>{date}</span>{time && <span> {time}</span>}</time>;
}
function ContentSectionView({ section, tenant, taskKey }: { section: ContentSection; tenant: number; taskKey: string }) {
  const media = ['IMAGE', 'VIDEO', 'AUDIO'].includes(section.kind);
  const references = section.key.startsWith('references');
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
  const settings = section.key === 'settings';
  const Section = settings ? 'details' : 'section';
  const Heading = settings ? 'summary' : 'div';
  return <Section className={`${styles.contentSection} ${settings ? styles.settingsSection : ''}`} aria-label={section.title}>
    <Heading className={styles.sectionHeading}>
      <Typography.Title level={5}><span>{section.title}</span></Typography.Title>
      {media && items.length > 0 && <Tag>{items.length} 项{itemsMore ? '+' : ''}</Tag>}
      {section.availability !== 'AVAILABLE' && <Tag>{availability[section.availability]}</Tag>}
      {settings && <DownOutlined className={styles.settingsChevron} />}
    </Heading>
    {section.sourceVersion && <p className={styles.sourceVersion}>来源版本：{displaySourceVersion(section.sourceVersion)}</p>}
    {sectionError && <Alert type="warning" title={sectionError} />}
    {displayText && <Typography.Paragraph copyable={textMore ? false : { text: displayText }} className={styles.textContent}>{displayText}</Typography.Paragraph>}
    {section.kind === 'TEXT' && (textMore || section.hasMore) && <div>{textMore && <Button type="link" size="small" loading={expanding} onClick={expandText}>继续展开</Button>}<Button type="link" size="small" loading={copying} onClick={copyFullText}>复制全文</Button></div>}
    {section.hasMore && !textMore && <Typography.Text type="secondary">仅展示前 20 项结果。</Typography.Text>}
    {section.fields.length > 0 && <Descriptions className={styles.fieldTable} bordered layout="vertical" size="small" column={{ xs: 1, sm: 2 }} items={section.fields.map((field) => ({ key: field.label, label: field.label, children: displayField(field.label, field.value) }))} />}
    {media && items.length > 0 && <div className={references ? styles.mediaGrid + ' ' + styles.referenceGrid : styles.mediaGrid}>
      {items.map((item, index) => {
        const kind = String(item.mediaType || section.kind);
        const name = String(item.displayName || item.fileName || item.label || (section.title + ' ' + (index + 1)));
        const alt = section.title + ' ' + (index + 1);
        const url = typeof item.url === 'string' ? item.url : '';
        const thumbnail = typeof item.thumbnailUrl === 'string' ? item.thumbnailUrl : '';
        const download = typeof item.downloadUrl === 'string' ? item.downloadUrl : '';
        const downloadSource = item.downloadSource as DownloadSource | undefined ?? sourceFromDownloadUrl(download);
        return <article className={styles.mediaCard} key={String(item.id ?? index)}>
          <div className={styles.mediaPreview} style={kind === 'VIDEO' && Number(item.width) > 0 && Number(item.height) > 0 ? { aspectRatio: `${item.width} / ${item.height}` } : undefined}>
            {kind === 'IMAGE' ? <LazyMediaImage width="100%" height="100%" src={thumbnail} alt={alt}
              imageStyle={{ objectFit: 'contain' }} preview={url ? { src: url } : false}
              placeholderFallback={<span className={styles.mediaFallback}>缩略图暂不可用</span>} />
              : kind === 'AUDIO' && url ? <audio controls preload="none" src={url} aria-label={name}><track kind="captions" /></audio>
              : kind === 'VIDEO' && url ? <ClickToPlayVideo src={url} poster={thumbnail} alt={alt} style={{ width: '100%', height: '100%' }} />
              : <span className={styles.mediaFallback}>素材暂不可预览</span>}
          </div>
          <div className={styles.mediaMeta}>
            <div className={styles.mediaName}>{name}</div>
            <div className={styles.mediaActions}>
              <Tag>{displayEnum(typeof item.role === 'string' ? item.role : kind)}</Tag>
              {item.selected && <Tag color="success">当前采用</Tag>}
              {kind === 'IMAGE' && !thumbnail && url && <a href={url} target="_blank" rel="noreferrer">查看原图</a>}
              {downloadSource && <DownloadButton name={name} sources={[downloadSource]}>{kind === 'AUDIO' ? '下载音频' : '下载'}</DownloadButton>}
            </div>
          </div>
        </article>;
      })}
    </div>}
    {itemsMore && !textMore && <Button type="link" loading={loadingItems} onClick={loadMoreItems}>加载更多结果</Button>}
    {!media && items.length > 0 && <List size="small" bordered dataSource={items} renderItem={(item) => <List.Item><Typography.Text>{Object.entries(item).filter(([key]) => !['id', 'url', 'thumbnailUrl', 'downloadUrl'].includes(key)).map(([key, value]) => `${resultLabels[key] ?? key}: ${displayField(resultLabels[key] ?? key, value)}`).join(' · ')}</Typography.Text></List.Item>} />}
  </Section>;
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
  const [advanced, setAdvanced] = useState(() => hasAdvancedFilters(queryFromUrl()));
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
      setAdvanced(hasAdvancedFilters(next));
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
          taskSummary(tenant, { ...query, statusGroup: undefined }, abort.signal),
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
      if (isPointEstimateCancelled(e)) return;
      if (mounted.current) setDetailError(errorText(e));
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const filterStatus = (statusGroup: string) => {
    const state = statusGroup || undefined;
    setQuery((current) => ({ ...current, statusGroup: state, page: 1 }));
    setDraftQuery((current) => ({ ...current, statusGroup: state }));
    setSelected(null);
  };
  const columns: ProColumns<Task>[] = [
    {
      title: '任务', key: 'title', width: '34%',
      render: (_: unknown, task: Task) => <div className={styles.taskCell}>
        <TaskTypeIcon type={task.type} />
        <button type="button" className={styles.taskName} onClick={() => select(task)} title={task.title}>{task.title}</button>
      </div>,
    },
    { title: '项目', key: 'project', width: '22%', render: (_: unknown, task: Task) => <span className={styles.projectName} title={task.projectName}>{task.projectName || '—'}</span> },
    { title: '状态', key: 'status', width: '18%', render: (_: unknown, task: Task) => <TaskStatusView task={task} /> },
    { title: '创建时间', key: 'createdAt', width: '22%', render: (_: unknown, task: Task) => <TaskTime value={task.createdAt} /> },
    { title: <span className={styles.srOnly}>查看详情</span>, key: 'actions', width: 44, align: 'right', render: (_: unknown, task: Task) => <Button type="text" size="small" className={styles.detailButton} icon={<RightOutlined />} aria-label={`查看${task.title}详情`} onClick={() => select(task)} /> },
  ];
  const childColumns = [
    { title: '任务', key: 'title', render: (_: unknown, task: Task) => <Button type="link" onClick={() => select(task, true)}>{task.title}</Button> },
    { title: '类型', render: (_: unknown, task: Task) => types[task.type] || '分集任务' },
    { title: '状态', render: (_: unknown, task: Task) => <TaskStatusView task={task} /> },
    { title: '创建时间', render: (_: unknown, task: Task) => <TaskTime value={task.createdAt} /> },
  ];
  const closeDetail = () => { setSelected(null); setParentTask(null); };
  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.pageTitle}>
          <h1>任务中心</h1>
          {page?.canViewTeamTasks ? <Tabs
            className={styles.scopeTabs}
            activeKey={query.scope}
            onChange={(scope) => {
              const next = { ...draftQuery, scope, creatorId: undefined, page: 1 };
              setDraftQuery(next); setQuery(next); setSelected(null);
            }}
            items={[{ key: 'mine', label: '我的任务' }, { key: 'team', label: '团队任务' }]}
          /> : <span className={styles.scopeLabel}>{query.scope === 'team' ? '团队任务' : '我的任务'}</span>}
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => setRevision((n) => n + 1)}>刷新</Button>
      </header>
      <section className={styles.listPanel} aria-label="任务列表">
        <fieldset className={styles.statusFilters}>
          <legend className={styles.srOnly}>任务状态筛选</legend>
          {[['', '全部'], ['RUNNING', '处理中'], ['WAITING_USER', '待处理'], ['SUCCEEDED', '完成'], ['FAILED', '失败']].map(([state, label]) => {
            const count = summary ? state ? summary.counts[state] ?? 0 : summary.total : undefined;
            return <button type="button" key={state} aria-pressed={(query.statusGroup || '') === state} onClick={() => filterStatus(state)}>
              <span>{label}</span><span className={styles.statusCount}>{count === undefined ? '—' : count.toLocaleString()}</span>
            </button>;
          })}
        </fieldset>
        <form className={styles.filters} onSubmit={(event) => { event.preventDefault(); submitQuery(); }}>
          <div className={styles.filterRow}>
            <Select className={styles.typeFilter} variant="outlined" aria-label="任务类型" placeholder="全部类型" allowClear value={draftQuery.type}
              options={Object.entries(types).map(([value, label]) => ({ value, label }))}
              onChange={(type) => updateDraft({ type })} />
            <Input className={styles.projectFilter} variant="outlined" aria-label="项目编号" placeholder="项目编号" prefix={<FolderOutlined />} type="number" min={1}
              value={draftQuery.projectId || ''} onChange={(e) => updateDraft({ projectId: e.target.value || undefined })} />
            <Button type="primary" htmlType="submit">查询</Button>
            <Button type="text" onClick={() => {
              const reset: Query = { scope: query.scope, page: 1, pageSize: 20 };
              setDraftQuery(reset); setQuery(reset); setSelected(null);
            }}>重置</Button>
            <Button className={styles.moreButton} type="text" aria-label="更多" aria-expanded={advanced} aria-controls="task-advanced-filters" onClick={() => setAdvanced((value) => !value)}>
              更多<DownOutlined rotate={advanced ? 180 : 0} />
            </Button>
          </div>
          {advanced && <div className={styles.advancedFilters} id="task-advanced-filters">
            <Select className={styles.stateFilter} variant="outlined" aria-label="任务状态" placeholder="全部状态" allowClear value={draftQuery.statusGroup}
              options={Object.entries(statuses).map(([value, label]) => ({ value, label }))}
              onChange={(statusGroup) => updateDraft({ statusGroup })} />
            {page?.canViewTeamTasks && query.scope === 'team' && <Input variant="outlined" aria-label="创建人编号" placeholder="创建人编号" type="number" min={1} className={styles.creatorFilter}
              value={draftQuery.creatorId || ''} onChange={(e) => updateDraft({ creatorId: e.target.value || undefined })} />}
            <Input variant="outlined" aria-label="开始时间" type="datetime-local" value={draftQuery.createdFrom || ''}
              onChange={(e) => updateDraft({ createdFrom: e.target.value || undefined })} />
            <span className={styles.rangeDivider}>至</span>
            <Input variant="outlined" aria-label="结束时间" type="datetime-local" value={draftQuery.createdTo || ''}
              onChange={(e) => updateDraft({ createdTo: e.target.value || undefined })} />
          </div>}
        </form>
        {error && <Alert className={styles.listError} type="error" title={error} action={<Button onClick={() => setRevision((n) => n + 1)}>重试</Button>} />}
        <ProTable<Task>
          className={styles.taskTable}
          rowKey="taskKey"
          columns={columns}
          dataSource={page?.items || []}
          loading={loading}
          search={false}
          options={false}
          cardProps={false}
          toolBarRender={false}
          size="middle"
          scroll={{ x: 680, y: advanced ? 'max(220px, calc(100dvh - 402px))' : 'max(220px, calc(100dvh - 330px))' }}
          pagination={{
            current: query.page, pageSize: query.pageSize, total: page?.total || 0, showSizeChanger: true,
            showTotal: (total) => `共 ${total.toLocaleString()} 项`,
            onChange: (pageNumber, pageSize) => setQuery((current) => ({ ...current, page: pageNumber, pageSize })),
          }}
        />
      </section>
      <Drawer
        title={detail?.title || '任务详情'}
        open={Boolean(selected)}
        size={660}
        classNames={{ body: styles.drawerBody, footer: styles.drawerFooter }}
        onClose={closeDetail}
        footer={detail && <div className={styles.taskActions}>
          {parentTask && <Button className={styles.parentAction} onClick={() => { setSelected(parentTask); setParentTask(null); }}>返回批次</Button>}
          <Button onClick={closeDetail}>关闭</Button>
          {detail.destination && <Button type="primary" onClick={() => { if (detail.destination) history.push(detail.destination); }}>返回业务</Button>}
          {detail.allowedActions.filter((action) => actions[action]).map((action) => <PointEstimateButton
            tenantId={tenant}
            operations={action === 'CANCEL' || ['VIRTUAL_AVATAR', 'SUBTITLE_ERASE'].includes(detail.type) ? [] : [{ url: `/api/tenants/${tenant}/production-tasks/${encodeURIComponent(detail.taskKey)}/${action.toLowerCase()}` }]}
            key={action} loading={busy} onClick={() => void run(detail, action)}
          >{actions[action]}</PointEstimateButton>)}
        </div>}
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
            <section className={styles.overview} aria-label="任务信息">
              <div className={styles.overviewHeading}><h3>{types[detail.type] || '任务信息'}</h3><TaskStatusView task={detail} /></div>
              <dl className={styles.overviewFields}>
                <div><dt>项目</dt><dd>{detail.projectName || '—'}</dd></div>
                <div><dt>创建人</dt><dd>{detail.creatorName || '—'}</dd></div>
                <div><dt>创建时间</dt><dd>{displayTime(detail.createdAt)}</dd></div>
                {detail.completedAt && <div><dt>完成时间</dt><dd>{displayTime(detail.completedAt)}</dd></div>}
                {active(detail) && detail.phase && <div><dt>当前阶段</dt><dd>{displayEnum(detail.phase)}</dd></div>}
              </dl>
            </section>
            {detail.resultSummary && <Typography.Paragraph style={{ marginTop: 12 }}>{detail.resultSummary}</Typography.Paragraph>}
            {detail.warningSummary && <Alert type="warning" title={detail.warningSummary} />}
            {detail.restricted && <p>当前无项目访问权限，仅展示任务状态和时间。</p>}
            {detail.type === 'FREE_CREATION' && <p>重试和重新生成会按原参数新建任务并确认积分。取消后停止跟踪结果，供应商已产生的用量仍会结算。</p>}
            {contentError && <Alert type="warning" title={contentError} />}
            {[...(content?.sections || [])].sort((a, b) => (a.key === 'results' ? 0 : a.key === 'settings' ? 2 : 1) - (b.key === 'results' ? 0 : b.key === 'settings' ? 2 : 1)).map((section) => <ContentSectionView key={section.key} section={section} tenant={tenant} taskKey={selected ?? detail.taskKey} />)}
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
    </div>
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
