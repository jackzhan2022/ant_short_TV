// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/definitions/agents */
export async function agents(options?: { [key: string]: any }) {
  return request<API.ApiResponseListEditableAgentResponse>(
    "/api/platform/ai/definitions/agents",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/definitions/agents/${param0} */
export async function updateAgent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateAgentParams,
  body: API.EditableAgentRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseEditableAgentResponse>(
    `/api/platform/ai/definitions/agents/${param0}`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/agents/${param0}/${param1} */
export async function setAgentStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.setAgentStatusParams,
  options?: { [key: string]: any }
) {
  const { code: param0, status: param1, ...queryParams } = params;
  return request<API.ApiResponseEditableAgentResponse>(
    `/api/platform/ai/definitions/agents/${param0}/${param1}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/agents/${param0}/preview */
export async function previewAgent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.previewAgentParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseString>(
    `/api/platform/ai/definitions/agents/${param0}/preview`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/agents/${param0}/publish */
export async function publishAgent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.publishAgentParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseEditableAgentResponse>(
    `/api/platform/ai/definitions/agents/${param0}/publish`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/agents/${param0}/rollback/${param1} */
export async function rollbackAgent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rollbackAgentParams,
  options?: { [key: string]: any }
) {
  const { code: param0, version: param1, ...queryParams } = params;
  return request<API.ApiResponseEditableAgentResponse>(
    `/api/platform/ai/definitions/agents/${param0}/rollback/${param1}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/ai/definitions/skills */
export async function skills1(options?: { [key: string]: any }) {
  return request<API.ApiResponseListEditableSkillResponse>(
    "/api/platform/ai/definitions/skills",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/definitions/skills/${param0} */
export async function updateSkill(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateSkillParams,
  body: API.EditableSkillRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseEditableSkillResponse>(
    `/api/platform/ai/definitions/skills/${param0}`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/skills/${param0}/${param1} */
export async function setSkillStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.setSkillStatusParams,
  options?: { [key: string]: any }
) {
  const { code: param0, status: param1, ...queryParams } = params;
  return request<API.ApiResponseEditableSkillResponse>(
    `/api/platform/ai/definitions/skills/${param0}/${param1}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/skills/${param0}/publish */
export async function publishSkill(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.publishSkillParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseEditableSkillResponse>(
    `/api/platform/ai/definitions/skills/${param0}/publish`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/definitions/skills/${param0}/rollback/${param1} */
export async function rollbackSkill(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rollbackSkillParams,
  options?: { [key: string]: any }
) {
  const { code: param0, version: param1, ...queryParams } = params;
  return request<API.ApiResponseEditableSkillResponse>(
    `/api/platform/ai/definitions/skills/${param0}/rollback/${param1}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
