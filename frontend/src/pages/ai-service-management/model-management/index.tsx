import { PageContainer } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Tabs } from 'antd';
import { useMemo, useState } from 'react';
import { AgentTabContent, SkillTabContent } from '../agents';
import AiCallLogsPage from '../logs';
import PlatformModelsPage from '../platform-models';
import PlatformProvidersPage from '../providers';
import WorkflowAgentsPage from '../workflow-agents';
import WorkflowSkillsPage from '../workflow-skills';

type ModelManagementTab =
  | 'providers'
  | 'models'
  | 'logs'
  | 'agents'
  | 'skills'
  | 'workflow-agents'
  | 'workflow-skills';

const ModelManagementPage = () => {
  const access = useAccess();
  const items = useMemo(() => {
    const tabs = [] as Array<{
      key: ModelManagementTab;
      label: string;
      children: React.ReactNode;
    }>;
    if (access.canViewPlatformAiProviders) {
      tabs.push({
        key: 'providers',
        label: '模型服务商',
        children: <PlatformProvidersPage />,
      });
    }
    if (access.canViewPlatformAiModels) {
      tabs.push({
        key: 'models',
        label: 'AI 大模型',
        children: <PlatformModelsPage />,
      });
    }
    if (access.canViewAiCallLogs) {
      tabs.push({
        key: 'logs',
        label: '调用日志',
        children: <AiCallLogsPage />,
      });
    }
    if (access.canViewBuiltInAiAgents) {
      tabs.push({
        key: 'agents',
        label: 'Agent 管理',
        children: <AgentTabContent />,
      });
      tabs.push({
        key: 'skills',
        label: 'Skill 管理',
        children: <SkillTabContent />,
      });
    }
    if (access.canViewWorkflowAgents) {
      tabs.push({
        key: 'workflow-agents',
        label: 'Agent（新）',
        children: <WorkflowAgentsPage />,
      });
    }
    if (access.canViewWorkflowSkills) {
      tabs.push({
        key: 'workflow-skills',
        label: 'Skill（新）',
        children: <WorkflowSkillsPage />,
      });
    }
    return tabs;
  }, [
    access.canViewAiCallLogs,
    access.canViewBuiltInAiAgents,
    access.canViewPlatformAiModels,
    access.canViewPlatformAiProviders,
    access.canViewWorkflowAgents,
    access.canViewWorkflowSkills,
  ]);
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
