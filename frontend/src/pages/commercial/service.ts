import { request } from '@umijs/max';

type ApiResponse<T> = { success: boolean; data: T };
export type CommercialEntitlement = { type: string; value: number };
export type CommercialCatalogItem = { packageId: number; packageVersionId: number; code: string; packageType: 'POINT_PACKAGE' | 'SUBSCRIPTION'; name: string; description?: string; billingPeriod?: string; periodMonths?: number; price: number; listPrice?: number; currency: string; entitlements: CommercialEntitlement[] };
export type CommercialOrder = { id: number; merchantOrderNo: string; amount: number; currency: string; status: string; expiresAt: string; codeUrl?: string };
export type TeamSubscription = { id: number; packageVersionId: number; status: string; startsAt: string; endsAt: string; nextGrantAt?: string; snapshotJson: string };
export type CommercialGrant = { id: number; entitlementType: string; amount?: number; periodNo?: number; status: string; grantedAt?: string; errorMessage?: string };

export const queryCommercialCatalog = (tenantId: number) => request<ApiResponse<CommercialCatalogItem[]>>(`/api/tenants/${tenantId}/commercial/catalog`);
export const queryCurrentSubscription = (tenantId: number) => request<ApiResponse<TeamSubscription | null>>(`/api/tenants/${tenantId}/commercial/subscription/current`);
export const queryQueuedSubscriptions = (tenantId: number) => request<ApiResponse<TeamSubscription[]>>(`/api/tenants/${tenantId}/commercial/subscription/queued`);
export const queryCommercialGrants = (tenantId: number) => request<ApiResponse<CommercialGrant[]>>(`/api/tenants/${tenantId}/commercial/subscription/grants`);
export const queryActiveCommercialOrders = (tenantId: number) => request<ApiResponse<CommercialOrder[]>>(`/api/tenants/${tenantId}/commercial/orders`);
export const createCommercialOrder = (tenantId: number, packageVersionId: number) => request<ApiResponse<CommercialOrder>>(`/api/tenants/${tenantId}/commercial/orders`, { method: 'POST', data: { packageVersionId } });
export const refreshCommercialOrder = (tenantId: number, orderId: number) => request<ApiResponse<CommercialOrder>>(`/api/tenants/${tenantId}/commercial/orders/${orderId}/refresh`, { method: 'POST' });
