import { request } from '@umijs/max';
import type { PropAsset } from './service';

export type PropProductionMode = 'INDEPENDENT' | 'SHARED' | 'VARIANTS' | 'SCENE';
export type PropProductionPolicy = {
  sourceId: number;
  sourceName: string;
  mode: PropProductionMode;
  name: string;
  targetId: number;
  sceneIds: number[];
  defaultVariantId?: number;
  variantMap: Record<string, number>;
  allowedVariantIds: number[];
  reason: string;
};
export type PropSimplificationGroup = {
  name: string;
  mode: PropProductionMode;
  targetId: number | null;
  memberIds: number[];
  sceneIds: number[];
  reason: string;
  evidence: { episodeId: number; quote: string }[];
  variants: {
    name: string;
    appearance: string;
    prompt: string;
    sourceVariantIds: number[];
    sourceAssetIds: number[];
  }[];
};
export type PropSimplificationStatus = {
  id?: number;
  status: 'IDLE' | 'RUNNING' | 'READY' | 'APPLIED' | 'REVERTED' | 'FAILED';
  executionId?: number;
  appliedPlanId?: number;
  errorMessage?: string;
  decision?: { groups?: PropSimplificationGroup[] };
  inventory?: {
    props: { id: number; name: string; appearance?: string }[];
    scenes: { id: number; name: string }[];
    episodes?: { id: number; episode_no: number }[];
    variants: { id: number; asset_id: number; name: string }[];
  };
  production: { policies: PropProductionPolicy[]; needsReview: boolean };
};
const path = (projectId: number) => `/api/projects/${projectId}/prop-simplification`;
export const queryPropSimplification = (projectId: number) =>
  request<{ data: PropSimplificationStatus }>(path(projectId));
export const submitPropSimplification = (projectId: number) =>
  request<{ data: API.AiExecutionResponse }>(path(projectId), {
    method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
export const applyPropSimplification = (projectId: number, planId: number, groups: PropSimplificationGroup[]) =>
  request<{ data: PropSimplificationStatus }>(`${path(projectId)}/${planId}/apply`, { method: 'POST', data: { groups } });
export const undoPropSimplification = (projectId: number, planId: number) =>
  request<{ data: PropSimplificationStatus }>(`${path(projectId)}/${planId}/undo`, { method: 'POST' });

export function propProductionView(props: PropAsset[], policies: PropProductionPolicy[]) {
  const bySource = new Map(policies.map((p) => [p.sourceId, p]));
  const assets = new Map(props.map((p) => [p.id, p]));
  const items = new Map<number, PropAsset>();
  const sceneItems: PropProductionPolicy[] = [];
  for (const prop of props) {
    const policy = bySource.get(prop.id);
    if (policy?.mode === 'SCENE') { sceneItems.push(policy); continue; }
    const target = policy && assets.get(policy.targetId);
    const id = target ? target.id : prop.id;
    const current = items.get(id) ?? { ...(target || prop) };
    if (target && policy) {
      current.production = {
        name: policy.name, mode: policy.mode,
        sourceNames: [...new Set([...(current.production?.sourceNames ?? []), prop.name])],
      };
    }
    items.set(id, current);
  }
  return { items: [...items.values()], sceneItems };
}
