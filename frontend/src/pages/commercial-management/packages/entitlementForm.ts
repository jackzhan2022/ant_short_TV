import type { CommercialEntitlement, CommercialEntitlementDefinition } from './service';

export const activeEntitlementOptions = (definitions: CommercialEntitlementDefinition[]) =>
  definitions
    .filter((definition) => definition.status === 'ACTIVE')
    .map((definition) => ({ label: definition.name, value: definition.code }));

export const entitlementRequiresValue = (
  definitions: CommercialEntitlementDefinition[],
  code?: string,
) => definitions.some((definition) => definition.code === code && definition.category === 'SYSTEM');

export const normalizePackageEntitlements = (
  entitlements: CommercialEntitlement[],
  definitions: CommercialEntitlementDefinition[],
): CommercialEntitlement[] => entitlements.map((entitlement) => {
  if (entitlementRequiresValue(definitions, entitlement.type)) {
    return { type: entitlement.type, value: entitlement.value };
  }
  return { type: entitlement.type };
});
