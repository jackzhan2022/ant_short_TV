// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/style-library */
export async function list4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list4Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListStyleLibraryResponse>(
    "/api/style-library",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/style-library/images/${param0} */
export async function image(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.imageParams,
  options?: { [key: string]: any }
) {
  const { externalId: param0, ...queryParams } = params;
  return request<string>(`/api/style-library/images/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}
