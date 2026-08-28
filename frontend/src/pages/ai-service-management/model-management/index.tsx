import { history, useAccess } from '@umijs/max';
import { Tabs } from 'antd';
import { useMemo } from 'react';
import AiCallLogsPage from '../logs';
import PlatformModelsPage from '../models';
import PlatformProvidersPage from '../providers';

type ModelManagementTab = 'providers' | 'models' | 'logs';

const tabFromSearch = (): ModelManagementTab | undefined => {
  const tab = new URLSearchParams(history.location.search).get('tab');
  return tab === 'providers' || tab === 'models' || tab === 'logs' ? tab : undefined;
};

const ModelManagementPage = () => {
  const access = useAccess();
  const items = useMemo(() => {
    const tabs = [] as Array<{ key: ModelManagementTab; label: string; children: React.ReactNode }>;
    if (access.canViewPlatformAiProviders) {
      tabs.push({ key: 'providers', label: '模型服务商', children: <PlatformProvidersPage /> });
    }
    if (access.canViewPlatformAiModels) {
      tabs.push({ key: 'models', label: 'AI 大模型', children: <PlatformModelsPage /> });
    }
    if (access.canViewAiCallLogs) {
      tabs.push({ key: 'logs', label: '调用日志', children: <AiCallLogsPage /> });
    }
    return tabs;
  }, [access.canViewAiCallLogs, access.canViewPlatformAiModels, access.canViewPlatformAiProviders]);
  const requestedTab = tabFromSearch();
  const activeKey = items.some((item) => item.key === requestedTab)
    ? requestedTab
    : items[0]?.key;

  if (!activeKey) return null;

  if (requestedTab && requestedTab !== activeKey) {
    history.replace(`/ai-service-management/model-management?tab=${activeKey}`);
  }

  return (
    <Tabs
      activeKey={activeKey}
      items={items}
      onChange={(tab) => history.replace(`/ai-service-management/model-management?tab=${tab}`)}
    />
  );
};

export default ModelManagementPage;
