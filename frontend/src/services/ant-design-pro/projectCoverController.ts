// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects/${param0}/cover */
export async function display(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.displayParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<any>(`/api/projects/${param0}/cover`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/cover/retry */
export async function retry2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.retry2Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCoverStatus>(
    `/api/projects/${param0}/cover/retry`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/cover/status */
export async function status(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.statusParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCoverStatus>(
    `/api/projects/${param0}/cover/status`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/cover/upload */
export async function upload(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadParams,
  body: API.CoverUploadRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCoverStatus>(
    `/api/projects/${param0}/cover/upload`,
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
