import { request } from '@umijs/max';

export type CreationMode = 'IMAGE' | 'VIDEO';

export type ReferenceConstraint = {
  formats?: string[];
  minCount?: number;
  maxCount?: number;
  maxBytes?: number;
  minDurationSeconds?: number;
  maxDurationSeconds?: number;
  maxTotalDurationSeconds?: number;
  minDimension?: number;
  maxDimension?: number;
  minAspectRatio?: number;
  maxAspectRatio?: number;
};

export type CreationModel = {
  id: number;
  name: string;
  providerCode?: string;
  providerName?: string;
  description?: string;
  serviceType: CreationMode;
  isDefault: boolean;
  configStatus: 'configured' | 'absent' | 'invalid';
  quality?: string;
  outputFormat?: string;
  duration?: { min: number; max: number; intelligent?: boolean };
  resolutions: string[];
  references: {
    image?: ReferenceConstraint;
    video?: ReferenceConstraint;
    audio?: ReferenceConstraint;
  };
};

type ModelResponse = {
  id: number;
  providerId: number;
  name: string;
  providerName?: string;
  description?: string;
  serviceType: string;
  status: string;
  isDefault?: boolean;
  sort?: number;
  capabilities?: string[];
  configJson?: unknown;
};
type ProviderResponse = { id: number; code?: string; status: string };
type ApiResponse<T> = { success: boolean; data: T };

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readConfiguration(
  model: ModelResponse,
  serviceType: CreationMode,
): CreationModel {
  const result: CreationModel = {
    id: model.id,
    name: model.name,
    providerName: model.providerName,
    description: model.description,
    serviceType,
    isDefault: model.isDefault === true,
    configStatus: 'absent',
    resolutions: [],
    references: {},
  };
  if (model.configJson == null || model.configJson === '') return result;
  if (typeof model.configJson !== 'string') {
    result.configStatus = 'invalid';
    return result;
  }
  if (!model.configJson.trim()) return result;
  let root: unknown;
  try {
    root = JSON.parse(model.configJson);
  } catch {
    result.configStatus = 'invalid';
    return result;
  }
  if (!isObject(root)) {
    result.configStatus = 'invalid';
    return result;
  }

  let invalid = false;
  let configured = false;
  const strings = (value: unknown): string[] | undefined => {
    if (value === undefined) return undefined;
    if (!Array.isArray(value)) {
      invalid = true;
      return undefined;
    }
    const accepted = value.filter((item): item is string => {
      if (typeof item === 'string' && item.trim()) return true;
      invalid = true;
      return false;
    });
    const values = [...new Set(accepted.map((item) => item.trim()))];
    if (values.length) configured = true;
    return values;
  };
  const number = (
    value: unknown,
    integer = false,
    allowZero = false,
  ): number | undefined => {
    if (value === undefined) return undefined;
    if (
      typeof value !== 'number' ||
      !Number.isFinite(value) ||
      value > Number.MAX_SAFE_INTEGER ||
      (allowZero ? value < 0 : value <= 0) ||
      (integer && !Number.isSafeInteger(value))
    ) {
      invalid = true;
      return undefined;
    }
    configured = true;
    return value;
  };
  const reference = (value: unknown): ReferenceConstraint | undefined => {
    if (value === undefined) return undefined;
    if (!isObject(value)) {
      invalid = true;
      return undefined;
    }
    const constraint: ReferenceConstraint = {};
    const formats = strings(value.formats);
    if (formats?.length) constraint.formats = formats;
    const integerFields = [
      'minCount',
      'maxCount',
      'maxBytes',
      'minDimension',
      'maxDimension',
    ] as const;
    const decimalFields = [
      'minDurationSeconds',
      'maxDurationSeconds',
      'maxTotalDurationSeconds',
      'minAspectRatio',
      'maxAspectRatio',
    ] as const;
    for (const field of [...integerFields, ...decimalFields]) {
      const parsed = number(
        value[field],
        integerFields.some((key) => key === field),
        field === 'minCount' || field === 'maxCount',
      );
      if (parsed !== undefined) constraint[field] = parsed;
    }
    for (const [min, max] of [
      ['minCount', 'maxCount'],
      ['minDimension', 'maxDimension'],
      ['minDurationSeconds', 'maxDurationSeconds'],
      ['minAspectRatio', 'maxAspectRatio'],
    ] as const) {
      const lower = constraint[min];
      const upper = constraint[max];
      if (lower !== undefined && upper !== undefined && lower > upper) {
        delete constraint[min];
        delete constraint[max];
        invalid = true;
      }
    }
    return Object.keys(constraint).length ? constraint : undefined;
  };

  if (serviceType === 'IMAGE') {
    for (const field of ['quality', 'outputFormat'] as const) {
      const value = root[field];
      if (value === undefined) continue;
      if (typeof value === 'string' && value.trim()) {
        result[field] = value.trim();
        configured = true;
      } else invalid = true;
    }
  } else if (root.videoGeneration !== undefined) {
    const video = root.videoGeneration;
    if (!isObject(video)) invalid = true;
    else {
      result.resolutions = strings(video.resolutions) ?? [];
      if (video.duration !== undefined) {
        if (!isObject(video.duration)) invalid = true;
        else {
          const min = number(video.duration.min, true);
          const max = number(video.duration.max, true);
          if (min !== undefined && max !== undefined && min <= max) {
            result.duration = { min, max };
            if (typeof video.duration.intelligent === 'boolean') {
              result.duration.intelligent = video.duration.intelligent;
            } else if (video.duration.intelligent !== undefined) invalid = true;
          } else invalid = true;
        }
      }
      for (const field of ['image', 'video', 'audio'] as const) {
        const parsed = reference(video[field]);
        if (parsed) result.references[field] = parsed;
      }
    }
  }
  result.configStatus = invalid
    ? 'invalid'
    : configured
      ? 'configured'
      : 'absent';
  return result;
}

export function normalizeCreationModels(
  models: ModelResponse[],
  providers: ProviderResponse[],
): CreationModel[] {
  const providerCodes = new Map(
    providers.map((provider) => [provider.id, provider.code]),
  );
  const enabledProviders = new Set(
    providers
      .filter((item) => item.status === 'ENABLED')
      .map((item) => item.id),
  );
  return models
    .filter(
      (model) =>
        model.status === 'ENABLED' &&
        enabledProviders.has(model.providerId) &&
        (model.serviceType === 'IMAGE' || model.serviceType === 'VIDEO') &&
        Array.isArray(model.capabilities) &&
        model.capabilities.includes(`${model.serviceType}_GENERATION`),
    )
    .sort(
      (left, right) =>
        Number(right.isDefault === true) - Number(left.isDefault === true) ||
        (right.sort ?? 0) - (left.sort ?? 0) ||
        right.id - left.id,
    )
    .map((model) => ({
      ...readConfiguration(model, model.serviceType as CreationMode),
      providerCode: providerCodes.get(model.providerId),
    }));
}

export async function loadCreationModels(
  signal?: AbortSignal,
): Promise<CreationModel[]> {
  try {
    const options = { method: 'GET', skipErrorHandler: true, signal };
    const response = await request<
      ApiResponse<{ models: ModelResponse[]; providers: ProviderResponse[] }>
    >('/api/free-creations/models', options);
    if (
      !response?.success ||
      !Array.isArray(response.data?.models) ||
      !Array.isArray(response.data?.providers)
    ) {
      throw new Error('Invalid model list response');
    }
    return normalizeCreationModels(response.data.models, response.data.providers);
  } catch (error) {
    if (signal?.aborted || (isObject(error) && error.name === 'AbortError'))
      throw error;
    throw new Error('模型列表加载失败，请稍后重试');
  }
}
