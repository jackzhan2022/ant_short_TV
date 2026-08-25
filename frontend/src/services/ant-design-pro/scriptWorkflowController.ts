// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/projects/${param0}/prompts/ai-generate */
export async function generatePrompts(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.generatePromptsParams,
  body: API.GeneratePromptRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/prompts/ai-generate`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-analysis/current */
export async function currentAnalysis(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.currentAnalysisParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptAnalysisTaskResponse>(
    `/api/projects/${param0}/script-analysis/current`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/script-analysis/current/reanalyze */
export async function reanalyze(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.reanalyzeParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/script-analysis/current/reanalyze`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/script-analysis/current/retry/${param1} */
export async function retryAnalysis(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.retryAnalysisParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, stageCode: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/script-analysis/current/retry/${param1}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/script-analysis/versions/${param1}/reanalyze */
export async function reanalyzeVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.reanalyzeVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/script-analysis/versions/${param1}/reanalyze`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/script-elements/${param1}/${param2} */
export async function updateElement(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateElementParams,
  body: API.UpdateScriptElementRequest,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}`,
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

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/script-elements/${param1}/${param2} */
export async function deleteElement(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteElementParams,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/script-elements/${param1}/${param2}/confirm */
export async function confirmElement(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.confirmElementParams,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}/confirm`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-workspace */
export async function workspace(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.workspaceParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/script-workspace`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/scripts/ai-extract-elements */
export async function extractElements(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.extractElementsParams,
  body: API.ExtractScriptElementsRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/scripts/ai-extract-elements`,
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

/** 此处后端没有提供注释 POST /api/projects/${param0}/scripts/ai-generate */
export async function generate(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.generateParams,
  body: API.GenerateScriptRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/scripts/ai-generate`,
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

/** 此处后端没有提供注释 POST /api/projects/${param0}/scripts/ai-rewrite */
export async function rewrite(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rewriteParams,
  body: API.RewriteScriptRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/scripts/ai-rewrite`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/scripts/current */
export async function saveCurrent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveCurrentParams,
  body: API.SaveScriptRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/scripts/current`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/scripts/versions/${param1}/apply */
export async function applyVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.applyVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/scripts/versions/${param1}/apply`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/storyboards */
export async function createStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createStoryboardParams,
  body: API.SaveStoryboardRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/storyboards`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboards/${param1} */
export async function updateStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStoryboardParams,
  body: API.SaveStoryboardRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, storyboardId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/storyboards/${param1}`,
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

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/storyboards/${param1} */
export async function deleteStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteStoryboardParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, storyboardId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/storyboards/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboards/${param1}/move */
export async function moveStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.moveStoryboardParams,
  body: API.MoveStoryboardRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, storyboardId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/storyboards/${param1}/move`,
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

/** 此处后端没有提供注释 POST /api/projects/${param0}/storyboards/ai-breakdown */
export async function breakdownStoryboards(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.breakdownStoryboardsParams,
  body: API.StoryboardBreakdownRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/storyboards/ai-breakdown`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboards/confirm */
export async function confirmStoryboards(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.confirmStoryboardsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptWorkspaceResponse>(
    `/api/projects/${param0}/storyboards/confirm`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
