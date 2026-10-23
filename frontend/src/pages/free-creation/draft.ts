import type {
  CreationParameters,
  CreationRecord,
  CreationRequest,
} from './api';
import type { ReferenceDraft } from './ReferenceFiles';
import type { CreationMode, CreationModel } from './service';
export type CreationDraft = {
  modelId?: number;
  prompt: string;
  parameters: CreationParameters;
  references: ReferenceDraft[];
};
export function imageCountOptions(model: CreationModel): number[] {
  return model.providerCode?.toLowerCase() === 'openai' ? [1] : [1, 2, 4];
}
export function newDraft(model?: CreationModel): CreationDraft {
  return {
    modelId: model?.id,
    prompt: '',
    references: [],
    parameters:
      model?.serviceType === 'VIDEO'
        ? {
            ...(model.duration ? { durationSeconds: model.duration.min } : {}),
            ...(model.resolutions[0]
              ? { resolution: model.resolutions[0] }
              : {}),
          }
        : {
            count: 1,
            ...(model?.quality ? { quality: model.quality } : {}),
            ...(model?.outputFormat
              ? { outputFormat: model.outputFormat }
              : {}),
          },
  };
}
export function draftError(
  draft: CreationDraft,
  model: CreationModel | undefined,
): string | undefined {
  if (!model) return '当前模型不可用，请重新选择模型';
  if (model.configStatus === 'invalid') return '模型配置有误，请联系管理员检查';
  if (!draft.prompt.trim()) return '请先输入创意描述';
  if (draft.prompt.length > 2000) return '创意描述不能超过 2000 字';
  if (
    draft.references.some(
      (item) =>
        item.status !== 'complete' ||
        (!item.uploadSessionToken && !item.referenceId),
    )
  )
    return '请等待所有参考素材上传并完成校验，失败或暂停的素材请重试或移除';
  for (const kind of ['image', 'video', 'audio'] as const) {
    const refs = draft.references.filter((item) => item.kind === kind);
    const rule = model.references[kind];
    if (model.serviceType === 'VIDEO' && refs.length && !rule)
      return '当前模型不支持这些参考素材，请移除后重试';
    if (rule?.minCount !== undefined && refs.length < rule.minCount)
      return `参考${kind === 'image' ? '图片' : kind === 'video' ? '视频' : '音频'}至少需要 ${rule.minCount} 个`;
    if (rule?.maxCount !== undefined && refs.length > rule.maxCount)
      return '参考素材数量超过当前模型限制';
    if (
      rule?.maxTotalDurationSeconds !== undefined &&
      refs.every((item) => item.durationSeconds !== undefined) &&
      refs.reduce((sum, item) => sum + (item.durationSeconds || 0), 0) >
        rule.maxTotalDurationSeconds
    )
      return '参考素材总时长超过当前模型限制';
  }
  const p = draft.parameters;
  if (
    model.serviceType === 'IMAGE' &&
    ((model.quality !== undefined && p.quality !== model.quality) ||
      (model.outputFormat !== undefined &&
        p.outputFormat !== model.outputFormat))
  )
    return '模型质量或输出格式配置已变化，请刷新页面后重新选择模型';
  if (
    model.serviceType === 'IMAGE' &&
    !imageCountOptions(model).includes(p.count ?? 1)
  )
    return '请选择有效的图片数量';
  if (model.serviceType === 'VIDEO') {
    if (p.intelligentDuration && !model.duration?.intelligent)
      return '当前模型不支持智能时长';
    if (
      !p.intelligentDuration &&
      model.duration &&
      (p.durationSeconds === undefined ||
        !Number.isInteger(p.durationSeconds) ||
        p.durationSeconds < model.duration.min ||
        p.durationSeconds > model.duration.max)
    )
      return '视频时长超出当前模型范围';
    if (p.resolution && !model.resolutions.includes(p.resolution))
      return '当前模型不支持所选分辨率';
  }
  return undefined;
}
export function requestFromDraft(
  draft: CreationDraft,
  mode: CreationMode,
  requestId: string,
): CreationRequest {
  return {
    requestId,
    mediaType: mode,
    modelId: draft.modelId as number,
    prompt: draft.prompt.trim(),
    parameters: { ...draft.parameters },
    references: draft.references.map((item) => ({
      kind: item.kind.toUpperCase() as 'IMAGE' | 'VIDEO' | 'AUDIO',
      ...(item.referenceId
        ? { referenceId: item.referenceId }
        : { uploadSessionToken: item.uploadSessionToken }),
    })),
  };
}
export function reuseDraft(
  record: CreationRecord,
  models: CreationModel[],
): CreationDraft {
  const model = models.find(
    (item) =>
      item.id === record.modelId && item.serviceType === record.mediaType,
  );
  if (!model) throw new Error('原模型已停用或不可用，请手动选择模型后重新创作');
  if (record.references.some((ref) => !ref.referenceId || !ref.previewUrl))
    throw new Error('参考素材已失效或无访问权限，无法复用');
  const draft: CreationDraft = {
    modelId: model.id,
    prompt: record.prompt,
    parameters: { ...record.parameters },
    references: record.references.map((ref) => ({
      id: `reference-${ref.referenceId}`,
      referenceId: ref.referenceId,
      kind: ref.kind.toLowerCase() as ReferenceDraft['kind'],
      name: ref.fileName,
      url:
        ref.kind === 'IMAGE' ? ref.thumbnailUrl || ref.previewUrl : undefined,
      status: 'complete',
      percent: 100,
    })),
  };
  if (model.serviceType === 'VIDEO' && draft.parameters.resolution) {
    const historicalResolution = draft.parameters.resolution.toLowerCase();
    draft.parameters.resolution =
      model.resolutions.find(
        (value) => value.toLowerCase() === historicalResolution,
      ) ?? draft.parameters.resolution;
  }
  const error = draftError(draft, model);
  if (error) throw new Error(`无法复用：${error}`);
  return draft;
}
