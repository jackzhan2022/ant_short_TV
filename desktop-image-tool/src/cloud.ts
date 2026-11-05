import type { Team } from './workspace-shared';

export function normalizeCloudUrl(value: string): string {
  const url = new URL(value.trim());
  if (
    (url.protocol !== 'https:' &&
      !(
        url.protocol === 'http:' &&
        ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
      )) ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash
  )
    throw new Error(
      '请填写剧智创平台根地址（HTTPS，本地开发可用 HTTP localhost）。',
    );
  return url.origin;
}

export class CloudClient {
  readonly url: string;
  tenantId?: number;
  teams: Team[] = [];
  private selectionRequired = false;
  constructor(
    url: string,
    private transport: (url: string, init: RequestInit) => Promise<Response>,
    previousTenantId?: number,
  ) {
    this.url = normalizeCloudUrl(url);
    if (previousTenantId !== undefined) this.tenantId = previousTenantId;
  }
  async refreshTeams(): Promise<Team[]> {
    const previous = this.tenantId;
    try {
      const data = await this.request<Team[]>(
        '/api/tenants/my',
        undefined,
        false,
      );
      if (!Array.isArray(data)) throw new Error('团队列表格式无效。');
      this.teams = data.filter(
        (team) => team.status === 'ACTIVE' && Number.isSafeInteger(team.id),
      );
      if (
        this.tenantId !== undefined &&
        !this.teams.some((team) => team.id === this.tenantId)
      ) {
        this.selectionRequired = true;
        this.tenantId = undefined;
      }
      if (!this.tenantId && !this.selectionRequired && this.teams.length === 1)
        this.tenantId = this.teams[0].id;
      return this.teams;
    } catch (error) {
      if (previous !== undefined) this.selectionRequired = true;
      this.tenantId = undefined;
      this.teams = [];
      throw error;
    }
  }
  async selectTeam(id: number) {
    await this.refreshTeams();
    if (!this.teams.some((team) => team.id === id)) {
      this.tenantId = undefined;
      this.selectionRequired = true;
      throw new Error('无权访问此团队，请重新选择。');
    }
    this.tenantId = id;
    this.selectionRequired = false;
  }
  get<T>(path: string): Promise<T> {
    this.validatePath(path);
    return this.request<T>(path);
  }
  send<T>(path: string, method: 'POST' | 'PUT', body: unknown): Promise<T> {
    this.validatePath(path);
    return this.request<T>(path, { method, body: JSON.stringify(body) });
  }
  private validatePath(path: string) {
    if (
      !path.startsWith('/api/') ||
      path.includes('\\') ||
      new URL(path, this.url).origin !== this.url
    )
      throw new Error('无效的平台接口地址。');
  }
  private async request<T>(
    path: string,
    init: RequestInit = {},
    scoped = true,
  ): Promise<T> {
    this.validatePath(path);
    if (scoped && !this.tenantId)
      throw new Error('请先选择有权限的剧智创团队。');
    const response = await this.transport(`${this.url}${path}`, {
      ...init,
      redirect: 'error',
      credentials: 'include',
      signal: AbortSignal.timeout(60000),
      headers: {
        'Content-Type': 'application/json',
        ...(scoped ? { 'X-Tenant-Id': String(this.tenantId) } : {}),
      },
    });
    if (response.status === 401) {
      if (this.tenantId !== undefined) this.selectionRequired = true;
      this.tenantId = undefined;
      throw new Error('剧智创登录已失效，请重新登录。');
    }
    if (!response.ok)
      throw new Error(
        response.status === 404 && path.startsWith('/api/desktop/')
          ? '平台尚未启用桌面资产接口，请先部署配套后端。'
          : `平台请求失败（${response.status}）。`,
      );
    const result = (await response.json()) as {
      success: boolean;
      data: T;
      errorMessage?: string;
    };
    if (!result.success)
      throw new Error(result.errorMessage || '平台操作失败。');
    return result.data;
  }
}
