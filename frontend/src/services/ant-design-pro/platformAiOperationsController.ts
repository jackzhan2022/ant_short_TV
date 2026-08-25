// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/operations/overview */
export async function overview(options?: { [key: string]: any }) {
  return request<API.ApiResponsePlatformAiOperationsOverview>(
    "/api/platform/ai/operations/overview",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}
