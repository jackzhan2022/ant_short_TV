// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/subscription/current */
export async function current(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.currentParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTeamSubscriptionEntity>(
    `/api/tenants/${param0}/commercial/subscription/current`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/subscription/grants */
export async function grants(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.grantsParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListCommercialEntitlementGrantEntity>(
    `/api/tenants/${param0}/commercial/subscription/grants`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/subscription/queued */
export async function queued(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.queuedParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListTeamSubscriptionEntity>(
    `/api/tenants/${param0}/commercial/subscription/queued`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
