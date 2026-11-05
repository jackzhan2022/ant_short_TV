import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { BrowserWindow, session } from 'electron';
import { CloudClient, normalizeCloudUrl } from './cloud';
import type { QueueState } from './shared';
import type { SourceCollector } from './source-types';
import type {
  BatchInput,
  SourceProject,
  SourceSnapshot,
  TargetProject,
  WorkspaceState,
} from './workspace-shared';

interface Config {
  cloudUrl: string;
  autoSyncMinutes: number;
  connectionId: string;
  tenantId?: number;
}
const DEFAULT_CLOUD_URL = 'https://antv.aixmax.cn';

export class WorkspaceController {
  private config: Config;
  private client?: CloudClient;
  private projects: SourceProject[] = [];
  private targets: TargetProject[] = [];
  private authenticated = false;
  private error?: string;
  private busy = false;
  private syncing = false;
  private version = 0;
  private closed = false;
  private refreshJob?: {
    client: CloudClient;
    promise: Promise<WorkspaceState>;
  };
  private timer?: ReturnType<typeof setInterval>;
  private loginWindow?: BrowserWindow;
  private file: string;

  constructor(
    root: string,
    private source: SourceCollector,
    private queue: () => QueueState,
    private changed: () => void,
  ) {
    this.file = path.join(root, 'workspace.json');
    const exists = fs.existsSync(this.file);
    this.config = exists
      ? JSON.parse(fs.readFileSync(this.file, 'utf8'))
      : {
          cloudUrl: DEFAULT_CLOUD_URL,
          autoSyncMinutes: 0,
          connectionId: randomUUID(),
        };
    const needsDefault = !this.config.cloudUrl?.trim();
    if (needsDefault) this.config.cloudUrl = DEFAULT_CLOUD_URL;
    if (!exists || needsDefault) this.save();
    this.connect();
    this.schedule();
  }
  state(): WorkspaceState {
    return {
      cloudUrl: this.config.cloudUrl,
      autoSyncMinutes: this.config.autoSyncMinutes,
      teams: this.client?.teams ?? [],
      tenantId: this.client?.tenantId,
      teamName: this.client?.teams.find(
        (team) => team.id === this.client?.tenantId,
      )?.name,
      authenticated: this.authenticated,
      error: this.error,
      projects: this.projects,
      targets: this.targets,
      source: { ...this.source.state(), syncing: this.syncing },
      queue: this.queue(),
    };
  }
  private connect() {
    const origin = normalizeCloudUrl(this.config.cloudUrl);
    const partition = `persist:antv-cloud-${createHash('sha256').update(origin).digest('hex').slice(0, 24)}`;
    const profile = session.fromPartition(partition);
    profile.setPermissionRequestHandler((_wc, _permission, callback) =>
      callback(false),
    );
    profile.setPermissionCheckHandler(() => false);
    this.client = new CloudClient(
      origin,
      async (url, init) => {
        const headers = { ...init.headers } as Record<string, string>;
        if (init.method && init.method !== 'GET') {
          const cookies = await profile.cookies.get({
            url: origin,
            name: 'XSRF-TOKEN',
          });
          if (cookies[0])
            headers['X-XSRF-TOKEN'] = decodeURIComponent(cookies[0].value);
        }
        return profile.fetch(url, { ...init, headers });
      },
      this.config.tenantId,
    );
    // Restored selection is accepted only after a fresh membership query.
  }
  private save() {
    const next = `${this.file}.tmp`;
    fs.writeFileSync(next, JSON.stringify(this.config), { mode: 0o600 });
    fs.renameSync(next, this.file);
  }
  configure(input: { cloudUrl?: string; autoSyncMinutes: number }) {
    this.idle();
    const cloudUrl = normalizeCloudUrl(input.cloudUrl ?? this.config.cloudUrl);
    if (
      !Number.isInteger(input.autoSyncMinutes) ||
      input.autoSyncMinutes < 0 ||
      input.autoSyncMinutes > 1440
    )
      throw new Error('同步间隔需为 0～1440 分钟，0 表示关闭。');
    this.version++;
    if (this.config.cloudUrl !== cloudUrl) {
      this.loginWindow?.destroy();
      this.loginWindow = undefined;
      this.config.tenantId = undefined;
      this.projects = [];
      this.targets = [];
      this.authenticated = false;
    }
    this.config = {
      ...this.config,
      cloudUrl,
      autoSyncMinutes: input.autoSyncMinutes,
    };
    this.save();
    this.connect();
    this.schedule();
    this.changed();
    return this.refresh();
  }
  async loginCloud(projectId?: number) {
    if (!this.client) throw new Error('剧智创连接尚未就绪，请重启客户端。');
    if (
      projectId !== undefined &&
      !this.targets.some((project) => project.id === projectId)
    )
      throw new Error('项目不可用，请刷新当前团队。');
    const destination =
      projectId === undefined
        ? `${this.client.url}/user/login`
        : `${this.client.url}/projects/${projectId}/production-workbench/settings`;
    if (this.loginWindow && !this.loginWindow.isDestroyed()) {
      this.loginWindow.show();
      this.loginWindow.focus();
      if (projectId !== undefined) await this.loginWindow.loadURL(destination);
      return;
    }
    const origin = this.client.url;
    const partition = `persist:antv-cloud-${createHash('sha256').update(origin).digest('hex').slice(0, 24)}`;
    const login = new BrowserWindow({
      width: 1160,
      height: 820,
      title: '登录剧智创 · 完成后关闭此窗口',
      webPreferences: {
        partition,
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        webSecurity: true,
      },
    });
    this.loginWindow = login;
    login.removeMenu();
    login.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    login.webContents.on('will-navigate', (event, url) => {
      if (new URL(url).origin !== origin) event.preventDefault();
    });
    login.webContents.on('will-redirect', (event, url) => {
      if (new URL(url).origin !== origin) event.preventDefault();
    });
    login.on('closed', () => {
      this.loginWindow = undefined;
      if (!this.closed) void this.refresh();
    });
    await login.loadURL(destination);
  }
  refresh(): Promise<WorkspaceState> {
    if (!this.client || this.busy || this.closed)
      return Promise.resolve(this.state());
    const client = this.client;
    if (this.refreshJob?.client === client) return this.refreshJob.promise;
    const promise = this.refreshNow(client, this.version).finally(() => {
      if (this.refreshJob?.promise === promise) this.refreshJob = undefined;
    });
    this.refreshJob = { client, promise };
    return promise;
  }
  private async refreshNow(client: CloudClient, version: number) {
    const current = () =>
      this.client === client && this.version === version && !this.closed;
    try {
      await client.refreshTeams();
      if (!current()) return this.state();
      this.authenticated = true;
      if (
        !client.tenantId &&
        this.config.tenantId &&
        client.teams.some((team) => team.id === this.config.tenantId)
      )
        await client.selectTeam(this.config.tenantId);
      if (!current()) return this.state();
      if (client.tenantId) {
        this.config.tenantId = client.tenantId;
        this.save();
        await this.loadProjects(client, version);
      } else {
        this.projects = [];
        this.targets = [];
      }
      this.error = undefined;
    } catch (error) {
      if (!current()) return this.state();
      this.error = error instanceof Error ? error.message : '平台连接失败。';
      this.authenticated = false;
      this.projects = [];
      this.targets = [];
    }
    this.changed();
    return this.state();
  }
  private async loadProjects(
    client = this.requireClient(),
    version = this.version,
  ) {
    const tenantId = client.tenantId;
    const assertCurrent = () => {
      if (
        client !== this.client ||
        version !== this.version ||
        tenantId !== client.tenantId ||
        this.closed
      )
        throw new Error('连接或团队已改变，请重新刷新。');
    };
    assertCurrent();
    const sources = await client.get<SourceProject[]>(
      '/api/desktop/source-projects',
    );
    const projects: TargetProject[] = [];
    const ids = new Set<number>();
    let total: number | undefined;
    for (let current = 1; current <= 10000; current++) {
      assertCurrent();
      const page = await client.get<{ data: TargetProject[]; total: number }>(
        `/api/projects?current=${current}&pageSize=100`,
      );
      assertCurrent();
      if (
        !Array.isArray(page.data) ||
        !Number.isSafeInteger(page.total) ||
        page.total < 0 ||
        (total !== undefined && total !== page.total)
      )
        throw new Error('剧智创项目分页格式无效。');
      total = page.total;
      for (const project of page.data) {
        if (!Number.isSafeInteger(project.id) || ids.has(project.id))
          throw new Error('剧智创项目分页重复，未加载不完整清单。');
        ids.add(project.id);
      }
      projects.push(...page.data);
      if (projects.length > total)
        throw new Error('剧智创项目分页数量超过总数。');
      if (projects.length === total) {
        this.projects = sources;
        this.targets = projects;
        return;
      }
      if (!page.data.length) break;
    }
    throw new Error('剧智创项目分页未完整返回，请重试。');
  }
  async selectTeam(id: number) {
    this.idle();
    const client = this.requireConnection();
    this.busy = true;
    try {
      if (this.refreshJob?.client === client) await this.refreshJob.promise;
      this.version++;
      this.projects = [];
      this.targets = [];
      await client.selectTeam(id);
      this.config.tenantId = id;
      this.save();
      return await this.refreshNow(client, this.version);
    } finally {
      this.busy = false;
      this.changed();
    }
  }
  private requireConnection() {
    if (!this.client) throw new Error('请先配置并登录剧智创。');
    return this.client;
  }
  requireClient() {
    const client = this.requireConnection();
    if (!this.authenticated || !client.tenantId)
      throw new Error('请先登录并选择剧智创团队。');
    return client;
  }
  private idle() {
    if (this.busy) throw new Error('正在同步或创建批量任务，请完成后再切换。');
  }
  loginSource() {
    return this.source.login();
  }
  async sync() {
    this.idle();
    const client = this.requireClient();
    this.busy = true;
    this.syncing = true;
    this.changed();
    try {
      const tenantId = client.tenantId;
      await client.selectTeam(tenantId as number);
      const collections = await this.source.collect();
      if (this.client !== client || client.tenantId !== tenantId)
        throw new Error('团队已改变，同步已中止。');
      for (const collection of collections) {
        const payload: SourceSnapshot = {
          connectionId: this.config.connectionId,
          sourceTeamId: collection.sourceTeamId,
          sourceProjectId: collection.sourceProjectId,
          sourceScriptId: collection.sourceScriptId,
          name: collection.name,
          complete: true,
          assets: collection.assets.map(
            ({ sourceAssetId, sourceParentId, name, type }) => ({
              sourceAssetId,
              sourceParentId,
              name,
              type,
            }),
          ),
        };
        await client.send('/api/desktop/source-projects/sync', 'POST', payload);
      }
      await this.loadProjects();
      this.error = undefined;
    } catch (error) {
      this.error = error instanceof Error ? error.message : '同步失败。';
      throw error;
    } finally {
      this.busy = false;
      this.syncing = false;
      this.changed();
    }
    return this.state();
  }
  async withBatch<T>(
    input: BatchInput,
    work: (
      client: CloudClient,
      input: BatchInput,
      tenantId: number,
      teamName: string,
    ) => Promise<T>,
  ): Promise<T> {
    this.idle();
    this.busy = true;
    this.changed();
    try {
      const client = this.requireClient();
      const tenantId = client.tenantId as number;
      await client.selectTeam(tenantId);
      return await work(
        client,
        input,
        tenantId,
        client.teams.find((team) => team.id === tenantId)?.name ??
          String(tenantId),
      );
    } finally {
      this.busy = false;
      this.changed();
    }
  }
  private schedule() {
    if (this.timer) clearInterval(this.timer);
    if (this.config.autoSyncMinutes > 0)
      this.timer = setInterval(() => {
        this.resumeSync();
      }, this.config.autoSyncMinutes * 60000);
  }
  resumeSync() {
    if (
      this.closed ||
      this.busy ||
      this.config.autoSyncMinutes <= 0 ||
      !this.client?.tenantId ||
      !this.authenticated ||
      !this.source.state().loggedIn
    )
      return;
    void this.sync().catch(() => {});
  }
  close() {
    this.closed = true;
    this.version++;
    if (this.timer) clearInterval(this.timer);
    this.loginWindow?.destroy();
    this.source.close();
  }
}
