// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/projects/${param0}/asset-image-batches */
export async function create5(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create5Params,
  body: API.AssetImageBatchRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAssetImageBatchResponse>(
    `/api/projects/${param0}/asset-image-batches`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/asset-image-batches/${param1} */
export async function get(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, batchId: param1, ...queryParams } = params;
  return request<API.ApiResponseAssetImageBatchResponse>(
    `/api/projects/${param0}/asset-image-batches/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/asset-image-batches/preflight */
export async function preflight(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.preflightParams,
  body: API.AssetImageBatchRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAssetImageBatchPreflightResponse>(
    `/api/projects/${param0}/asset-image-batches/preflight`,
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
