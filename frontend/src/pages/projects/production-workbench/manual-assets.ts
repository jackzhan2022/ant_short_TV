import { pointEstimatedRequest as request } from '@/services/point-estimate';
import type { ApiResponse } from './service';

export type ManualAssetType = 'CHARACTER' | 'SCENE' | 'PROP';
export type ConfigurationField = {
  key: string;
  label: string;
  group: string;
  placeholder: string;
  unknown?: string | null;
  textarea: boolean;
  maxLength: number;
};
export type AssetDefinition = { fields: ConfigurationField[]; task: string };
export type ManualAssetOptions = {
  scriptId: number | null;
  canCreate: boolean;
  disabledReason: string;
  definitions: Record<ManualAssetType, AssetDefinition>;
};
export type ManualAssetCommand = {
  requestId: string;
  scriptId: number;
  assetType: ManualAssetType;
  name: string;
  aliases: string[];
  configuration: Record<string, string>;
  ownerCharacterId?: number;
};
export type CreatedManualAsset = {
  assetType: ManualAssetType;
  assetId: number;
  variantId: number;
};
export const queryManualAssetOptions = (projectId: number) =>
  request<ApiResponse<ManualAssetOptions>>(
    `/api/projects/${projectId}/manual-assets/configuration`,
  );
export const createManualAsset = (
  projectId: number,
  data: ManualAssetCommand,
) =>
  request<ApiResponse<CreatedManualAsset>>(
    `/api/projects/${projectId}/manual-assets`,
    { method: 'POST', data, skipErrorHandler: true },
  );

const characterOrder = [
  'era',
  'country',
  'ethnicity',
  'genre',
  'face',
  'hair',
  'earrings',
  'body',
  'ratio',
  'upper',
  'lower',
  'shoes',
  'gender',
  'age',
];
const unknownValues = new Set([
  '未明确',
  '未明确（待确认）',
  '未知',
  '不详',
  '待补充',
  '待确认',
  '未填写',
  '待填写',
  '暂无',
  '不确定',
  '无依据',
  '未明确(待确认)',
]);
export const normalizeAssetName = (name: string) =>
  name.toLowerCase().replace(/[^\p{L}\p{Nd}]/gu, '');
export function fieldIssue(field: ConfigurationField, raw?: string) {
  const value = raw?.trim() || '';
  if (!value || value.length > field.maxLength)
    return `${field.label}不能为空且不能超过 ${field.maxLength} 字符。`;
  if (
    !normalizeAssetName(value) ||
    /\{[^}]*\}/s.test(value) ||
    value.includes('[待填写') ||
    (unknownValues.has(value) && value !== field.unknown)
  )
    return `请为${field.label}填写具体设定。`;
  return undefined;
}
export function buildManualAssetPrompt(
  type: ManualAssetType,
  name: string,
  definition: AssetDefinition,
  values: Record<string, string>,
) {
  const value = (field: ConfigurationField) =>
    values[field.key]?.trim() || `[待填写：${field.label}]`;
  const description = definition.fields
    .map((field) => `${field.label}：${value(field)}`)
    .join('；');
  if (type === 'CHARACTER') {
    return `${definition.task}\n\n角色描述:\n\n${[...definition.fields]
      .sort(
        (left, right) =>
          characterOrder.indexOf(left.key) - characterOrder.indexOf(right.key),
      )
      .map((field) => `- **${field.label}**: ${value(field)}`)
      .join('\n')}\n\n展示状态：自然站立、中性表情、双手自然下垂。`;
  }
  if (type === 'SCENE')
    return `${definition.task}\n\n场景描述: ${description}。`;
  return `${name.trim() || '[待填写：道具名称]'}。${description}。${definition.task}`;
}
