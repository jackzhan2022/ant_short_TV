import { beforeEach, describe, expect, it, vi } from 'vitest';
import { operationsApi, validateRange } from './service';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@umijs/max', () => ({ request }));

describe('operations API', () => {
  beforeEach(() => {
    request.mockReset();
    request.mockResolvedValue({
      success: true,
      data: { items: [], total: 0, nextCursor: null },
    });
  });
  it('sends bounded UTC queries, cursor and cancellation to the platform endpoint', async () => {
    const signal = new AbortController().signal;
    const params = {
      from: '2026-11-04T00:00:00.000Z',
      to: '2026-11-05T00:00:00.000Z',
      tenantId: 9,
      pageSize: 20,
      cursor: 'next',
    };
    await operationsApi.events(params, signal);
    expect(request).toHaveBeenCalledWith(
      '/api/platform/operations/events',
      expect.objectContaining({ params, signal }),
    );
  });
  it('keeps optimistic versions in writes and surfaces conflicts without pretending success', async () => {
    request.mockResolvedValue({
      success: false,
      errorCode: 'CONFLICT',
      errorMessage: '版本冲突，请重新加载',
    });
    await expect(
      operationsApi.saveSettings({ version: 4, telemetryEnabled: true }),
    ).rejects.toThrow('版本冲突');
    expect(request).toHaveBeenCalledWith(
      '/api/platform/operations/settings',
      expect.objectContaining({
        method: 'PUT',
        data: { version: 4, telemetryEnabled: true },
      }),
    );
  });
  it('rejects reversed and over-90-day ranges', () => {
    expect(
      validateRange('2026-11-05T00:00:00Z', '2026-11-04T00:00:00Z'),
    ).toBeTruthy();
    expect(
      validateRange('2026-01-01T00:00:00Z', '2026-11-05T00:00:00Z'),
    ).toBeTruthy();
    expect(
      validateRange('2026-11-04T00:00:00Z', '2026-11-05T00:00:00Z'),
    ).toBeUndefined();
  });
  it('uses the selected historical bounds when opening alert actions', async () => {
    const signal = new AbortController().signal;
    const params = { from: '2026-10-01T00:00:00Z', to: '2026-10-02T00:00:00Z' };
    await operationsApi.actions(7, signal, params);
    expect(request).toHaveBeenCalledWith(
      '/api/platform/operations/alerts/7/actions',
      expect.objectContaining({ params, signal }),
    );
  });
  it('loads a bounded execution timeline only through the explicit detail endpoint', async () => {
    const signal = new AbortController().signal;
    const params = {
      from: '2026-11-04T00:00:00Z',
      to: '2026-11-05T00:00:00Z',
      tenantId: 4,
      providerId: 8,
      pageSize: 100,
    };
    await operationsApi.execution(42, params, signal);
    expect(request).toHaveBeenCalledWith(
      '/api/platform/operations/executions/42',
      expect.objectContaining({ params, signal }),
    );
  });
});
