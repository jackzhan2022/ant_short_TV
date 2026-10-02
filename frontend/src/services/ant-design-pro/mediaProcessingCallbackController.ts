// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/media-processing/callbacks/tencent-ci/${param0} */
export async function callback(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.callbackParams,
  body: API.TencentCiTaskCallback,
  options?: { [key: string]: any }
) {
  const { token: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/media-processing/callbacks/tencent-ci/${param0}`,
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
