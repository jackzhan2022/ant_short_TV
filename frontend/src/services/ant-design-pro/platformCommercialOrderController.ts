// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/commercial/orders */
export async function list10(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list10Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePlatformCommercialOrderPageResponse>(
    "/api/platform/commercial/orders",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/commercial/orders/${param0} */
export async function detail9(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail9Params,
  options?: { [key: string]: any }
) {
  const { orderId: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformCommercialOrderDetailResponse>(
    `/api/platform/commercial/orders/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/commercial/orders/${param0}/reconcile */
export async function reconcile(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.reconcileParams,
  options?: { [key: string]: any }
) {
  const { orderId: param0, ...queryParams } = params;
  return request<API.ApiResponseCommercialOrderResponse>(
    `/api/platform/commercial/orders/${param0}/reconcile`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
