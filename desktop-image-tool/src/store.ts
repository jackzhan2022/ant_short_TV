import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {
  automaticName,
  copyAssetWithoutOverwrite,
  copyWithoutOverwrite,
  validateAssetOutputName,
  validateOutputName,
} from './output';
import { decodeImage } from './provider';
import {
  type AssetOrigin,
  BASE_URL,
  type Creation,
  type Draft,
  type Format,
  type Settings,
} from './shared';

interface Codec {
  encrypt(value: string): Buffer;
  decrypt(value: Buffer): string;
}
interface Config {
  outputDirectory?: string;
  keyCipher?: string;
  responseModel: string;
  concurrency?: number;
}
interface Stored extends Creation {
  outputFormats: Format[];
}

function atomicJson(file: string, data: unknown) {
  const temporary = `${file}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify(data), { mode: 0o600 });
  fs.renameSync(temporary, file);
}

export class StudioStore {
  readonly records = new Map<string, Stored>();
  readonly library: string;
  private readonly configFile: string;
  private config: Config;

  constructor(
    readonly root: string,
    private codec: Codec,
    private defaultOutputDirectory = path.join(root, 'images'),
  ) {
    this.library = path.join(root, 'creations');
    this.configFile = path.join(root, 'settings.json');
    fs.mkdirSync(this.library, { recursive: true });
    this.config = fs.existsSync(this.configFile)
      ? JSON.parse(fs.readFileSync(this.configFile, 'utf8'))
      : { responseModel: 'gpt-6-astra' };
    for (const entry of fs.readdirSync(this.library)) {
      if (!/^[a-f0-9-]{36}$/.test(entry)) continue;
      try {
        const record: Stored = JSON.parse(
          fs.readFileSync(
            path.join(this.library, entry, 'record.json'),
            'utf8',
          ),
        );
        if (record.id !== entry || !Array.isArray(record.outputFormats))
          continue;
        if (record.status === 'running') {
          record.status = 'interrupted';
          record.error =
            '上次应用退出时仍在生成，服务商可能已受理；不会自动重试。';
          record.finishedAt = new Date().toISOString();
          atomicJson(path.join(this.library, entry, 'record.json'), record);
        }
        this.records.set(entry, record);
      } catch {
        /* A damaged record must not prevent opening the remaining library. */
      }
    }
  }

  settings(): Settings {
    return {
      outputDirectory:
        this.config.outputDirectory || this.defaultOutputDirectory,
      hasKey: Boolean(this.config.keyCipher),
      responseModel: this.config.responseModel,
      baseUrl: BASE_URL,
      concurrency: this.config.concurrency ?? 5,
    };
  }

  setOutputDirectory(directory: string): Settings {
    if (!path.isAbsolute(directory) || !fs.statSync(directory).isDirectory())
      throw new Error('请选择有效的本地文件夹。');
    const probe = path.join(directory, `.bear-write-test-${randomUUID()}`);
    try {
      fs.writeFileSync(probe, '', { flag: 'wx' });
    } catch {
      throw new Error('所选目录不可写，请选择其他文件夹。');
    } finally {
      if (fs.existsSync(probe)) fs.unlinkSync(probe);
    }
    const next = { ...this.config, outputDirectory: directory };
    atomicJson(this.configFile, next);
    this.config = next;
    return this.settings();
  }

  updateSettings(input: {
    key?: string;
    responseModel: string;
    clearKey?: boolean;
    concurrency?: number;
  }): Settings {
    if (
      !input ||
      typeof input.responseModel !== 'string' ||
      !/^[\w.:-]{1,100}$/.test(input.responseModel)
    )
      throw new Error('请求模型名称无效。');
    const next = { ...this.config, responseModel: input.responseModel };
    if (input.concurrency !== undefined) {
      if (
        !Number.isInteger(input.concurrency) ||
        input.concurrency < 1 ||
        input.concurrency > 5
      )
        throw new Error('图片任务并发数必须为 1～5。');
      next.concurrency = input.concurrency;
    }
    if (input.clearKey) delete next.keyCipher;
    else if (input.key !== undefined && input.key !== '') {
      if (
        typeof input.key !== 'string' ||
        !input.key.trim() ||
        input.key.length > 4096 ||
        /[\r\n]/.test(input.key)
      )
        throw new Error('请输入有效的个人 Key。');
      next.keyCipher = this.codec.encrypt(input.key.trim()).toString('base64');
    }
    atomicJson(this.configFile, next);
    this.config = next;
    return this.settings();
  }

  key(): string {
    if (!this.config.keyCipher) throw new Error('请先配置自己的 API Key。');
    try {
      return this.codec.decrypt(Buffer.from(this.config.keyCipher, 'base64'));
    } catch {
      throw new Error('当前 Windows 用户无法解密已保存的 Key，请重新配置。');
    }
  }

  list(): Creation[] {
    return [...this.records.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((record) => this.publicRecord(record));
  }

  private publicRecord(record: Stored): Creation {
    const { outputFormats: _formats, ...visible } = record;
    return visible;
  }

  get(id: string): Stored {
    const record = this.records.get(id);
    if (!record) throw new Error('创作记录不存在。');
    return record;
  }

  create(
    draft: Draft,
    options: {
      queued?: boolean;
      outputGroup?: string[];
      origin?: AssetOrigin;
      exactAssetName?: string;
    } = {},
  ): Creation {
    const exactName = options.exactAssetName ?? options.origin?.assetName;
    const outputName =
      exactName === undefined
        ? draft.outputName
        : validateAssetOutputName(exactName);
    const group = (options.outputGroup ?? []).map((name) => {
      const valid = validateOutputName(name);
      if (!valid || valid !== name) throw new Error('资产保存分组名称无效。');
      return valid;
    });
    const id = randomUUID();
    const { references, ...parameters } = draft;
    const record: Stored = {
      ...parameters,
      ...(outputName !== undefined ? { outputName } : {}),
      responseModel: this.settings().responseModel,
      outputDirectory: path.join(this.settings().outputDirectory, ...group),
      ...(options.origin ? { origin: options.origin } : {}),
      id,
      createdAt: new Date().toISOString(),
      status: options.queued ? 'queued' : 'running',
      referenceCount: references.length,
      imageCount: 0,
      outputFormats: [],
    };
    fs.mkdirSync(path.join(this.library, id));
    atomicJson(path.join(this.library, id, 'references.json'), references);
    atomicJson(path.join(this.library, id, 'record.json'), record);
    this.records.set(id, record);
    return this.publicRecord(record);
  }

  start(id: string): Creation {
    const record = this.get(id);
    if (record.status !== 'queued')
      throw new Error('只有等待中的任务可以提交。');
    record.status = 'running';
    record.error = undefined;
    record.finishedAt = undefined;
    atomicJson(path.join(this.library, id, 'record.json'), record);
    return this.publicRecord(record);
  }

  retry(id: string): Creation {
    const original = this.get(id);
    if (
      !['failed', 'stopped', 'interrupted', 'cancelled'].includes(
        original.status,
      )
    )
      throw new Error('该任务不能重试。');
    const created = this.create(this.reuse(id), {
      queued: true,
      origin: original.origin,
    });
    const record = this.get(created.id);
    record.outputDirectory = original.outputDirectory;
    record.responseModel =
      original.responseModel ?? this.settings().responseModel;
    atomicJson(path.join(this.library, record.id, 'record.json'), record);
    return this.publicRecord(record);
  }

  finish(
    id: string,
    update: Partial<Pick<Creation, 'status' | 'error' | 'responseId'>>,
    images: { bytes: Buffer; format: Format; thumbnail: Buffer }[] = [],
  ): Creation {
    const record = {
      ...this.get(id),
      ...update,
      finishedAt: new Date().toISOString(),
      imageCount: images.length,
      outputFormats: images.map((image) => image.format),
    };
    images.forEach((image, index) => {
      fs.writeFileSync(
        path.join(this.library, id, `output-${index}.${image.format}`),
        image.bytes,
      );
      fs.writeFileSync(
        path.join(this.library, id, `thumb-${index}.png`),
        image.thumbnail,
      );
    });
    atomicJson(path.join(this.library, id, 'record.json'), record);
    this.records.set(id, record);
    return this.publicRecord(record);
  }

  imagePath(id: string, index: number, thumbnail = false): string {
    const record = this.get(id);
    if (!Number.isInteger(index) || index < 0 || index >= record.imageCount)
      throw new Error('图片不存在。');
    const format = record.outputFormats[index];
    if (!['png', 'jpeg', 'webp'].includes(format))
      throw new Error('图片格式无效。');
    return path.join(
      this.library,
      id,
      thumbnail ? `thumb-${index}.png` : `output-${index}.${format}`,
    );
  }

  exportImages(id: string): Creation {
    const previousFiles = this.get(id).savedFiles ?? [];
    const record = {
      ...this.get(id),
      savedFiles: [] as string[],
      saveError: undefined as string | undefined,
    };
    const directory = record.outputDirectory || this.settings().outputDirectory;
    try {
      const assetName = record.origin
        ? validateAssetOutputName(record.origin.assetName)
        : undefined;
      for (let index = 0; index < record.imageCount; index++) {
        const previous = previousFiles[index];
        if (assetName !== undefined && previous) {
          const relative = path.relative(directory, previous);
          if (
            relative &&
            relative !== '..' &&
            !relative.startsWith(`..${path.sep}`) &&
            !path.isAbsolute(relative) &&
            path.basename(previous) ===
              `${assetName}.${record.outputFormats[index]}` &&
            fs.existsSync(previous) &&
            fs.statSync(previous).isFile()
          ) {
            record.savedFiles.push(previous);
            continue;
          }
        }
        const copy =
          assetName === undefined
            ? copyWithoutOverwrite
            : copyAssetWithoutOverwrite;
        const file = copy(
          this.imagePath(id, index),
          directory,
          assetName ?? (record.outputName || automaticName(record.createdAt)),
          record.outputFormats[index],
        );
        record.savedFiles.push(file);
      }
    } catch (error) {
      record.saveError =
        error instanceof Error && error.message.startsWith('资产名称')
          ? error.message
          : '图片已保留在本机创作记录，但默认保存目录写入失败。请检查目录或使用「另存原图」。';
    }
    atomicJson(path.join(this.library, id, 'record.json'), record);
    this.records.set(id, record);
    return this.publicRecord(record);
  }

  reference(id: string, index: number) {
    const bytes = fs.readFileSync(this.imagePath(id, index));
    const format = this.get(id).outputFormats[index];
    const dataUrl = `data:image/${format};base64,${bytes.toString('base64')}`;
    decodeImage(dataUrl, 10 * 1024 * 1024);
    return { name: `创作-${id.slice(0, 8)}-${index + 1}.${format}`, dataUrl };
  }

  reuse(id: string): Draft {
    const r = this.get(id);
    return {
      ...(r.outputName !== undefined ? { outputName: r.outputName } : {}),
      prompt: r.prompt,
      model: r.model,
      size: r.size,
      quality: r.quality,
      format: r.format,
      references: JSON.parse(
        fs.readFileSync(path.join(this.library, id, 'references.json'), 'utf8'),
      ),
    };
  }
}
