import { history, useModel } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { applyBootstrapSelection } from '@/services/account-team/bootstrap';
import access from '@/access';
import { workspaceLandingPath } from '@/services/account-team/navigation';

export const useTeamEntry = () => {
  const { initialState, setInitialState } = useModel('@@initialState');
  const [pendingTenantId, setPendingTenantId] = useState<number>();
  const currentTenantId = initialState?.currentTenantId;
  const selectedTenantId = initialState?.selectedTenant?.tenant.id;
  const membershipStatus = initialState?.selectedTenant?.membership.status;
  const tenants = initialState?.tenants;
  const destination = workspaceLandingPath(access(initialState));

  useEffect(() => {
    if (
      pendingTenantId === undefined ||
      currentTenantId !== pendingTenantId ||
      selectedTenantId !== pendingTenantId ||
      membershipStatus !== 'ACTIVE' ||
      !tenants?.some((team) => team.id === pendingTenantId)
    )
      return;

    // Umi publishes model updates through effects. Navigate only from a committed subscriber.
    setPendingTenantId(undefined);
    history.push(destination);
  }, [
    currentTenantId,
    membershipStatus,
    pendingTenantId,
    selectedTenantId,
    tenants,
    destination,
  ]);

  return useCallback(
    async (tenantId: number) => {
      await applyBootstrapSelection(tenantId, setInitialState);
      setPendingTenantId(tenantId);
    },
    [setInitialState],
  );
};
