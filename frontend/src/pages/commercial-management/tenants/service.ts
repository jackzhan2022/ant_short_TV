import { request } from '@umijs/max';

export type ApiResponse<T> = { success: boolean; data: T };
export type PlatformTenantStatus = 'ACTIVE' | 'DISABLED';
export type CommercialPackageType = 'POINT_PACKAGE' | 'SUBSCRIPTION';

export type PlatformTenantOwner = {
  memberId: number;
  userId: number;
  nickname: string;
  mobile: string;
  email?: string;
};

export type PlatformTenantPackage = {
  subscriptionId: number;
  packageId: number;
  packageVersionId: number;
  packageType: CommercialPackageType;
  name: string;
  subscriptionStatus: 'ACTIVE' | 'QUEUED';
  startsAt: string;
  endsAt: string;
};

export type PlatformTenantSummary = {
  id: number;
  code: string;
  name: string;
  type: string;
  status: PlatformTenantStatus;
  owner?: PlatformTenantOwner;
  activeMemberCount: number;
  pointBalance: number | string;
  currentPackage?: PlatformTenantPackage | null;
  createdAt: string;
};

export type PlatformTenantDetail = PlatformTenantSummary & {
  logo?: string;
  description?: string;
  queuedPackages: PlatformTenantPackage[];
  updatedAt: string;
};

export type PlatformTenantQuery = {
  current?: number;
  pageSize?: number;
  keyword?: string;
  status?: PlatformTenantStatus;
  packageType?: CommercialPackageType;
};

export type PlatformTenantPage = {
  records: PlatformTenantSummary[];
  total: number;
  current: number;
  pageSize: number;
};

export const queryPlatformTenants = (params: PlatformTenantQuery) =>
  request<ApiResponse<PlatformTenantPage>>('/api/platform/tenants', { params });

export const getPlatformTenant = (tenantId: number) =>
  request<ApiResponse<PlatformTenantDetail>>(
    `/api/platform/tenants/${tenantId}`,
  );

export const updatePlatformTenantStatus = (
  tenantId: number,
  status: PlatformTenantStatus,
) =>
  request<ApiResponse<PlatformTenantSummary>>(
    `/api/platform/tenants/${tenantId}/status`,
    { method: 'PUT', data: { status } },
  );
