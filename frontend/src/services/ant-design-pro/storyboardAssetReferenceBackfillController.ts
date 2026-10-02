// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/platform/storyboard-asset-references/backfill */
export async function backfill(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.backfillParams,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseStoryboardAssetReferenceBackfillResult>(
    "/api/platform/storyboard-asset-references/backfill",
    {
      method: "POST",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/storyboard-asset-references/consistency-audit */
export async function audit(options?: { [key: string]: any }) {
  return request<API.ApiResponseMapStringInteger>(
    "/api/platform/storyboard-asset-references/consistency-audit",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}
