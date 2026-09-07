// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/agent-tools */
export async function catalog(options?: { [key: string]: any }) {
  return request<API.ApiResponseListWorkflowToolMetadata>(
    "/api/platform/ai/agent-tools",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}
