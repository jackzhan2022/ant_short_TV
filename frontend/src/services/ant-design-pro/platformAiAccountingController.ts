// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/executions/${param0}/accounting */
export async function accountingDetail(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.accountingDetailParams,
  options?: { [key: string]: any }
) {
  const { executionId: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformAiAccountingDetailResponse>(
    `/api/platform/ai/executions/${param0}/accounting`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/price-versions */
export async function publishModelPrice(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.publishModelPriceParams,
  body: API.PublishModelPriceRequest,
  options?: { [key: string]: any }
) {
  const { modelId: param0, ...queryParams } = params;
  return request<API.ApiResponseModelPriceVersionResponse>(
    `/api/platform/ai/models/${param0}/price-versions`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/point-policy-versions */
export async function publishPointPolicy(
  body: API.PublishPointPolicyRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePointPolicyVersionResponse>(
    "/api/platform/ai/point-policy-versions",
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
