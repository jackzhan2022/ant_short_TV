import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  nativeImage,
  powerMonitor,
  protocol,
  safeStorage,
  session,
  shell,
} from 'electron';
import { AiSettingsStore } from './ai-match';
import type { AiApplyInput, AiModelInput } from './ai-match-types';
import { AiReviewManager } from './ai-review';
import { DramartCollector } from './dramart';
import { automaticName, validateAssetOutputName } from './output';
import { generateImage, validateDraft } from './provider';
import { ImageQueue } from './queue';
import type { Creation, Draft, Result } from './shared';
import { StudioStore } from './store';
import { WorkspaceController } from './workspace';
import type {
  AssetPrompt,
  BatchInput,
  SourceAsset,
  SourceProject,
  WorkspaceAPI,
} from './workspace-shared';

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'antv-media',
    privileges: { standard: true, secure: true, supportFetchAPI: false },
  },
]);
if (!app.isPackaged && process.env.ANTV_STUDIO_TEST_DATA)
  app.setPath('userData', process.env.ANTV_STUDIO_TEST_DATA);
app.setAppUserModelId('com.antshorttv.antv-image-studio');
const ownsLock = app.requestSingleInstanceLock();
let window: BrowserWindow | undefined;
let store: StudioStore;
interface ActiveTask {
  id: string;
  controller: AbortController;
  stopped: boolean;
}
const active = new Map<string, ActiveTask>();
let queue: ImageQueue;
let workspace: WorkspaceController;
let aiSettings: AiSettingsStore;
let aiReviews: AiReviewManager;
let quitting = false;
const indexPath = path.join(__dirname, 'index.html');

function emit(record: Creation) {
  if (window && !window.isDestroyed())
    window.webContents.send('studio:update', record);
}
function changed() {
  if (window && !window.isDestroyed())
    window.webContents.send('workspace:changed');
}

function handle<T>(name: string, work: (input: T) => unknown) {
  ipcMain.handle(
    `studio:${name}`,
    async (event, input: T): Promise<Result<unknown>> => {
      try {
        if (
          event.sender !== window?.webContents ||
          event.senderFrame !== window.webContents.mainFrame ||
          fileURLToPath(event.senderFrame.url) !== indexPath
        )
          throw new Error('无效的调用来源。');
        return { ok: true, value: await work(input) };
      } catch (error) {
        return {
          ok: false,
          error: error instanceof Error ? error.message : '本地操作失败。',
        };
      }
    },
  );
}

async function run(
  record: Creation,
  draft: Draft,
  key: string,
  responseModel: string,
  task: ActiveTask,
) {
  const timeout = AbortSignal.timeout(10 * 60 * 1000);
  try {
    const result = await generateImage(
      draft,
      key,
      responseModel,
      AbortSignal.any([task.controller.signal, timeout]),
    );
    if (task.stopped) throw new Error('stopped');
    const images = result.images.map((image) => {
      const decoded = nativeImage.createFromBuffer(image.bytes);
      if (decoded.isEmpty()) throw new Error('返回的图片无法解码。');
      const { width, height } = decoded.getSize();
      const thumbnail = decoded
        .resize(
          width >= height
            ? { width: Math.min(width, 720) }
            : { height: Math.min(height, 720) },
        )
        .toPNG();
      return { ...image, thumbnail };
    });
    const completed = store.finish(
      record.id,
      { status: 'succeeded', responseId: result.responseId },
      images,
    );
    try {
      emit(store.exportImages(record.id));
    } catch {
      emit({
        ...completed,
        saveError:
          '原图已在本机记录中保存，但默认目录保存信息写入失败，请使用「另存原图」。',
      });
    }
  } catch (error) {
    const status = quitting
      ? 'interrupted'
      : task.stopped
        ? 'stopped'
        : 'failed';
    const message = quitting
      ? '应用退出时仍在生成，上游可能已受理；不会自动重试。'
      : task.stopped
        ? '已停止本机等待。服务商可能继续生成并计费，本工具不会自动重试。'
        : timeout.aborted
          ? '等待超过 10 分钟。服务商可能已受理，请核查后再决定是否重试。'
          : error instanceof TypeError
            ? '无法连接图片服务，或响应中断。请核查网络及服务商记录后再决定是否重试。'
            : error instanceof Error
              ? error.message
              : '图片生成失败。';
    try {
      emit(
        store.finish(record.id, {
          status,
          error: message.replaceAll(key, '[已隐藏]'),
        }),
      );
    } catch {
      emit({
        ...record,
        status: 'failed',
        finishedAt: new Date().toISOString(),
        error: '无法保存创作记录，请检查磁盘空间与目录权限。',
      });
    }
  } finally {
    active.delete(task.id);
  }
}

async function dispatch(id: string) {
  try {
    const key = store.key();
    const restored = store.reuse(id);
    const original = store.get(id);
    if (original.origin) validateAssetOutputName(original.origin.assetName);
    const draft = validateDraft({
      ...restored,
      outputName: original.origin ? '' : restored.outputName,
    });
    const record = store.start(id);
    const task: ActiveTask = {
      id,
      controller: new AbortController(),
      stopped: false,
    };
    active.set(id, task);
    emit(record);
    await run(
      record,
      draft,
      key,
      record.responseModel ?? store.settings().responseModel,
      task,
    );
  } catch (error) {
    emit(
      store.finish(id, {
        status: 'failed',
        error: error instanceof Error ? error.message : '任务提交失败。',
      }),
    );
  }
}
function safeComponent(name: string, id: number) {
  const clean =
    name
      .replace(/[<>:"/\\|?*]/g, '_')
      .split('')
      .map((character) => (character.charCodeAt(0) < 32 ? '_' : character))
      .join('')
      .trim()
      .replace(/[. ]+$/g, '')
      .slice(0, 48) || '未命名';
  return `${clean}_${id}`;
}
function positiveId(value: number) {
  if (!Number.isSafeInteger(value) || value < 1)
    throw new Error('无效的资源编号。');
  return value;
}

async function initialize() {
  store = new StudioStore(
    app.getPath('userData'),
    {
      encrypt(value) {
        if (!safeStorage.isEncryptionAvailable())
          throw new Error('系统凭据加密不可用，无法保存 Key。');
        return safeStorage.encryptString(value);
      },
      decrypt: (value) => safeStorage.decryptString(value),
    },
    !app.isPackaged && process.env.ANTV_STUDIO_TEST_DATA
      ? path.join(app.getPath('userData'), 'images')
      : path.join(app.getPath('pictures'), 'ANTV生图'),
  );
  queue = new ImageQueue(dispatch, changed, {
    limit: store.settings().concurrency,
    waiting: store
      .list()
      .filter((record) => record.status === 'queued')
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .map((record) => record.id),
  });
  workspace = new WorkspaceController(
    app.getPath('userData'),
    new DramartCollector(changed),
    () => queue.state(),
    changed,
  );
  aiSettings = new AiSettingsStore(
    app.getPath('userData'),
    {
      encrypt(value) {
        if (!safeStorage.isEncryptionAvailable())
          throw new Error('系统凭据加密不可用，无法保存文本模型 Key。');
        return safeStorage.encryptString(value);
      },
      decrypt: (value) => safeStorage.decryptString(value),
    },
    () => ({
      model: store.settings().responseModel,
      hasKey: store.settings().hasKey,
      key: () => store.key(),
    }),
  );
  aiReviews = new AiReviewManager(
    () => ({
      client: workspace.requireClient(),
      identity: workspace.assetIdentity(),
    }),
    () => ({ settings: aiSettings.settings(), key: aiSettings.key() }),
    undefined,
    (progress) => {
      if (window && !window.isDestroyed())
        window.webContents.send('workspace:ai-progress', progress);
    },
  );
  powerMonitor.on('resume', () => workspace.resumeSync());
  protocol.handle('antv-media', (request) => {
    try {
      const url = new URL(request.url);
      const parts = url.pathname.split('/').filter(Boolean);
      if (url.hostname !== 'image' || parts.length !== 2)
        return new Response(null, { status: 404 });
      const thumb = url.searchParams.get('thumb') === '1';
      const file = store.imagePath(parts[0], Number(parts[1]), thumb);
      const type = thumb ? 'png' : path.extname(file).slice(1);
      return new Response(new Uint8Array(fs.readFileSync(file)), {
        headers: {
          'Content-Type': `image/${type}`,
          'Cache-Control': 'private, max-age=86400',
        },
      });
    } catch {
      return new Response(null, { status: 404 });
    }
  });
  session.defaultSession.setPermissionRequestHandler(
    (_contents, _permission, callback) => callback(false),
  );
  session.defaultSession.setPermissionCheckHandler(() => false);
  handle('state', () => ({
    settings: store.settings(),
    records: store.list(),
  }));
  handle('directory', async () => {
    const selection = await dialog.showOpenDialog(window as BrowserWindow, {
      title: '选择图片自动保存目录',
      defaultPath: store.settings().outputDirectory,
      properties: ['openDirectory', 'createDirectory'],
    });
    if (selection.canceled || !selection.filePaths[0]) return store.settings();
    return store.setOutputDirectory(selection.filePaths[0]);
  });
  handle<Parameters<StudioStore['updateSettings']>[0]>('settings', (input) => {
    const settings = store.updateSettings(input);
    queue.setLimit(settings.concurrency);
    return settings;
  });
  handle<Draft>('generate', (input) => {
    const draft = validateDraft(input);
    store.key();
    const record = store.create(draft, { queued: true });
    emit(record);
    queue.add([record.id]);
    return store.list().find((item) => item.id === record.id);
  });
  handle<string | undefined>('stop', (id) => {
    for (const task of active.values()) {
      if (id && task.id !== id) continue;
      task.stopped = true;
      task.controller.abort();
    }
  });
  handle<boolean>('workspace-state', (refresh) =>
    refresh ? workspace.refresh() : workspace.state(),
  );
  handle('workspace-ai-settings', () => aiSettings.settings());
  handle<AiModelInput>('workspace-ai-configure', (input) => {
    aiReviews.cancel();
    return aiSettings.update(input);
  });
  handle<number>('workspace-ai-match', (id) => aiReviews.match(positiveId(id)));
  handle<AiApplyInput>('workspace-ai-apply', (input) =>
    workspace.withAssetOperation(() => aiReviews.apply(input)),
  );
  handle<string | undefined>('workspace-ai-cancel', (id) =>
    aiReviews.cancel(id),
  );
  handle<Parameters<WorkspaceAPI['configure']>[0]>(
    'workspace-configure',
    (input) => {
      aiReviews.cancel();
      return workspace.configure(input);
    },
  );
  handle('workspace-login-cloud', () => workspace.loginCloud());
  handle<number>('workspace-open-project', (id) =>
    workspace.loginCloud(positiveId(id)),
  );
  handle<number>('workspace-team', (id) => {
    aiReviews.cancel();
    return workspace.selectTeam(positiveId(id));
  });
  handle('workspace-login-source', () => workspace.loginSource());
  handle('workspace-check-source', () => workspace.checkSource());
  handle('workspace-sync', () => workspace.sync());
  handle<number>('workspace-assets', (id) =>
    workspace
      .requireClient()
      .get(`/api/desktop/source-projects/${positiveId(id)}/assets`),
  );
  handle<{ id: number; projectId: number }>('workspace-bind', async (input) => {
    aiReviews.cancel();
    await workspace.withAssetOperation(() =>
      workspace
        .requireClient()
        .send<SourceProject>(
          `/api/desktop/source-projects/${positiveId(input.id)}/binding`,
          'PUT',
          { projectId: positiveId(input.projectId) },
        ),
    );
    return workspace.refresh();
  });
  handle<number>('workspace-match', (id) => {
    aiReviews.cancel();
    return workspace.withAssetOperation(() =>
      workspace
        .requireClient()
        .send<SourceAsset[]>(
          `/api/desktop/source-projects/${positiveId(id)}/match`,
          'POST',
          {},
        ),
    );
  });
  handle<Parameters<WorkspaceAPI['candidates']>[0]>(
    'workspace-candidates',
    (input) => {
      if (!['CHARACTER', 'SCENE', 'PROP'].includes(input.type))
        throw new Error('资产类型无效。');
      return workspace
        .requireClient()
        .get(
          `/api/desktop/source-projects/${positiveId(input.id)}/candidates?type=${input.type}${input.sourceParentId ? `&sourceParentId=${encodeURIComponent(input.sourceParentId)}` : ''}`,
        );
    },
  );
  handle<Parameters<WorkspaceAPI['resolve']>[0]>(
    'workspace-resolve',
    (input) => {
      aiReviews.cancel();
      return workspace.withAssetOperation(() =>
        workspace
          .requireClient()
          .send(
            `/api/desktop/source-projects/assets/${positiveId(input.id)}/match`,
            'PUT',
            {
              assetId: positiveId(input.assetId),
              variantId:
                input.variantId === undefined
                  ? null
                  : positiveId(input.variantId),
            },
          ),
      );
    },
  );
  function validateIds(ids: number[]) {
    if (
      !Array.isArray(ids) ||
      !ids.length ||
      ids.length > 500 ||
      new Set(ids).size !== ids.length
    )
      throw new Error('请选择 1～500 个不同的资产。');
    return ids.map(positiveId);
  }
  handle<number[]>('workspace-prompts', (ids) =>
    workspace
      .requireClient()
      .send('/api/desktop/source-projects/prompts', 'POST', {
        assetIds: validateIds(ids),
      }),
  );
  handle<BatchInput>('workspace-batch', (input) =>
    workspace.withBatch(
      input,
      async (client, parameters, tenantId, teamName) => {
        const ids = validateIds(parameters.assetIds);
        store.key();
        const prompts = await client.send<AssetPrompt[]>(
          '/api/desktop/source-projects/prompts',
          'POST',
          { assetIds: ids },
        );
        if (
          prompts.length !== ids.length ||
          new Set(prompts.map((item) => item.sourceAssetId)).size !==
            ids.length ||
          prompts.some((item) => !ids.includes(item.sourceAssetId))
        )
          throw new Error('部分资产提示词不可用，请刷新后重试。');
        const drafts = prompts.map((item) =>
          validateDraft({
            ...parameters,
            // Naming is validated for the full batch before any image request.
            outputName: '',
            prompt: item.prompt,
            references: [],
          }),
        );
        for (const item of prompts) validateAssetOutputName(item.name);
        const importBatch = `导入批次-${randomUUID()}`;
        const created: Creation[] = [];
        try {
          prompts.forEach((item, index) => {
            created.push(
              store.create(drafts[index], {
                queued: true,
                outputGroup: [
                  safeComponent(teamName, tenantId),
                  safeComponent(
                    item.sourceProjectName,
                    positiveId(item.sourceProjectId),
                  ),
                  item.type,
                  importBatch,
                ],
                exactAssetName: item.name,
                origin: {
                  tenantId,
                  teamName,
                  sourceAssetId: item.sourceAssetId,
                  projectName: item.sourceProjectName,
                  assetName: item.name,
                  type: item.type,
                  targetProjectId: item.targetProjectId,
                  targetAssetId: item.targetAssetId,
                  targetVariantId: item.targetVariantId,
                  sourceProjectId: item.sourceProjectId,
                  sourceParentName: item.sourceParentName,
                },
              }),
            );
          });
        } catch (error) {
          for (const item of created)
            emit(
              store.finish(item.id, {
                status: 'cancelled',
                error: '批量任务保存失败，未提交。',
              }),
            );
          throw error;
        }
        for (const item of created) emit(item);
        queue.add(created.map((record) => record.id));
        return store
          .list()
          .filter((item) => created.some((record) => record.id === item.id));
      },
    ),
  );
  handle<Parameters<WorkspaceAPI['queue']>[0]>('workspace-queue', (input) => {
    if (input.action === 'pause') queue.pause();
    else if (input.action === 'resume') {
      store.key();
      queue.resume();
    } else if (input.action === 'limit') {
      const settings = store.updateSettings({
        responseModel: store.settings().responseModel,
        concurrency: input.limit,
      });
      queue.setLimit(settings.concurrency);
    } else if (input.action === 'cancel') {
      if (!input.id || !queue.cancel(input.id))
        throw new Error('只能取消等待中的任务。');
      emit(store.finish(input.id, { status: 'cancelled' }));
    } else if (input.action === 'retry') {
      const record = store.get(input.id ?? '');
      if (
        !['failed', 'stopped', 'interrupted', 'cancelled'].includes(
          record.status,
        )
      )
        throw new Error('该任务不能重试。');
      store.key();
      validateDraft(store.reuse(record.id));
      const retry = store.retry(record.id);
      emit(retry);
      queue.add([retry.id]);
    } else throw new Error('无效的队列操作。');
    return queue.state();
  });
  handle<string>('reuse', (id) => store.reuse(id));
  handle<{ id: string; index: number }>('reference', (input) =>
    store.reference(input.id, input.index),
  );
  handle<{ id: string; index: number }>('save', async (input) => {
    const source = store.imagePath(input.id, input.index);
    const ext = path.extname(source).slice(1);
    const record = store.get(input.id);
    const result = await dialog.showSaveDialog(window as BrowserWindow, {
      title: '保存生成的原图',
      defaultPath: path.join(
        store.settings().outputDirectory,
        `${record.outputName || automaticName(record.createdAt)}.${ext}`,
      ),
      filters: [{ name: `${ext.toUpperCase()} 图片`, extensions: [ext] }],
    });
    if (result.canceled || !result.filePath) return false;
    await fs.promises.copyFile(source, result.filePath);
    return true;
  });
  handle('folder', async () => {
    fs.mkdirSync(store.settings().outputDirectory, { recursive: true });
    const error = await shell.openPath(store.settings().outputDirectory);
    if (error) throw new Error('无法打开作品文件夹。');
  });
  window = new BrowserWindow({
    width: 1280,
    height: 880,
    minWidth: 820,
    minHeight: 640,
    title: 'ANTV生图',
    icon: path.join(__dirname, 'icon.ico'),
    backgroundColor: '#f6f7f9',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });
  window.removeMenu();
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event) => event.preventDefault());
  window.once('ready-to-show', () => window?.show());
  window.once('closed', () => {
    window = undefined;
    app.quit();
  });
  window.on('close', (event) => {
    if (
      active.size > 0 &&
      dialog.showMessageBoxSync(window as BrowserWindow, {
        type: 'question',
        buttons: ['继续等待', '退出工具'],
        defaultId: 0,
        cancelId: 0,
        message: '图片仍在生成，确定退出？',
        detail: '退出会停止本机等待，上游可能继续生成并计费。',
      }) === 0
    )
      event.preventDefault();
  });
  await window.loadFile(indexPath);
}

if (!ownsLock) app.quit();
else {
  app.on('second-instance', () => {
    if (!window || window.isDestroyed()) return;
    if (window.isMinimized()) window.restore();
    window.show();
    window?.focus();
  });
  app
    .whenReady()
    .then(initialize)
    .catch(() => {
      dialog.showErrorBox(
        'ANTV生图启动失败',
        '无法读取本地配置或作品目录。请检查磁盘空间及权限；不要删除已有作品。',
      );
      app.quit();
    });
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', () => {
    quitting = true;
    aiReviews?.cancel();
    queue?.pause();
    for (const task of active.values()) task.controller.abort();
    workspace?.close();
  });
}
