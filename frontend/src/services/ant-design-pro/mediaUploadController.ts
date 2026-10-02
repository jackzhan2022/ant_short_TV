// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/media-uploads */
export async function create12(
  body: API.CreateMediaUploadRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseMediaUploadSession>("/api/media-uploads", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/media-uploads/${param0} */
export async function status1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.status1Params,
  options?: { [key: string]: any }
) {
  const { sessionToken: param0, ...queryParams } = params;
  return request<API.ApiResponseMediaUploadSession>(
    `/api/media-uploads/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/media-uploads/${param0} */
export async function cancel4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancel4Params,
  options?: { [key: string]: any }
) {
  const { sessionToken: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/media-uploads/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/media-uploads/${param0}/authorization */
export async function authorize(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.authorizeParams,
  body: API.CosUploadAuthorizationRequest,
  options?: { [key: string]: any }
) {
  const { sessionToken: param0, ...queryParams } = params;
  return request<API.ApiResponseCosUploadAuthorization>(
    `/api/media-uploads/${param0}/authorization`,
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

/** 此处后端没有提供注释 POST /api/media-uploads/${param0}/complete */
export async function complete(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.completeParams,
  options?: { [key: string]: any }
) {
  const { sessionToken: param0, ...queryParams } = params;
  return request<API.ApiResponseVerifiedMediaUpload>(
    `/api/media-uploads/${param0}/complete`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
