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
  SourceAsset,
  SourceProject,
  WorkspaceAPI,
  WorkspaceState,
} from './workspace-shared';

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
  let targetSignature = '';
  let resolvingContext = '';
  let batchContext = '';
  function context() {
    return `${state?.cloudUrl}|${state?.tenantId}|${projectId}|${loadVersion}`;
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
    item.onclick = () =>
      get<HTMLDialogElement>(item.dataset.close ?? '').close();
  get('connection-open').onclick = () => {
    get('cloud-url').textContent = state?.cloudUrl ?? '';
    get<HTMLInputElement>('auto-sync').value = String(
      state?.autoSyncMinutes ?? 0,
    );
    get<HTMLDialogElement>('connection-dialog').showModal();
  };
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
    void run(async () => {
      const targetId = Number(get<HTMLSelectElement>('target-project').value);
      if (!projectId || !targetId)
        throw new Error('请选择来源项目和对应的剧智创项目。');
      const current = state?.projects.find(
        (project) => project.id === projectId,
      );
      if (
        current?.targetProjectId &&
        current.targetProjectId !== targetId &&
        !confirm('更换对应项目将清除原匹配关系，继续？')
      )
        return;
      apply(unwrap(await api.bind({ id: projectId, projectId: targetId })));
      await loadAssets();
      hooks.notify('项目绑定已保存，可按名称匹配。');
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
      renderAssets();
    });
  };
  function currentProject(): SourceProject | undefined {
    return state?.projects.find((project) => project.id === projectId);
  }
  function renderProjects() {
    get('project-list').replaceChildren(
      ...(state?.projects ?? [])
        .filter((project) => project.active)
        .map((project) => {
          const item = button(
            '',
            async () => {
              projectId = project.id;
              renderProjects();
              await loadAssets();
            },
            `project-item${project.id === projectId ? ' selected' : ''}`,
          );
          item.append(
            node('strong', project.name),
            node(
              'small',
              project.targetProjectName
                ? `→ ${project.targetProjectName}`
                : '尚未绑定剧智创项目',
            ),
          );
          return item;
        }),
    );
    const project = currentProject();
    get('asset-project-name').textContent =
      project?.name ?? '连接团队，开始匹配资产';
    get('binding-label').textContent = project
      ? `来源团队 ${project.sourceTeamId} · 同步 ${project.syncedAt.replace('T', ' ')}`
      : '登录剧智创并选择团队，再同步火山剧创项目名称。';
    const target = get<HTMLSelectElement>('target-project');
    const signature = JSON.stringify([
      state?.tenantId,
      projectId,
      project?.targetProjectId,
      state?.targets,
    ]);
    if (signature !== targetSignature) {
      targetSignature = signature;
      target.replaceChildren(
        option('', '请选择对应项目'),
        ...(state?.targets ?? []).map((item) =>
          option(String(item.id), item.name),
        ),
      );
      target.value = project?.targetProjectId
        ? String(project.targetProjectId)
        : '';
    }
    get<HTMLButtonElement>('match-assets').disabled = !project?.targetProjectId;
    get<HTMLButtonElement>('bind-project').disabled = !project;
  }
  async function loadAssets() {
    const version = ++loadVersion;
    const id = projectId;
    selected.clear();
    assets = [];
    renderAssets();
    if (!id) return;
    const data = unwrap(await api.assets(id));
    if (version !== loadVersion || id !== projectId) return;
    assets = data;
    renderAssets();
  }
  function apply(next: WorkspaceState) {
    const previousProject = currentProject();
    const changedTeam =
      state?.tenantId !== next.tenantId || state?.cloudUrl !== next.cloudUrl;
    state = next;
    get('team-label').textContent = next.teamName
      ? `剧智创 · ${next.teamName}`
      : next.authenticated
        ? '请选择剧智创团队'
        : '尚未连接剧智创';
    const teamSelect = get<HTMLSelectElement>('team-select');
    teamSelect.replaceChildren(
      option('', next.authenticated ? '请选择团队' : '请先登录'),
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
    get('source-status').textContent = next.source.syncing
      ? '正在核对全部项目并同步名称…'
      : (next.source.error ??
        (next.source.loggedIn
          ? `火山剧创已登录 · 已发现 ${next.source.discoveredProjects} 个项目${next.source.lastSync ? ` · 最近拉取 ${new Date(next.source.lastSync).toLocaleTimeString()}` : ''}`
          : '请登录火山剧创；登录失效时自动同步暂停。'));
    get<HTMLButtonElement>('sync-source').disabled =
      !next.tenantId || next.source.syncing;
    if (next.error) error(next.error);
    if (
      changedTeam ||
      !next.projects.some((project) => project.id === projectId)
    ) {
      projectId = next.projects.find((project) => project.active)?.id;
      assets = [];
      selected.clear();
      get<HTMLDialogElement>('batch-dialog').close();
      get<HTMLDialogElement>('resolve-dialog').close();
      get<HTMLDialogElement>('prompt-dialog').close();
      void run(loadAssets);
    } else if (previousProject?.syncedAt !== currentProject()?.syncedAt) {
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
    const type = get<HTMLSelectElement>('asset-type').value;
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
    get('asset-empty').textContent = assets.length
      ? '没有符合筛选条件的资产。'
      : '暂无资产名称。登录火山剧创并同步全部项目后，绑定对应剧智创项目。';
    get('asset-summary').textContent =
      `共 ${assets.length} 个名称 · 可生成 ${assets.filter(ready).length} 个 · 当前显示 ${visible.length} 个。仅保存名称及匹配关系，不读取剧本原文。`;
    selectionChanged();
  }
  for (const id of ['asset-search', 'asset-type', 'asset-status'])
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
        `保存至 ${hooks.settings().outputDirectory} / 团队 / 项目 / 类型，文件名使用资产名称，重名追加序号。`;
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
  function renderQueue() {
    const queue = state?.queue;
    get('queue-count').textContent = String(
      (queue?.running.length ?? 0) + (queue?.waiting.length ?? 0),
    );
    get('queue-summary').textContent =
      `${queue?.paused ? '已暂停派发' : '正在派发'} · 生成中 ${queue?.running.length ?? 0} / ${queue?.limit ?? 5} · 等待 ${queue?.waiting.length ?? 0} · 重启后未提交任务需手动继续。`;
    get<HTMLSelectElement>('queue-limit').value = String(queue?.limit ?? 5);
    get<HTMLButtonElement>('queue-pause').disabled = !queue || queue.paused;
    get<HTMLButtonElement>('queue-resume').disabled = !queue?.paused;
    get('queue-empty').hidden = records.length > 0;
    get('queue-rows').replaceChildren(
      ...[...records]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((record) => {
          const row = node('article', '', 'queue-row');
          row.dataset.taskId = record.id;
          const text = node('div');
          text.append(
            node(
              'strong',
              record.origin
                ? `${record.origin.projectName} / ${record.origin.assetName}`
                : record.outputName || '自由创作',
            ),
            node(
              'small',
              record.origin
                ? `${record.origin.teamName} · ${typeNames[record.origin.type] ?? record.origin.type}`
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
            text,
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
