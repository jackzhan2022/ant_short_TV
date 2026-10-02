// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/style-library */
export async function list5(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list5Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseMediaPageStyleLibraryResponse>(
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

/** 此处后端没有提供注释 GET /api/style-library/${param0} */
export async function detail6(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail6Params,
  options?: { [key: string]: any }
) {
  const { styleId: param0, ...queryParams } = params;
  return request<API.ApiResponseStyleLibraryResponse>(
    `/api/style-library/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/style-library/categories */
export async function categories(options?: { [key: string]: any }) {
  return request<API.ApiResponseListString>("/api/style-library/categories", {
    method: "GET",
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/style-library/images/${param0} */
export async function image(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.imageParams,
  options?: { [key: string]: any }
) {
  const { externalId: param0, ...queryParams } = params;
  return request<any>(`/api/style-library/images/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}
