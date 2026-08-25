// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/video-script-decomposition/batches */
export async function list(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listParams,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListVideoDecompositionBatchResponse>(
    "/api/video-script-decomposition/batches",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/video-script-decomposition/batches */
export async function create(
  body: API.CreateVideoDecompositionBatchRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseVideoDecompositionBatchResponse>(
    "/api/video-script-decomposition/batches",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/video-script-decomposition/batches/${param0} */
export async function detail(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detailParams,
  options?: { [key: string]: any }
) {
  const { batchId: param0, ...queryParams } = params;
  return request<API.ApiResponseVideoDecompositionBatchResponse>(
    `/api/video-script-decomposition/batches/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/video-script-decomposition/episodes/${param0} */
export async function episode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeParams,
  options?: { [key: string]: any }
) {
  const { episodeId: param0, ...queryParams } = params;
  return request<API.ApiResponseVideoDecompositionEpisodeDetailResponse>(
    `/api/video-script-decomposition/episodes/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/video-script-decomposition/episodes/${param0}/confirm */
export async function confirm(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.confirmParams,
  body: API.ConfirmVideoDecompositionDraftRequest,
  options?: { [key: string]: any }
) {
  const { episodeId: param0, ...queryParams } = params;
  return request<API.ApiResponseVideoDecompositionEpisodeResponse>(
    `/api/video-script-decomposition/episodes/${param0}/confirm`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/video-script-decomposition/episodes/${param0}/draft */
export async function updateDraft(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateDraftParams,
  body: API.UpdateVideoDecompositionDraftRequest,
  options?: { [key: string]: any }
) {
  const { episodeId: param0, ...queryParams } = params;
  return request<API.ApiResponseVideoDecompositionEpisodeResponse>(
    `/api/video-script-decomposition/episodes/${param0}/draft`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/video-script-decomposition/episodes/${param0}/retry */
export async function retry(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.retryParams,
  body: API.RetryVideoDecompositionEpisodeRequest,
  options?: { [key: string]: any }
) {
  const { episodeId: param0, ...queryParams } = params;
  return request<API.ApiResponseVideoDecompositionEpisodeResponse>(
    `/api/video-script-decomposition/episodes/${param0}/retry`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/video-script-decomposition/uploads */
export async function upload(body: {}, options?: { [key: string]: any }) {
  return request<API.ApiResponseVideoDecompositionUploadResponse>(
    "/api/video-script-decomposition/uploads",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}
