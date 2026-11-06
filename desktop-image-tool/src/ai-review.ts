import { randomUUID } from 'node:crypto';
import { type MatchTask, matchAssetNames } from './ai-match';
import type {
  AiApplyInput,
  AiApplyResult,
  AiMatchMode,
  AiMatchProgress,
  AiMatchReview,
  AiModelSettings,
  AiSuggestion,
} from './ai-match-types';
import { type CloudClient, CloudRequestError } from './cloud';
import type { Candidate, SourceAsset, SourceProject } from './workspace-shared';

type Client = Pick<CloudClient, 'get' | 'send'>;
interface Connection {
  client: Client;
  identity: string;
}
interface Snapshot {
  review: AiMatchReview;
  connection: Connection;
  expires: number;
  project: string;
  tasks: Map<number, MatchTask>;
}
const sourceStamp = (a: SourceAsset) =>
  JSON.stringify([
    a.id,
    a.name,
    a.type,
    a.sourceParentId,
    a.active,
    a.matchStatus,
    a.targetAssetId,
    a.targetVariantId,
    a.targetName,
    a.hasPrompt,
  ]);
const projectStamp = (p: SourceProject) =>
  JSON.stringify([p.id, p.active, p.targetProjectId, p.name, p.syncedAt]);
const optionStamp = (c: Candidate) =>
  JSON.stringify([
    c.assetId,
    c.variantId,
    c.name,
    c.type,
    c.parentName,
    c.hasPrompt,
  ]);
const candidateStamp = (rows: Candidate[]) =>
  JSON.stringify(rows.map(optionStamp).sort());
const pending = (a: SourceAsset) =>
  a.active &&
  a.matchStatus === 'UNMATCHED' &&
  !a.targetAssetId &&
  !a.targetVariantId;

export class AiReviewManager {
  private snapshot?: Snapshot;
  private controller?: AbortController;
  private applying = false;
  private applyController?: AbortController;
  private applyingReviewId?: string;
  constructor(
    private connect: () => Connection,
    private model: () => { settings: AiModelSettings; key: string },
    private matchNames: typeof matchAssetNames = matchAssetNames,
    private progress: (value: AiMatchProgress) => void = () => {},
    private vectorMatch?: (
      tasks: MatchTask[],
      signal: AbortSignal,
      progress: (completed: number, total: number, detail?: string) => void,
    ) => Promise<AiSuggestion[]>,
  ) {}
  cancel(reviewId?: string) {
    if (
      reviewId &&
      this.snapshot?.review.reviewId !== reviewId &&
      this.applyingReviewId !== reviewId
    )
      return;
    this.controller?.abort();
    this.applyController?.abort();
    this.controller = undefined;
    this.snapshot = undefined;
  }
  private current(connection: Connection) {
    const next = this.connect();
    if (
      next.client !== connection.client ||
      next.identity !== connection.identity
    )
      throw new Error('团队或连接已变化，请重新进行 AI 匹配。');
  }
  private async inventory(connection: Connection, projectId: number) {
    this.current(connection);
    const projects = await connection.client.get<SourceProject[]>(
      '/api/desktop/source-projects',
    );
    this.current(connection);
    const project = projects.find((p) => p.id === projectId && p.active);
    if (!project?.targetProjectId)
      throw new Error('项目未绑定或已变化，请先绑定剧智创项目。');
    const assets = await connection.client.get<SourceAsset[]>(
      `/api/desktop/source-projects/${projectId}/assets`,
    );
    this.current(connection);
    return { project, assets };
  }
  private async candidates(
    connection: Connection,
    projectId: number,
    a: SourceAsset,
    parentTargetId?: number,
  ) {
    const rows = await connection.client.get<Candidate[]>(
      `/api/desktop/source-projects/${projectId}/candidates?type=${a.type}${a.sourceParentId ? `&sourceParentId=${encodeURIComponent(a.sourceParentId)}` : ''}`,
    );
    this.current(connection);
    if (!Array.isArray(rows) || rows.length > 2000)
      throw new Error('候选资产过多或格式无效，请检查项目。');
    return rows.filter(
      (c) =>
        c.type === a.type &&
        c.hasPrompt &&
        Number.isSafeInteger(c.assetId) &&
        c.assetId > 0 &&
        (a.sourceParentId
          ? c.assetId === parentTargetId &&
            Number.isSafeInteger(c.variantId) &&
            (c.variantId as number) > 0
          : c.variantId === null),
    );
  }
  async match(
    projectId: number,
    mode: AiMatchMode = 'llm',
  ): Promise<AiMatchReview> {
    if (mode !== 'vector' && mode !== 'llm') throw new Error('匹配方式无效。');
    if (this.applying) throw new Error('正在保存匹配，请稍后重试。');
    if (!Number.isSafeInteger(projectId) || projectId < 1)
      throw new Error('项目编号无效。');
    this.cancel();
    const controller = new AbortController();
    this.controller = controller;
    const connection = this.connect();
    this.progress({ projectId, phase: 'preparing', completed: 0, total: 0 });
    const { project, assets } = await this.inventory(connection, projectId);
    const tasks: MatchTask[] = [];
    const skipped: AiSuggestion[] = [];
    const cache = new Map<string, Candidate[]>();
    const check = () => {
      this.current(connection);
      if (controller.signal.aborted || this.controller !== controller)
        throw new Error('AI 匹配已取消。');
    };
    for (const a of assets.filter(pending)) {
      check();
      const parent = a.sourceParentId
        ? assets.find((p) => p.sourceAssetId === a.sourceParentId && p.active)
        : undefined;
      if (
        a.sourceParentId &&
        (!parent ||
          parent.type !== a.type ||
          parent.sourceParentId ||
          !['MATCHED', 'NO_PROMPT'].includes(parent.matchStatus) ||
          !parent.targetAssetId)
      ) {
        skipped.push({
          sourceAssetId: a.id,
          sourceName: a.name,
          type: a.type,
          sourceParentName: parent?.name,
          options: [],
          reason: '请先匹配主体，再匹配其变体。',
          status: 'parentRequired',
        });
        continue;
      }
      const group = `${a.type}:${a.sourceParentId ?? ''}`;
      let candidates = cache.get(group);
      if (!candidates) {
        candidates = await this.candidates(
          connection,
          projectId,
          a,
          parent?.targetAssetId ?? undefined,
        );
        cache.set(group, candidates);
      }
      tasks.push({
        source: structuredClone(a),
        parentName: parent?.name,
        candidates: structuredClone(candidates),
      });
    }
    check();
    const suggestions: AiSuggestion[] = [];
    const nonempty = tasks.filter((t) => t.candidates.length);
    if (nonempty.length && mode === 'vector') {
      if (!this.vectorMatch) throw new Error('本地向量匹配尚未初始化。');
      try {
        suggestions.push(
          ...(await this.vectorMatch(
            nonempty,
            controller.signal,
            (completed, total, detail) => {
              check();
              this.progress({
                projectId,
                phase: 'vector',
                completed,
                total,
                detail,
              });
            },
          )),
        );
      } catch (error) {
        controller.abort();
        if (this.controller === controller) this.controller = undefined;
        throw error;
      }
    } else if (nonempty.length) {
      const model = this.model();
      let next = 0;
      let completed = 0;
      let failure: unknown;
      let failed = false;
      const report = () =>
        this.progress({
          projectId,
          phase: 'matching',
          completed,
          total: nonempty.length,
        });
      report();
      const worker = async () => {
        try {
          while (next < nonempty.length) {
            check();
            const start = next;
            next += 25;
            const batch = nonempty.slice(start, start + 25);
            const result = await this.matchNames(
              batch,
              model.settings,
              model.key,
              controller.signal,
            );
            check();
            suggestions.push(...result);
            completed += batch.length;
            report();
          }
        } catch (error) {
          if (!failed) {
            failed = true;
            failure = error;
          }
          controller.abort();
        }
      };
      await Promise.all(
        Array.from(
          { length: Math.min(3, Math.ceil(nonempty.length / 25)) },
          worker,
        ),
      );
      if (failed) {
        if (this.controller === controller) this.controller = undefined;
        throw failure;
      }
    }
    for (const task of tasks.filter((t) => !t.candidates.length))
      suggestions.push({
        sourceAssetId: task.source.id,
        sourceName: task.source.name,
        type: task.source.type,
        sourceParentName: task.parentName,
        options: [],
        reason: '没有同类型且具有提示词的合法候选。',
        status: 'unmatched',
      });
    check();
    const review: AiMatchReview = {
      reviewId: randomUUID(),
      projectId,
      targetProjectId: project.targetProjectId as number,
      model: nonempty.length
        ? mode === 'vector'
          ? '本地向量 · BGE-small-zh-v1.5'
          : this.model().settings.model
        : '',
      suggestions: [...suggestions, ...skipped].sort(
        (a, b) =>
          assets.findIndex((s) => s.id === a.sourceAssetId) -
          assets.findIndex((s) => s.id === b.sourceAssetId),
      ),
    };
    this.snapshot = {
      review: structuredClone(review),
      connection,
      expires: Date.now() + 10 * 60 * 1000,
      project: projectStamp(project),
      tasks: new Map(tasks.map((t) => [t.source.id, t])),
    };
    this.controller = undefined;
    return review;
  }
  async apply(input: AiApplyInput): Promise<AiApplyResult> {
    const snapshot = this.snapshot;
    if (
      !input ||
      !snapshot ||
      input.reviewId !== snapshot.review.reviewId ||
      Date.now() > snapshot.expires
    )
      throw new Error('AI 推荐已失效，请重新匹配。');
    if (this.applying) throw new Error('正在保存匹配，请稍后重试。');
    if (
      !Array.isArray(input.choices) ||
      !input.choices.length ||
      input.choices.length > 500 ||
      new Set(input.choices.map((c) => c?.sourceAssetId)).size !==
        input.choices.length
    )
      throw new Error('请选择 1～500 个不同资产，不能重复。');
    const choices = input.choices.map((c) => {
      const suggestion = snapshot.review.suggestions.find(
        (s) => s.sourceAssetId === c?.sourceAssetId,
      );
      const option = suggestion?.options.find(
        (o) =>
          o.assetId === c.assetId &&
          (o.variantId ?? null) === (c.variantId ?? null) &&
          o.hasPrompt,
      );
      const task = snapshot.tasks.get(c?.sourceAssetId);
      if (!option || !task)
        throw new Error('所选资产不在本次推荐候选中，请重新选择。');
      return { task, option };
    });
    this.applying = true;
    const applying = new AbortController();
    this.applyController = applying;
    this.applyingReviewId = input.reviewId;
    const checkCancellation = () => {
      if (applying.signal.aborted) throw new Error('AI 匹配保存已取消。');
    };
    this.snapshot = undefined; // Consume on submission: retries require a fresh review.
    try {
      const { project, assets } = await this.inventory(
        snapshot.connection,
        snapshot.review.projectId,
      );
      checkCancellation();
      if (projectStamp(project) !== snapshot.project)
        throw new Error('项目绑定或资产清单已变化，请重新匹配。');
      // Check the entire selection before writing its first match.
      const candidateGroups = new Map<string, Candidate[]>();
      for (const { task } of choices) {
        const current = assets.find((a) => a.id === task.source.id);
        if (!current || sourceStamp(current) !== sourceStamp(task.source))
          throw new Error('来源资产已变化，请重新匹配。');
        const parent = current.sourceParentId
          ? assets.find(
              (a) => a.sourceAssetId === current.sourceParentId && a.active,
            )
          : undefined;
        if (task.parentName !== parent?.name)
          throw new Error('主体名称已变化，请重新匹配。');
        const group = `${current.type}:${current.sourceParentId ?? ''}:${parent?.targetAssetId ?? ''}`;
        let now = candidateGroups.get(group);
        if (!now) {
          now = await this.candidates(
            snapshot.connection,
            project.id,
            current,
            parent?.targetAssetId ?? undefined,
          );
          candidateGroups.set(group, now);
        }
        checkCancellation();
        if (candidateStamp(now) !== candidateStamp(task.candidates))
          throw new Error('候选名称、提示词状态或主体关系已变化，请重新匹配。');
      }
      const result: AiApplyResult = { applied: 0, assets, failures: [] };
      for (const { task, option } of choices) {
        try {
          checkCancellation();
          this.current(snapshot.connection);
          const saved = await snapshot.connection.client.send<SourceAsset>(
            `/api/desktop/source-projects/assets/${task.source.id}/ai-match`,
            'PUT',
            {
              assetId: option.assetId,
              variantId: option.variantId,
              expected: {
                sourceName: task.source.name,
                sourceParentId: task.source.sourceParentId,
                sourceTargetAssetId: task.source.targetAssetId,
                sourceTargetVariantId: task.source.targetVariantId,
                sourceMatchStatus: task.source.matchStatus,
                targetProjectId: snapshot.review.targetProjectId,
                candidates: task.candidates,
              },
            },
          );
          this.current(snapshot.connection);
          result.assets = result.assets.map((a) =>
            a.id === saved.id ? saved : a,
          );
          result.applied++;
        } catch (error) {
          result.failures.push({
            sourceAssetId: task.source.id,
            message:
              error instanceof CloudRequestError && error.status === 404
                ? '当前云端尚不支持 AI 匹配确认，请先更新配套后端。'
                : '本条保存未确认，请刷新匹配状态后重试。',
          });
          // A network or identity failure may have committed on the server; never retry automatically.
          break;
        }
      }
      const completed = new Set(
        result.assets
          .filter((a) => a.matchStatus === 'MATCHED')
          .map((a) => a.id),
      );
      for (const { task } of choices)
        if (
          !completed.has(task.source.id) &&
          !result.failures.some((f) => f.sourceAssetId === task.source.id)
        )
          result.failures.push({
            sourceAssetId: task.source.id,
            message: '尚未保存，请刷新后重新匹配。',
          });
      return result;
    } finally {
      this.applying = false;
      this.applyController = undefined;
      this.applyingReviewId = undefined;
    }
  }
}
