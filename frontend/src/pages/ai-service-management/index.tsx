import { history, useAccess } from '@umijs/max';
import { useEffect } from 'react';

const AiServiceManagementIndex = () => {
  const access = useAccess();

  useEffect(() => {
    if (access.canViewPlatformAiProviders) {
      history.replace('/ai-service-management/providers');
    } else if (access.canViewPlatformAiModels) {
      history.replace('/ai-service-management/models');
    } else if (access.canViewModelBilling) {
      history.replace('/ai-service-management/billing');
    } else if (access.canViewBuiltInAiAgents) {
      history.replace('/ai-service-management/agents');
    } else if (access.canViewAiCallLogs) {
      history.replace('/ai-service-management/logs');
    }
  }, [access]);

  return null;
};

export default AiServiceManagementIndex;
