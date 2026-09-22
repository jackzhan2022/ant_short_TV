// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/inspiration-creations */
export async function list10(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list10Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseInspirationManagementPageResponse>(
    "/api/platform/inspiration-creations",
    {
      method: "GET",
      params: {
        // page has a default value: 1
        page: "1",
        // pageSize has a default value: 20
        pageSize: "20",

        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/inspiration-creations */
export async function create8(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create8Params,
  body: {},
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseInspirationManagementItemResponse>(
    "/api/platform/inspiration-creations",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: {
        // publishStatus has a default value: UNPUBLISHED
        publishStatus: "UNPUBLISHED",
        ...params,
      },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/inspiration-creations/${param0} */
export async function update2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update2Params,
  body: API.InspirationMetadataRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseInspirationManagementItemResponse>(
    `/api/platform/inspiration-creations/${param0}`,
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

/** 此处后端没有提供注释 DELETE /api/platform/inspiration-creations/${param0} */
export async function delete2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete2Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/platform/inspiration-creations/${param0}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/inspiration-creations/${param0}/publish-status */
export async function updatePublishStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updatePublishStatusParams,
  body: API.InspirationPublishStatusRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseInspirationManagementItemResponse>(
    `/api/platform/inspiration-creations/${param0}/publish-status`,
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

/** 此处后端没有提供注释 PUT /api/platform/inspiration-creations/reorder */
export async function reorder(
  body: API.InspirationReorderRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseVoid>(
    "/api/platform/inspiration-creations/reorder",
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}
