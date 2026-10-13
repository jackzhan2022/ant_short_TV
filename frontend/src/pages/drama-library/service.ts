import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export type Drama = {
  id: number;
  tenantId: number;
  title: string;
  description?: string | null;
  createdAt: string;
};
export type Episode = {
  id: number;
  dramaId: number;
  episodeNo: number;
  title: string;
  sourceType: string;
  sourceProjectId?: number | null;
  sourceVersionId?: number | null;
  fileSize?: number | null;
  createdAt: string;
};
export type Miniapp = {
  id: number;
  appid: string;
  name: string;
  bindingType: string;
  authStatus?: string;
  enabled: boolean;
  createdAt: string;
};
export type Release = {
  id: number;
  miniappId: number;
  dramaId: number;
  title: string;
  description?: string | null;
  published: boolean;
  createdAt: string;
};
export type DramaInput = { title: string; description?: string };
export type EpisodeInput = { episodeNo: number; title: string } & (
  | { uploadSessionToken: string; projectId?: never; sourceVersionId?: never }
  | { projectId: number; sourceVersionId: number; uploadSessionToken?: never }
);

export async function call<T>(
  url: string,
  method = 'GET',
  data?: unknown,
): Promise<T> {
  const response = await request<ApiResponse<T>>(url, { method, data });
  if (!response.success) throw new Error(response.errorMessage || '请求失败');
  return response.data;
}
export type AuthorizationAvailability = {
  status: 'DISABLED' | 'NOT_CONFIGURED' | 'WAITING_TICKET' | 'READY';
  available: boolean;
};
export type AuthorizationCreated = {
  requestId: string;
  authorizationUrl: string;
  expiresAt: string;
};
export type AuthorizationRequestStatus = {
  requestId: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  appid: string | null;
  expiresAt: string;
};
const authorization = '/api/team-miniapps/authorization';
export const getAuthorizationAvailability = () =>
  call<AuthorizationAvailability>(`${authorization}/status`);
export const createAuthorizationRequest = () =>
  call<AuthorizationCreated>(`${authorization}/requests`, 'POST');
export const getAuthorizationRequest = (id: string) =>
  call<AuthorizationRequestStatus>(
    `${authorization}/requests/${encodeURIComponent(id)}`,
  );
export const canUseMiniapp = (app: Miniapp) =>
  app.bindingType !== 'OFFICIAL' || app.authStatus === 'AUTHORIZED';

const library = '/api/drama-library';
const miniapps = '/api/team-miniapps';
export const listDramas = () => call<Drama[]>(library);
export const getDrama = (id: number) => call<Drama>(`${library}/${id}`);
export const saveDrama = (data: DramaInput, id?: number) =>
  call<Drama>(id ? `${library}/${id}` : library, id ? 'PUT' : 'POST', data);
export const deleteDrama = (id: number) =>
  call<void>(`${library}/${id}`, 'DELETE');
export const listEpisodes = (id: number) =>
  call<Episode[]>(`${library}/${id}/episodes`);
export const importEpisode = (id: number, data: EpisodeInput) =>
  call<Episode>(`${library}/${id}/episodes`, 'POST', data);
export const deleteEpisode = (id: number, episodeId: number) =>
  call<void>(`${library}/${id}/episodes/${episodeId}`, 'DELETE');
export const listMiniapps = () => call<Miniapp[]>(miniapps);
export const bindMiniapp = (data: { appid: string; name: string }) =>
  call<Miniapp>(miniapps, 'POST', data);
export const enableMiniapp = (id: number, enabled: boolean) =>
  call<Miniapp>(`${miniapps}/${id}`, 'PATCH', { enabled });
export const listReleases = (id: number) =>
  call<Release[]>(`${miniapps}/${id}/dramas`);
export const publishDrama = (
  id: number,
  dramaId: number,
  episodeIds: number[],
) =>
  call<Release>(`${miniapps}/${id}/dramas/${dramaId}`, 'PUT', { episodeIds });
export const unpublishDrama = (id: number, dramaId: number) =>
  call<void>(`${miniapps}/${id}/dramas/${dramaId}`, 'DELETE');
