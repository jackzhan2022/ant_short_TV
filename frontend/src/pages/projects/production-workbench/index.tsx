import {
  ArrowLeftOutlined,
  BookOutlined,
  EditOutlined,
  RobotOutlined,
  SettingOutlined,
  SplitCellsOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, Outlet, useLocation, useParams } from '@umijs/max';
import { App, Button, Flex, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount } from '@/services/account-team/points';
import { queryProject } from '../detail/service';

type ProjectLite = {
  id: number;
  name: string;
  code?: string;
  status?: string;
  coverUrl?: string | null;
  aspectRatio?: string | null;
  fileFormat?: string | null;
  scriptType?: string | null;
  breakdownStrength?: string | null;
  visualStyle?: string | null;
};

const topSteps = [
  { key: 'script', label: '剧本', icon: <BookOutlined /> },
  { key: 'settings', label: '设定', icon: <SettingOutlined /> },
  { key: 'storyboard', label: '分镜', icon: <SplitCellsOutlined /> },
  { key: 'video', label: '视频', icon: <VideoCameraOutlined /> },
] as const;

const stepPaths = {
  script: 'script',
  settings: 'settings',
  storyboard: 'storyboard',
  video: 'video',
} as const;

const ProductionWorkbench = () => {
  const params = useParams<{ id: string }>();
  const location = useLocation();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const [project, setProject] = useState<ProjectLite>();
  const [pointBalance, setPointBalance] = useState<number>();

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    queryProject(projectId)
      .then((response) => {
        if (active) {
          setProject(response.data);
        }
      })
      .catch(() => {
        if (active) {
          message.error('制作台加载失败');
        }
      });
    return () => {
      active = false;
    };
  }, [message, projectId]);

  useEffect(() => {
    const tenantId = getCurrentTenantId();
    if (!tenantId) {
      return;
    }
    let active = true;
    queryTeamPointAccount(tenantId)
      .then((response) => {
        if (active) {
          setPointBalance(response.data?.balance);
        }
      })
      .catch(() => {
        if (active) {
          setPointBalance(undefined);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const activeStep = useMemo(() => {
    const matched = topSteps.find((step) =>
      location.pathname.includes(`/production-workbench/${step.key}`),
    );
    return matched?.key || 'storyboard';
  }, [location.pathname]);

  if (!projectId) {
    return null;
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f6f7fb' }}>
      <header
        style={{
          height: 68,
          background: '#fff',
          borderBottom: '1px solid #e4e9f3',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
        }}
      >
        <Flex align="center" gap={12}>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => history.push(`/projects/${projectId}`)}
            aria-label="返回"
            style={{ paddingInline: 0 }}
          />
          <div>
            <Flex align="center" gap={6}>
              <Typography.Text strong style={{ fontSize: 15 }}>
                {project?.name || '项目'}
              </Typography.Text>
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                aria-label="编辑项目名"
                style={{ paddingInline: 2, height: 20 }}
              />
            </Flex>
          <div style={{ marginTop: 5, color: '#65708a', fontSize: 13 }}>
            <span style={{ marginRight: 12 }}>
              {project?.aspectRatio || '-'}
            </span>
            <span style={{ marginRight: 12 }}>720p</span>
            <span style={{ marginRight: 12 }}>
              {project?.visualStyle || '-'}
            </span>
              <Button
                type="link"
                size="small"
                icon={<BookOutlined />}
                style={{ padding: 0, height: 'auto' }}
                onClick={() =>
                  history.push(`/projects/${projectId}/production-workbench/script`)
                }
              >
                查看剧本
              </Button>
              <Button
                type="link"
                size="small"
                icon={<RobotOutlined />}
                aria-label="AI 模型"
                style={{ padding: 0, height: 'auto', marginLeft: 10 }}
                onClick={() =>
                  history.push(
                    `/projects/${projectId}/production-workbench/ai-config`,
                  )
                }
              >
                AI 模型
              </Button>
            </div>
          </div>
        </Flex>

        <Flex align="center" gap={0} style={{ transform: 'translateX(-20px)' }}>
          {topSteps.map((step, index) => {
            const active = step.key === activeStep;
            return (
              <div
                key={step.key}
                style={{ display: 'flex', alignItems: 'center' }}
              >
                <button
                  type="button"
                  aria-label={step.label}
                  onClick={() =>
                    history.push(
                      `/projects/${projectId}/production-workbench/${stepPaths[step.key]}`,
                    )
                  }
                  style={{
                    width: 72,
                    height: 44,
                    borderRadius: 22,
                    border: active ? '1px solid #8fa2ff' : '1px solid #e8edf6',
                    color: active ? '#3156ff' : '#111827',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: active ? '#f5f7ff' : '#fff',
                    fontSize: 12,
                    boxShadow: active
                      ? '0 0 0 1px rgba(49,86,255,0.03)'
                      : 'none',
                    cursor: 'pointer',
                  }}
                >
                  <span style={{ height: 16, lineHeight: '16px' }}>{step.icon}</span>
                  <span style={{ marginTop: 1 }}>{step.label}</span>
                </button>
                {index < topSteps.length - 1 && (
                  <div style={{ width: 18, height: 1, background: '#dfe5f2' }} />
                )}
              </div>
            );
          })}
        </Flex>

        <div
          style={{
            minWidth: 96,
            height: 32,
            borderRadius: 8,
            background: '#f1f4ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#6672a8',
            fontSize: 14,
            fontWeight: 600,
          }}
        >
          ✦ {pointBalance ?? '-'}
        </div>
      </header>

      <main
        style={{
          margin: '0 auto',
          width: '100%',
          maxWidth: 1880,
          minWidth: 1100,
          boxSizing: 'border-box',
          padding: 0,
          minHeight: 'calc(100vh - 100px)',
        }}
      >
        <Outlet />
      </main>

      <div
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          height: 32,
          background: 'rgba(255,255,255,0.94)',
          borderTop: '1px solid #eef2f7',
          color: '#9aa3b5',
          textAlign: 'center',
          lineHeight: '32px',
          fontSize: 12,
        }}
      >
        平台内容均由人工智能模型生成，不代表平台立场
      </div>
    </div>
  );
};

export default ProductionWorkbench;
