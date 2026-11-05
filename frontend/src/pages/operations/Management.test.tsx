import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertActionDialog, RuleEditor, SettingsPanel } from './Management';
import type { Rule, Settings } from './service';

const api = vi.hoisted(() => ({
  saveSettings: vi.fn(),
  saveRule: vi.fn(),
  acknowledge: vi.fn(),
  silence: vi.fn(),
}));
vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('./service', async (original) => ({
  ...(await original<typeof import('./service')>()),
  operationsApi: api,
}));
const settings: Settings = {
  version: 4,
  telemetryEnabled: true,
  notificationsEnabled: false,
  persistentDigestEnabled: true,
  observationUntil: '2026-11-06T01:00:00Z',
  externalProbeConfigured: false,
  refreshSeconds: 15,
  minuteRetentionDays: 7,
  hourlyRetentionDays: 90,
  eventRetentionDays: 14,
  projectionRetentionDays: 30,
  alertRetentionDays: 90,
  notificationRetentionDays: 30,
  channelConfigured: true,
  signatureConfigured: false,
  instanceId: 'node-1',
};
const rule: Rule = {
  id: 2,
  name: 'HTTP errors',
  description: '',
  enabled: true,
  severity: 'WARNING',
  metric: 'http.errorRate',
  operator: 'GT',
  thresholdValue: 0.05,
  recoveryThreshold: 0.02,
  windowMinutes: 5,
  sustainedMinutes: 3,
  minimumSamples: 20,
  cooldownMinutes: 30,
  dimensionsJson: '{}',
  notify: false,
  version: 8,
};

describe('operations management forms', () => {
  beforeEach(() => vi.clearAllMocks());
  it('uses percentage units for a new failure-rate rule', () => {
    render(<RuleEditor open close={vi.fn()} saved={vi.fn()} />);
    expect(screen.getByRole('spinbutton', { name: '触发阈值' })).toHaveValue(
      '5',
    );
    expect(screen.getByRole('spinbutton', { name: '恢复阈值' })).toHaveValue(
      '2',
    );
    expect(screen.getByText('HTTP技术失败率（%）')).toBeInTheDocument();
  });
  it('requires a phase for active phase attempt duration rules', async () => {
    api.saveRule.mockResolvedValue({});
    render(
      <RuleEditor
        rule={{ ...rule, metric: 'execution.phaseAgeSeconds' }}
        open
        close={vi.fn()}
        saved={vi.fn()}
      />,
    );
    expect(screen.getByText('活跃阶段尝试耗时（秒）')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(
        screen.getByText('活跃阶段尝试耗时指标必须填写 phase'),
      ).toBeInTheDocument(),
    );
    expect(api.saveRule).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: /匹配维度/ }), {
      target: { value: '{"phase":"SUBMIT","scene":"FREE_CREATION"}' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(api.saveRule).toHaveBeenCalledWith(
        expect.objectContaining({
          metric: 'execution.phaseAgeSeconds',
          dimensionsJson: '{"phase":"SUBMIT","scene":"FREE_CREATION"}',
        }),
        2,
      ),
    );
  });
  it('saves HTTP rules scoped to a typed route and method', async () => {
    api.saveRule.mockResolvedValue({});
    render(<RuleEditor rule={rule} open close={vi.fn()} saved={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox', { name: /匹配维度/ }), {
      target: { value: '{"route":"/api/projects/{id}","method":"GET"}' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(api.saveRule).toHaveBeenCalledWith(
        expect.objectContaining({
          metric: 'http.errorRate',
          dimensionsJson: '{"route":"/api/projects/{id}","method":"GET"}',
        }),
        2,
      ),
    );
  });
  it('retains settings input and version when a concurrent update conflicts', async () => {
    api.saveSettings.mockRejectedValue(new Error('版本冲突，请重新加载'));
    const saved = vi.fn();
    render(<SettingsPanel settings={settings} editable saved={saved} />);
    fireEvent.change(
      screen.getByRole('spinbutton', { name: '页面刷新间隔（秒）' }),
      { target: { value: '30' } },
    );
    fireEvent.click(screen.getByRole('button', { name: '保存设置' }));
    await waitFor(() =>
      expect(screen.getByText('版本冲突，请重新加载')).toBeInTheDocument(),
    );
    expect(api.saveSettings).toHaveBeenCalledWith(
      expect.objectContaining({
        version: 4,
        refreshSeconds: 30,
        persistentDigestEnabled: true,
      }),
    );
    expect(
      screen.getByRole('spinbutton', { name: '页面刷新间隔（秒）' }),
    ).toHaveValue('30');
    expect(saved).not.toHaveBeenCalled();
  });
  it('retains the edited rule after a conflict and prevents invalid required fields from submitting', async () => {
    api.saveRule.mockRejectedValue(new Error('规则版本冲突'));
    const close = vi.fn();
    render(<RuleEditor rule={rule} open close={close} saved={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox', { name: '规则名称' }), {
      target: { value: '' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(
        screen.getByText(/Please enter|请输入|name.*required/),
      ).toBeInTheDocument(),
    );
    expect(api.saveRule).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '规则名称' }), {
      target: { value: 'edited errors' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(screen.getByText('规则版本冲突')).toBeInTheDocument(),
    );
    expect(api.saveRule).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'edited errors', version: 8 }),
      2,
    );
    expect(screen.getByRole('textbox', { name: '规则名称' })).toHaveValue(
      'edited errors',
    );
    expect(close).not.toHaveBeenCalled();
  });
  it('keeps confirmation distinct from recovery and requires a reason', async () => {
    api.acknowledge.mockResolvedValue({});
    const saved = vi.fn();
    render(
      <AlertActionDialog
        target={{
          event: { id: 5, ruleName: 'queue' } as never,
          action: 'acknowledge',
        }}
        close={vi.fn()}
        saved={saved}
      />,
    );
    expect(screen.getByText(/此操作不改变故障状态/)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '处理原因' }), {
      target: { value: 'investigating' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(api.acknowledge).toHaveBeenCalledWith(5, 'investigating'),
    );
    expect(saved).toHaveBeenCalledOnce();
  });
});
