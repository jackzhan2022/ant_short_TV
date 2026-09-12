// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/projects/${param0}/asset-reextraction */
export async function scopedAssetReextraction(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.scopedAssetReextractionParams,
  body: API.ScopedAssetReextractionRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiExecutionResponse>(
    `/api/projects/${param0}/asset-reextraction`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/asset-reextraction/preflight */
export async function assetReextractionPreflight(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.assetReextractionPreflightParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAssetReextractionPreflight>(
    `/api/projects/${param0}/asset-reextraction/preflight`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/asset-settings-summary */
export async function assetSettingsSummary(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.assetSettingsSummaryParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAssetSettingsSummaryResponse>(
    `/api/projects/${param0}/asset-settings-summary`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episodes/${param1}/assets/regenerate */
export async function regenerateEpisodeAssets(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateEpisodeAssetsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, episodeId: param1, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRunResult>(
    `/api/projects/${param0}/episodes/${param1}/assets/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/episodes/${param1}/summary */
export async function updateEpisodeSummary(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateEpisodeSummaryParams,
  body: API.SaveEpisodeSummaryRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, episodeId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptEpisodeSummaryDocument>(
    `/api/projects/${param0}/episodes/${param1}/summary`,
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

/** 此处后端没有提供注释 POST /api/projects/${param0}/episodes/${param1}/summary/regenerate */
export async function regenerateEpisodeSummary(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateEpisodeSummaryParams,
  body: API.RegenerateEpisodeSummaryRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, episodeId: param1, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRunResult>(
    `/api/projects/${param0}/episodes/${param1}/summary/regenerate`,
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

/** 此处后端没有提供注释 POST /api/projects/${param0}/script-analysis/current/regenerate-episodes */
export async function regenerateEpisodes(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateEpisodesParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseWorkflowAgentRunResult>(
    `/api/projects/${param0}/script-analysis/current/regenerate-episodes`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-content */
export async function scriptContent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.scriptContentParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptResponse>(
    `/api/projects/${param0}/script-content`,
    {
      method: "GET",
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
  return request<API.ApiResponseVoid>(
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
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-elements/${param1}/${param2}/episode-bindings */
export async function visualVariantBindings(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.visualVariantBindingsParams,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseListBindingResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}/episode-bindings`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-elements/${param1}/${param2}/visual-variants */
export async function visualVariants(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.visualVariantsParams,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseListVariantResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}/visual-variants`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/script-elements/${param1}/${param2}/visual-variants */
export async function createVisualVariant(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createVisualVariantParams,
  body: API.VariantCommand,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseVariantResponse>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}/visual-variants`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-elements/${param1}/${param2}/visual-workspace */
export async function assetVisualWorkspace(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.assetVisualWorkspaceParams,
  options?: { [key: string]: any }
) {
  const {
    projectId: param0,
    elementType: param1,
    elementId: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseAssetVisualWorkspace>(
    `/api/projects/${param0}/script-elements/${param1}/${param2}/visual-workspace`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-episodes/${param1} */
export async function scriptEpisode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.scriptEpisodeParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, episodeId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptEpisodeResponse>(
    `/api/projects/${param0}/script-episodes/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-page-workspace */
export async function scriptPageWorkspace(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.scriptPageWorkspaceParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseScriptPageWorkspaceResponse>(
    `/api/projects/${param0}/script-page-workspace`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/script-versions/${param1} */
export async function scriptVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.scriptVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseScriptVersionResponse>(
    `/api/projects/${param0}/script-versions/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
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
  return request<API.ApiResponseVoid>(
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
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/scripts/versions/${param1}/apply`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/storyboard-batches */
export async function createStoryboardBatch(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createStoryboardBatchParams,
  body: API.CreateStoryboardBatchRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseStoryboardBatchResponse>(
    `/api/projects/${param0}/storyboard-batches`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-batches/${param1} */
export async function storyboardBatch(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.storyboardBatchParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, batchId: param1, ...queryParams } = params;
  return request<API.ApiResponseStoryboardBatchResponse>(
    `/api/projects/${param0}/storyboard-batches/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-batches/latest */
export async function latestStoryboardBatch(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.latestStoryboardBatchParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseStoryboardBatchResponse>(
    `/api/projects/${param0}/storyboard-batches/latest`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-workspace */
export async function storyboardWorkspace(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.storyboardWorkspaceParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseStoryboardWorkspacePageResponse>(
    `/api/projects/${param0}/storyboard-workspace`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
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
  return request<API.ApiResponseVoid>(`/api/projects/${param0}/storyboards`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboards/${param1} */
export async function updateStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStoryboardParams,
  body: API.SaveStoryboardRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, storyboardId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
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
  return request<API.ApiResponseVoid>(
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
  return request<API.ApiResponseVoid>(
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
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/storyboards/confirm`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/visual-variants/${param1} */
export async function updateVisualVariant(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateVisualVariantParams,
  body: API.VariantCommand,
  options?: { [key: string]: any }
) {
  const { projectId: param0, variantId: param1, ...queryParams } = params;
  return request<API.ApiResponseVariantResponse>(
    `/api/projects/${param0}/visual-variants/${param1}`,
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

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/visual-variants/${param1} */
export async function deleteVisualVariant(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteVisualVariantParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, variantId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/visual-variants/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/visual-variants/${param1}/episode-bindings */
export async function bindVisualVariantEpisodes(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bindVisualVariantEpisodesParams,
  body: API.BindingCommand,
  options?: { [key: string]: any }
) {
  const { projectId: param0, variantId: param1, ...queryParams } = params;
  return request<API.ApiResponseListBindingResponse>(
    `/api/projects/${param0}/visual-variants/${param1}/episode-bindings`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/visual-variants/${param1}/primary */
export async function selectPrimaryVisualVariant(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.selectPrimaryVisualVariantParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, variantId: param1, ...queryParams } = params;
  return request<API.ApiResponseVariantResponse>(
    `/api/projects/${param0}/visual-variants/${param1}/primary`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
