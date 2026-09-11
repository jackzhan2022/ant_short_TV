// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/commercial/entitlements */
export async function list11(options?: { [key: string]: any }) {
  return request<API.ApiResponseListCommercialEntitlementDefinitionResponse>(
    "/api/platform/commercial/entitlements",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/entitlements */
export async function create7(
  body: API.CommercialDisplayEntitlementCommand,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseCommercialEntitlementDefinitionResponse>(
    "/api/platform/commercial/entitlements",
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

/** 此处后端没有提供注释 PUT /api/platform/commercial/entitlements/${param0} */
export async function update2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update2Params,
  body: API.CommercialDisplayEntitlementCommand,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCommercialEntitlementDefinitionResponse>(
    `/api/platform/commercial/entitlements/${param0}`,
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

/** 此处后端没有提供注释 POST /api/platform/commercial/entitlements/${param0}/disable */
export async function disable(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.disableParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCommercialEntitlementDefinitionResponse>(
    `/api/platform/commercial/entitlements/${param0}/disable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/entitlements/${param0}/enable */
export async function enable(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.enableParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseCommercialEntitlementDefinitionResponse>(
    `/api/platform/commercial/entitlements/${param0}/enable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
