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

/** 此处后端没有提供注释 GET /api/platform/ai/models/${param0}/billing */
export async function billingHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.billingHistoryParams,
  options?: { [key: string]: any }
) {
  const { modelId: param0, ...queryParams } = params;
  return request<API.ApiResponseModelBillingHistoryResponse>(
    `/api/platform/ai/models/${param0}/billing`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/cost-price-versions/${param1}/revoke */
export async function revokeCostPrice(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.revokeCostPriceParams,
  options?: { [key: string]: any }
) {
  const { modelId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseModelPriceVersionResponse>(
    `/api/platform/ai/models/${param0}/cost-price-versions/${param1}/revoke`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/point-price-versions */
export async function publishPointPrice(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.publishPointPriceParams,
  body: API.PublishModelPointPriceRequest,
  options?: { [key: string]: any }
) {
  const { modelId: param0, ...queryParams } = params;
  return request<API.ApiResponseModelPointPriceVersionResponse>(
    `/api/platform/ai/models/${param0}/point-price-versions`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/point-price-versions/${param1}/revoke */
export async function revokePointPrice(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.revokePointPriceParams,
  options?: { [key: string]: any }
) {
  const { modelId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseModelPointPriceVersionResponse>(
    `/api/platform/ai/models/${param0}/point-price-versions/${param1}/revoke`,
    {
      method: "POST",
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
