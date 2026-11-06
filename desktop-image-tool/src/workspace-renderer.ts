import type { AiMatchReview } from './ai-match-types';
import type {
  Creation,
  Draft,
  Format,
  Quality,
  Result,
  Settings,
  StudioAPI,
} from './shared';
import type {
  Candidate,
  LoginStatus,
  SourceAsset,
  SourceProject,
  WorkspaceAPI,
  WorkspaceState,
} from './workspace-shared';
import {
  aiChoiceKey,
  groupProjects,
  initialAiChoices,
  queueCategory,
  targetOptions,
} from './workspace-view.js';

declare global {
  interface Window {
    workspace: WorkspaceAPI;
    studio: StudioAPI;
  }
}
interface Hooks {
  notify(message: string): void;
  settings(): Settings;
  parameters(): Draft;
  openSettings(): void;
  merge(records: Creation[]): void;
}
const typeNames: Record<string, string> = {
  CHARACTER: '角色',
  SCENE: '场景',
  PROP: '道具',
};
const statuses: Record<string, string> = {
  MATCHED: '已匹配',
  UNMATCHED: '未匹配',
  CONFLICT: '名称冲突',
  NO_PROMPT: '缺少提示词',
  UNBOUND: '未绑定项目',
  INACTIVE: '已失效',
  REVIEW: '需复核',
};
const taskStatuses: Record<Creation['status'], string> = {
  queued: '等待中',
  running: '生成中',
  succeeded: '已完成',
  failed: '失败',
  stopped: '已停止等待',
  interrupted: '上次中断',
  cancelled: '已取消',
};
function get<T extends HTMLElement = HTMLElement>(id: string) {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing element: ${id}`);
  return element as T;
}
function unwrap<T>(result: Result<T>): T {
  if (!result.ok) throw new Error(result.error);
  return result.value;
}
function node<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  text = '',
  className = '',
) {
  const element = document.createElement(tag);
  element.textContent = text;
  element.className = className;
  return element;
}
function option(value: string, name: string) {
  const item = node('option', name);
  item.value = value;
  return item;
}

export function initializeWorkspace(hooks: Hooks) {
  const api = window.workspace;
  let state: WorkspaceState | undefined;
  let projectId: number | undefined;
  let assets: SourceAsset[] = [];
  const selected = new Set<number>();
  let records: Creation[] = [];
  let candidates: Candidate[] = [];
  let resolving: SourceAsset | undefined;
  let loadVersion = 0;
  let currentView = 'assets';
  let batchIds: number[] = [];
  let draftProjectId: number | undefined;
  let assetType = '';
  let queueFilter = 'all';
  let aiReview: AiMatchReview | undefined;
  let aiChoices = new Map<number, string>();
  let aiContext = '';
  let aiVersion = 0;
  let aiBusy = false;
  let aiApplying = false;
  let bindingTargetId: number | undefined;
  let bindingContext = '';
  let bindingBusy = false;
  let assetsLoading = false;
  let resolvingContext = '';
  let batchContext = '';
  function context() {
    return `${state?.cloudUrl}|${state?.tenantId}|${projectId}|${loadVersion}`;
  }
  function projectContext() {
    return `${state?.cloudUrl}|${state?.tenantId}|${projectId}`;
  }
  function selectionKey() {
    return `antv-source-selection:${state?.cloudUrl}:${state?.tenantId}`;
  }
  function rememberSelection() {
    try {
      if (projectId && state?.tenantId)
        localStorage.setItem(selectionKey(), String(projectId));
    } catch {
      /* Selection is optional local convenience. */
    }
  }
  function assertContext(value: string) {
    if (context() !== value)
      throw new Error('项目或团队选择已改变，请重新操作。');
  }

  function error(message = '') {
    get('workspace-error').textContent = message;
    get('workspace-error').hidden = !message;
  }
  async function run(work: () => void | Promise<void>) {
    try {
      error();
      await work();
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : '操作失败。';
      error(message);
      hooks.notify(message);
    }
  }
  function button(
    label: string,
    work: () => void | Promise<void>,
    className = 'text-button',
  ) {
    const element = node('button', label, className);
    element.type = 'button';
    element.onclick = () => {
      element.disabled = true;
      void run(work).finally(() => {
        element.disabled = false;
      });
    };
    return element;
  }
  function view(name: string) {
    currentView = name;
    for (const id of ['assets', 'create', 'queue'])
      get(`${id}-view`).hidden = id !== name;
    for (const item of document.querySelectorAll<HTMLButtonElement>(
      '[data-view]',
    ))
      item.setAttribute('aria-pressed', String(item.dataset.view === name));
  }
  for (const item of document.querySelectorAll<HTMLButtonElement>(
    '[data-view]',
  ))
    item.onclick = () => view(item.dataset.view ?? 'assets');
  for (const item of document.querySelectorAll<HTMLButtonElement>(
    '[data-close]',
  ))
    item.onclick = () => {
      if (item.dataset.close === 'binding-dialog' && bindingBusy) return;
      if (item.dataset.close === 'ai-match-dialog' && aiApplying) return;
      if (item.dataset.close === 'ai-match-dialog') invalidateAi();
      get<HTMLDialogElement>(item.dataset.close ?? '').close();
    };
  get<HTMLDialogElement>('binding-dialog').oncancel = (event) => {
    if (bindingBusy) event.preventDefault();
  };
  function openConnection() {
    get('cloud-url').textContent = state?.cloudUrl ?? '';
    get<HTMLInputElement>('auto-sync').value = String(
      state?.autoSyncMinutes ?? 0,
    );
    get<HTMLDialogElement>('connection-dialog').showModal();
  }
  get('connection-open').onclick = openConnection;
  get<HTMLFormElement>('connection-form').onsubmit = (event) => {
    event.preventDefault();
    void run(async () => {
      apply(
        unwrap(
          await api.configure({
            autoSyncMinutes: Number(get<HTMLInputElement>('auto-sync').value),
          }),
        ),
      );
      hooks.notify('同步设置已保存。');
    });
  };
  get('login-cloud').onclick = () => {
    void run(async () => {
      unwrap(await api.loginCloud());
    });
  };
  get('login-source').onclick = () => {
    void run(async () => {
      unwrap(await api.loginSource());
    });
  };
  async function refresh() {
    apply(unwrap(await api.state(true)));
  }
  get('verify-cloud').onclick = () => {
    void run(refresh);
  };
  get('verify-source').onclick = () => {
    void run(async () => {
      apply(unwrap(await api.checkSource()));
      hooks.notify('火山剧创登录状态已验证。');
    });
  };
  get('refresh-cloud').onclick = () => {
    void run(async () => {
      await refresh();
      await loadAssets();
    });
  };
  get<HTMLSelectElement>('team-select').onchange = () => {
    const id = Number(get<HTMLSelectElement>('team-select').value);
    if (!id) return;
    void run(async () => {
      if (state?.queue.running.length || state?.queue.waiting.length)
        unwrap(await api.queue({ action: 'pause' }));
      apply(unwrap(await api.selectTeam(id)));
      hooks.notify('团队已切换；已有任务保留原归属，队列可手动继续。');
    });
  };
  get('sync-source').onclick = () => {
    void run(async () => {
      apply(unwrap(await api.sync()));
      await loadAssets();
      hooks.notify('全部项目资产名称已同步。');
    });
  };
  get('bind-project').onclick = () => {
    const project = currentProject();
    if (!project || bindingBusy) return;
    bindingContext = projectContext();
    bindingTargetId = project.targetProjectId ?? undefined;
    get<HTMLInputElement>('target-search').value = '';
    get('binding-title').textContent = project.targetProjectId
      ? '更换剧智创项目'
      : '绑定剧智创项目';
    get('binding-source').textContent =
      `火山剧创 · ${project.name} · ${scopeName(project)}`;
    get('binding-current').textContent =
      `当前剧智创团队：${state?.teamName ?? ''} · 当前绑定：${project.targetProjectName ?? '未绑定'}`;
    get('binding-error').hidden = true;
    renderBindingOptions();
    get<HTMLDialogElement>('binding-dialog').showModal();
    get('target-search').focus();
  };
  get('target-search').oninput = renderBindingOptions;
  get('binding-submit').onclick = () => {
    if (
      bindingBusy ||
      !projectId ||
      !bindingTargetId ||
      projectContext() !== bindingContext
    )
      return;
    const sourceId = projectId;
    const targetId = bindingTargetId;
    const expected = bindingContext;
    bindingBusy = true;
    renderBindingOptions();
    renderProjects();
    void run(async () => {
      let saved = false;
      try {
        const next = unwrap(
          await api.bind({ id: sourceId, projectId: targetId }),
        );
        saved = true;
        if (projectContext() !== expected) return;
        apply(next);
        const rows = unwrap(await api.match(sourceId));
        if (projectContext() !== expected) return;
        // Any older asset load must not replace this matching result.
        loadVersion++;
        assetsLoading = false;
        assets = rows;
        selected.clear();
        hooks.notify(
          `绑定已保存，可生成 ${assets.filter(ready).length} 个资产。`,
        );
        get<HTMLDialogElement>('binding-dialog').close();
        renderAssets();
        hooks.notify('绑定与名称匹配已完成。');
      } catch (failure) {
        if (projectContext() !== expected) return;
        const reason =
          failure instanceof Error ? failure.message : '操作失败。';
        const message = saved
          ? `绑定已保存，但名称匹配失败：${reason}。可关闭此窗口后点击“按名称匹配”重试。`
          : `绑定未保存：${reason}`;
        get('binding-error').textContent = message;
        get('binding-error').hidden = false;
        if (saved) {
          await loadAssets();
        }
        hooks.notify(message);
      } finally {
        bindingBusy = false;
        renderBindingOptions();
        renderProjects();
      }
    });
  };
  get('match-assets').onclick = () => {
    void run(async () => {
      if (!projectId) throw new Error('请先选择项目。');
      const expected = context();
      const rows = unwrap(await api.match(projectId));
      assertContext(expected);
      assets = rows;
      selected.clear();
      hooks.notify(
        `名称匹配完成，可生成 ${assets.filter(ready).length} 个资产。`,
      );
      renderAssets();
    });
  };
  function currentProject(): SourceProject | undefined {
    return state?.projects.find((project) => project.id === projectId);
  }
  function scopeName(project: SourceProject) {
    return project.sourceScriptId
      ? `剧本 ${project.sourceScriptId}`
      : '项目资产清单';
  }
  function renderSourceOptions() {
    const groups = groupProjects(
      state?.projects ?? [],
      get<HTMLInputElement>('project-search').value,
      get<HTMLSelectElement>('project-binding-filter').value,
    );
    get('project-list').replaceChildren(
      ...groups.map((group) => {
        const section = node('section', '', 'source-group');
        section.append(
          node('strong', group.name),
          node(
            'small',
            `来源团队 ${group.scopes[0].sourceTeamId} · 项目 ${group.scopes[0].sourceProjectId} · 已绑定 ${group.bound}/${group.scopes.length}`,
            'cell-secondary',
          ),
        );
        section.append(
          ...group.scopes.map((project) => {
            const item = button(
              '',
              () => {
                draftProjectId = project.id;
                renderSourceOptions();
                get('project-list')
                  .querySelector<HTMLButtonElement>(
                    `[data-source-project-id="${project.id}"]`,
                  )
                  ?.focus();
              },
              `project-item${project.id === draftProjectId ? ' selected' : ''}`,
            );
            item.dataset.sourceProjectId = String(project.id);
            item.setAttribute(
              'aria-pressed',
              String(project.id === draftProjectId),
            );
            item.append(
              node('strong', scopeName(project)),
              node(
                'small',
                project.targetProjectName
                  ? `→ ${project.targetProjectName} · #${project.targetProjectId}`
                  : '尚未绑定剧智创项目',
              ),
            );
            return item;
          }),
        );
        return section;
      }),
    );
    get('project-empty').hidden = groups.length > 0;
    get<HTMLButtonElement>('source-project-confirm').disabled =
      !state?.projects.some((row) => row.id === draftProjectId && row.active);
  }
  get('source-project-open').onclick = () => {
    draftProjectId = projectId;
    get<HTMLInputElement>('project-search').value = '';
    get<HTMLSelectElement>('project-binding-filter').value = 'all';
    renderSourceOptions();
    get<HTMLDialogElement>('source-project-dialog').showModal();
    get('project-search').focus();
  };
  get('project-search').oninput = renderSourceOptions;
  get('project-binding-filter').onchange = renderSourceOptions;
  get('clear-project-filters').onclick = () => {
    get<HTMLInputElement>('project-search').value = '';
    get<HTMLSelectElement>('project-binding-filter').value = 'all';
    renderSourceOptions();
  };
  get('source-project-confirm').onclick = () => {
    if (!state?.projects.some((row) => row.id === draftProjectId && row.active))
      return;
    get<HTMLDialogElement>('source-project-dialog').close();
    if (projectId === draftProjectId) return;
    invalidateAi();
    projectId = draftProjectId;
    rememberSelection();
    for (const id of [
      'resolve-dialog',
      'prompt-dialog',
      'batch-dialog',
      'binding-dialog',
    ])
      get<HTMLDialogElement>(id).close();
    renderProjects();
    void run(loadAssets);
  };
  function renderBindingOptions() {
    const project = currentProject();
    const rows = targetOptions(
      state?.targets ?? [],
      project?.name ?? '',
      get<HTMLInputElement>('target-search').value,
    );
    get('target-count').textContent = rows.length
      ? `找到 ${rows.length} 个项目 · 同名项目优先，需手动确认`
      : '没有找到项目，请修改搜索词或到账号与同步中刷新剧智创连接。';
    get('target-list').replaceChildren(
      ...rows.map((target) => {
        const item = button(
          '',
          () => {
            bindingTargetId = target.id;
            renderBindingOptions();
            get('target-list')
              .querySelector<HTMLButtonElement>(
                `[data-target-id="${target.id}"]`,
              )
              ?.focus();
          },
          `target-item${bindingTargetId === target.id ? ' selected' : ''}`,
        );
        item.dataset.targetId = String(target.id);
        item.setAttribute(
          'aria-pressed',
          String(bindingTargetId === target.id),
        );
        item.disabled = bindingBusy;
        item.append(
          node('strong', target.name),
          node(
            'small',
            `${target.name.trim() === project?.name.trim() ? '同名候选 · ' : ''}项目编号 ${target.id}`,
          ),
        );
        return item;
      }),
    );
    const target = state?.targets.find((row) => row.id === bindingTargetId);
    get('binding-preview').textContent = target
      ? `${project?.name}（${project ? scopeName(project) : ''}） → ${target.name}（${state?.teamName}）`
      : '请选择对应的剧智创项目；确认前不会修改绑定。';
    const replacing = Boolean(
      project?.targetProjectId &&
        target &&
        project.targetProjectId !== target.id,
    );
    get('binding-warning').hidden = !replacing;
    get<HTMLButtonElement>('binding-submit').disabled =
      bindingBusy || !target || projectContext() !== bindingContext;
    get('binding-submit').textContent = bindingBusy
      ? '正在保存并匹配…'
      : replacing
        ? '更换绑定并重新匹配'
        : '确认绑定并匹配';
    get<HTMLInputElement>('target-search').disabled = bindingBusy;
    for (const item of document.querySelectorAll<HTMLButtonElement>(
      '[data-close="binding-dialog"]',
    ))
      item.disabled = bindingBusy;
    get<HTMLSelectElement>('team-select').disabled =
      bindingBusy || Boolean(state?.source.syncing);
  }
  function renderProjects() {
    const groups = groupProjects(state?.projects ?? []);
    get('project-count').textContent =
      `已同步 ${groups.length} 个项目 · ${groups.reduce((count, group) => count + group.scopes.length, 0)} 份剧本清单`;
    const project = currentProject();
    get('asset-project-name').textContent = project?.name ?? '选择来源项目';
    get('binding-label').textContent = project
      ? `${scopeName(project)} · 来源团队 ${project.sourceTeamId}`
      : '选择已同步项目和剧本清单';
    get('binding-summary').textContent =
      project?.targetProjectName ?? '选择对应项目';
    get('binding-help').textContent = project?.targetProjectId
      ? `${state?.teamName ?? ''} · 项目 #${project.targetProjectId}`
      : project
        ? '当前剧本清单尚未绑定'
        : '先选择来源项目';
    get<HTMLButtonElement>('match-assets').disabled =
      !project?.targetProjectId || bindingBusy || assetsLoading;
    get<HTMLButtonElement>('ai-match-open').disabled =
      !project?.targetProjectId || bindingBusy || assetsLoading;
    get<HTMLButtonElement>('bind-project').disabled = !project || bindingBusy;
    get<HTMLButtonElement>('source-project-open').disabled = bindingBusy;
    if (get<HTMLDialogElement>('source-project-dialog').open)
      renderSourceOptions();
  }
  async function loadAssets() {
    const version = ++loadVersion;
    const id = projectId;
    selected.clear();
    assets = [];
    assetsLoading = Boolean(id);
    renderProjects();
    renderAssets();
    if (!id) return;
    try {
      const data = unwrap(await api.assets(id));
      if (version !== loadVersion || id !== projectId) return;
      assets = data;
    } finally {
      if (version === loadVersion) {
        assetsLoading = false;
        renderProjects();
        renderAssets();
      }
    }
  }
  function renderLogin(prefix: string, status: LoginStatus) {
    const labels: Record<LoginStatus['phase'], string> = {
      unknown: '待验证',
      checking: '验证中',
      connected: '已连接',
      signedOut: '未登录',
      error: '连接异常',
    };
    get(`${prefix}-session-label`).textContent = labels[status.phase];
    get(`${prefix}-session-label`).dataset.phase = status.phase;
    get(`${prefix}-session-detail`).textContent =
      status.message ??
      (status.phase === 'connected'
        ? '登录状态已验证。'
        : '完成登录后重新验证。');
    get<HTMLButtonElement>(`verify-${prefix}`).disabled =
      status.phase === 'checking';
    get<HTMLButtonElement>(`login-${prefix}`).disabled = Boolean(
      status.loginOpen,
    );
  }
  function apply(next: WorkspaceState) {
    const previousProject = currentProject();
    const changedTeam =
      state?.tenantId !== next.tenantId || state?.cloudUrl !== next.cloudUrl;
    state = next;
    renderLogin('cloud', next.cloudConnection);
    renderLogin('source', next.source.connection);
    const connected =
      next.cloudConnection.phase === 'connected' &&
      next.source.connection.phase === 'connected';
    get('account-summary').textContent = connected
      ? '账号已连接'
      : next.cloudConnection.phase === 'checking' ||
          next.source.connection.phase === 'checking'
        ? '正在验证账号'
        : '账号待连接';
    get('account-dot').dataset.connected = String(connected);
    get('connection-open').title = next.teamName
      ? `当前剧智创团队：${next.teamName}`
      : '账号与同步';
    const teamSelect = get<HTMLSelectElement>('team-select');
    teamSelect.replaceChildren(
      option('', next.authenticated ? '请选择剧智创团队' : '请先登录'),
      ...next.teams.map((team) => option(String(team.id), team.name)),
    );
    teamSelect.value = String(next.tenantId ?? '');
    get('cloud-status').textContent =
      next.error ??
      (next.authenticated
        ? next.teams.length
          ? '仅展示当前账号有权限的团队。'
          : '当前账号暂无可用团队。'
        : '团队列表由登录后的实际权限返回。');
    teamSelect.disabled = next.source.syncing;
    const sync = next.syncStatus;
    const messages = [
      sync.phase === 'collecting'
        ? '正在核对全部来源项目与资产名称，可继续创建图片任务。'
        : sync.phase === 'uploading'
          ? `正在上传 ${sync.uploadedScopes}/${sync.totalScopes} 份资产清单 · ${sync.failedScopes} 份失败`
          : sync.phase === 'failed'
            ? (sync.error ?? '同步失败，请重试。')
            : sync.phase === 'complete'
              ? `同步完成：${sync.uploadedProjects} 个项目 · ${sync.uploadedScopes} 份资产清单`
              : '点击同步全部项目名称；将自动检查已保存的火山登录状态。',
    ];
    if (sync.lastSuccessAt)
      messages.push(
        `最近完整同步 ${new Date(sync.lastSuccessAt).toLocaleString()}`,
      );
    if (sync.automaticPaused)
      messages.push('自动同步已暂停；手动同步成功或保存同步设置后恢复。');
    else if (sync.nextRunAt)
      messages.push(
        `下次自动同步 ${new Date(sync.nextRunAt).toLocaleTimeString()}`,
      );
    if (next.source.skippedUnnamedVariants)
      messages.push(
        `${next.source.skippedUnnamedVariants} 个空白名称变体未纳入匹配`,
      );
    get('source-status').textContent = messages.join(' · ');
    get<HTMLButtonElement>('sync-source').disabled =
      !next.tenantId || next.source.syncing;
    if (next.error) error(next.error);
    if (
      changedTeam ||
      !next.projects.some(
        (project) => project.id === projectId && project.active,
      )
    ) {
      invalidateAi();
      let remembered: number | undefined;
      try {
        remembered = Number(localStorage.getItem(selectionKey()));
      } catch {
        /* Use first available project. */
      }
      projectId =
        next.projects.find(
          (project) => project.active && project.id === remembered,
        )?.id ?? next.projects.find((project) => project.active)?.id;
      rememberSelection();
      get<HTMLDialogElement>('binding-dialog').close();
      get<HTMLDialogElement>('source-project-dialog').close();
      assets = [];
      selected.clear();
      get<HTMLDialogElement>('batch-dialog').close();
      get<HTMLDialogElement>('resolve-dialog').close();
      get<HTMLDialogElement>('prompt-dialog').close();
      void run(loadAssets);
    } else if (
      previousProject?.syncedAt !== currentProject()?.syncedAt ||
      previousProject?.targetProjectId !== currentProject()?.targetProjectId
    ) {
      invalidateAi();
      void run(loadAssets);
    }
    renderProjects();
    renderAssets();
    renderQueue();
  }
  function visibleAssets() {
    const search = get<HTMLInputElement>('asset-search')
      .value.trim()
      .toLocaleLowerCase();
    const type = assetType;
    const status = get<HTMLSelectElement>('asset-status').value;
    return assets.filter(
      (asset) =>
        (!search || asset.name.toLocaleLowerCase().includes(search)) &&
        (!type || asset.type === type) &&
        (!status || asset.matchStatus === status),
    );
  }
  function ready(asset: SourceAsset) {
    return asset.active && asset.matchStatus === 'MATCHED' && asset.hasPrompt;
  }
  function selectionChanged() {
    get<HTMLButtonElement>('batch-open').disabled = !selected.size;
    get('batch-open').textContent = `批量生图 · ${selected.size}`;
    const selectable = visibleAssets().filter(ready);
    const all = get<HTMLInputElement>('select-assets');
    all.checked =
      selectable.length > 0 &&
      selectable.every((asset) => selected.has(asset.id));
    all.indeterminate =
      !all.checked && selectable.some((asset) => selected.has(asset.id));
  }
  function renderAssets() {
    const visible = visibleAssets();
    get('asset-rows').replaceChildren(
      ...visible.map((asset) => {
        const row = node('tr');
        const checkCell = node('td');
        const check = node('input');
        check.type = 'checkbox';
        check.checked = selected.has(asset.id);
        check.disabled = !ready(asset);
        check.setAttribute('aria-label', `选择 ${asset.name}`);
        check.onchange = () => {
          if (check.checked) selected.add(asset.id);
          else selected.delete(asset.id);
          selectionChanged();
        };
        checkCell.append(check);
        const name = node('td');
        name.append(node('strong', asset.name));
        if (asset.sourceParentId)
          name.append(
            node(
              'small',
              `变体 · ${assets.find((item) => item.sourceAssetId === asset.sourceParentId)?.name ?? asset.sourceParentId}`,
              'cell-secondary',
            ),
          );
        const status = node('td');
        status.append(
          node(
            'span',
            statuses[asset.matchStatus] ?? asset.matchStatus,
            `match-status ${asset.matchStatus.toLowerCase()}`,
          ),
        );
        const actions = node('td');
        if (ready(asset))
          actions.append(
            button('提示词', async () => {
              const expected = context();
              const prompts = unwrap(await api.prompts([asset.id]));
              assertContext(expected);
              const prompt = prompts[0];
              if (!prompt) throw new Error('提示词不可用，请刷新。');
              showPrompt(
                asset.name,
                prompt.prompt,
                `剧智创 · ${state?.teamName ?? ''} / ${prompt.projectName}`,
              );
            }),
          );
        if (asset.active && currentProject()?.targetProjectId)
          actions.append(button('选择匹配', () => resolve(asset)));
        if (
          asset.matchStatus === 'NO_PROMPT' &&
          currentProject()?.targetProjectId
        ) {
          const targetId = currentProject()?.targetProjectId as number;
          actions.append(
            button('维护提示词 ↗', async () => {
              unwrap(await api.openProject(targetId));
            }),
          );
        }
        row.append(
          checkCell,
          name,
          node('td', typeNames[asset.type]),
          node('td', asset.targetName ?? '—'),
          status,
          actions,
        );
        return row;
      }),
    );
    get('asset-empty').hidden = visible.length > 0;
    get('asset-empty').textContent = assetsLoading
      ? '正在读取资产名称…'
      : assets.length
        ? '没有符合筛选条件的资产。'
        : '暂无资产名称。登录火山剧创并同步全部项目后，绑定对应剧智创项目。';
    get('asset-summary').textContent =
      `共 ${assets.length} 个名称 · 可生成 ${assets.filter(ready).length} 个 · 当前显示 ${visible.length} 个。仅保存名称及匹配关系，不读取剧本原文。`;
    selectionChanged();
  }
  for (const item of document.querySelectorAll<HTMLButtonElement>(
    '[data-asset-type]',
  ))
    item.onclick = () => {
      assetType = item.dataset.assetType ?? '';
      for (const tab of document.querySelectorAll<HTMLButtonElement>(
        '[data-asset-type]',
      ))
        tab.setAttribute('aria-pressed', String(tab === item));
      renderAssets();
    };
  for (const id of ['asset-search', 'asset-status'])
    get(id).addEventListener(
      id === 'asset-search' ? 'input' : 'change',
      renderAssets,
    );
  get<HTMLInputElement>('select-assets').onchange = () => {
    const checked = get<HTMLInputElement>('select-assets').checked;
    for (const asset of visibleAssets().filter(ready)) {
      if (checked) selected.add(asset.id);
      else selected.delete(asset.id);
    }
    renderAssets();
  };
  function showPrompt(name: string, prompt: string, origin: string) {
    get('prompt-title').textContent = name;
    get('prompt-origin').textContent = origin;
    get('asset-prompt').textContent = prompt;
    get<HTMLDialogElement>('prompt-dialog').showModal();
  }
  async function resolve(asset: SourceAsset) {
    if (!projectId) return;
    const expected = context();
    const rows = unwrap(
      await api.candidates({
        id: projectId,
        type: asset.type,
        sourceParentId: asset.sourceParentId ?? undefined,
      }),
    );
    assertContext(expected);
    candidates = rows;
    resolvingContext = expected;
    resolving = asset;
    get('resolve-source').textContent =
      `火山剧创 · ${typeNames[asset.type]} · ${asset.name}`;
    get<HTMLSelectElement>('match-candidate').replaceChildren(
      option('', candidates.length ? '请选择对应资产' : '没有可匹配的同类资产'),
      ...candidates.map((candidate, index) =>
        option(
          String(index),
          `${candidate.parentName ? `${candidate.parentName} / ` : ''}${candidate.name}${candidate.hasPrompt ? '' : '（缺少提示词）'}`,
        ),
      ),
    );
    get<HTMLDialogElement>('resolve-dialog').showModal();
  }
  get('resolve-save').onclick = () => {
    void run(async () => {
      const value = get<HTMLSelectElement>('match-candidate').value;
      const candidate = value === '' ? undefined : candidates[Number(value)];
      if (!resolving || !candidate) throw new Error('请选择一个匹配资产。');
      assertContext(resolvingContext);
      unwrap(
        await api.resolve({
          id: resolving.id,
          assetId: candidate.assetId,
          variantId: candidate.variantId ?? undefined,
        }),
      );
      assertContext(resolvingContext);
      get<HTMLDialogElement>('resolve-dialog').close();
      await loadAssets();
    });
  };
  function invalidateAi() {
    aiVersion++;
    const reviewId = aiReview?.reviewId;
    const shouldCancel = aiBusy || Boolean(aiReview);
    aiReview = undefined;
    aiChoices.clear();
    aiBusy = false;
    if (shouldCancel) void api.aiCancel(reviewId).catch(() => undefined);
    get<HTMLDialogElement>('ai-match-dialog').close();
  }
  function aiError(message = '') {
    get('ai-error').textContent = message;
    get('ai-error').hidden = !message;
  }
  function renderAiSelection() {
    get('ai-selection').textContent = `已选 ${aiChoices.size} 项 · 确认后生效`;
    get<HTMLButtonElement>('ai-apply').disabled = !aiChoices.size || aiApplying;
    get('ai-apply').textContent = aiApplying
      ? '正在保存…'
      : `确认应用 ${aiChoices.size} 项`;
  }
  function renderAiReview() {
    get('ai-results').replaceChildren(
      ...(aiReview?.suggestions ?? []).map((suggestion) => {
        const row = node('div', '', 'ai-review-row');
        row.dataset.aiSourceId = String(suggestion.sourceAssetId);
        const check = node('input');
        check.type = 'checkbox';
        check.setAttribute(
          'aria-label',
          `采用 ${suggestion.sourceName} 的匹配建议`,
        );
        const eligible =
          suggestion.status !== 'parentRequired' &&
          suggestion.status !== 'unmatched';
        check.disabled =
          !eligible || !aiChoices.has(suggestion.sourceAssetId) || aiApplying;
        check.checked = aiChoices.has(suggestion.sourceAssetId);
        const source = node('div');
        source.append(
          node('strong', suggestion.sourceName),
          node(
            'small',
            `${typeNames[suggestion.type]}${suggestion.sourceParentName ? ` · 主体 ${suggestion.sourceParentName}` : ''}`,
            'cell-secondary',
          ),
        );
        const target = node('div', '', 'ai-review-target');
        if (eligible && suggestion.options.length) {
          const select = node('select');
          select.setAttribute(
            'aria-label',
            `${suggestion.sourceName}的对应资产`,
          );
          select.append(
            option(
              '',
              suggestion.status === 'ambiguous' || suggestion.options.length > 1
                ? '多个候选，请明确选择'
                : '不采用本条建议',
            ),
          );
          for (const candidate of suggestion.options) {
            const item = option(
              aiChoiceKey(candidate),
              `${candidate.parentName ? `${candidate.parentName} / ` : ''}${candidate.name} · 资产 #${candidate.assetId}${candidate.variantId !== null ? ` · 变体 #${candidate.variantId}` : ' · 主体'}${candidate.hasPrompt ? '' : '（缺少提示词）'}`,
            );
            item.disabled = !candidate.hasPrompt;
            select.append(item);
          }
          select.value = aiChoices.get(suggestion.sourceAssetId) ?? '';
          select.disabled = aiApplying;
          select.onchange = () => {
            if (select.value)
              aiChoices.set(suggestion.sourceAssetId, select.value);
            else aiChoices.delete(suggestion.sourceAssetId);
            renderAiReview();
            get('ai-results')
              .querySelector<HTMLSelectElement>(
                `[data-ai-source-id="${suggestion.sourceAssetId}"] select`,
              )
              ?.focus();
          };
          check.onchange = () => {
            aiChoices.delete(suggestion.sourceAssetId);
            renderAiReview();
            get('ai-results')
              .querySelector<HTMLSelectElement>(
                `[data-ai-source-id="${suggestion.sourceAssetId}"] select`,
              )
              ?.focus();
          };
          target.append(select);
        } else
          target.append(
            node(
              'span',
              suggestion.status === 'parentRequired'
                ? '先匹配主体，再重新分析本变体'
                : '未找到合适候选',
              'help',
            ),
          );
        target.append(node('p', suggestion.reason, 'help'));
        row.append(check, source, node('span', '→', 'project-arrow'), target);
        return row;
      }),
    );
    if (aiReview && !aiReview.suggestions.length)
      get('ai-results').append(
        node('p', '当前没有需要 AI 辅助匹配的资产。', 'panel-empty'),
      );
    renderAiSelection();
  }
  get<HTMLDialogElement>('ai-match-dialog').oncancel = (event) => {
    if (aiApplying) {
      event.preventDefault();
      return;
    }
    invalidateAi();
  };
  get('ai-match-open').onclick = () => {
    const project = currentProject();
    if (!project?.targetProjectId) return;
    invalidateAi();
    aiContext = projectContext();
    aiError();
    get('ai-context').textContent =
      `${project.name} · ${scopeName(project)} → ${project.targetProjectName} · #${project.targetProjectId} · ${state?.teamName ?? ''}`;
    get('ai-intro').hidden = false;
    get('ai-progress').hidden = true;
    get('ai-results').replaceChildren();
    get('ai-start').hidden = false;
    get<HTMLButtonElement>('ai-start').disabled = false;
    get('ai-apply').hidden = true;
    get('ai-selection').textContent = '已有匹配保留 · 确认前不修改匹配关系';
    get<HTMLDialogElement>('ai-match-dialog').showModal();
  };
  get('ai-model-settings').onclick = () => {
    invalidateAi();
    hooks.openSettings();
    get<HTMLDetailsElement>('ai-settings-details').open = true;
  };
  get('ai-start').onclick = () => {
    if (!projectId || aiBusy || projectContext() !== aiContext) return;
    const sourceId = projectId;
    const version = ++aiVersion;
    aiBusy = true;
    aiError();
    get<HTMLButtonElement>('ai-start').disabled = true;
    get('ai-progress').hidden = false;
    void (async () => {
      try {
        const review = unwrap(await api.aiMatch(sourceId));
        if (
          version !== aiVersion ||
          aiContext !== projectContext() ||
          !get<HTMLDialogElement>('ai-match-dialog').open
        ) {
          void api.aiCancel(review.reviewId).catch(() => undefined);
          return;
        }
        aiReview = review;
        aiChoices = initialAiChoices(review.suggestions);
        get('ai-intro').hidden = true;
        get('ai-start').hidden = true;
        get('ai-apply').hidden = false;
        renderAiReview();
        get('ai-results')
          .querySelector<HTMLSelectElement>('select:not(:disabled)')
          ?.focus();
      } catch (failure) {
        if (version === aiVersion)
          aiError(
            failure instanceof Error
              ? failure.message
              : 'AI 匹配失败，请重试。',
          );
      } finally {
        if (version === aiVersion) {
          aiBusy = false;
          get('ai-progress').hidden = true;
          get<HTMLButtonElement>('ai-start').disabled = false;
        }
      }
    })();
  };
  get('ai-apply').onclick = () => {
    if (
      !aiReview ||
      aiApplying ||
      !aiChoices.size ||
      projectContext() !== aiContext
    )
      return;
    const review = aiReview;
    const version = aiVersion;
    const choices = review.suggestions.flatMap((suggestion) => {
      const choice = suggestion.options.find(
        (item) =>
          item.hasPrompt &&
          aiChoiceKey(item) === aiChoices.get(suggestion.sourceAssetId),
      );
      return choice
        ? [
            {
              sourceAssetId: suggestion.sourceAssetId,
              assetId: choice.assetId,
              variantId: choice.variantId,
            },
          ]
        : [];
    });
    aiApplying = true;
    aiError();
    renderAiReview();
    for (const item of document.querySelectorAll<HTMLButtonElement>(
      '[data-close="ai-match-dialog"]',
    ))
      item.disabled = true;
    void (async () => {
      try {
        const result = unwrap(
          await api.aiApply({ reviewId: review.reviewId, choices }),
        );
        if (version !== aiVersion || projectContext() !== aiContext) return;
        aiReview = undefined;
        aiChoices.clear();
        get('ai-results').replaceChildren();
        get('ai-apply').hidden = true;
        get('ai-selection').textContent = `已保存 ${result.applied} 项`;
        const failures = result.failures.map(
          (item) =>
            `${review.suggestions.find((row) => row.sourceAssetId === item.sourceAssetId)?.sourceName ?? item.sourceAssetId}：${item.message}`,
        );
        try {
          await loadAssets();
        } catch (failure) {
          failures.push(
            `资产列表刷新失败：${failure instanceof Error ? failure.message : '请手动刷新'}`,
          );
        }
        if (version !== aiVersion || projectContext() !== aiContext) return;
        if (failures.length) {
          aiError(
            `已保存 ${result.applied} 项，${result.failures.length} 项未保存。${failures.join('；')}`,
          );
          hooks.notify(`已保存 ${result.applied} 项，请查看匹配结果明细。`);
        } else {
          get<HTMLDialogElement>('ai-match-dialog').close();
          hooks.notify(`已保存 ${result.applied} 项匹配。`);
        }
      } catch (failure) {
        if (version === aiVersion)
          aiError(
            failure instanceof Error
              ? failure.message
              : '保存失败，请重新核对。',
          );
      } finally {
        aiApplying = false;
        for (const item of document.querySelectorAll<HTMLButtonElement>(
          '[data-close="ai-match-dialog"]',
        ))
          item.disabled = false;
        if (aiReview) renderAiReview();
      }
    })();
  };
  get('batch-key').onclick = () => {
    get<HTMLDialogElement>('batch-dialog').close();
    hooks.openSettings();
  };
  get('batch-open').onclick = () => {
    void run(async () => {
      if (!selected.size || selected.size > 500)
        throw new Error('每批请选择 1～500 个可生成资产。');
      if (!hooks.settings().hasKey) {
        hooks.openSettings();
        return;
      }
      const expected = context();
      const ids = [...selected];
      const prompts = unwrap(await api.prompts(ids));
      assertContext(expected);
      batchContext = expected;
      batchIds = ids;
      if (prompts.length !== ids.length)
        throw new Error('部分提示词不可用，请刷新后重选。');
      const parameters = hooks.parameters();
      get<HTMLInputElement>('batch-model').value = parameters.model;
      get<HTMLSelectElement>('batch-size').value = parameters.size;
      get<HTMLSelectElement>('batch-quality').value = parameters.quality;
      get<HTMLSelectElement>('batch-format').value = parameters.format;
      get('batch-summary').textContent =
        `${state?.teamName ?? ''} / ${currentProject()?.name ?? ''} · ${batchIds.length} 个资产`;
      get('batch-destination').textContent =
        `保存至 ${hooks.settings().outputDirectory} / 团队 / 项目 / 类型 / 导入批次-批次编号。文件名完整使用火山剧创原名，仅添加格式扩展名；同名资产保存在独立批次子目录。`;
      get<HTMLDialogElement>('batch-dialog').showModal();
    });
  };
  get<HTMLFormElement>('batch-form').onsubmit = (event) => {
    event.preventDefault();
    const submit = get<HTMLButtonElement>('batch-submit');
    if (submit.disabled) return;
    submit.disabled = true;
    void run(async () => {
      assertContext(batchContext);
      const created = unwrap(
        await api.batch({
          assetIds: batchIds,
          model: get<HTMLInputElement>('batch-model').value.trim(),
          size: get<HTMLSelectElement>('batch-size').value,
          quality: get<HTMLSelectElement>('batch-quality').value as Quality,
          format: get<HTMLSelectElement>('batch-format').value as Format,
        }),
      );
      hooks.merge(created);
      selected.clear();
      renderAssets();
      get<HTMLDialogElement>('batch-dialog').close();
      view('queue');
      hooks.notify(`已加入 ${created.length} 个图片任务。`);
    }).finally(() => {
      submit.disabled = false;
    });
  };
  get('queue-pause').onclick = () => {
    void run(async () => {
      unwrap(await api.queue({ action: 'pause' }));
      await fetchState();
    });
  };
  get('queue-resume').onclick = () => {
    void run(async () => {
      unwrap(await api.queue({ action: 'resume' }));
      await fetchState();
    });
  };
  get<HTMLSelectElement>('queue-limit').onchange = () => {
    void run(async () => {
      unwrap(
        await api.queue({
          action: 'limit',
          limit: Number(get<HTMLSelectElement>('queue-limit').value),
        }),
      );
      await fetchState();
    });
  };
  for (const item of document.querySelectorAll<HTMLButtonElement>(
    '[data-queue-filter]',
  ))
    item.onclick = () => {
      queueFilter = item.dataset.queueFilter ?? 'all';
      renderQueue();
    };
  get('queue-folder').onclick = () => {
    void run(async () => {
      unwrap(await window.studio.openFolder());
    });
  };
  function renderQueue() {
    const queue = state?.queue;
    get('queue-count').textContent = String(
      (queue?.running.length ?? 0) + (queue?.waiting.length ?? 0),
    );
    get('queue-capacity').textContent =
      `生成中 ${queue?.running.length ?? 0} / ${queue?.limit ?? 5}`;
    for (const tab of document.querySelectorAll<HTMLButtonElement>(
      '[data-queue-filter]',
    )) {
      const filter = tab.dataset.queueFilter ?? 'all';
      const labels: Record<string, string> = {
        all: '全部',
        running: '进行中',
        waiting: '等待',
        done: '完成',
        failed: '失败',
      };
      const count = records.filter(
        (record) => filter === 'all' || queueCategory(record.status) === filter,
      ).length;
      tab.textContent = `${labels[filter]} ${count}`;
      tab.setAttribute('aria-pressed', String(queueFilter === filter));
    }
    const visible = records.filter(
      (record) =>
        queueFilter === 'all' || queueCategory(record.status) === queueFilter,
    );
    get('queue-summary').textContent =
      `${queue?.paused ? '已暂停派发' : '正在派发'} · 生成中 ${queue?.running.length ?? 0} / ${queue?.limit ?? 5} · 等待 ${queue?.waiting.length ?? 0} · 重启后未提交任务需手动继续。`;
    get<HTMLSelectElement>('queue-limit').value = String(queue?.limit ?? 5);
    get<HTMLButtonElement>('queue-pause').disabled = !queue || queue.paused;
    get<HTMLButtonElement>('queue-resume').disabled = !queue?.paused;
    get('queue-empty').hidden = visible.length > 0;
    get('queue-empty').textContent = records.length
      ? '暂无此状态的任务。'
      : '尚无图片任务。选择资产批量生图，或进入图片创作。';
    get('queue-rows').replaceChildren(
      ...[...visible]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((record) => {
          const row = node('article', '', 'queue-row');
          row.dataset.taskId = record.id;
          const identity = node('div', '', 'queue-identity');
          const thumbnail = node(
            'div',
            record.origin?.type
              ? (typeNames[record.origin.type] ?? '图')
              : '图',
            'queue-thumbnail',
          );
          if (record.imageCount) {
            const image = node('img');
            image.src = `antv-media://image/${encodeURIComponent(record.id)}/0?thumb=1`;
            image.alt =
              record.origin?.assetName ?? record.outputName ?? '生成图片';
            image.loading = 'lazy';
            image.onerror = () => {
              thumbnail.textContent = record.origin?.type
                ? (typeNames[record.origin.type] ?? '图')
                : '图';
            };
            thumbnail.replaceChildren(image);
          }
          const text = node('div');
          text.append(
            node(
              'strong',
              record.origin
                ? record.origin.assetName
                : record.outputName || '自由创作',
            ),
            node(
              'small',
              record.origin
                ? `${record.origin.teamName} / ${record.origin.projectName} · ${typeNames[record.origin.type] ?? record.origin.type}`
                : '个人图片创作',
              'cell-secondary',
            ),
          );
          if (record.error || record.saveError)
            text.append(
              node(
                'small',
                record.saveError || record.error || '',
                'record-error',
              ),
            );
          text.append(
            node(
              'small',
              `${record.model} · ${record.size} · ${new Date(record.createdAt).toLocaleString()}`,
              'cell-secondary',
            ),
          );
          if (record.status === 'queued') {
            const position = queue?.waiting.indexOf(record.id) ?? -1;
            if (position >= 0)
              text.append(
                node('small', `排队第 ${position + 1} 项`, 'cell-secondary'),
              );
          }
          if (record.savedFiles?.length)
            text.title = record.savedFiles.join('\n');
          identity.append(thumbnail, text);
          const actions = node('div', '', 'queue-row-actions');
          actions.append(
            button('提示词', () =>
              showPrompt(
                record.origin?.assetName ?? '任务提示词',
                record.prompt,
                '本机任务快照',
              ),
            ),
          );
          if (record.status === 'queued')
            actions.append(
              button('取消', async () => {
                unwrap(await api.queue({ action: 'cancel', id: record.id }));
              }),
            );
          if (record.status === 'running')
            actions.append(
              button('停止等待', async () => {
                if (confirm('停止本机等待？上游可能已受理并计费。'))
                  unwrap(await window.studio.stop(record.id));
              }),
            );
          if (
            ['failed', 'stopped', 'interrupted', 'cancelled'].includes(
              record.status,
            )
          )
            actions.append(
              button('重试', async () => {
                if (
                  confirm(
                    '确认重新提交？上次请求可能已受理并计费，请先核查服务商记录。',
                  )
                )
                  unwrap(await api.queue({ action: 'retry', id: record.id }));
              }),
            );
          if (record.imageCount)
            actions.append(
              button('查看作品', () => {
                view('create');
                document
                  .querySelector<HTMLElement>(`[data-id="${record.id}"]`)
                  ?.scrollIntoView({ block: 'center' });
              }),
            );
          row.append(
            identity,
            node(
              'span',
              taskStatuses[record.status],
              `status ${record.status}`,
            ),
            actions,
          );
          return row;
        }),
    );
  }
  async function fetchState() {
    apply(unwrap(await api.state()));
  }
  let refreshTimer: ReturnType<typeof setTimeout>;
  api.onChanged(() => {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => {
      void run(fetchState);
    }, 80);
  });
  void run(async () => {
    await fetchState();
    if (state?.cloudUrl) await refresh();
  });
  return {
    updateRecords(values: Creation[]) {
      records = values;
      renderQueue();
    },
    view,
    currentView: () => currentView,
  };
}
