import { request } from '@umijs/max';

export type ApiResponse<T> = { success: boolean; data: T };
export type CommercialOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'ENTITLEMENT_PENDING' | 'COMPLETED' | 'CLOSED' | 'PAYMENT_EXCEPTION';
export type CommercialPackageType = 'POINT_PACKAGE' | 'SUBSCRIPTION';

export type PlatformCommercialOrderPayment = {
  provider?: string;
  providerTradeNo?: string;
  status?: string;
  paidAt?: string;
};

export type PlatformCommercialOrderSummary = {
  id: number;
  merchantOrderNo: string;
  tenantId: number;
  tenantName?: string;
  tenantCode?: string;
  packageVersionId: number;
  packageName: string;
  packageVersionNo: number;
  packageType: CommercialPackageType;
  amount: number | string;
  currency: string;
  status: CommercialOrderStatus;
  paidAt?: string;
  createdAt: string;
  payment?: PlatformCommercialOrderPayment;
};

export type PlatformCommercialOrderDetail = PlatformCommercialOrderSummary & {
  expiresAt?: string;
  completedAt?: string;
  updatedAt?: string;
};

export type PlatformCommercialOrderQuery = {
  current?: number;
  pageSize?: number;
  keyword?: string;
  status?: CommercialOrderStatus;
  packageType?: CommercialPackageType;
};

export type PlatformCommercialOrderPage = {
  records: PlatformCommercialOrderSummary[];
  total: number;
  current: number;
  pageSize: number;
};

export const queryPlatformCommercialOrders = (params: PlatformCommercialOrderQuery) =>
  request<ApiResponse<PlatformCommercialOrderPage>>('/api/platform/commercial/orders', { params });

export const getPlatformCommercialOrder = (orderId: number) =>
  request<ApiResponse<PlatformCommercialOrderDetail>>(`/api/platform/commercial/orders/${orderId}`);
