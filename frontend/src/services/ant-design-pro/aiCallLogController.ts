// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/ai-call-logs */
export async function list3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list3Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiCallLogPageResponse>(
    `/api/tenants/${param0}/ai-call-logs`,
    {
      method: "GET",
      params: {
        // current has a default value: 1
        current: "1",
        // pageSize has a default value: 20
        pageSize: "20",

        ...queryParams,
      },
      ...(options || {}),
    }
  );
}
