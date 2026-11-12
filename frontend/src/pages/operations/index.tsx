import { useAccess } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Result,
  Select,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useEffect, useMemo, useState } from 'react';
import { ExecutionTimelineDetails } from './ExecutionTimelineDetails';
import { useStyles } from './index.style';
import { AlertActionDialog, RuleEditor, SettingsPanel } from './Management';
import {
  columns,
  DataTable,
  Histogram,
  Metrics,
  Trend,
  time,
  value,
} from './presentation';
import {
  type AiCall,
  type AlertAction,
  type AlertEvent,
  type Event,
  type EventFingerprintGroup,
  type EventPage,
  type Execution,
  type ExecutionTimeline,
  type Job,
  type Notification,
  operationsApi,
  type Page,
  type Query,
  type RequestMetric,
  type Resource,
  type Rule,
  type Settings,
  type Snapshot,
  validateRange,
} from './service';
import { useOperationsRefresh } from './useOperationsRefresh';

dayjs.extend(utc);
dayjs.extend(timezone);
type Tab =
  | 'overview'
  | 'requests'
  | 'ai'
  | 'jobs'
  | 'events'
  | 'alerts'
  | 'settings';
type Filters = Omit<Query, 'from' | 'to' | 'cursor' | 'pageSize'> & {
  range: string;
  dates?: [dayjs.Dayjs, dayjs.Dayjs];
};
type Data = {
  snapshot?: Snapshot;
  settings?: Settings;
  requests?: Page<RequestMetric>;
  ai?: Page<AiCall> & { summary: Snapshot['ai'] };
  executions?: Page<Execution>;
  jobs?: Page<Job>;
  events?: EventPage;
  alerts?: Page<AlertEvent>;
  rules?: Page<Rule>;
  notifications?: Page<Notification>;
};
type Source = (signal: AbortSignal, at: string) => Promise<unknown>;
const labels: Record<Tab, string> = {
  overview: '总览',
  requests: '接口性能',
  ai: 'AI运行',
  jobs: '后台任务',
  events: '错误事件',
  alerts: '告警中心',
  settings: '监控设置',
};
const percentage = (n: number | null | undefined) =>
  n == null ? undefined : n;
const snapshotPartLabels: Record<string, string> = {
  overview: 'HTTP指标',
  trend: 'HTTP趋势',
  resources: '资源',
  queue: '队列',
  ai: 'AI指标',
  activeAlerts: '告警',
  settings: '监控设置',
};
const redisLabels: Record<Resource['redisStatus'], string> = {
  UP: '可用',
  DOWN: '不可用',
  UNKNOWN: '未知',
  NOT_CONFIGURED: '未配置',
};

function OperationsWorkspace() {
  const { styles } = useStyles();
  const access = useAccess();
  const [tab, setTab] = useState<Tab>('overview');
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({ range: '1' });
  const range = Form.useWatch('range', form) ?? '1';
  const [rangeError, setRangeError] = useState('');
  const [cursors, setCursors] = useState<
    Record<string, (string | undefined)[]>
  >({});
  const [ruleEditor, setRuleEditor] = useState<{ rule?: Rule }>();
  const [action, setAction] = useState<{
    event: AlertEvent;
    action: 'acknowledge' | 'silence';
  }>();
  const [detail, setDetail] = useState<{
    kind: 'event' | 'actions' | 'execution';
    id: number;
    range?: Query;
  }>();
  const [detailData, setDetailData] = useState<
    Event | Page<AlertAction> | ExecutionTimeline
  >();
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [requestHistogram, setRequestHistogram] = useState<RequestMetric>();
  const [refreshSeconds, setRefreshSeconds] = useState(15);
  const scopeKey = JSON.stringify(filters);
  const live = filters.range !== 'custom';
  const sources = useMemo(() => {
    const query = (name: string, at: string): Query => {
      const { range, dates, ...dimensions } = filters;
      const to = range === 'custom' && dates ? dates[1].toISOString() : at;
      const from =
        range === 'custom' && dates
          ? dates[0].toISOString()
          : new Date(Date.parse(to) - Number(range) * 3600000).toISOString();
      const history = cursors[name] ?? [undefined];
      return {
        ...Object.fromEntries(
          Object.entries(dimensions).filter(
            ([, selected]) =>
              selected != null &&
              (typeof selected !== 'string' || selected.trim() !== ''),
          ),
        ),
        from,
        to,
        pageSize: 20,
        cursor: history[history.length - 1],
      };
    };
    const result: Record<string, Source> = {
      snapshot: (signal, at) =>
        operationsApi.snapshot(query('snapshot', at), signal),
      settings: (signal) => operationsApi.settings(signal),
    };
    const add = (
      name: keyof Data,
      loader: (params: Query, signal?: AbortSignal) => Promise<unknown>,
    ) => {
      result[name] = (signal, at) => loader(query(name, at), signal);
    };
    if (tab === 'requests') add('requests', operationsApi.requests);
    if (tab === 'ai' && access.canViewOperationsLogs) {
      add('ai', operationsApi.ai);
      add('executions', operationsApi.executions);
    }
    if (tab === 'jobs') add('jobs', operationsApi.jobs);
    if (tab === 'events' && access.canViewOperationsLogs)
      add('events', operationsApi.events);
    if (tab === 'alerts') {
      add('alerts', operationsApi.alerts);
      add('rules', operationsApi.rules);
      add('notifications', operationsApi.notifications);
    }
    return result;
  }, [filters, cursors, tab, access.canViewOperationsLogs]);
  const refresh = useOperationsRefresh({
    key: `${scopeKey}:${tab}:${JSON.stringify(cursors)}`,
    sources,
    live: live && tab !== 'settings',
    delay: refreshSeconds * 1000,
  });
  const data = refresh.data as Data;
  const snapshot = data.snapshot;
  useEffect(() => {
    if (data.settings?.refreshSeconds)
      setRefreshSeconds(data.settings.refreshSeconds);
  }, [data.settings?.refreshSeconds]);
  useEffect(() => {
    setDetailData(undefined);
    setDetailError('');
    if (
      !detail ||
      (detail.kind !== 'actions' && !access.canViewOperationsLogs)
    ) {
      setDetailLoading(false);
      return;
    }
    const controller = new AbortController();
    setDetailLoading(true);
    const load =
      detail.kind === 'event'
        ? operationsApi.event(detail.id, controller.signal)
        : detail.kind === 'execution' && detail.range
          ? operationsApi.execution(detail.id, detail.range, controller.signal)
          : operationsApi.actions(detail.id, controller.signal, detail.range);
    void load
      .then((result) => {
        if (!controller.signal.aborted) setDetailData(result);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setDetailError(
            error instanceof Error ? error.message : '详情加载失败',
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [detail, access.canViewOperationsLogs]);
  const apply = (next: Filters) => {
    if (next.range === 'custom') {
      if (!next.dates?.[0] || !next.dates[1]) {
        setRangeError('请选择完整的起止时间');
        return;
      }
      const error = validateRange(
        next.dates[0].toISOString(),
        next.dates[1].toISOString(),
      );
      if (error) {
        setRangeError(error);
        return;
      }
    }
    setRangeError('');
    setCursors({});
    setDetail(undefined);
    setRequestHistogram(undefined);
    setFilters(next);
  };
  const page = <T extends object>(
    name: keyof Data,
    title: string,
    rows: Page<T> | undefined,
    definitions: ColumnsType<T>,
    rowKey: (row: T) => string | number,
    extra?: React.ReactNode,
  ) => (
    <DataTable<T>
      title={title}
      data={rows}
      columns={definitions}
      rowKey={rowKey}
      loading={refresh.loading}
      extra={extra}
      hasPrevious={(cursors[name]?.length ?? 1) > 1}
      next={() => {
        if (rows?.nextCursor)
          setCursors((old) => ({
            ...old,
            [name]: [
              ...(old[name] ?? [undefined]),
              rows.nextCursor ?? undefined,
            ],
          }));
      }}
      previous={() =>
        setCursors((old) => ({
          ...old,
          [name]: (old[name] ?? [undefined]).slice(0, -1),
        }))
      }
    />
  );
  const withExecutionLinks = <T extends { executionId: number | null }>(
    definitions: ColumnsType<T>,
  ): ColumnsType<T> =>
    definitions.map((column) =>
      column.key !== 'executionId'
        ? column
        : {
            ...column,
            render: (id: number | null) =>
              id == null || !access.canViewOperationsLogs ? (
                value(id)
              ) : (
                <Button
                  type="link"
                  aria-label={`查看执行${id}时间线`}
                  onClick={() => {
                    const {
                      range,
                      dates,
                      instanceId: _instanceId,
                      ...dimensions
                    } = filters;
                    const to =
                      snapshot?.to ??
                      (range === 'custom' && dates
                        ? dates[1].toISOString()
                        : new Date().toISOString());
                    const from =
                      snapshot?.from ??
                      (range === 'custom' && dates
                        ? dates[0].toISOString()
                        : new Date(
                            Date.parse(to) - Number(range) * 3600000,
                          ).toISOString());
                    setDetail({
                      kind: 'execution',
                      id,
                      range: {
                        ...Object.fromEntries(
                          Object.entries(dimensions).filter(
                            ([, selected]) =>
                              selected != null &&
                              (typeof selected !== 'string' ||
                                selected.trim() !== ''),
                          ),
                        ),
                        from,
                        to,
                        pageSize: 100,
                      },
                    });
                  }}
                >
                  {id}
                </Button>
              ),
          },
    );
  const requestColumns = columns<RequestMetric>([
    ['method', '方法'],
    ['route', '规范化路由'],
    ['requests', '请求样本数'],
    ['errors', '技术错误'],
    ['businessRejections', '业务拒绝'],
    ['p50Ms', '近似P50 ms'],
    ['p95Ms', '近似P95 ms'],
    ['p99Ms', '近似P99 ms'],
    ['maxMs', '最大延迟 ms'],
    ['sqlCount', '同步SQL次数'],
    ['sqlMs', '累计SQL ms'],
  ]);
  requestColumns.push({
    key: 'histogram',
    title: '分布',
    render: (_, row) => (
      <Button type="link" onClick={() => setRequestHistogram(row)}>
        延迟分布
      </Button>
    ),
  });
  const eventColumns = columns<Event>([
    ['occurredAt', '发生时间'],
    ['source', '来源'],
    ['code', '错误码'],
    ['fingerprint', '错误指纹'],
    ['message', '脱敏摘要'],
    ['tenantId', '团队'],
    ['projectId', '项目'],
    ['executionId', '执行ID'],
    ['requestId', '请求ID'],
    ['traceId', 'Trace ID'],
    ['trustedContext', '可信归属'],
  ]);
  if (access.canViewOperationsLogs)
    eventColumns.push({
      key: 'detail',
      title: '详情',
      render: (_, row) => (
        <Button
          type="link"
          onClick={() => setDetail({ kind: 'event', id: row.id })}
        >
          查看详情
        </Button>
      ),
    });
  const alertColumns = columns<AlertEvent>([
    ['eventKey', '稳定编号'],
    ['ruleName', '规则'],
    ['severity', '级别'],
    ['status', '故障状态'],
    ['evidenceStatus', '最新证据状态'],
    ['lastWindowAt', '最新评估窗口'],
    ['metricValue', '触发值'],
    ['thresholdValue', '触发阈值'],
    ['sampleCount', '窗口样本数'],
    ['dimensionsJson', '影响维度'],
    ['firedAt', '触发时间'],
    ['resolvedAt', '恢复时间'],
    ['acknowledgedAt', '人工确认'],
    ['silenceUntil', '静默截止'],
    ['silenceReason', '静默原因'],
  ]);
  alertColumns.push({
    key: 'actions',
    title: '操作',
    width: 260,
    render: (_, row) => (
      <Space>
        <Button
          type="link"
          onClick={() => {
            const to =
              snapshot?.to ??
              (filters.range === 'custom' && filters.dates
                ? filters.dates[1].toISOString()
                : new Date().toISOString());
            const from =
              snapshot?.from ??
              (filters.range === 'custom' && filters.dates
                ? filters.dates[0].toISOString()
                : new Date(
                    Date.parse(to) - Number(filters.range) * 3600000,
                  ).toISOString());
            setDetail({ kind: 'actions', id: row.id, range: { from, to } });
          }}
        >
          操作记录
        </Button>
        {access.canManageOperationsAlerts && (
          <>
            <Button
              type="link"
              onClick={() => setAction({ event: row, action: 'acknowledge' })}
            >
              确认
            </Button>
            <Button
              type="link"
              onClick={() => setAction({ event: row, action: 'silence' })}
            >
              静默
            </Button>
          </>
        )}
      </Space>
    ),
  });
  const ruleColumns = columns<Rule>([
    ['name', '名称'],
    ['enabled', '启用'],
    ['severity', '级别'],
    ['metric', '指标'],
    ['operator', '条件'],
    ['thresholdValue', '阈值'],
    ['recoveryThreshold', '恢复阈值'],
    ['windowMinutes', '窗口 分钟'],
    ['sustainedMinutes', '持续 分钟'],
    ['minimumSamples', '最小样本'],
    ['cooldownMinutes', '冷却 分钟'],
    ['dimensionsJson', '匹配维度'],
    ['notify', '通知'],
    ['version', '版本'],
  ]);
  if (access.canEditOperationsRules)
    ruleColumns.push({
      key: 'edit',
      title: '操作',
      render: (_, row) => (
        <Button type="link" onClick={() => setRuleEditor({ rule: row })}>
          编辑规则
        </Button>
      ),
    });

  const overview = (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Metrics
        items={[
          ['HTTP请求样本', snapshot?.overview.requestCount, '次'],
          ['技术失败率', percentage(snapshot?.overview.errorRate), '%'],
          ['业务拒绝', snapshot?.overview.businessRejections, '次'],
          ['桶平均QPS（近似）', snapshot?.overview.qps, '次/秒'],
          ['观测覆盖桶时长', snapshot?.overview.coveredSeconds, '秒'],
          ['近似P50', snapshot?.overview.p50Ms, 'ms'],
          ['近似P95', snapshot?.overview.p95Ms, 'ms'],
          ['近似P99', snapshot?.overview.p99Ms, 'ms'],
          ['活动告警', snapshot?.activeAlerts, '条'],
        ]}
      />
      <Trend trend={snapshot?.trend ?? []} />
      <Histogram bins={snapshot?.overview.histogram} />
      <Card title="JVM与依赖资源（当前快照，不随团队/模型筛选）" size="small">
        <DataTable<Resource>
          title="各实例最新采样"
          data={{
            items: snapshot?.resources ?? [],
            total: snapshot?.resources.length ?? 0,
          }}
          columns={[
            ...columns<Resource>([
              ['instanceId', '实例'],
              ['at', '采样时间'],
              ['cpuPercent', '进程CPU %'],
              ['heapUsedBytes', 'JVM堆使用 bytes'],
              ['heapMaxBytes', 'JVM堆上限 bytes'],
              ['threads', 'JVM线程'],
              ['dbActive', '数据库活跃'],
              ['dbIdle', '数据库空闲'],
              ['dbPending', '数据库等待'],
              ['dbMax', '业务连接池上限'],
            ]),
            {
              key: 'redis',
              title: 'Redis',
              render: (_, row) => redisLabels[row.redisStatus] ?? '未知',
            },
          ]}
          rowKey={(row) => row.instanceId}
        />
      </Card>
    </Space>
  );
  const queue = (
    <Metrics
      items={[
        ['待处理执行版本', snapshot?.queue.pending, '个'],
        ['可运行等待', snapshot?.queue.runnable, '个'],
        ['未来退避/重试', snapshot?.queue.retrying, '个'],
        ['运行中', snapshot?.queue.running, '个'],
        ['最老可运行等待', snapshot?.queue.oldestRunnableSeconds, '秒'],
        ['过期租约', snapshot?.queue.expiredLeases, '个'],
        ['终态结算滞留', snapshot?.queue.settlementPending, '个'],
      ]}
    />
  );
  const ai = data.ai?.summary ?? snapshot?.ai;
  const panels: Record<Tab, React.ReactNode> = {
    overview,
    requests: (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="按规范化路由汇总；分位数为合并直方图近似值"
          description="HTTP指标只应用实例维度；团队、项目、服务商、模型和场景筛选仅作用于AI及执行数据。SQL统计仅包含已采集的同步调用，累计时间不能直接证明缺少索引。"
        />
        <Trend trend={snapshot?.trend ?? []} />
        <Histogram bins={snapshot?.overview.histogram} />
        {page(
          'requests',
          '接口性能排行',
          data.requests,
          requestColumns,
          (row) => `${row.method}:${row.route}`,
        )}
        {requestHistogram && (
          <>
            <Typography.Text>
              {requestHistogram.method} {requestHistogram.route}
            </Typography.Text>
            <Histogram bins={requestHistogram.histogram} />
          </>
        )}
      </Space>
    ),
    ai: (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="AI调用、执行版本与尝试分别计数"
          description="传输成功后解析失败计为同一次调用的业务失败；取消和业务拒绝不等于技术错误。tokens仅统计供应商返回的用量，覆盖率不足时不能推断总成本。"
        />
        <Metrics
          items={[
            ['AI调用', ai?.calls, '次'],
            ['技术失败', ai?.technicalFailures, '次'],
            ['业务失败', ai?.businessFailures, '次'],
            ['传输成功率', percentage(ai?.successRate), '%'],
            ['限流', ai?.rateLimited, '次'],
            ['超时', ai?.timeouts, '次'],
            ['输入tokens', ai?.promptTokens],
            ['输出tokens', ai?.completionTokens],
            ['token覆盖率', percentage(ai?.tokenCoverage), '%'],
          ]}
        />
        {access.canViewOperationsLogs &&
          page(
            'ai',
            'AI调用轻字段投影',
            data.ai,
            withExecutionLinks(
              columns<AiCall>([
                ['occurredAt', '发生时间'],
                ['callLogId', '调用ID'],
                ['executionId', '执行ID'],
                ['attemptId', '尝试ID'],
                ['tenantId', '团队'],
                ['projectId', '项目'],
                ['providerId', '服务商'],
                ['modelId', '模型'],
                ['scene', '场景'],
                ['serviceType', '服务类型'],
                ['transportOutcome', '传输结果'],
                ['businessOutcome', '业务结果'],
                ['status', '状态'],
                ['errorCode', '错误码'],
                ['durationMs', '耗时 ms'],
                ['traceId', 'Trace ID'],
              ]),
            ),
            (row) => row.callLogId,
          )}
        {queue}
        {!access.canViewOperationsLogs && (
          <Alert type="info" title="AI调用与执行明细需要运维日志查看权限" />
        )}
        {access.canViewOperationsLogs &&
          page(
            'executions',
            '执行版本观察',
            data.executions,
            withExecutionLinks(
              columns<Execution>([
                ['executionId', '执行ID'],
                ['executionVersion', '版本'],
                ['tenantId', '团队'],
                ['projectId', '项目'],
                ['modelId', '模型'],
                ['scene', '场景'],
                ['taskType', '任务类型'],
                ['status', '状态'],
                ['phase', '阶段'],
                ['createdAt', '创建时间'],
                ['startedAt', '开始时间'],
                ['completedAt', '完成时间'],
                ['observedAt', '观察时间'],
                ['nextRunAt', '下次运行'],
                ['heartbeatAt', '心跳'],
                ['claimExpiresAt', '租约截止'],
                ['pointSettlementStatus', '结算状态'],
                ['errorCode', '错误码'],
                ['traceId', 'Trace ID'],
              ]),
            ),
            (row) => `${row.executionId}:${row.executionVersion}`,
          )}
      </Space>
    ),
    jobs: (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          title="队列为当前只读状态；作业心跳按实例与启动批次区分"
          description="队列支持团队/项目/模型/场景筛选；作业仅支持实例维度。未来退避不计为可运行等待，fixed-delay计划以完成时间为基准。"
        />
        {queue}
        {page(
          'jobs',
          '调度计划与心跳',
          data.jobs,
          columns<Job>([
            ['jobCode', '作业'],
            ['instanceId', '实例'],
            ['bootId', '启动批次'],
            ['enabled', '启用'],
            ['scheduleType', '计划类型'],
            ['intervalMs', '间隔 ms'],
            ['scheduleExpression', 'Cron'],
            ['expectedAt', '预期执行'],
            ['startedAt', '最近开始'],
            ['finishedAt', '最近完成'],
            ['succeededAt', '最近成功'],
            ['errorAt', '最近失败'],
            ['durationMs', '耗时 ms'],
            ['consecutiveFailures', '连续失败'],
            ...(access.canViewOperationsLogs
              ? ([['errorMessage', '脱敏错误']] as [keyof Job, string][])
              : []),
            ['updatedAt', '更新时间'],
          ]),
          (row) => row.id,
        )}
      </Space>
    ),
    events: access.canViewOperationsLogs ? (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <DataTable<EventFingerprintGroup>
          title="错误指纹聚合（前20组，按当前范围）"
          data={{
            items: data.events?.groups?.slice(0, 20) ?? [],
            total: data.events?.groups?.length ?? 0,
          }}
          columns={columns<EventFingerprintGroup>([
            ['fingerprint', '错误指纹'],
            ['source', '来源'],
            ['code', '错误码'],
            ['location', '脱敏定位'],
            ['count', '出现次数'],
            ['lastAt', '最近发生'],
          ])}
          rowKey={(row) =>
            `${row.fingerprint}:${row.source}:${row.code}:${row.location}`
          }
          loading={refresh.loading}
        />
        {page(
          'events',
          '脱敏错误事件与指纹',
          data.events,
          eventColumns,
          (row) => row.id,
        )}
      </Space>
    ) : (
      <Result
        status="403"
        title="需要运维日志查看权限"
        subTitle="指标查看权限不会授予错误日志访问权限"
      />
    ),
    alerts: (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          title="确认与静默仅记录人工处置，恢复由有效健康窗口判定"
          description="低样本、缺窗口与监控中断会显示未知，不能当作故障恢复。通知记录是本地投递证据，不保证用户已阅读。"
        />
        {page('alerts', '告警事件', data.alerts, alertColumns, (row) => row.id)}
        {page(
          'rules',
          '告警规则（关闭以停用）',
          data.rules,
          ruleColumns,
          (row) => row.id,
          access.canEditOperationsRules ? (
            <Button type="primary" onClick={() => setRuleEditor({})}>
              新增规则
            </Button>
          ) : undefined,
        )}
        {page(
          'notifications',
          '通知投递记录',
          data.notifications,
          columns<Notification>([
            ['notificationKey', '稳定通知编号'],
            ['eventId', '告警ID'],
            ['transitionType', '通知类型'],
            ['status', '投递状态'],
            ['attempts', '尝试次数'],
            ['nextAttemptAt', '下次尝试'],
            ['deliveredAt', '交付时间'],
            ['lastError', '失败原因'],
            ['createdAt', '创建时间'],
          ]),
          (row) => row.id,
        )}
      </Space>
    ),
    settings: (
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <SettingsPanel
          settings={data.settings}
          editable={!!access.canEditOperationsSettings}
          saved={refresh.refresh}
        />
        <Card title="监控自身开销与新鲜度" size="small">
          <Metrics
            items={[
              ['采集队列深度', snapshot?.pipeline.queueDepth],
              ['累计丢弃', snapshot?.pipeline.dropped],
              ['维度溢出', snapshot?.pipeline.dimensionOverflow],
            ]}
          />
          <Descriptions
            className="mt-3"
            items={[
              {
                key: 'flush',
                label: '最近落库',
                children: time(snapshot?.pipeline.lastFlushAt),
              },
              {
                key: 'aggregate',
                label: '最近聚合',
                children: time(snapshot?.pipeline.lastAggregateAt),
              },
            ]}
          />
        </Card>
      </Space>
    ),
  };
  return (
    <div className={styles.page}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <div className={styles.heading}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            平台运维中心
          </Typography.Title>
          <Space>
            <Tag>
              {tab === 'settings'
                ? '设置页 · 手动刷新'
                : live
                  ? `完成后${refreshSeconds}秒刷新`
                  : '固定历史范围 · 手动刷新'}
            </Tag>
            <Button onClick={refresh.refresh} loading={refresh.loading}>
              刷新当前页
            </Button>
          </Space>
        </div>
        <Card size="small" className={styles.filters}>
          <Form
            form={form}
            layout="inline"
            initialValues={{
              range: '1',
              dates: [
                dayjs().tz('Asia/Shanghai').subtract(1, 'hour'),
                dayjs().tz('Asia/Shanghai'),
              ],
            }}
            onFinish={apply}
            className="gap-y-3"
          >
            <Form.Item name="range" label="时间">
              <Select
                style={{ width: 150 }}
                options={[
                  { value: '1', label: '最近1小时' },
                  { value: '24', label: '最近24小时' },
                  { value: '168', label: '最近7天' },
                  { value: '720', label: '最近30天' },
                  { value: '2160', label: '最近90天' },
                  { value: 'custom', label: '自定义历史' },
                ]}
              />
            </Form.Item>
            {range === 'custom' && (
              <Form.Item name="dates" label="北京时间">
                <DatePicker.RangePicker showTime allowClear={false} />
              </Form.Item>
            )}
            {(['tenantId', 'projectId', 'providerId', 'modelId'] as const).map(
              (name, index) => (
                <Form.Item
                  key={name}
                  name={name}
                  label={['团队ID', '项目ID', '服务商ID', '模型ID'][index]}
                >
                  <InputNumber min={1} precision={0} style={{ width: 100 }} />
                </Form.Item>
              ),
            )}
            <Form.Item name="scene" label="场景">
              <Input allowClear maxLength={120} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="instanceId" label="实例">
              <Input allowClear maxLength={120} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">
                应用筛选
              </Button>
            </Form.Item>
          </Form>
          <Typography.Paragraph
            type="secondary"
            className="mt-3"
            style={{ marginBottom: 0 }}
          >
            时间展示为Asia/Shanghai；HTTP和作业按实例，AI按团队/项目/服务商/模型/场景，资源和队列为当前快照。明细每页20条，范围最多90天。
          </Typography.Paragraph>
          {rangeError && <Alert type="error" title={rangeError} />}
        </Card>
        {snapshot && (
          <>
            <Space wrap className={styles.freshness}>
              <Tag
                color={
                  snapshot.health.status === 'NORMAL'
                    ? 'success'
                    : snapshot.health.status === 'ERROR'
                      ? 'error'
                      : 'warning'
                }
              >
                {snapshot.health.status}
              </Tag>
              <Typography.Text>
                快照 {time(snapshot.generatedAt)} · {time(snapshot.from)} —{' '}
                {time(snapshot.to)}
              </Typography.Text>
              <Typography.Text>
                粒度 {snapshot.coverage.granularity} · 覆盖{' '}
                {snapshot.coverage.complete ? '完整' : '不完整'} · 首末样本{' '}
                {time(snapshot.coverage.firstAt)} /{' '}
                {time(snapshot.coverage.lastAt)}
              </Typography.Text>
            </Space>
            {snapshot.health.reasons.length > 0 && (
              <Alert
                type="warning"
                title={snapshot.health.reasons.join('；')}
              />
            )}
            {Object.entries(snapshot.sourceStatus ?? {})
              .filter(([, source]) => source.status === 'ERROR')
              .map(([part, source]) => (
                <Alert
                  key={part}
                  type="warning"
                  title={`${snapshotPartLabels[part] ?? part}数据暂不可用，${source.observedAt ? '保留上次成功结果' : '尚无成功结果'}`}
                  description={`最近成功：${time(source.observedAt)}（Asia/Shanghai）`}
                />
              ))}
            {snapshot.coverage.reasons.length > 0 && (
              <Alert
                type="warning"
                title={snapshot.coverage.reasons.join('；')}
              />
            )}
            {!snapshot.externalProbeConfigured && (
              <Alert
                showIcon
                type="warning"
                title="整机失联告警未配置"
                description="应用内监控无法覆盖整机或应用完全失联；需在独立主机部署外部探测。"
              />
            )}
          </>
        )}
        {Object.entries(refresh.errors).map(([name, error]) => (
          <Alert
            key={name}
            type="error"
            showIcon
            title={`${name} 数据源失败：${error}`}
            description={`保留本范围最近成功结果，可能已过期；最近成功：${time(refresh.updatedAt[name])}`}
          />
        ))}
        <Tabs
          className={styles.tabs}
          activeKey={tab}
          destroyOnHidden
          onChange={(key) => {
            setTab(key as Tab);
            setDetail(undefined);
          }}
          items={(Object.keys(labels) as Tab[]).map((key) => ({
            key,
            label: labels[key],
            children: panels[key],
          }))}
        />
        {access.canEditOperationsRules && (
          <RuleEditor
            open={!!ruleEditor}
            rule={ruleEditor?.rule}
            close={() => setRuleEditor(undefined)}
            saved={refresh.refresh}
          />
        )}
        {access.canManageOperationsAlerts && (
          <AlertActionDialog
            target={action}
            close={() => setAction(undefined)}
            saved={refresh.refresh}
          />
        )}
        <Drawer
          title={
            detail?.kind === 'event'
              ? '脱敏事件详情'
              : detail?.kind === 'execution'
                ? '执行任务只读时间线'
                : '告警操作审计'
          }
          open={!!detail}
          onClose={() => setDetail(undefined)}
          size="large"
          loading={detailLoading}
          destroyOnHidden
        >
          {detailError && (
            <Alert
              type="error"
              title={detailError}
              description="明细可能已超过保留期；历史聚合仍可查询。"
            />
          )}
          {detailData &&
            ('versions' in detailData ? (
              <ExecutionTimelineDetails data={detailData} />
            ) : 'items' in detailData ? (
              <DataTable<AlertAction>
                title="确认/静默/恢复记录"
                data={detailData}
                columns={columns<AlertAction>([
                  ['action', '动作'],
                  ['actorId', '操作人ID'],
                  ['reason', '原因'],
                  ['untilAt', '截止时间'],
                  ['createdAt', '操作时间'],
                ])}
                rowKey={(row) => row.id}
              />
            ) : (
              <>
                <Descriptions
                  column={1}
                  bordered
                  items={Object.entries(detailData)
                    .filter(([key]) => key !== 'stack')
                    .map(([key, item]) => ({
                      key,
                      label: key,
                      children: key.endsWith('At')
                        ? time(typeof item === 'string' ? item : null)
                        : value(item),
                    }))}
                />
                <Typography.Title level={5}>脱敏堆栈</Typography.Title>
                <pre className="whitespace-pre-wrap break-all">
                  {detailData.stack || '无堆栈数据'}
                </pre>
              </>
            ))}
        </Drawer>
      </Space>
    </div>
  );
}

export default function OperationsPage() {
  const access = useAccess();
  return access.canViewOperations ? (
    <OperationsWorkspace />
  ) : (
    <Result status="403" title="无平台运维访问权限" />
  );
}
