import { request } from '@umijs/max';

export type Query = {
  from: string;
  to: string;
  tenantId?: number;
  projectId?: number;
  providerId?: number;
  modelId?: number;
  scene?: string;
  instanceId?: string;
  cursor?: string;
  pageSize?: number;
};
export type Page<T> = { items: T[]; total: number; nextCursor?: string | null };
export type Observation = number | null;
export type HistogramBin = { upperBoundMs: number | null; count: number };
export type Overview = {
  requestCount: Observation;
  technicalErrors: Observation;
  businessRejections: Observation;
  errorRate: Observation;
  p50Ms: Observation;
  p95Ms: Observation;
  p99Ms: Observation;
  qps: Observation;
  coveredSeconds?: Observation;
  histogram?: HistogramBin[];
};
export type AiSummary = {
  calls: Observation;
  technicalFailures: Observation;
  businessFailures: Observation;
  rateLimited: Observation;
  timeouts: Observation;
  successRate: Observation;
  promptTokens: Observation;
  completionTokens: Observation;
  tokenCoverage: Observation;
};
export type Resource = {
  instanceId: string;
  at: string;
  cpuPercent: Observation;
  heapUsedBytes: Observation;
  heapMaxBytes: Observation;
  threads: Observation;
  dbActive: Observation;
  dbIdle: Observation;
  dbPending: Observation;
  dbMax: Observation;
  redisStatus: 'NOT_CONFIGURED' | 'UNKNOWN' | 'UP' | 'DOWN';
};
export type Snapshot = {
  from: string;
  to: string;
  generatedAt: string;
  sourceStatus?: Record<
    string,
    { status: 'OK' | 'ERROR'; observedAt: string | null }
  >;
  coverage: {
    granularity: string;
    firstAt: string | null;
    lastAt: string | null;
    complete: boolean;
    reasons: string[];
  };
  overview: Overview;
  trend: {
    at: string;
    requests: Observation;
    errors: Observation;
    p95Ms: Observation;
  }[];
  resources: Resource[];
  queue: {
    pending: Observation;
    runnable: Observation;
    retrying: Observation;
    running: Observation;
    oldestRunnableSeconds: Observation;
    expiredLeases: Observation;
    settlementPending: Observation;
  };
  ai: AiSummary;
  pipeline: {
    queueDepth: Observation;
    dropped: Observation;
    dimensionOverflow: Observation;
    lastFlushAt: string | null;
    lastAggregateAt: string | null;
  };
  activeAlerts: number;
  externalProbeConfigured: boolean;
  health: {
    status: 'NORMAL' | 'WARNING' | 'ERROR' | 'NO_DATA';
    reasons: string[];
  };
};
export type RequestMetric = {
  route: string;
  method: string;
  requests: Observation;
  errors: Observation;
  businessRejections: Observation;
  p50Ms: Observation;
  p95Ms: Observation;
  p99Ms: Observation;
  maxMs: Observation;
  sqlCount: Observation;
  sqlMs: Observation;
  histogram?: HistogramBin[];
};
export type AiCall = {
  callLogId: number;
  occurredAt: string;
  tenantId: number | null;
  projectId: number | null;
  providerId: number | null;
  modelId: number | null;
  scene: string | null;
  serviceType: string;
  traceId: string | null;
  executionId: number | null;
  attemptId: number | null;
  transportOutcome: string;
  businessOutcome: string;
  status: string;
  errorCode: string | null;
  durationMs: Observation;
  promptTokens: Observation;
  completionTokens: Observation;
};
export type Execution = {
  id: number;
  executionId: number;
  executionVersion: number;
  tenantId: number | null;
  projectId: number | null;
  modelId: number | null;
  scene: string | null;
  taskType: string;
  status: string;
  phase: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  observedAt: string;
  nextRunAt: string | null;
  heartbeatAt: string | null;
  claimExpiresAt: string | null;
  pointSettlementStatus: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  traceId: string | null;
};
export type Job = {
  id: number;
  instanceId: string;
  bootId: string;
  jobCode: string;
  enabled: boolean;
  scheduleType: string;
  intervalMs: Observation;
  scheduleExpression: string | null;
  expectedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  succeededAt: string | null;
  errorAt: string | null;
  durationMs: Observation;
  consecutiveFailures: number;
  errorMessage: string | null;
  updatedAt: string;
};
export type Attempt = {
  id: number;
  executionId: number;
  executionVersion: number;
  phase: string;
  attemptNo: number;
  status: string;
  providerId: number | null;
  modelId: number | null;
  transportOutcome: string | null;
  businessOutcome: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};
export type ExecutionTimeline = {
  executionId: number;
  from: string;
  to: string;
  coverage: 'PARTIAL';
  versionScope?: 'LATEST_RETAINED_CONTEXT';
  versions: Execution[];
  attempts: Attempt[];
  calls: AiCall[];
  truncated: boolean;
};
export type Event = {
  id: number;
  eventKey: string;
  occurredAt: string;
  source: string;
  code: string;
  location: string;
  message: string;
  stack?: string | null;
  fingerprint: string;
  userId: number | null;
  tenantId: number | null;
  projectId: number | null;
  executionId: number | null;
  requestId: string | null;
  traceId: string | null;
  trustedContext: boolean;
  durationMs: Observation;
  sqlCount: Observation;
  sqlMs: Observation;
};
export type EventFingerprintGroup = {
  fingerprint: string;
  source: string;
  code: string;
  location: string;
  count: number;
  lastAt: string;
};
export type EventPage = Page<Event> & { groups?: EventFingerprintGroup[] };
export type AlertEvent = {
  id: number;
  eventKey: string;
  ruleId: number;
  ruleName: string;
  severity: string;
  status: string;
  dimensionsJson: string;
  metricValue: Observation;
  thresholdValue: number;
  sampleCount: number;
  firedAt: string;
  resolvedAt: string | null;
  acknowledgedAt: string | null;
  acknowledgedBy: number | null;
  silenceUntil: string | null;
  silenceReason: string | null;
  updatedAt: string;
  evidenceStatus?: string;
  lastWindowAt?: string | null;
};
export type AlertAction = {
  id: number;
  eventId: number;
  action: string;
  actorId: number;
  reason: string | null;
  untilAt: string | null;
  createdAt: string;
};
export type Notification = {
  id: number;
  notificationKey: string;
  eventId: number | null;
  transitionType: string;
  message: string;
  status: string;
  attempts: number;
  nextAttemptAt: string | null;
  deliveredAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
};
export type Rule = {
  id: number;
  name: string;
  description: string;
  enabled: boolean;
  severity: string;
  metric: string;
  operator: string;
  thresholdValue: number;
  recoveryThreshold: number | null;
  windowMinutes: number;
  sustainedMinutes: number;
  minimumSamples: number;
  cooldownMinutes: number;
  dimensionsJson: string;
  notify: boolean;
  version: number;
};
export type RuleInput = Omit<Rule, 'id'>;
export type Settings = {
  version: number;
  telemetryEnabled: boolean;
  notificationsEnabled: boolean;
  persistentDigestEnabled: boolean;
  observationUntil: string;
  externalProbeConfigured: boolean;
  refreshSeconds: number;
  minuteRetentionDays: number;
  hourlyRetentionDays: number;
  eventRetentionDays: number;
  projectionRetentionDays: number;
  alertRetentionDays: number;
  notificationRetentionDays: number;
  channelConfigured: boolean;
  signatureConfigured: boolean;
  instanceId: string;
};
export type SettingsInput = Omit<
  Settings,
  'channelConfigured' | 'signatureConfigured' | 'instanceId'
>;

const base = '/api/platform/operations';
async function call<T>(
  path: string,
  options: {
    method?: string;
    params?: Query;
    signal?: AbortSignal;
    data?: unknown;
  } = {},
): Promise<T> {
  const response = await request<{
    success: boolean;
    data: T;
    errorMessage?: string;
    errorCode?: string;
  }>(`${base}${path}`, { ...options, skipErrorHandler: true });
  if (!response.success)
    throw new Error(
      response.errorMessage || response.errorCode || '运维数据请求失败',
    );
  return response.data;
}
export const operationsApi = {
  snapshot: (params: Query, signal?: AbortSignal) =>
    call<Snapshot>('/snapshot', { params, signal }),
  requests: (params: Query, signal?: AbortSignal) =>
    call<Page<RequestMetric>>('/requests', { params, signal }),
  ai: (params: Query, signal?: AbortSignal) =>
    call<Page<AiCall> & { summary: AiSummary }>('/ai', { params, signal }),
  executions: (params: Query, signal?: AbortSignal) =>
    call<Page<Execution>>('/executions', { params, signal }),
  execution: (id: number, params: Query, signal?: AbortSignal) =>
    call<ExecutionTimeline>(`/executions/${id}`, { params, signal }),
  jobs: (params: Query, signal?: AbortSignal) =>
    call<Page<Job>>('/jobs', { params, signal }),
  events: (params: Query, signal?: AbortSignal) =>
    call<EventPage>('/events', { params, signal }),
  event: (id: number, signal?: AbortSignal) =>
    call<Event>(`/events/${id}`, { signal }),
  alerts: (params: Query, signal?: AbortSignal) =>
    call<Page<AlertEvent>>('/alerts', { params, signal }),
  actions: (id: number, signal?: AbortSignal, params?: Query) =>
    call<Page<AlertAction>>(`/alerts/${id}/actions`, { signal, params }),
  acknowledge: (id: number, reason: string) =>
    call<AlertEvent>(`/alerts/${id}/acknowledge`, {
      method: 'POST',
      data: { reason },
    }),
  silence: (id: number, until: string, reason: string) =>
    call<AlertEvent>(`/alerts/${id}/silence`, {
      method: 'POST',
      data: { until, reason },
    }),
  rules: (params: Query, signal?: AbortSignal) =>
    call<Page<Rule>>('/rules', { params, signal }),
  saveRule: (data: RuleInput, id?: number) =>
    call<Rule>(id === undefined ? '/rules' : `/rules/${id}`, {
      method: id === undefined ? 'POST' : 'PUT',
      data,
    }),
  notifications: (params: Query, signal?: AbortSignal) =>
    call<Page<Notification>>('/notifications', { params, signal }),
  settings: (signal?: AbortSignal) => call<Settings>('/settings', { signal }),
  saveSettings: (
    data: SettingsInput | (Partial<SettingsInput> & { version: number }),
  ) => call<Settings>('/settings', { method: 'PUT', data }),
};

export function validateRange(from: string, to: string): string | undefined {
  const duration = Date.parse(to) - Date.parse(from);
  if (!Number.isFinite(duration) || duration <= 0)
    return '开始时间必须早于结束时间';
  if (duration > 90 * 24 * 60 * 60 * 1000) return '查询范围不得超过90天';
  return undefined;
}
