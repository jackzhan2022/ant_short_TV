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

export type CodeTemplate = {
  templateId: number;
  version: string;
  description: string;
  sourceAppid: string;
  sourceName: string;
};
export type CodeCommitInput = {
  templateId: number;
  version: string;
  description: string;
};
export const canManageCode = (app: Miniapp) =>
  app.enabled &&
  app.bindingType === 'OFFICIAL' &&
  app.authStatus === 'AUTHORIZED' &&
  /^wx[0-9a-fA-F]{16}$/.test(app.appid);

const codeOptions = (tenantId: number, signal: AbortSignal) => ({
  headers: { 'X-Tenant-Id': String(tenantId) },
  signal,
  skipErrorHandler: true,
  timeout: 60000,
});
export async function getCodeTemplates(tenantId: number, signal: AbortSignal) {
  const result = await request<ApiResponse<CodeTemplate[]>>(
    '/api/team-miniapps/code/templates',
    { ...codeOptions(tenantId, signal), method: 'GET' },
  );
  if (!result.success) throw new Error('模板加载失败');
  return result.data;
}
export async function commitCode(
  tenantId: number,
  id: number,
  data: CodeCommitInput,
  signal: AbortSignal,
): Promise<'SUCCEEDED' | 'UNKNOWN'> {
  // 普通 request 没有重试机制；网络异常无法证明微信未执行上传。
  try {
    const result = await request<ApiResponse<{ status: string }>>(
      `/api/team-miniapps/${id}/code/commit`,
      { ...codeOptions(tenantId, signal), method: 'POST', data },
    );
    if (result.success && result.data?.status === 'SUCCEEDED')
      return 'SUCCEEDED';
    return 'UNKNOWN';
  } catch (error) {
    const failure = error as {
      info?: { errorCode?: string };
      response?: { data?: { errorCode?: string } };
    };
    const code = failure?.info?.errorCode ?? failure?.response?.data?.errorCode;
    if (
      code &&
      [
        'WECHAT_CODE_REJECTED',
        'WECHAT_COMMIT_IN_PROGRESS',
        'FORBIDDEN',
        'VALIDATION_ERROR',
        'UNAUTHORIZED',
        'WECHAT_CODE_UPSTREAM_ERROR',
      ].includes(code)
    )
      throw error;
    return 'UNKNOWN';
  }
}
async function qrResponseError(value: unknown): Promise<Error> {
  // blob 模式下非 2xx 的 JSON 也以 Blob 返回；只保留已知错误码，不透传上游文本。
  if (
    value instanceof Blob &&
    value.size <= 64 * 1024 &&
    value.type.split(';')[0].trim().toLowerCase() === 'application/json'
  ) {
    try {
      const body: unknown = JSON.parse(await value.text());
      if (body && typeof body === 'object' && 'errorCode' in body) {
        const code = body.errorCode;
        if (
          typeof code === 'string' &&
          [
            'FORBIDDEN',
            'UNAUTHORIZED',
            'VALIDATION_ERROR',
            'WECHAT_CODE_REJECTED',
            'WECHAT_CODE_UPSTREAM_ERROR',
          ].includes(code)
        )
          return new Error(`二维码获取失败 (${code})`);
      }
    } catch {
      // 非 JSON 或畸形错误体统一按无效响应处理。
    }
  }
  return new Error('二维码响应无效');
}

export async function getTrialQrCode(
  tenantId: number,
  id: number,
  path: string,
  signal: AbortSignal,
): Promise<Blob> {
  // 已核对 Umi：getResponse 默认 false，blob 不包在 response.data 中。
  let blob: Blob;
  try {
    blob = await request<Blob>(`/api/team-miniapps/${id}/code/trial-qrcode`, {
      ...codeOptions(tenantId, signal),
      method: 'GET',
      responseType: 'blob',
      params: path ? { path } : undefined,
    });
  } catch (error) {
    const failure = error as { response?: { data?: unknown } } | null;
    throw await qrResponseError(failure?.response?.data);
  }
  if (
    !(blob instanceof Blob) ||
    blob.size < 8 ||
    blob.size > 2 * 1024 * 1024 ||
    !['image/png', 'image/jpeg'].includes(blob.type.toLowerCase())
  ) {
    throw await qrResponseError(blob);
  }
  const bytes = new Uint8Array(await blob.slice(0, 8).arrayBuffer());
  const png = [137, 80, 78, 71, 13, 10, 26, 10].every(
    (value, i) => bytes[i] === value,
  );
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  if (!(blob.type.toLowerCase() === 'image/png' ? png : jpeg))
    throw new Error('二维码响应无效');
  return blob;
}

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
