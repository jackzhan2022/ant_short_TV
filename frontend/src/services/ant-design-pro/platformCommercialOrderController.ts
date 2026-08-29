// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

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
