// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/inspiration-creations */
export async function list8(options?: { [key: string]: any }) {
  return request<API.ApiResponseListInspirationCreationListResponse>(
    "/api/inspiration-creations",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/inspiration-creations/${param0} */
export async function detail8(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail8Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseInspirationCreationDetailResponse>(
    `/api/inspiration-creations/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/inspiration-creations/${param0}/file */
export async function file(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.fileParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<string>(`/api/inspiration-creations/${param0}/file`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}
