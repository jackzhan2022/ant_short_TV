import { request } from '@umijs/max';
import type {
  ApiResponse,
  TeamPointAccount,
  TeamPointTransactionPage,
} from './types';

export async function queryTeamPointAccount(tenantId: number) {
  return request<ApiResponse<TeamPointAccount>>(
    `/api/tenants/${tenantId}/points/account`,
  );
}

export async function queryTeamPointTransactions(
  tenantId: number,
  params?: { current?: number; pageSize?: number; transactionType?: string },
) {
  return request<ApiResponse<TeamPointTransactionPage>>(
    `/api/tenants/${tenantId}/points/transactions`,
    { params },
  );
}
