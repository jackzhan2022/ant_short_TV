// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks */
export async function list1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list1Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponsePage>(
    `/api/tenants/${param0}/production-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
        query: undefined,
        ...queryParams["query"],
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks/${param1} */
export async function detail2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail2Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, taskKey: param1, ...queryParams } = params;
  return request<API.ApiResponseMapStringObject>(
    `/api/tenants/${param0}/production-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/production-tasks/${param1}/${param2} */
export async function control(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.controlParams,
  options?: { [key: string]: any }
) {
  const {
    tenantId: param0,
    taskKey: param1,
    action: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseMapStringObject>(
    `/api/tenants/${param0}/production-tasks/${param1}/${param2}`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks/${param1}/children */
export async function children(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.childrenParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, taskKey: param1, ...queryParams } = params;
  return request<API.ApiResponsePage>(
    `/api/tenants/${param0}/production-tasks/${param1}/children`,
    {
      method: "GET",
      params: {
        ...queryParams,
        query: undefined,
        ...queryParams["query"],
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks/${param1}/content */
export async function content(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.contentParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, taskKey: param1, ...queryParams } = params;
  return request<API.ApiResponseMapStringObject>(
    `/api/tenants/${param0}/production-tasks/${param1}/content`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks/${param1}/content/${param2} */
export async function contentSection(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.contentSectionParams,
  options?: { [key: string]: any }
) {
  const {
    tenantId: param0,
    taskKey: param1,
    sectionKey: param2,
    ...queryParams
  } = params;
  return request<API.ApiResponseMapStringObject>(
    `/api/tenants/${param0}/production-tasks/${param1}/content/${param2}`,
    {
      method: "GET",
      params: {
        // page has a default value: 1
        page: "1",
        // pageSize has a default value: 20
        pageSize: "20",
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/production-tasks/summary */
export async function summary(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.summaryParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseSummary>(
    `/api/tenants/${param0}/production-tasks/summary`,
    {
      method: "GET",
      params: {
        ...queryParams,
        query: undefined,
        ...queryParams["query"],
      },
      ...(options || {}),
    }
  );
}
