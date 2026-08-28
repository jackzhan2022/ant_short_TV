import { useAccess } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Tabs } from 'antd';
import { useMemo, useState } from 'react';
import AiCallLogsPage from '../logs';
import PlatformModelsPage from '../models';
import PlatformProvidersPage from '../providers';

type ModelManagementTab = 'providers' | 'models' | 'logs';

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
  const [activeKey, setActiveKey] = useState<ModelManagementTab>();
  const selectedKey = items.some((item) => item.key === activeKey)
    ? activeKey
    : items[0]?.key;

  if (!selectedKey) return null;

  return (
    <PageContainer title="模型管理">
      <Tabs
        activeKey={selectedKey}
        items={items}
        onChange={(tab) => setActiveKey(tab as ModelManagementTab)}
      />
    </PageContainer>
  );
};

export default ModelManagementPage;
