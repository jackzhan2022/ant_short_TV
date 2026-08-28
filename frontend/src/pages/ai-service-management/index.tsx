import { history, useAccess } from '@umijs/max';
import { useEffect } from 'react';

const AiServiceManagementIndex = () => {
  const access = useAccess();

  useEffect(() => {
    if (access.canViewPlatformAiProviders) {
      history.replace('/ai-service-management/model-management');
    } else if (access.canViewPlatformAiModels) {
      history.replace('/ai-service-management/model-management');
    } else if (access.canViewBuiltInAiAgents) {
      history.replace('/ai-service-management/agents');
    } else if (access.canViewAiCallLogs) {
      history.replace('/ai-service-management/model-management');
    }
  }, [access]);

  return null;
};

export default AiServiceManagementIndex;
