import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { BrowserWindow, ipcMain, session } from 'electron';
import type { NameEncoder } from './vector-match';
import {
  LocalModelFiles,
  MODEL_FILES,
  VECTOR_ID,
  VECTOR_MODEL,
} from './vector-model';

const pageUrl = 'antv-vector://local/index.html';
const vendorFiles = [
  'transformers.min.js',
  'ort-wasm-simd-threaded.jsep.mjs',
  'ort-wasm-simd-threaded.jsep.wasm',
];
export class OfflineNameEncoder implements NameEncoder {
  readonly id = VECTOR_ID;
  readonly dimensions = 512;
  private window?: BrowserWindow;
  private ready?: () => void;
  private readyReject?: (error: Error) => void;
  private pending?: {
    id: string;
    resolve: (vectors: number[][]) => void;
    reject: (error: Error) => void;
  };
  private busy = false;
  private models: LocalModelFiles;
  constructor(root: string) {
    this.models = new LocalModelFiles(root);
    const isolated = session.fromPartition('vector-local');
    isolated.setPermissionRequestHandler((_contents, _permission, callback) =>
      callback(false),
    );
    isolated.setPermissionCheckHandler(() => false);
    isolated.webRequest.onBeforeRequest(
      { urls: ['http://*/*', 'https://*/*'] },
      (_details, callback) => callback({ cancel: true }),
    );
    isolated.protocol.handle('antv-vector', async (request) => {
      const url = new URL(request.url);
      if (url.hostname !== 'local' || request.method !== 'GET')
        return new Response(null, { status: 404 });
      const localFiles = new Map([
        ['/index.html', path.join(__dirname, 'vector.html')],
        ['/vector-page.mjs', path.join(__dirname, 'vector-page.mjs')],
        ...vendorFiles.map(
          (name) =>
            [
              `/vendor/${name}`,
              path.join(__dirname, 'vector-vendor', name),
            ] as const,
        ),
        ...MODEL_FILES.map(
          (file) =>
            [
              `/models/${VECTOR_MODEL}/${file.name}`,
              path.join(this.models.directory, file.name),
            ] as const,
        ),
      ]);
      const file = localFiles.get(url.pathname);
      if (!file) return new Response(null, { status: 404 });
      try {
        const type = file.endsWith('.wasm')
          ? 'application/wasm'
          : file.endsWith('.html')
            ? 'text/html'
            : file.endsWith('.json')
              ? 'application/json'
              : 'text/javascript';
        return new Response(new Uint8Array(await fs.promises.readFile(file)), {
          headers: { 'Content-Type': type },
        });
      } catch {
        return new Response(null, { status: 404 });
      }
    });
    ipcMain.on('vector:ready', (event) => {
      if (
        event.sender === this.window?.webContents &&
        event.senderFrame?.url === pageUrl
      )
        this.ready?.();
    });
    ipcMain.on(
      'vector:result',
      (event, value: { id?: string; vectors?: number[][]; error?: string }) => {
        if (
          event.sender !== this.window?.webContents ||
          event.senderFrame?.url !== pageUrl ||
          !this.pending ||
          value?.id !== this.pending.id
        )
          return;
        if (value.error)
          this.pending.reject(
            new Error(
              '本地向量模型加载或计算失败，请重新开始；模型文件会保留。',
            ),
          );
        else if (Array.isArray(value.vectors))
          this.pending.resolve(value.vectors);
        else this.pending.reject(new Error('本地向量结果无效。'));
      },
    );
  }
  close() {
    this.readyReject?.(new Error('本地向量匹配已取消。'));
    this.pending?.reject(new Error('本地向量匹配已取消。'));
    if (this.window && !this.window.isDestroyed()) this.window.destroy();
    this.window = undefined;
  }
  async encode(
    texts: string[],
    signal: AbortSignal,
    progress: (detail: string) => void = () => {},
  ): Promise<number[][]> {
    if (this.busy) throw new Error('本地向量计算正在进行，请稍后重试。');
    if (signal.aborted) throw new Error('本地向量匹配已取消。');
    if (
      !texts.length ||
      texts.length > 16 ||
      texts.some((t) => typeof t !== 'string' || t.length > 4000)
    )
      throw new Error('本地向量名称输入无效。');
    this.busy = true;
    const abort = () => this.close();
    signal.addEventListener('abort', abort, { once: true });
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      if (!this.window || this.window.isDestroyed()) {
        await this.models.ensure(signal, progress);
        if (signal.aborted) throw new Error('本地向量匹配已取消。');
        progress('正在加载本地中文模型…');
        const ready = new Promise<void>((resolve, reject) => {
          this.ready = resolve;
          this.readyReject = reject;
          timer = setTimeout(
            () => reject(new Error('本地向量运行环境启动超时，请重新开始。')),
            30000,
          );
        });
        const runtime = new BrowserWindow({
          show: false,
          webPreferences: {
            preload: path.join(__dirname, 'vector-preload.js'),
            partition: 'vector-local',
            sandbox: true,
            contextIsolation: true,
            nodeIntegration: false,
            backgroundThrottling: false,
          },
        });
        this.window = runtime;
        runtime.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
        runtime.webContents.on('will-navigate', (event) =>
          event.preventDefault(),
        );
        runtime.webContents.on('render-process-gone', () => {
          if (this.window !== runtime) return;
          this.readyReject?.(new Error('本地向量运行环境已停止，请重新开始。'));
          this.pending?.reject(
            new Error('本地向量计算进程已停止，请重新开始。'),
          );
          this.window = undefined;
        });
        await Promise.all([runtime.loadURL(pageUrl), ready]);
        clearTimeout(timer);
        this.ready = undefined;
        this.readyReject = undefined;
      }
      if (signal.aborted) throw new Error('本地向量匹配已取消。');
      return await new Promise<number[][]>((resolve, reject) => {
        const id = randomUUID();
        this.pending = { id, resolve, reject };
        timer = setTimeout(
          () => reject(new Error('本地向量计算超时，请重新开始。')),
          120000,
        );
        this.window?.webContents.send('vector:request', { id, texts });
      });
    } catch (error) {
      this.close();
      if (signal.aborted) throw new Error('本地向量匹配已取消。');
      throw error;
    } finally {
      clearTimeout(timer);
      signal.removeEventListener('abort', abort);
      this.pending = undefined;
      this.ready = undefined;
      this.readyReject = undefined;
      this.busy = false;
    }
  }
}
