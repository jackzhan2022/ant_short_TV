import type { SourceCollection } from './source-types';
import type { AssetType, SourceName } from './workspace-shared';

export const SOURCE_ORIGIN = 'https://console.volcengine.com';
const ASSET_BASE = `${SOURCE_ORIGIN}/api/top/dramart/cn-beijing/2026-01-12/`;
const LIST_ACTIONS = ['ListTeams', 'ListProjects', 'ListScripts'] as const;
type ListAction = (typeof LIST_ACTIONS)[number];
type Row = Record<string, unknown>;
type ScopeKey = 'TeamId' | 'ProjectId' | 'ScriptId';
type Scope = Partial<Record<ScopeKey, string>>;
export interface ListTemplate {
  url: string;
  action: ListAction;
  pageSize: number;
  filters: boolean;
  scopes: Partial<Record<ScopeKey, 'top' | 'filters'>>;
}
export type ListFetch = (url: string, body: Row) => Promise<unknown>;

export function record(value: unknown): Row | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Row)
    : undefined;
}

export function authenticationExpired(value: unknown): boolean {
  const root = record(value);
  const error =
    record(record(root?.ResponseMetadata)?.Error) ?? record(root?.Error);
  return (
    typeof error?.Code === 'string' &&
    /unauth|login|expired|invalid.*token|session/i.test(error.Code)
  );
}

function id(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim() && value.length <= 200)
    return value;
  if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0)
    return String(value);
  return undefined;
}

function requiredId(row: Row, key: string): string {
  const value = id(row[key]);
  if (!value) throw new Error(`源站列表缺少 ${key} 身份，无法证明完整同步。`);
  return value;
}

function name(row: Row): string {
  const value = row.ProjectName ?? record(row.Meta)?.Name ?? row.Name;
  // Source names can contain formatting newlines/tabs; cloud names are single-line.
  const normalized =
    typeof value === 'string' ? value.replace(/[\t\r\n]+/g, ' ').trim() : '';
  if (
    !normalized ||
    normalized.length > 200 ||
    [...normalized].some((char) => {
      const code = char.charCodeAt(0);
      return code < 32 || (code >= 127 && code <= 159);
    })
  ) {
    throw new Error('源站列表名称结构不受支持，未同步任何部分结果。');
  }
  return normalized;
}

export function accessibleTeamIds(value: unknown): string[] {
  const root = record(value);
  if (record(root?.ResponseMetadata)?.Error || root?.Error)
    throw new Error('无法获取火山账号的可访问范围，请检查登录状态。');
  const teams = record(record(root?.Result)?.MemDescription)?.Teams;
  if (!Array.isArray(teams) || teams.length > 10000)
    throw new Error('火山账号成员信息缺少完整团队范围，未同步部分结果。');
  const ids = teams.map((team) => {
    const row = record(team);
    if (!row) throw new Error('火山账号团队范围结构不受支持。');
    return requiredId(row, 'TeamID');
  });
  if (new Set(ids).size !== ids.length)
    throw new Error('火山账号团队范围出现重复身份。');
  return ids;
}

export function recognizedListEndpoint(
  url: string,
  method: string,
): { url: string; action: ListAction } | undefined {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return undefined;
  }
  const match =
    /^\/api\/top\/dramart\/cn-beijing\/\d{4}-\d{2}-\d{2}\/(ListTeams|ListProjects|ListScripts)$/.exec(
      parsed.pathname,
    );
  if (parsed.origin !== SOURCE_ORIGIN || method !== 'POST' || !match)
    return undefined;
  return {
    url: `${parsed.origin}${parsed.pathname}`,
    action: match[1] as ListAction,
  };
}

// Keep only the observed contract, never headers, signatures, search text or IDs.
export function learnListRequest(
  url: string,
  method: string,
  input: unknown,
): ListTemplate | undefined {
  const endpoint = recognizedListEndpoint(url, method);
  const body = record(input);
  if (!endpoint || !body) return undefined;
  if (
    !Number.isInteger(body.PageIndex) ||
    Number(body.PageIndex) < 1 ||
    !Number.isInteger(body.PageSize) ||
    Number(body.PageSize) < 1 ||
    Number(body.PageSize) > 1000
  )
    return undefined;
  if (
    Object.keys(body).some(
      (key) =>
        ![
          'PageIndex',
          'PageSize',
          'Filters',
          'TeamId',
          'ProjectId',
          'ScriptId',
        ].includes(key),
    )
  )
    return undefined;
  const filters = body.Filters === undefined ? undefined : record(body.Filters);
  if (body.Filters !== undefined && !filters) return undefined;
  const scopes: ListTemplate['scopes'] = {};
  for (const key of ['TeamId', 'ProjectId', 'ScriptId'] as const) {
    if (body[key] !== undefined && !id(body[key])) return undefined;
    if (filters?.[key] !== undefined && !id(filters[key])) return undefined;
    if (body[key] !== undefined && filters?.[key] !== undefined)
      return undefined;
    if (body[key] !== undefined) scopes[key] = 'top';
    if (filters?.[key] !== undefined) scopes[key] = 'filters';
  }
  const action = endpoint.action;
  if (action === 'ListTeams' && Object.keys(scopes).length) return undefined;
  if (
    action === 'ListProjects' &&
    (!scopes.TeamId || scopes.ProjectId || scopes.ScriptId)
  )
    return undefined;
  if (
    action === 'ListScripts' &&
    (!scopes.TeamId || !scopes.ProjectId || scopes.ScriptId)
  )
    return undefined;
  return {
    url: endpoint.url,
    action,
    pageSize: Number(body.PageSize),
    filters: Boolean(filters),
    scopes,
  };
}

export function pageBody(
  template: ListTemplate,
  scope: Scope,
  page: number,
): Row {
  const body: Row = { PageIndex: page, PageSize: template.pageSize };
  const filters: Row = {};
  for (const key of ['TeamId', 'ProjectId', 'ScriptId'] as const) {
    const location = template.scopes[key];
    if (location && !scope[key])
      throw new Error(`缺少 ${key}，无法复用观察到的列表接口。`);
    if (scope[key] && !location)
      throw new Error(`观察到的 ${template.action} 不支持 ${key} 范围。`);
    if (location === 'top') body[key] = scope[key];
    if (location === 'filters') filters[key] = scope[key];
  }
  if (template.filters) body.Filters = filters;
  return body;
}

export function listResult(value: unknown): { items: Row[]; total: number } {
  const root = record(value);
  if (record(root?.ResponseMetadata)?.Error || root?.Error)
    throw new Error('源站列表请求失败，请检查登录状态和可访问权限。');
  const result = record(root?.Result);
  if (
    !result ||
    !Array.isArray(result.Items) ||
    !Number.isSafeInteger(result.Total) ||
    Number(result.Total) < 0 ||
    Number(result.Total) > 100000
  ) {
    throw new Error(
      '源站列表缺少受支持的 Result.Items / Total 总数，无法证明全量覆盖。',
    );
  }
  const items = result.Items.map((item) => {
    const row = record(item);
    if (!row) throw new Error('源站列表行结构不受支持。');
    return row;
  });
  return { items, total: Number(result.Total) };
}

export async function readAllPages(
  template: ListTemplate,
  scope: Scope,
  fetcher: ListFetch,
  identity: string,
): Promise<Row[]> {
  const rows: Row[] = [];
  const ids = new Set<string>();
  let expected: number | undefined;
  for (let page = 1; page <= 1000; page++) {
    const { items, total } = listResult(
      await fetcher(template.url, pageBody(template, scope, page)),
    );
    if (expected !== undefined && expected !== total)
      throw new Error('源站列表在同步期间变化，请重试完整同步。');
    expected = total;
    if (items.length > template.pageSize)
      throw new Error('源站分页大小不符合观察到的约定。');
    for (const item of items) {
      const key = requiredId(item, identity);
      if (ids.has(key))
        throw new Error('源站分页出现重复身份，无法证明完整覆盖。');
      ids.add(key);
      rows.push(item);
    }
    if (rows.length > total)
      throw new Error('源站分页数量超过总数，未同步部分结果。');
    if (rows.length === total) return rows;
    if (!items.length) throw new Error('源站分页提前结束，结果不完整。');
  }
  throw new Error('源站列表超过分页安全上限，结果不完整。');
}

export function projectAssetNames(
  type: AssetType,
  items: Row[],
  skippedUnnamedVariant: () => void = () => {},
): SourceName[] {
  const key =
    type === 'CHARACTER'
      ? 'CharacterId'
      : type === 'SCENE'
        ? 'SceneId'
        : 'PropId';
  const names = new Map<string, SourceName>();
  const visit = (row: Row, inheritedParent?: string) => {
    const sourceAssetId = requiredId(row, key);
    const explicitParent =
      id(row[`Parent${key}`]) ?? id(row.ParentId) ?? id(row.SourceParentId);
    const sourceParentId = explicitParent ?? inheritedParent;
    if (sourceParentId === sourceAssetId)
      throw new Error('源站变体父级身份无效。');
    const rawName = record(row.Meta)?.Name ?? row.Name;
    if (sourceParentId && typeof rawName === 'string' && !rawName.trim()) {
      if (
        row.Variants !== undefined &&
        (!Array.isArray(row.Variants) || row.Variants.length)
      )
        throw new Error('源站变体层级不受支持。');
      skippedUnnamedVariant();
      return;
    }
    // Asset names become import filenames. Keep their literal spelling; Windows
    // filename validation runs before generation instead of silently renaming.
    if (
      typeof rawName !== 'string' ||
      !rawName.trim() ||
      rawName.length > 200 ||
      [...rawName].some((char) => {
        const code = char.charCodeAt(0);
        return (
          (code < 32 && !['\t', '\r', '\n'].includes(char)) ||
          (code >= 127 && code <= 159)
        );
      })
    )
      throw new Error('源站资产名称结构不受支持，未同步任何部分结果。');
    const value: SourceName = {
      sourceAssetId,
      ...(sourceParentId ? { sourceParentId } : {}),
      name: rawName,
      type,
    };
    const existing = names.get(sourceAssetId);
    if (existing && JSON.stringify(existing) !== JSON.stringify(value))
      throw new Error('源站资产身份冲突，未同步部分结果。');
    names.set(sourceAssetId, value);
    if (row.Variants !== undefined) {
      if (!Array.isArray(row.Variants))
        throw new Error('源站变体列表结构不受支持。');
      for (const variant of row.Variants) {
        const child = record(variant);
        if (!child) throw new Error('源站变体结构不受支持。');
        if (inheritedParent) throw new Error('源站变体层级不受支持。');
        visit(child, sourceAssetId);
      }
    }
  };
  for (const item of items) visit(item);
  return [...names.values()];
}

export async function collectAllProjects(
  templates: ListTemplate[],
  fetcher: ListFetch,
  discovered: (count: number) => void = () => {},
  skippedUnnamedVariant: () => void = () => {},
): Promise<SourceCollection[]> {
  const projectTemplate =
    templates.find((item) => item.action === 'ListProjects') ??
    ({
      url: `${ASSET_BASE}ListProjects`,
      action: 'ListProjects',
      pageSize: 100,
      filters: true,
      scopes: { TeamId: 'top' },
    } satisfies ListTemplate);
  const apiBase = projectTemplate.url.slice(0, -'ListProjects'.length);
  const scriptTemplate =
    templates.find((item) => item.action === 'ListScripts') ??
    ({
      url: `${apiBase}ListScripts`,
      action: 'ListScripts',
      pageSize: 100,
      filters: true,
      scopes: { TeamId: 'top', ProjectId: 'filters' },
    } satisfies ListTemplate);
  // The live website obtains all accessible memberships from DescribeUser.
  // Refresh this on every sync; the currently selected source team is not the scope.
  const teams = accessibleTeamIds(await fetcher(`${apiBase}DescribeUser`, {}));
  const projects: SourceCollection[] = [];
  let projectCount = 0;
  for (const sourceTeamId of teams) {
    const rows = await readAllPages(
      projectTemplate,
      { TeamId: sourceTeamId },
      fetcher,
      'ProjectId',
    );
    for (const row of rows) {
      const sourceProjectId = requiredId(row, 'ProjectId');
      if (row.TeamId !== undefined && id(row.TeamId) !== sourceTeamId)
        throw new Error('项目所属团队与列表范围不一致。');
      const projectName = name(row);
      const scriptRows = await readAllPages(
        scriptTemplate,
        { TeamId: sourceTeamId, ProjectId: sourceProjectId },
        fetcher,
        'ScriptId',
      );
      if (!scriptRows.length)
        throw new Error('项目没有可验证的 ScriptId，无法声明完整同步。');
      const scripts = scriptRows.map((script) => {
        if (
          script.ProjectId !== undefined &&
          id(script.ProjectId) !== sourceProjectId
        )
          throw new Error('剧本所属项目与列表范围不一致。');
        if (script.TeamId !== undefined && id(script.TeamId) !== sourceTeamId)
          throw new Error('剧本所属团队与列表范围不一致。');
        return requiredId(script, 'ScriptId');
      });
      for (const sourceScriptId of scripts)
        projects.push({
          sourceTeamId,
          sourceProjectId,
          sourceScriptId,
          name: projectName,
          assets: [],
        });
      if (projects.length > 10000)
        throw new Error('源站项目超过安全上限，无法完成完整同步。');
      discovered(++projectCount);
    }
  }
  for (const project of projects) {
    for (const [type, action, identity] of [
      ['CHARACTER', 'ListCharacters', 'CharacterId'],
      ['SCENE', 'ListScenes', 'SceneId'],
      ['PROP', 'ListProps', 'PropId'],
    ] as const) {
      const template: ListTemplate = {
        url: `${apiBase}${action}`,
        action: 'ListProjects',
        pageSize: 1000,
        filters: true,
        scopes: { TeamId: 'top', ScriptId: 'filters', ProjectId: 'filters' },
      };
      const rows = await readAllPages(
        template,
        {
          TeamId: project.sourceTeamId,
          ProjectId: project.sourceProjectId,
          ScriptId: project.sourceScriptId,
        },
        fetcher,
        identity,
      );
      project.assets.push(
        ...projectAssetNames(type, rows, skippedUnnamedVariant),
      );
    }
  }
  return projects;
}
