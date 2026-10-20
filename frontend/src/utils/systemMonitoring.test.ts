import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  installSystemMonitoring,
  reportFrontendError,
  reportRequestFailure,
} from './systemMonitoring';

describe('system monitoring', () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true });
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-20T01:30:00Z'));
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockClear();
    localStorage.clear();
    // biome-ignore lint/suspicious/noDocumentCookie: reproduce the existing CSRF cookie contract in the browser test.
    document.cookie = 'XSRF-TOKEN=csrf%20token; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('uses session and CSRF and removes secrets and query strings', async () => {
    localStorage.setItem('currentTenantId', '21');
    await reportFrontendError(
      'FRONTEND_RENDER',
      new Error('password=private Bearer secret 13800138000'),
      '/projects?token=hidden#secret',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/monitoring/frontend-events');
    expect(options.credentials).toBe('include');
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf token');
    expect(options.headers['X-Tenant-Id']).toBe('21');
    expect(options.body).not.toMatch(
      /private|Bearer secret|13800138000|hidden/,
    );
    expect(JSON.parse(options.body).location).toBe('/projects');
  });

  it('reports transport failures but excludes cancellations, responses and monitoring requests', async () => {
    await reportRequestFailure({
      message: 'Network error',
      request: {},
      config: { url: '/api/projects?token=x' },
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).source).toBe(
      'FRONTEND_REQUEST',
    );
    await reportRequestFailure({ code: 'ERR_CANCELED', request: {} });
    await reportRequestFailure({ response: { status: 500 }, request: {} });
    await reportRequestFailure({
      request: {},
      config: { url: '/api/monitoring/frontend-events' },
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('limits repeated errors and never propagates failed reporting', async () => {
    fetchMock.mockRejectedValueOnce(new Error('unavailable'));
    const error = new Error('repeat unique');
    await expect(
      reportFrontendError('FRONTEND_RENDER', error),
    ).resolves.toBeUndefined();
    await reportFrontendError('FRONTEND_RENDER', error);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('installs and removes global error listeners', async () => {
    const cleanup = installSystemMonitoring();
    window.dispatchEvent(
      new ErrorEvent('error', {
        message: 'global unique',
        error: new Error('global unique'),
      }),
    );
    await Promise.resolve();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    cleanup();
    vi.advanceTimersByTime(61000);
    window.dispatchEvent(new ErrorEvent('error', { message: 'global unique' }));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('keeps distinct occurrences of the same message for server aggregation and redacts echoed API keys', async () => {
    const message = 'Incorrect API key provided: sk-proj-1234567890abcdef';
    await reportFrontendError('FRONTEND_RUNTIME', new Error(message));
    await reportFrontendError('FRONTEND_RUNTIME', new Error(message));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][1].body).not.toContain('1234567890abcdef');
  });

  it('does not re-report already handled business errors and aborted operations as promise failures', () => {
    const cleanup = installSystemMonitoring();
    for (const reason of [
      { name: 'BizError', message: 'points insufficient' },
      { name: 'AbortError', message: 'cancelled' },
    ]) {
      const event = new Event('unhandledrejection');
      Object.defineProperty(event, 'reason', { value: reason });
      window.dispatchEvent(event);
    }
    cleanup();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
