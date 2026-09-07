// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-agent-runs */
export async function list13(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list13Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListWorkflowAgentRunSummary>(
    "/api/platform/ai/workflow-agent-runs",
    {
      method: "GET",
      params: {
        // limit has a default value: 50
        limit: "50",
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agent-runs */
export async function runFormal(
  body: API.FormalRunRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseWorkflowAgentRunResult>(
    "/api/platform/ai/workflow-agent-runs",
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

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-agent-runs/${param0} */
export async function detail12(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail12Params,
  options?: { [key: string]: any }
) {
  const { runId: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRunDetail>(
    `/api/platform/ai/workflow-agent-runs/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-agent-runs/test */
export async function runTest(
  body: API.TestRunRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseWorkflowAgentRunResult>(
    "/api/platform/ai/workflow-agent-runs/test",
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
