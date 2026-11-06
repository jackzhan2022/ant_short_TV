import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export const VECTOR_REVISION = '75c43b069aac4d136ba6bc1122f995fedcfd2781';
export const VECTOR_MODEL = 'bge-small-zh-v1.5';
export const VECTOR_ID = `${VECTOR_MODEL}:${VECTOR_REVISION}:q8:cls:128:text-v1`;
interface ModelFile {
  name: string;
  size: number;
  sha256: string;
}
export const MODEL_FILES: readonly ModelFile[] = [
  {
    name: 'config.json',
    size: 716,
    sha256: 'd4193ead3a810fd694fa8a31d7fc72fbaebc0668b603e398734bf2f6538ff42f',
  },
  {
    name: 'tokenizer.json',
    size: 439125,
    sha256: '48cea5d44424912a6fd1ea647bf4fe50b55ab8b1e5879c3275f80e339e8fae26',
  },
  {
    name: 'tokenizer_config.json',
    size: 367,
    sha256: 'e6f3b96db926a37d4039995fbf5ad17de158dfb8f6343d607e4dbaad18d75f5a',
  },
  {
    name: 'special_tokens_map.json',
    size: 125,
    sha256: 'b6d346be366a7d1d48332dbc9fdf3bf8960b5d879522b7799ddba59e76237ee3',
  },
  {
    name: 'onnx/model_quantized.onnx',
    size: 24010842,
    sha256: '15b717c382bcb518ba457b93ea6850ede7f4f1cd8937454aa06972366cd19bcc',
  },
];
const bases = ['https://hf-mirror.com', 'https://huggingface.co'];
const check = (signal: AbortSignal) => {
  if (signal.aborted) throw new Error('本地模型准备已取消。');
};

export class LocalModelFiles {
  readonly directory: string;
  constructor(
    root: string,
    private fetcher: typeof fetch = fetch,
    private files = MODEL_FILES,
  ) {
    this.directory = path.join(
      root,
      'local-models',
      `${VECTOR_MODEL}-${VECTOR_REVISION.slice(0, 7)}`,
    );
  }
  async ensure(
    signal: AbortSignal,
    progress: (detail: string) => void = () => {},
  ) {
    const total = this.files.reduce((sum, f) => sum + f.size, 0);
    let completed = 0;
    for (const file of this.files) {
      check(signal);
      const destination = path.join(this.directory, file.name);
      try {
        const stat = await fs.promises.stat(destination);
        if (
          stat.size === file.size &&
          createHash('sha256')
            .update(await fs.promises.readFile(destination))
            .digest('hex') === file.sha256
        ) {
          completed += file.size;
          continue;
        }
      } catch {
        /* Missing or damaged files are replaced only after full verification. */
      }
      await fs.promises.mkdir(path.dirname(destination), { recursive: true });
      let saved = false;
      for (const base of bases) {
        check(signal);
        const temporary = `${destination}.${randomUUID()}.part`;
        let handle: fs.promises.FileHandle | undefined;
        let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
        try {
          progress(
            `正在下载本地中文模型 · ${(completed / 1e6).toFixed(1)} / ${(total / 1e6).toFixed(1)} MB`,
          );
          const response = await this.fetcher(
            `${base}/Xenova/${VECTOR_MODEL}/resolve/${VECTOR_REVISION}/${file.name}`,
            {
              credentials: 'omit',
              signal: AbortSignal.any([signal, AbortSignal.timeout(120000)]),
            },
          );
          if (!response.ok || !response.body) {
            await response.body?.cancel();
            throw new Error();
          }
          reader = response.body.getReader();
          handle = await fs.promises.open(temporary, 'wx');
          const digest = createHash('sha256');
          let size = 0;
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            check(signal);
            size += value.length;
            if (size > file.size) throw new Error();
            digest.update(value);
            await handle.writeFile(value);
            progress(
              `正在下载本地中文模型 · ${((completed + size) / 1e6).toFixed(1)} / ${(total / 1e6).toFixed(1)} MB`,
            );
          }
          if (size !== file.size || digest.digest('hex') !== file.sha256)
            throw new Error();
          check(signal);
          await handle.close();
          handle = undefined;
          await fs.promises.rename(temporary, destination);
          saved = true;
          break;
        } catch {
          check(signal);
        } finally {
          await reader?.cancel().catch(() => {});
          await handle?.close();
          await fs.promises.rm(temporary, { force: true });
        }
      }
      if (!saved)
        throw new Error(
          '本地模型下载失败或校验未通过，请检查网络后重新开始；已下载的完整文件会保留。',
        );
      completed += file.size;
    }
    check(signal);
    return this.directory;
  }
}
