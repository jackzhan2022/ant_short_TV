// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-media */
export async function summary1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.summary1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseMapStringListMapStringObject>(
    `/api/projects/${param0}/storyboard-media`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}
