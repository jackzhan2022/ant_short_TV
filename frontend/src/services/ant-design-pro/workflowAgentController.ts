// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-agents */
export async function list13(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list13Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListWorkflowAgentRecord>(
    "/api/platform/ai/workflow-agents",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agents */
export async function create9(
  body: API.CreateAgentRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseWorkflowAgentRecord>(
    "/api/platform/ai/workflow-agents",
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

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-agents/${param0} */
export async function detail11(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail11Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRecord>(
    `/api/platform/ai/workflow-agents/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/workflow-agents/${param0} */
export async function update4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update4Params,
  body: API.UpdateAgentRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRecord>(
    `/api/platform/ai/workflow-agents/${param0}`,
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

/** 此处后端没有提供注释 DELETE /api/platform/ai/workflow-agents/${param0} */
export async function delete3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete3Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/platform/ai/workflow-agents/${param0}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agents/${param0}/copy */
export async function copy1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.copy1Params,
  body: API.CopyAgentRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRecord>(
    `/api/platform/ai/workflow-agents/${param0}/copy`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agents/${param0}/disable */
export async function disable1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.disable1Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRecord>(
    `/api/platform/ai/workflow-agents/${param0}/disable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agents/${param0}/enable */
export async function enable1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.enable1Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRecord>(
    `/api/platform/ai/workflow-agents/${param0}/enable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
