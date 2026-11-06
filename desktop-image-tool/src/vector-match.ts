import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import type { MatchTask } from './ai-match';
import type { AiSuggestion } from './ai-match-types';
import type { Candidate } from './workspace-shared';

export interface NameEncoder {
  id: string;
  dimensions: number;
  encode(
    texts: string[],
    signal: AbortSignal,
    progress?: (detail: string) => void,
  ): Promise<number[][]>;
}
const check = (signal: AbortSignal) => {
  if (signal.aborted) throw new Error('本地向量匹配已取消。');
};
const textFor = (type: string, name: string, parent?: string | null) =>
  `${type === 'CHARACTER' ? '角色' : type === 'SCENE' ? '场景' : '道具'}：${parent ? `${parent} / ` : ''}${name}`;
const normalName = (value: string) => value.normalize('NFKC').trim();

export class LocalVectorMatcher {
  private db: DatabaseSync;
  constructor(
    root: string,
    private encoder: NameEncoder,
  ) {
    fs.mkdirSync(root, { recursive: true });
    this.db = new DatabaseSync(path.join(root, 'asset-vectors.sqlite'));
    this.db.exec(
      'CREATE TABLE IF NOT EXISTS embeddings (id TEXT PRIMARY KEY, vector BLOB NOT NULL) STRICT',
    );
  }
  close() {
    if (this.db.isOpen) this.db.close();
  }
  private key(text: string) {
    return createHash('sha256')
      .update(JSON.stringify([this.encoder.id, this.encoder.dimensions, text]))
      .digest('hex');
  }
  private normalize(values: number[]): number[] {
    if (
      values.length !== this.encoder.dimensions ||
      values.some((n) => !Number.isFinite(n))
    )
      throw new Error('本地模型返回的向量格式无效。');
    const length = Math.hypot(...values);
    if (!Number.isFinite(length) || length < 1e-12)
      throw new Error('本地模型返回了空向量。');
    return values.map((n) => n / length);
  }
  async match(
    tasks: MatchTask[],
    signal: AbortSignal,
    progress: (
      completed: number,
      total: number,
      detail?: string,
    ) => void = () => {},
  ): Promise<AiSuggestion[]> {
    check(signal);
    const rows = tasks.map((task) => {
      const candidates = task.candidates.filter(
        (c) =>
          c.hasPrompt &&
          c.type === task.source.type &&
          (task.source.sourceParentId
            ? c.variantId !== null
            : c.variantId === null),
      );
      const exact = candidates.filter(
        (c) => normalName(c.name) === normalName(task.source.name),
      );
      return { task, candidates, exact };
    });
    const texts = [
      ...new Set(
        rows
          .filter((r) => !r.exact.length && r.candidates.length)
          .flatMap(({ task, candidates }) => [
            textFor(task.source.type, task.source.name, task.parentName),
            ...candidates.map((c) => textFor(c.type, c.name, c.parentName)),
          ]),
      ),
    ];
    const vectors = new Map<string, number[]>();
    const missing: string[] = [];
    const read = this.db.prepare('SELECT vector FROM embeddings WHERE id = ?');
    for (const text of texts) {
      const row = read.get(this.key(text));
      try {
        const buffer = row?.vector;
        if (
          !(buffer instanceof Uint8Array) ||
          buffer.byteLength !== this.encoder.dimensions * 4
        )
          throw new Error();
        const view = new DataView(
          buffer.buffer,
          buffer.byteOffset,
          buffer.byteLength,
        );
        vectors.set(
          text,
          this.normalize(
            Array.from({ length: this.encoder.dimensions }, (_, i) =>
              view.getFloat32(i * 4, true),
            ),
          ),
        );
      } catch {
        missing.push(text);
      }
    }
    progress(texts.length - missing.length, texts.length);
    const write = this.db.prepare(
      'INSERT OR REPLACE INTO embeddings (id, vector) VALUES (?, ?)',
    );
    for (let i = 0; i < missing.length; i += 16) {
      check(signal);
      const batch = missing.slice(i, i + 16);
      const encoded = await this.encoder.encode(batch, signal, (detail) =>
        progress(texts.length - missing.length + i, texts.length, detail),
      );
      check(signal);
      if (!Array.isArray(encoded) || encoded.length !== batch.length)
        throw new Error('本地模型返回的向量数量无效。');
      const normalized = encoded.map((v) => this.normalize(v));
      this.db.exec('BEGIN');
      try {
        batch.forEach((text, index) => {
          const buffer = Buffer.alloc(this.encoder.dimensions * 4);
          normalized[index].forEach((value, dim) => {
            buffer.writeFloatLE(value, dim * 4);
          });
          write.run(this.key(text), buffer);
          vectors.set(text, normalized[index]);
        });
        this.db.exec('COMMIT');
      } catch (error) {
        this.db.exec('ROLLBACK');
        throw error;
      }
      progress(
        texts.length - missing.length + Math.min(i + 16, missing.length),
        texts.length,
      );
    }
    check(signal);
    return rows.map(({ task, candidates, exact }) => {
      const base = {
        sourceAssetId: task.source.id,
        sourceName: task.source.name,
        type: task.source.type,
        sourceParentName: task.parentName,
      };
      if (exact.length)
        return {
          ...base,
          options: exact.slice(0, 3),
          status: exact.length === 1 ? 'suggested' : 'ambiguous',
          reason:
            exact.length === 1
              ? '当前项目内名称相同，请确认后应用。'
              : '当前项目有多个同名候选，请明确选择。',
        };
      if (!candidates.length)
        return {
          ...base,
          options: [],
          status: 'unmatched',
          reason: '没有同类型且具有提示词的合法候选。',
        };
      const query = vectors.get(
        textFor(task.source.type, task.source.name, task.parentName),
      ) as number[];
      const options = candidates
        .map((candidate) => {
          const vector = vectors.get(
            textFor(candidate.type, candidate.name, candidate.parentName),
          ) as number[];
          const similarity = Math.max(
            -1,
            Math.min(
              1,
              query.reduce((sum, n, i) => sum + n * vector[i], 0),
            ),
          );
          return { ...candidate, similarity };
        })
        .filter((c) => c.similarity > 0)
        .sort(
          (a, b) =>
            b.similarity - a.similarity ||
            a.assetId - b.assetId ||
            (a.variantId ?? 0) - (b.variantId ?? 0),
        )
        .slice(0, 3);
      return {
        ...base,
        options: options as (Candidate & { similarity: number })[],
        status: options.length ? 'ambiguous' : 'unmatched',
        reason: options.length
          ? '本地向量候选；相似度不是准确率，请核对主体、年龄和服装后选择。'
          : '未检索到相近候选，保留未匹配。',
      };
    });
  }
}
