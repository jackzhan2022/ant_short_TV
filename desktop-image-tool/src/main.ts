import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  nativeImage,
  protocol,
  safeStorage,
  session,
  shell,
} from 'electron';
import { automaticName } from './output';
import { generateImage, validateDraft } from './provider';
import type { Creation, Draft, Result } from './shared';
import { StudioStore } from './store';

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
let active:
  | { id: string; controller: AbortController; stopped: boolean }
  | undefined;
const indexPath = path.join(__dirname, 'index.html');

function emit(record: Creation) {
  if (window && !window.isDestroyed())
    window.webContents.send('studio:update', record);
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
  task: NonNullable<typeof active>,
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
    const status = task.stopped ? 'stopped' : 'failed';
    const message = task.stopped
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
    if (active === task) active = undefined;
  }
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
  handle<Parameters<StudioStore['updateSettings']>[0]>('settings', (input) =>
    store.updateSettings(input),
  );
  handle<Draft>('generate', (input) => {
    if (active) throw new Error('请等待当前图片生成完成。');
    const draft = validateDraft(input);
    const key = store.key();
    const record = store.create(draft);
    const task = {
      id: record.id,
      controller: new AbortController(),
      stopped: false,
    };
    active = task;
    void run(record, draft, key, store.settings().responseModel, task);
    return record;
  });
  handle('stop', () => {
    if (active) {
      active.stopped = true;
      active.controller.abort();
    }
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
  window.on('close', (event) => {
    if (
      active &&
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
    if (window?.isMinimized()) window.restore();
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
  app.on('before-quit', () => active?.controller.abort());
}
