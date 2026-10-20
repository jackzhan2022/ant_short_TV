type FrontendErrorSource =
  | 'FRONTEND_RENDER'
  | 'FRONTEND_RUNTIME'
  | 'FRONTEND_PROMISE'
  | 'FRONTEND_REQUEST';

const endpoint = '/api/monitoring/frontend-events';
const observed = new WeakSet<object>();
let minuteStart = 0;
let minuteCount = 0;

function clean(value: string, limit: number): string {
  return value
    .slice(0, 16000)
    .replace(
      /((?:cookie|set-cookie|authorization)\s*:\s*)[^\r\n]*/gi,
      '$1[REDACTED]',
    )
    .replace(/(https?:\/\/[^\s?#]+)[?#][^\s]*/g, '$1')
    .replace(/\bsk-[A-Za-z0-9_-]{8,}\b/g, '[REDACTED]')
    .replace(
      /(["']?(?:access[_-]?key|secret[_-]?key)["']?\s*[:=]\s*)[^\s,;]+/gi,
      '$1[REDACTED]',
    )
    .replace(
      /((?:api key|access token|secret key)(?: provided)?\s*[:=]\s*)[^\s,;]+/gi,
      '$1[REDACTED]',
    )
    .replace(
      /(["']?(?:authorization|cookie|set-cookie|password|passwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|session[_-]?id)["']?\s*[:=]\s*)(?:Bearer\s+)?(?:["'][^"'\r\n]*["']|[^\s,;]+)/gi,
      '$1[REDACTED]',
    )
    .replace(/Bearer\s+[^\s,;]+/gi, 'Bearer [REDACTED]')
    .replace(/(?<!\d)1[3-9]\d{9}(?!\d)/g, '[MOBILE]')
    .slice(0, limit);
}

function path(value: string): string {
  return clean(
    value.split(/[?#]/, 1)[0].replace(/^https?:\/\/[^/\s]+/, ''),
    300,
  );
}

function errorField(
  error: unknown,
  name: 'message' | 'stack' | 'name',
): string {
  if (error && typeof error === 'object' && name in error) {
    const value = (error as Record<string, unknown>)[name];
    return typeof value === 'string' ? value : '';
  }
  return name === 'message' && typeof error === 'string' ? error : '';
}

export async function reportFrontendError(
  source: FrontendErrorSource,
  error: unknown,
  location = typeof window === 'undefined' ? '' : window.location.pathname,
  componentStack = '',
): Promise<void> {
  // Use native fetch so failed telemetry cannot re-enter the request interceptor.
  try {
    if (typeof window === 'undefined' || typeof fetch !== 'function') return;
    if (error && typeof error === 'object' && observed.has(error)) return;
    const message = clean(
      errorField(error, 'message') || 'Unknown runtime error',
      500,
    );
    const code = clean(errorField(error, 'name') || source, 100);
    const locationPath = path(location);
    if (locationPath.includes(endpoint)) return;
    const now = Date.now();
    if (now - minuteStart >= 60000) {
      minuteStart = now;
      minuteCount = 0;
    }
    if (minuteCount >= 20) return;
    minuteCount += 1;
    if (error && typeof error === 'object') observed.add(error);
    const entry = document.cookie
      .split(';')
      .map((item) => item.trim())
      .find((item) => item.startsWith('XSRF-TOKEN='));
    const csrf = entry
      ? decodeURIComponent(entry.slice('XSRF-TOKEN='.length))
      : '';
    const tenantId = localStorage.getItem('currentTenantId');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    try {
      await fetch(`${process.env.API_BASE_URL || ''}${endpoint}`, {
        method: 'POST',
        credentials: 'include',
        keepalive: true,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
          ...(tenantId ? { 'X-Tenant-Id': tenantId } : {}),
        },
        body: JSON.stringify({
          source,
          code,
          location: locationPath,
          message,
          stack: clean(`${errorField(error, 'stack')}\n${componentStack}`, 800),
        }),
      });
    } finally {
      clearTimeout(timer);
    }
  } catch {
    // Reporting failure must remain silent and cannot break user operations.
  }
}

export function reportRequestFailure(error: unknown): Promise<void> {
  if (!error || typeof error !== 'object') return Promise.resolve();
  const detail = error as {
    response?: unknown;
    request?: unknown;
    code?: string;
    name?: string;
    config?: { url?: string };
  };
  // HTTP failures are collected by the backend; cancellation is a normal navigation event.
  if (
    detail.response ||
    !detail.request ||
    detail.code === 'ERR_CANCELED' ||
    detail.name === 'AbortError' ||
    detail.name === 'CanceledError'
  )
    return Promise.resolve();
  return reportFrontendError(
    'FRONTEND_REQUEST',
    error,
    detail.config?.url || window.location.pathname,
  );
}

export function installSystemMonitoring(): () => void {
  const onError = (event: ErrorEvent) => {
    void reportFrontendError('FRONTEND_RUNTIME', event.error || event.message);
  };
  const onRejection = (event: PromiseRejectionEvent) => {
    // Rejections from HTTP requests follow the same exclusion rules.
    const reason = event.reason;
    if (
      reason &&
      typeof reason === 'object' &&
      ['BizError', 'AbortError', 'CanceledError'].includes(reason.name)
    )
      return;
    if (
      reason &&
      typeof reason === 'object' &&
      ('response' in reason ||
        'request' in reason ||
        reason.code === 'ERR_CANCELED')
    ) {
      void reportRequestFailure(reason);
      return;
    }
    void reportFrontendError('FRONTEND_PROMISE', reason);
  };
  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onRejection);
  return () => {
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onRejection);
  };
}
