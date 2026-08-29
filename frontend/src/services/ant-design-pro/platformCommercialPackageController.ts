// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/commercial/packages */
export async function list8(options?: { [key: string]: any }) {
  return request<API.ApiResponseListCommercialPackageSummaryResponse>(
    "/api/platform/commercial/packages",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/packages */
export async function createDraft(
  body: API.CommercialPackageDraftCommand,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseCommercialPackageVersionResponse>(
    "/api/platform/commercial/packages",
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

/** 此处后端没有提供注释 GET /api/platform/commercial/packages/${param0}/versions */
export async function history(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.historyParams,
  options?: { [key: string]: any }
) {
  const { packageId: param0, ...queryParams } = params;
  return request<API.ApiResponseListCommercialPackageVersionResponse>(
    `/api/platform/commercial/packages/${param0}/versions`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/packages/${param0}/versions/${param1}/publish */
export async function publish(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.publishParams,
  options?: { [key: string]: any }
) {
  const { packageId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseCommercialPackageVersionResponse>(
    `/api/platform/commercial/packages/${param0}/versions/${param1}/publish`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/packages/${param0}/versions/${param1}/unpublish */
export async function unpublish(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.unpublishParams,
  options?: { [key: string]: any }
) {
  const { packageId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseCommercialPackageVersionResponse>(
    `/api/platform/commercial/packages/${param0}/versions/${param1}/unpublish`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
