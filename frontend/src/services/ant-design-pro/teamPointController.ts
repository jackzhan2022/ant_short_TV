// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/points/account */
export async function account(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.accountParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTeamPointAccountResponse>(
    `/api/tenants/${param0}/points/account`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/points/adjust */
export async function adjust(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.adjustParams,
  body: API.TeamPointAdjustmentRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTeamPointAccountResponse>(
    `/api/tenants/${param0}/points/adjust`,
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

/** 此处后端没有提供注释 GET /api/tenants/${param0}/points/transactions */
export async function transactions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.transactionsParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTeamPointTransactionPageResponse>(
    `/api/tenants/${param0}/points/transactions`,
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
