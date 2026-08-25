// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /materials/${param0}/${param1}/&#42;&#42; */
export async function read(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.readParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, projectId: param1, ...queryParams } = params;
  return request<string>(`/materials/${param0}/${param1}/**`, {
    method: "GET",
    params: {
      ...queryParams,
    },
    ...(options || {}),
  });
}
