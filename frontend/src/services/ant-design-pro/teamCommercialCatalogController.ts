// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/catalog */
export async function list2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list2Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListCommercialCatalogItemResponse>(
    `/api/tenants/${param0}/commercial/catalog`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
