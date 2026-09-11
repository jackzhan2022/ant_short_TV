// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-skills */
export async function list12(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list12Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListWorkflowSkillView>(
    "/api/platform/ai/workflow-skills",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-skills */
export async function create8(
  body: API.CreateSkillRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseWorkflowSkillView>(
    "/api/platform/ai/workflow-skills",
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

/** 此处后端没有提供注释 GET /api/platform/ai/workflow-skills/${param0} */
export async function detail10(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail10Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowSkillView>(
    `/api/platform/ai/workflow-skills/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/workflow-skills/${param0} */
export async function update3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update3Params,
  body: API.UpdateSkillRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowSkillView>(
    `/api/platform/ai/workflow-skills/${param0}`,
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

/** 此处后端没有提供注释 DELETE /api/platform/ai/workflow-skills/${param0} */
export async function delete2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete2Params,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/platform/ai/workflow-skills/${param0}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/workflow-skills/${param0}/copy */
export async function copy(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.copyParams,
  body: API.CopySkillRequest,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowSkillView>(
    `/api/platform/ai/workflow-skills/${param0}/copy`,
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
