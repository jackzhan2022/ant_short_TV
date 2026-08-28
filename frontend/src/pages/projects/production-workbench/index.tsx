import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  BookOutlined,
  EditOutlined,
  RobotOutlined,
  SettingOutlined,
  SplitCellsOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, Outlet, useLocation, useParams } from '@umijs/max';
import { App, Button, Flex, Result, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount } from '@/services/account-team/points';
import { queryProject } from '@/services/account-team/project';

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
  effectivePermissions?: string[];
  capabilities?: {
    canView: boolean;
    canEdit: boolean;
  };
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
  const [forbidden, setForbidden] = useState(false);

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
      .catch((error: { response?: { status?: number } }) => {
        if (active) {
          if (error.response?.status === 403) {
            setForbidden(true);
          } else {
            message.error('制作台加载失败');
          }
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

  const nextStep = activeStep === 'script' ? 'settings' : activeStep === 'settings' ? 'storyboard' : undefined;

  if (!projectId) {
    return null;
  }

  if (forbidden) {
    return (
      <Result
        status="403"
        title="无权访问该项目"
        subTitle="当前账号没有该项目的访问权限。"
        extra={<Button onClick={() => history.push('/projects/list')}>返回项目列表</Button>}
      />
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--app-color-bg-layout)' }}>
      <header
        style={{
          height: 68,
          background: 'var(--app-color-bg-container)',
          borderBottom: '1px solid var(--app-color-border)',
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
            onClick={() => history.push('/projects/list')}
            aria-label="返回"
            style={{ paddingInline: 0 }}
          />
          <div>
            <Flex align="center" gap={6}>
              <Typography.Text strong style={{ fontSize: 15 }}>
                {project?.name || '项目'}
              </Typography.Text>
              {project?.capabilities?.canEdit && (
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  aria-label="编辑项目名"
                  style={{ paddingInline: 2, height: 20 }}
                />
              )}
            </Flex>
          <div style={{ marginTop: 5, color: 'var(--app-color-text-secondary)', fontSize: 13 }}>
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
              {project?.effectivePermissions?.includes('PROJECT_AI_CONFIG_VIEW') && (
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
              )}
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
                    border: active ? '1px solid var(--app-color-primary)' : '1px solid var(--app-color-border-secondary)',
                    color: active ? 'var(--app-color-primary)' : 'var(--app-color-text)',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: active ? 'var(--app-color-primary-bg)' : 'var(--app-color-bg-container)',
                    fontSize: 12,
                    boxShadow: active
                      ? '0 0 0 1px rgb(82 82 255 / 8%)'
                      : 'none',
                    cursor: 'pointer',
                  }}
                >
                  <span style={{ height: 16, lineHeight: '16px' }}>{step.icon}</span>
                  <span style={{ marginTop: 1 }}>{step.label}</span>
                </button>
                {index < topSteps.length - 1 && (
                  <div style={{ width: 18, height: 1, background: 'var(--app-color-border-secondary)' }} />
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
            background: 'var(--app-color-primary-bg)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--app-color-text-secondary)',
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
          padding: '0 0 72px',
          minHeight: 'calc(100vh - 100px)',
        }}
      >
        <Outlet />
      </main>

      {nextStep ? (
        <Button
          type="primary"
          icon={<ArrowRightOutlined />}
          onClick={() =>
            history.push(
              `/projects/${projectId}/production-workbench/${stepPaths[nextStep]}`,
            )
          }
          style={{
            position: 'fixed',
            right: 28,
            bottom: 46,
            zIndex: 20,
            height: 44,
            paddingInline: 20,
            borderRadius: 22,
            background: '#111111',
            boxShadow: '0 10px 24px rgba(17, 17, 17, 0.22)',
          }}
        >
          进入下一步
        </Button>
      ) : null}

      <div
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          height: 32,
          background: 'rgba(255,255,255,0.94)',
          borderTop: '1px solid var(--app-color-border-secondary)',
          color: 'var(--app-color-text-tertiary)',
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
