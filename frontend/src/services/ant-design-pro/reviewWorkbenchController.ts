// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/script-review/exports/${param0} */
export async function downloadExport(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadExportParams,
  options?: { [key: string]: any }
) {
  const { fileName: param0, ...queryParams } = params;
  return request<string>(`/api/script-review/exports/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/script-review/issues/${param0}/resolve */
export async function resolveIssue(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resolveIssueParams,
  body: API.MarkReviewIssueResolvedRequest,
  options?: { [key: string]: any }
) {
  const { issueId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewIssueResponse>(
    `/api/script-review/issues/${param0}/resolve`,
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

/** 此处后端没有提供注释 GET /api/script-review/projects */
export async function projects(options?: { [key: string]: any }) {
  return request<API.ApiResponseListReviewProjectSummaryResponse>(
    "/api/script-review/projects",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/script-review/projects */
export async function importProject(
  body: {
    mainProjectId?: number;
    content?: string;
    name?: string;
  },
  file?: File,
  options?: { [key: string]: any }
) {
  const formData = new FormData();

  if (file) {
    formData.append("file", file);
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele];

    if (item !== undefined && item !== null) {
      if (typeof item === "object" && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ""));
        } else {
          formData.append(
            ele,
            new Blob([JSON.stringify(item)], { type: "application/json" })
          );
        }
      } else {
        formData.append(ele, item);
      }
    }
  });

  return request<API.ApiResponseReviewProjectDetailResponse>(
    "/api/script-review/projects",
    {
      method: "POST",
      data: formData,
      requestType: "form",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/script-review/projects/${param0} */
export async function project(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.projectParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewProjectDetailResponse>(
    `/api/script-review/projects/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/script-review/projects/${param0}/binding */
export async function bindProject(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bindProjectParams,
  body: API.BindReviewProjectRequest,
  options?: { [key: string]: any }
) {
  const { reviewProjectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewProjectDetailResponse>(
    `/api/script-review/projects/${param0}/binding`,
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

/** 此处后端没有提供注释 POST /api/script-review/projects/${param0}/exports */
export async function exportUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportUsingPOSTParams,
  body: API.ExportReviewReportRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewExportRecordResponse>(
    `/api/script-review/projects/${param0}/exports`,
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

/** 此处后端没有提供注释 POST /api/script-review/projects/${param0}/rollback */
export async function rollback(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rollbackParams,
  body: API.RollbackReviewVersionRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewVersionResponse>(
    `/api/script-review/projects/${param0}/rollback`,
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

/** 此处后端没有提供注释 GET /api/script-review/projects/${param0}/tasks */
export async function tasks(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.tasksParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListReviewTaskResponse>(
    `/api/script-review/projects/${param0}/tasks`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/script-review/projects/${param0}/tasks */
export async function createTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createTaskParams,
  body: API.CreateReviewTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/projects/${param0}/tasks`,
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

/** 此处后端没有提供注释 PUT /api/script-review/projects/${param0}/versions */
export async function saveVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveVersionParams,
  body: API.SaveReviewVersionRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewVersionResponse>(
    `/api/script-review/projects/${param0}/versions`,
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

/** 此处后端没有提供注释 GET /api/script-review/projects/${param0}/versions/${param1}/history */
export async function versionHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.versionHistoryParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseReviewVersionHistoryResponse>(
    `/api/script-review/projects/${param0}/versions/${param1}/history`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/script-review/tasks/${param0} */
export async function task(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.taskParams,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/tasks/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/script-review/tasks/${param0}/batch-repair */
export async function batchRepair(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.batchRepairParams,
  body: API.BatchRepairReviewRequest,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/tasks/${param0}/batch-repair`,
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

/** 此处后端没有提供注释 POST /api/script-review/tasks/${param0}/cancel */
export async function cancelTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelTaskParams,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/tasks/${param0}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/script-review/tasks/${param0}/config */
export async function updateTaskConfig(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateTaskConfigParams,
  body: API.UpdateReviewTaskRequest,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/tasks/${param0}/config`,
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

/** 此处后端没有提供注释 POST /api/script-review/tasks/${param0}/retry */
export async function retryTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.retryTaskParams,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ApiResponseReviewTaskResponse>(
    `/api/script-review/tasks/${param0}/retry`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
