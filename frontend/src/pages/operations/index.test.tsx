import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OperationsPage from './index';

const mocks = vi.hoisted(() => ({
  access: {
    canViewOperations: true,
    canViewOperationsLogs: false,
    canEditOperationsRules: false,
    canManageOperationsAlerts: false,
    canEditOperationsSettings: false,
  },
  snapshot: vi.fn(),
  settings: vi.fn(),
  events: vi.fn(),
  alerts: vi.fn(),
  rules: vi.fn(),
  notifications: vi.fn(),
  event: vi.fn(),
  requests: vi.fn(),
  ai: vi.fn(),
  executions: vi.fn(),
  execution: vi.fn(),
  jobs: vi.fn(),
  actions: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useAccess: () => mocks.access,
  request: vi.fn(),
}));
vi.mock('./service', async (original) => ({
  ...(await original<typeof import('./service')>()),
  operationsApi: mocks,
}));

describe('operations workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.assign(mocks.access, {
      canViewOperations: true,
      canViewOperationsLogs: false,
      canEditOperationsRules: false,
      canManageOperationsAlerts: false,
      canEditOperationsSettings: false,
    });
    mocks.snapshot.mockResolvedValue({
      from: '2026-11-05T00:00:00Z',
      to: '2026-11-05T01:00:00Z',
      generatedAt: '2026-11-05T01:00:00Z',
      coverage: {
        granularity: 'MINUTE',
        complete: false,
        reasons: ['采集空窗'],
        firstAt: null,
        lastAt: null,
      },
      overview: { requestCount: 0, p95Ms: null },
      trend: [],
      resources: [],
      queue: {},
      ai: {},
      pipeline: {},
      activeAlerts: 0,
      externalProbeConfigured: false,
      health: { status: 'NO_DATA', reasons: ['没有有效样本'] },
    });
    mocks.settings.mockResolvedValue({
      version: 1,
      telemetryEnabled: true,
      notificationsEnabled: false,
      channelConfigured: false,
      signatureConfigured: false,
      refreshSeconds: 15,
    });
    for (const api of [
      mocks.events,
      mocks.alerts,
      mocks.rules,
      mocks.notifications,
      mocks.requests,
      mocks.ai,
      mocks.executions,
      mocks.jobs,
      mocks.actions,
    ])
      api.mockResolvedValue({ items: [], total: 0 });
  });
  it('shows seven native tabs, explicit missing samples, coverage and external-probe gap', async () => {
    render(<OperationsPage />);
    await waitFor(() =>
      expect(screen.getByText('整机失联告警未配置')).toBeInTheDocument(),
    );
    expect(screen.getAllByRole('tab')).toHaveLength(7);
    expect(screen.getAllByText('无数据').length).toBeGreaterThan(0);
    expect(screen.getByText(/采集空窗/)).toBeInTheDocument();
    expect(mocks.snapshot.mock.calls[0][0]).toMatchObject({ pageSize: 20 });
    const { from, to } = mocks.snapshot.mock.calls[0][0];
    expect(Date.parse(to) - Date.parse(from)).toBe(3600000);
  });
  it('omits log calls and detail actions for metric-only users and keeps controls read-only', async () => {
    render(<OperationsPage />);
    fireEvent.click(screen.getByRole('tab', { name: '错误事件' }));
    await waitFor(() =>
      expect(screen.getByText('需要运维日志查看权限')).toBeInTheDocument(),
    );
    expect(mocks.events).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('tab', { name: 'AI运行' }));
    await waitFor(() =>
      expect(
        screen.getByText('AI调用与执行明细需要运维日志查看权限'),
      ).toBeInTheDocument(),
    );
    expect(mocks.ai).not.toHaveBeenCalled();
    expect(mocks.executions).not.toHaveBeenCalled();
    expect(mocks.execution).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('tab', { name: '告警中心' }));
    await waitFor(() => expect(mocks.alerts).toHaveBeenCalled());
    expect(
      screen.queryByRole('button', { name: '新增规则' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '监控设置' }));
    await waitFor(() =>
      expect(screen.getByText('只读配置')).toBeInTheDocument(),
    );
    expect(
      screen.queryByRole('button', { name: '保存设置' }),
    ).not.toBeInTheDocument();
  });
  it('denies the whole page when access is lost', () => {
    mocks.access.canViewOperations = false;
    render(<OperationsPage />);
    expect(screen.getByText('无平台运维访问权限')).toBeInTheDocument();
    expect(mocks.snapshot).not.toHaveBeenCalled();
  });
  it('loads sanitized event details only on demand and resets cursors after a scope change', async () => {
    mocks.access.canViewOperationsLogs = true;
    mocks.events.mockResolvedValue({
      items: [
        {
          id: 7,
          occurredAt: '2026-11-05T01:00:00Z',
          fingerprint: 'fingerprint-7',
          message: 'sanitized',
        },
      ],
      total: 2,
      nextCursor: 'event-cursor',
    });
    mocks.event.mockResolvedValue({
      id: 7,
      message: 'sanitized detail',
      stack: 'sanitized stack',
    });
    render(<OperationsPage />);
    fireEvent.click(screen.getByRole('tab', { name: '错误事件' }));
    await waitFor(() =>
      expect(screen.getByText('fingerprint-7')).toBeInTheDocument(),
    );
    expect(mocks.event).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    await waitFor(() =>
      expect(screen.getByText('sanitized stack')).toBeInTheDocument(),
    );
    expect(mocks.event).toHaveBeenCalledWith(7, expect.any(AbortSignal));
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() =>
      expect(mocks.events.mock.lastCall?.[0].cursor).toBe('event-cursor'),
    );
    fireEvent.change(screen.getByRole('spinbutton', { name: '团队ID' }), {
      target: { value: '9' },
    });
    fireEvent.click(screen.getByRole('button', { name: '应用筛选' }));
    await waitFor(() =>
      expect(mocks.events.mock.lastCall?.[0]).toMatchObject({
        tenantId: 9,
        cursor: undefined,
      }),
    );
  });
  it('uses one UTC boundary for every active source and exposes permitted actions', async () => {
    mocks.access.canManageOperationsAlerts = true;
    mocks.access.canEditOperationsRules = true;
    mocks.alerts.mockResolvedValue({
      items: [{ id: 3, ruleName: 'error rate' }],
      total: 1,
    });
    render(<OperationsPage />);
    fireEvent.click(screen.getByRole('tab', { name: '告警中心' }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '确认' })).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('button', { name: '新增规则' }),
    ).toBeInTheDocument();
    expect(mocks.snapshot.mock.lastCall?.[0].to).toBe(
      mocks.alerts.mock.lastCall?.[0].to,
    );
    expect(mocks.alerts.mock.lastCall?.[0].from).toBe(
      mocks.notifications.mock.lastCall?.[0].from,
    );
    fireEvent.click(screen.getByRole('button', { name: '操作记录' }));
    await waitFor(() =>
      expect(mocks.actions).toHaveBeenCalledWith(3, expect.any(AbortSignal), {
        from: '2026-11-05T00:00:00Z',
        to: '2026-11-05T01:00:00Z',
      }),
    );
  });
  it('shows the last successful timestamp for a failed snapshot partition', async () => {
    const snapshot = await mocks.snapshot();
    mocks.snapshot.mockResolvedValue({
      ...snapshot,
      sourceStatus: {
        resources: { status: 'ERROR', observedAt: '2026-11-05T01:00:00Z' },
      },
    });
    render(<OperationsPage />);
    await waitFor(() =>
      expect(
        screen.getByText('资源数据暂不可用，保留上次成功结果'),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByText('最近成功：2026/11/05 09:00:00（Asia/Shanghai）'),
    ).toBeInTheDocument();
  });
  it('omits cleared optional filters instead of sending invalid blank dimensions', async () => {
    render(<OperationsPage />);
    await waitFor(() => expect(mocks.snapshot).toHaveBeenCalled());
    fireEvent.change(screen.getByRole('textbox', { name: '场景' }), {
      target: { value: 'video' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '场景' }), {
      target: { value: '' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '实例' }), {
      target: { value: '   ' },
    });
    const previousCalls = mocks.snapshot.mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: '应用筛选' }));
    await waitFor(() => {
      expect(mocks.snapshot.mock.calls.length).toBeGreaterThan(previousCalls);
      expect(mocks.snapshot.mock.lastCall?.[0]).not.toHaveProperty('scene');
      expect(mocks.snapshot.mock.lastCall?.[0]).not.toHaveProperty(
        'instanceId',
      );
    });
  });
  it('loads the execution timeline on demand and aborts when closed', async () => {
    mocks.access.canViewOperationsLogs = true;
    mocks.ai.mockResolvedValue({
      items: [{ callLogId: 1, executionId: 42 }],
      total: 1,
    });
    mocks.executions.mockResolvedValue({
      items: [{ id: 1, executionId: 42, executionVersion: 2 }],
      total: 1,
    });
    mocks.execution.mockResolvedValue({
      executionId: 42,
      from: '2026-11-05T00:00:00Z',
      to: '2026-11-05T01:00:00Z',
      coverage: 'PARTIAL',
      truncated: true,
      versions: [
        {
          id: 1,
          executionId: 42,
          executionVersion: 2,
          status: 'FAILED',
          observedAt: '2026-11-05T00:30:00Z',
        },
      ],
      attempts: Array.from({ length: 2 }, (_, index) => ({
        id: index + 1,
        executionId: 42,
        executionVersion: 2,
        attemptNo: index + 1,
        phase: `attempt-${index + 1}`,
        status: 'FAILED',
        startedAt: '2026-11-05T00:30:00Z',
      })),
      calls: [],
    });
    render(<OperationsPage />);
    fireEvent.click(screen.getByRole('tab', { name: 'AI运行' }));
    await waitFor(() =>
      expect(
        screen.getAllByRole('button', { name: '查看执行42时间线' }),
      ).toHaveLength(2),
    );
    expect(mocks.execution).not.toHaveBeenCalled();
    fireEvent.click(
      screen.getAllByRole('button', { name: '查看执行42时间线' })[0],
    );
    await waitFor(() =>
      expect(screen.getByText('attempt-2')).toBeInTheDocument(),
    );
    expect(screen.getByText('时间线覆盖不完整（PARTIAL）')).toBeInTheDocument();
    expect(
      screen.getByText('结果已截断：每个数据源最多100条'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('2026/11/05 08:30:00').length).toBeGreaterThan(
      0,
    );
    expect(mocks.execution).toHaveBeenCalledWith(
      42,
      {
        from: '2026-11-05T00:00:00Z',
        to: '2026-11-05T01:00:00Z',
        pageSize: 100,
      },
      expect.any(AbortSignal),
    );
    fireEvent.click(screen.getByText('刷新当前页'));
    await waitFor(() => expect(mocks.ai.mock.calls.length).toBeGreaterThan(1));
    expect(mocks.execution).toHaveBeenCalledTimes(1);
    const signal = mocks.execution.mock.calls[0][2] as AbortSignal;
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(signal.aborted).toBe(true));
  });
  it('shows fingerprint counts and sanitized locations from the existing events response', async () => {
    mocks.access.canViewOperationsLogs = true;
    mocks.events.mockResolvedValue({
      items: [],
      total: 0,
      groups: [
        {
          fingerprint: 'group-one',
          source: 'API',
          code: 'INTERNAL_ERROR',
          location: 'GET /api/projects/{id}',
          count: 7,
          lastAt: '2026-11-05T00:30:00Z',
        },
      ],
    });
    render(<OperationsPage />);
    fireEvent.click(screen.getByRole('tab', { name: '错误事件' }));
    await waitFor(() =>
      expect(screen.getByText('group-one')).toBeInTheDocument(),
    );
    expect(
      screen.getByText('错误指纹聚合（前20组，按当前范围）'),
    ).toBeInTheDocument();
    expect(screen.getByText('GET /api/projects/{id}')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('2026/11/05 08:30:00')).toBeInTheDocument();
    expect(mocks.event).not.toHaveBeenCalled();
  });
});
