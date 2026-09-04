import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { SettingDrawer } from '@ant-design/pro-components';
import type { RequestConfig, RunTimeLayoutConfig } from '@umijs/max';
import { history, Link } from '@umijs/max';
import { App as AntdApp } from 'antd';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import React from 'react';

// Initialize dayjs plugins globally
dayjs.extend(relativeTime);

import { ErrorBoundary, Footer, OfflineBanner, SidebarAccount, TeamSwitcher } from '@/components';
import {
  getCurrentTenantId,
  queryAuthBootstrap,
  setCurrentTenantId,
} from '@/services/account-team/auth';
import { toBootstrapState } from '@/services/account-team/bootstrap';
import type {
  AuthBootstrap,
  LayoutCurrentUser,
  SelectedTenantAccess,
  TenantSummary,
} from '@/services/account-team/types';
import defaultSettings from '../config/defaultSettings';
import { errorConfig } from './requestErrorConfig';

const isDev = process.env.NODE_ENV === 'development';
const loginPath = '/user/login';
const authenticationPaths = [loginPath, '/user/register', '/user/register-result'];
const isAuthenticationPath = (pathname: string) =>
  authenticationPaths.some(
    (path) => pathname === path || pathname === `${path}/`,
  );

export type AppInitialState = {
  settings?: Partial<LayoutSettings>;
  currentUser?: LayoutCurrentUser;
  currentTenantId?: number;
  tenants?: TenantSummary[];
  selectedTenant?: SelectedTenantAccess | null;
  tenantPermissions?: string[];
  platformPermissions?: string[];
  bootstrap?: AuthBootstrap;
  loading?: boolean;
  fetchUserInfo?: () => Promise<LayoutCurrentUser | undefined>;
  settingDrawerOpen?: boolean;
};

/**
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 * */
export async function getInitialState(): Promise<AppInitialState> {
  const fetchUserInfo = async () => {
    try {
      const response = await queryAuthBootstrap(getCurrentTenantId(), {
        skipErrorHandler: true,
      });
      return toBootstrapState(response.data).currentUser;
    } catch (_error) {
      const { pathname, search, hash } = history.location;
      history.replace(
        `${loginPath}?redirect=${encodeURIComponent(pathname + search + hash)}`,
      );
    }
    return undefined;
  };
  // 如果不是登录页面，执行
  const { location } = history;
  if (!isAuthenticationPath(location.pathname)) {
    try {
      const response = await queryAuthBootstrap(getCurrentTenantId(), {
        skipErrorHandler: true,
      });
      return {
        ...toBootstrapState(response.data),
        fetchUserInfo,
        settings: defaultSettings as Partial<LayoutSettings>,
        settingDrawerOpen: false,
      };
    } catch (_error) {
      const { pathname, search, hash } = history.location;
      history.replace(
        `${loginPath}?redirect=${encodeURIComponent(pathname + search + hash)}`,
      );
    }
    return {
      fetchUserInfo,
      settings: defaultSettings as Partial<LayoutSettings>,
      settingDrawerOpen: false,
    };
  }
  return {
    fetchUserInfo,
    settings: defaultSettings as Partial<LayoutSettings>,
    settingDrawerOpen: false,
  };
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({
  initialState,
  setInitialState,
}) => {
  const refreshTenantContext = async (tenantId: number) => {
    const response = await queryAuthBootstrap(tenantId, {
      skipErrorHandler: true,
    });
    if (response.data.selectedTenant?.tenant.id !== tenantId) {
      throw new Error(
        response.data.unavailableSelectionReason || 'Selected tenant is unavailable',
      );
    }
    await setInitialState((state) => ({
      ...state,
      ...toBootstrapState(response.data),
    }));
    setCurrentTenantId(tenantId);
  };

  return {
    menuDataRender: (menuData) => {
      const find = (path: string, items: any[]): any | undefined => {
        for (const item of items) {
          if (item.path === path) return item;
          const nested = item.children ? find(path, item.children) : undefined;
          if (nested) return nested;
        }
        return undefined;
      };
      const pick = (path: string) => {
        const item = find(path, menuData as any[]);
        return item ? { ...item, children: undefined } : undefined;
      };
      const group = (key: string, name: string, paths: string[]) => ({
        key,
        name,
        children: paths.map(pick).filter(Boolean),
      });
      return [
        group('creation', '创作', [
          '/short-drama-creation',
          '/video-script-decomposition',
          '/script-review-library',
        ]),
        group('mine', '我的', ['/projects/list', '/style-library']),
        group('management', '管理', [
          '/team/my',
          '/ai-service-management/model-management',
        ]),
        group('commercial', '商业', [
          '/commercial-management/packages',
          '/commercial-management/tenants',
          '/ai-service-management/operations',
        ]),
      ].filter((item) => item.children.length > 0);
    },
    menuItemRender: (item, dom) => {
      if (item.path) {
        return (
          <Link to={item.path} prefetch>
            {dom}
          </Link>
        );
      }
      return dom;
    },
    actionsRender: () => {
      return [];
    },
    // waterMarkProps: {
    //   content: initialState?.currentUser?.name,
    // },
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      // 如果没有登录，重定向到 login
      if (
        !initialState?.currentUser &&
        !isAuthenticationPath(location.pathname)
      ) {
        history.replace(
          `${loginPath}?redirect=${encodeURIComponent(location.pathname + location.search + location.hash)}`,
        );
      }
    },
    bgLayoutImgList: [
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/D2LWSqNny4sAAAAAAAAAAAAAFl94AQBr',
        left: 85,
        bottom: 100,
        height: '303px',
      },
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/C2TWRpJpiC0AAAAAAAAAAAAAFl94AQBr',
        bottom: -68,
        right: -45,
        height: '303px',
      },
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/F6vSTbj8KpYAAAAAAAAAAAAAFl94AQBr',
        bottom: 0,
        left: 0,
        width: '331px',
      },
    ],
    links: isDev ? [] : [],
    menuExtraRender: (props) =>
      props.collapsed || !initialState?.currentUser ? null : (
        <div className="ant-short-team-switcher-shell">
          <TeamSwitcher
            currentTenantId={initialState.currentTenantId}
            tenants={initialState.tenants}
            onChange={refreshTenantContext}
          />
        </div>
      ),
    menuFooterRender: (props) =>
      props?.collapsed ? null : (
        <SidebarAccount currentUser={initialState?.currentUser} />
      ),
    // Replace ProLayout's default ErrorBoundary with our offline-aware version,
    // so chunk load errors show friendly messages instead of "Something went wrong."
    ErrorBoundary,
    menuHeaderRender: undefined,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => {
      // if (initialState?.loading) return <PageLoading />;
      return (
        <>
          {children}
          <SettingDrawer
            disableUrlParams
            enableDarkTheme
            collapse={initialState?.settingDrawerOpen}
            onCollapseChange={(open) => {
              setInitialState((s) => ({
                ...s,
                settingDrawerOpen: open,
              }));
            }}
            settings={initialState?.settings}
            onSettingChange={(settings) => {
              setInitialState((s) => ({
                ...s,
                settings,
              }));
            }}
          />
        </>
      );
    },
    ...initialState?.settings,
  };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request: RequestConfig = {
  baseURL: process.env.API_BASE_URL || '',
  ...errorConfig,
};

export function rootContainer(container: React.ReactNode) {
  return (
    <>
      <OfflineBanner />
      <AntdApp>
        <ErrorBoundary>{container}</ErrorBoundary>
      </AntdApp>
    </>
  );
}
