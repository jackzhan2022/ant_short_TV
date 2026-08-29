// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/orders */
export async function active(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.activeParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListCommercialOrderResponse>(
    `/api/tenants/${param0}/commercial/orders`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/commercial/orders */
export async function create3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create3Params,
  body: API.CommercialOrderCreateRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseCommercialOrderResponse>(
    `/api/tenants/${param0}/commercial/orders`,
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

/** 此处后端没有提供注释 GET /api/tenants/${param0}/commercial/orders/${param1} */
export async function detail2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail2Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, orderId: param1, ...queryParams } = params;
  return request<API.ApiResponseCommercialOrderEntity>(
    `/api/tenants/${param0}/commercial/orders/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/commercial/orders/${param1}/refresh */
export async function refresh(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.refreshParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, orderId: param1, ...queryParams } = params;
  return request<API.ApiResponseCommercialOrderResponse>(
    `/api/tenants/${param0}/commercial/orders/${param1}/refresh`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
