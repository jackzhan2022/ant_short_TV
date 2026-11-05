import { BrowserWindow } from 'electron';
import {
  authenticationExpired,
  collectAllProjects,
  type ListTemplate,
  learnListRequest,
  listResult,
  recognizedListEndpoint,
  record,
  SOURCE_ORIGIN,
} from './source-data';
import type {
  SourceCollection,
  SourceCollector,
  SourceStatus,
} from './source-types';

const PROJECT_LIST = `${SOURCE_ORIGIN}/dramart/projectlist/`;
const RESPONSE_LIMIT = 8 * 1024 * 1024;

export class DramartCollector implements SourceCollector {
  private window?: BrowserWindow;
  private initializing?: Promise<unknown>;
  private templates = new Map<string, ListTemplate>();
  private pending = new Map<string, ListTemplate>();
  private status: SourceStatus = {
    loggedIn: false,
    syncing: false,
    discoveredProjects: 0,
  };

  constructor(private readonly changed: () => void) {}

  state(): SourceStatus {
    return { ...this.status };
  }

  private update(value: Partial<SourceStatus>): void {
    this.status = { ...this.status, ...value };
    this.changed();
  }

  private async open(show: boolean): Promise<BrowserWindow> {
    if (this.window && !this.window.isDestroyed()) {
      if (show) {
        this.window.show();
        this.window.focus();
      }
      await this.initializing;
      return this.window;
    }
    const window = new BrowserWindow({
      width: 1280,
      height: 900,
      show,
      title: '火山剧创 · 登录与项目发现',
      webPreferences: {
        partition: 'persist:dramart-source-v1',
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
        webSecurity: true,
      },
    });
    this.window = window;
    this.templates.clear();
    this.pending.clear();
    window.webContents.session.setPermissionRequestHandler(
      (_contents, _permission, callback) => callback(false),
    );
    window.webContents.session.setPermissionCheckHandler(() => false);
    window.webContents.setWindowOpenHandler(({ url }) => {
      if (this.safeNavigation(url)) void window.loadURL(url).catch(() => {});
      return { action: 'deny' };
    });
    window.webContents.on('will-navigate', (event, url) => {
      if (!this.safeNavigation(url)) event.preventDefault();
    });
    window.webContents.on('will-redirect', (event, url) => {
      if (!this.safeNavigation(url)) event.preventDefault();
    });
    window.webContents.on('did-navigate', (_event, url) => {
      const parsed = new URL(url);
      if (/login|passport|signin/i.test(parsed.hostname + parsed.pathname))
        this.update({ loggedIn: false });
    });
    window.on('close', (event) => {
      // A manual close keeps the independently authenticated browser available for scheduled syncs.
      event.preventDefault();
      window.hide();
    });
    window.on('closed', () => {
      if (this.window === window) this.window = undefined;
    });
    try {
      const debuggerClient = window.webContents.debugger;
      debuggerClient.attach('1.3');
      debuggerClient.on('message', (_event, method, parameters) => {
        void this.observe(method, record(parameters) ?? {}, window).catch(
          () => {
            this.update({
              error: '源站列表发现失败；请重新打开团队和项目列表后重试。',
            });
          },
        );
      });
      // Network.enable needs a renderer, which the first navigation creates.
      // Register observation first, then start both operations without awaiting either.
      this.initializing = Promise.all([
        debuggerClient.sendCommand('Network.enable', {
          maxTotalBufferSize: RESPONSE_LIMIT * 2,
          maxResourceBufferSize: RESPONSE_LIMIT,
        }),
        window.loadURL(PROJECT_LIST),
      ]);
      await this.initializing;
      this.update({ error: undefined });
    } catch {
      if (this.window === window) this.window = undefined;
      if (!window.isDestroyed()) window.destroy();
      this.update({ error: '无法打开火山剧创，请检查网络后重试。' });
      throw new Error('无法打开火山剧创登录窗口。');
    } finally {
      this.initializing = undefined;
    }
    return window;
  }

  private safeNavigation(url: string): boolean {
    try {
      return new URL(url).protocol === 'https:';
    } catch {
      return false;
    }
  }

  private async observe(
    method: string,
    parameters: Record<string, unknown>,
    window: BrowserWindow,
  ): Promise<void> {
    const requestId =
      typeof parameters.requestId === 'string'
        ? parameters.requestId
        : undefined;
    if (!requestId) return;
    if (method === 'Network.requestWillBeSent') {
      const request = record(parameters.request);
      if (
        !request ||
        typeof request.url !== 'string' ||
        typeof request.method !== 'string'
      )
        return;
      if (!recognizedListEndpoint(request.url, request.method)) return;
      if (
        typeof request.postData !== 'string' ||
        request.postData.length > 64000
      )
        return;
      let body: unknown;
      try {
        body = JSON.parse(request.postData);
      } catch {
        return;
      }
      const template = learnListRequest(request.url, request.method, body);
      if (template && this.pending.size < 500)
        this.pending.set(requestId, template);
      return;
    }
    const template = this.pending.get(requestId);
    if (!template) return;
    if (method === 'Network.responseReceived') {
      const response = record(parameters.response);
      if (response?.status === 401 || response?.status === 403) {
        this.pending.delete(requestId);
        this.templates.clear();
        this.update({
          loggedIn: false,
          error: '源站登录已失效或权限不足，请重新登录。',
        });
      }
      if (response?.status !== 200) this.pending.delete(requestId);
      return;
    }
    if (method === 'Network.loadingFailed') {
      this.pending.delete(requestId);
      return;
    }
    if (method !== 'Network.loadingFinished') return;
    this.pending.delete(requestId);
    if (
      Number(parameters.encodedDataLength) > RESPONSE_LIMIT ||
      window.isDestroyed()
    )
      return;
    const response = record(
      await window.webContents.debugger.sendCommand('Network.getResponseBody', {
        requestId,
      }),
    );
    if (
      typeof response?.body !== 'string' ||
      response.body.length > RESPONSE_LIMIT
    )
      return;
    const text = response.base64Encoded
      ? Buffer.from(response.body, 'base64').toString('utf8')
      : response.body;
    let value: unknown;
    try {
      value = JSON.parse(text);
    } catch {
      return;
    }
    if (authenticationExpired(value)) {
      this.templates.clear();
      this.update({ loggedIn: false, error: '源站登录已失效，请重新登录。' });
      return;
    }
    // Validate the pagination response without retaining source response bodies.
    listResult(value);
    this.templates.set(template.action, template);
    this.update({ loggedIn: true, error: undefined });
  }

  async login(): Promise<void> {
    await this.open(true);
  }

  private async fetchList(
    window: BrowserWindow,
    url: string,
    body: Record<string, unknown>,
  ): Promise<unknown> {
    if (
      window.isDestroyed() ||
      !window.webContents.getURL().startsWith(`${SOURCE_ORIGIN}/`)
    )
      throw new Error('请先在源站窗口完成登录并返回项目列表。');
    // Runs in the authenticated page so normal cookies and the site's fetch/signing hooks apply.
    // No cookies or authorization headers cross into the application or its persisted state.
    const response: unknown =
      await window.webContents.executeJavaScript(`(async () => {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 30000);
      try {
        const response = await window.fetch(${JSON.stringify(url)}, {
          method: 'POST', credentials: 'include', redirect: 'error',
          headers: { 'Content-Type': 'application/json' },
          body: ${JSON.stringify(JSON.stringify(body))}, signal: controller.signal
        });
        if (!response.ok) return { status: response.status };
        const length = Number(response.headers.get('content-length') || 0);
        if (length > ${RESPONSE_LIMIT}) return { oversized: true };
        const reader = response.body.getReader();
        const chunks = []; let size = 0;
        while (true) {
          const next = await reader.read();
          if (next.done) break;
          size += next.value.byteLength;
          if (size > ${RESPONSE_LIMIT}) { await reader.cancel(); return { oversized: true }; }
          chunks.push(next.value);
        }
        const bytes = new Uint8Array(size); let offset = 0;
        for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
        return { status: response.status, value: JSON.parse(new TextDecoder().decode(bytes)) };
      } catch { return { failed: true }; }
      finally { clearTimeout(timer); }
    })()`);
    const result = record(response);
    if (
      result?.status === 401 ||
      result?.status === 403 ||
      authenticationExpired(result?.value)
    ) {
      this.templates.clear();
      this.update({ loggedIn: false });
      throw new Error('源站登录已失效或权限不足，请重新登录后重试完整同步。');
    }
    if (result?.oversized)
      throw new Error('源站列表响应超过安全大小，无法完成完整同步。');
    if (result?.failed || result?.status !== 200)
      throw new Error(
        '源站只读列表请求失败；可能需要重新登录或接口签名约定已变化。',
      );
    return result.value;
  }

  async collect(): Promise<SourceCollection[]> {
    if (this.status.syncing)
      throw new Error('源站正在同步，请等待本次同步结束。');
    this.update({ syncing: true, discoveredProjects: 0, error: undefined });
    try {
      const window = await this.open(false);
      if (!this.status.loggedIn)
        throw new Error(
          '请先在火山剧创窗口登录，并打开团队切换列表与项目列表，再重试同步。',
        );
      const projects = await collectAllProjects(
        [...this.templates.values()],
        (url, body) => this.fetchList(window, url, body),
        (count) => this.update({ discoveredProjects: count }),
      );
      this.update({ lastSync: new Date().toISOString(), error: undefined });
      return projects;
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '源站完整同步失败。';
      this.update({ error: message });
      throw new Error(message);
    } finally {
      this.update({ syncing: false });
    }
  }

  close(): void {
    const window = this.window;
    this.window = undefined;
    this.templates.clear();
    this.pending.clear();
    if (window && !window.isDestroyed()) window.destroy();
  }
}
