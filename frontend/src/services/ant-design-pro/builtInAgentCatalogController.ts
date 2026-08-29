// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/agents */
export async function agents1(options?: { [key: string]: any }) {
  return request<API.ApiResponseListBuiltInAgentResponse>(
    "/api/platform/ai/agents",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/ai/agents/${param0} */
export async function agent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.agentParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseBuiltInAgentResponse>(
    `/api/platform/ai/agents/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/agents/${param0}/preview */
export async function preview(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.previewParams,
  body: API.BuiltInAgentPreviewRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseBuiltInAgentPreviewResponse>(
    `/api/platform/ai/agents/${param0}/preview`,
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

/** 此处后端没有提供注释 GET /api/platform/ai/skills */
export async function skills(options?: { [key: string]: any }) {
  return request<API.ApiResponseListBuiltInSkillResponse>(
    "/api/platform/ai/skills",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/ai/skills/${param0} */
export async function skill(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.skillParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseBuiltInSkillResponse>(
    `/api/platform/ai/skills/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
