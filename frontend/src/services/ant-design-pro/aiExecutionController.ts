// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/ai-executions/${param1} */
export async function detail2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail2Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, executionId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/tenants/${param0}/ai-executions/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/ai-executions/${param1}/cancel */
export async function cancel1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancel1Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, executionId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/tenants/${param0}/ai-executions/${param1}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/ai-executions/${param1}/regenerate */
export async function regenerate(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateParams,
  body: API.AiExecutionRegenerateRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, executionId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/tenants/${param0}/ai-executions/${param1}/regenerate`,
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

/** 此处后端没有提供注释 POST /api/tenants/${param0}/ai-executions/${param1}/retry */
export async function retry1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.retry1Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, executionId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/tenants/${param0}/ai-executions/${param1}/retry`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
