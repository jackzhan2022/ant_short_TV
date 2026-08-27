import { request } from '@umijs/max';

export type ApiResponse<T> = { success: boolean; data: T };
export type CommercialEntitlement = { type: string; value: number };
export type CommercialPackageSummary = {
  id: number;
  code: string;
  packageType: 'POINT_PACKAGE' | 'SUBSCRIPTION';
  status: string;
};
export type CommercialPackageVersion = {
  packageId: number;
  versionId: number;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'OFF_SALE';
  name: string;
  description?: string;
  billingPeriod?: string;
  periodMonths?: number;
  price: number;
  listPrice?: number;
  currency: string;
  effectiveFrom: string;
  effectiveTo?: string;
  entitlements: CommercialEntitlement[];
};
export type CommercialPackageDraft = {
  code?: string;
  packageType: string;
  name: string;
  description?: string;
  billingPeriod?: string;
  periodMonths?: number;
  price: number;
  listPrice?: number;
  currency: string;
  effectiveFrom: string;
  effectiveTo?: string;
  entitlements: CommercialEntitlement[];
};

const isoLocalDateTime = (value?: string) => value?.replace(
  /^(\d{4}-\d{2}-\d{2}) (?=\d{2}:\d{2}:\d{2}(?:\.\d+)?$)/,
  '$1T',
);

export const listCommercialPackages = () =>
  request<ApiResponse<CommercialPackageSummary[]>>('/api/platform/commercial/packages');
export const listCommercialPackageVersions = (packageId: number) =>
  request<ApiResponse<CommercialPackageVersion[]>>(`/api/platform/commercial/packages/${packageId}/versions`);
export const createCommercialPackageDraft = (data: CommercialPackageDraft) =>
  request<ApiResponse<CommercialPackageVersion>>('/api/platform/commercial/packages', {
    method: 'POST',
    data: {
      ...data,
      effectiveFrom: isoLocalDateTime(data.effectiveFrom),
      effectiveTo: isoLocalDateTime(data.effectiveTo),
    },
  });
export const publishCommercialPackageVersion = (packageId: number, versionId: number) =>
  request<ApiResponse<CommercialPackageVersion>>(`/api/platform/commercial/packages/${packageId}/versions/${versionId}/publish`, { method: 'POST' });
export const unpublishCommercialPackageVersion = (packageId: number, versionId: number) =>
  request<ApiResponse<CommercialPackageVersion>>(`/api/platform/commercial/packages/${packageId}/versions/${versionId}/unpublish`, { method: 'POST' });
