/**
 * @name umi 的路由配置
 * @description 只支持 path,component,routes,redirect,wrappers,name,icon 的配置
 * @param path  path 只支持两种占位符配置，第一种是动态参数 :id 的形式，第二种是 * 通配符，通配符只能出现路由字符串的最后。
 * @param component 配置 location 和 path 匹配后用于渲染的 React 组件路径。可以是绝对路径，也可以是相对路径，如果是相对路径，会从 src/pages 开始找起。
 * @param routes 配置子路由，通常在需要为多个路径增加 layout 组件时使用。
 * @param redirect 配置路由跳转
 * @param wrappers 配置路由组件的包装组件，通过包装组件可以为当前的路由组件组合进更多的功能。 比如，可以用于路由级别的权限校验
 * @param name 配置路由的标题，默认读取国际化文件 menu.ts 中 menu.xxxx 的值，如配置 name 为 login，则读取 menu.ts 中 menu.login 的取值作为标题
 * @param icon 配置路由的图标，取值参考 https://ant.design/components/icon-cn， 注意去除风格后缀和大小写，如想要配置图标为 <StepBackwardOutlined /> 则取值应为 stepBackward 或 StepBackward，如想要配置图标为 <UserOutlined /> 则取值应为 user 或者 User
 * @doc https://umijs.org/docs/guides/routes
 */
export default [
  {
    path: '/user',
    layout: false,
    routes: [
      {
        path: '/user/login',
        name: 'login',
        component: './user/login',
      },
      {
        path: '/user',
        redirect: '/user/login',
      },
      {
        name: 'register-result',
        icon: 'checkCircle',
        path: '/user/register-result',
        component: './user/register-result',
      },
      {
        name: 'register',
        icon: 'userAdd',
        path: '/user/register',
        component: './user/register',
      },
      {
        name: '404',
        component: './exception/404',
        path: '/user/*',
      },
    ],
  },
  {
    path: '/team',
    name: 'team',
    icon: 'team',
    routes: [
      {
        path: '/team',
        redirect: '/team/my',
      },
      {
        path: '/team/my',
        name: 'my',
        icon: 'profile',
        component: './team/my',
      },
      {
        path: '/team/select',
        name: 'select',
        icon: 'select',
        hideInMenu: true,
        component: './team/select',
      },
      {
        path: '/team/members',
        name: 'members',
        icon: 'usergroupAdd',
        hideInMenu: true,
        component: './team/members',
      },
      {
        path: '/team/roles',
        name: 'roles',
        icon: 'safetyCertificate',
        access: 'canManageRoles',
        component: './team/roles',
      },
      {
        path: '/team/invitations',
        name: 'invitations',
        icon: 'mail',
        component: './team/invitations',
      },
      {
        path: '/team/invitations/:token',
        component: './team/invitations/detail',
      },
      {
        path: '/team/settings',
        name: 'settings',
        icon: 'setting',
        hideInMenu: true,
        component: './team/settings',
      },
    ],
  },
  {
    path: '/recharge',
    name: 'recharge',
    layout: false,
    access: 'canManageBilling',
    component: './commercial',
  },
  {
    name: 'exception',
    icon: 'warning',
    path: '/exception',
    hideInMenu: true,
    routes: [
      {
        path: '/exception',
        redirect: '/exception/403',
      },
      {
        name: '403',
        icon: 'stop',
        path: '/exception/403',
        component: './exception/403',
      },
      {
        name: '404',
        icon: 'warning',
        path: '/exception/404',
        component: './exception/404',
      },
      {
        name: '500',
        icon: 'bug',
        path: '/exception/500',
        component: './exception/500',
      },
    ],
  },
  {
    path: '/chatbot',
    name: 'chatbot',
    icon: 'robot',
    hideInMenu: true,
    component: './chatbot',
  },
  {
    path: '/ai-service-management',
    name: 'ai-service-management',
    icon: 'api',
    access: 'canViewAiManagement',
    routes: [
      {
        path: '/ai-service-management',
        component: './ai-service-management',
      },
      {
        path: '/ai-service-management/providers',
        name: 'providers',
        icon: 'api',
        access: 'canViewPlatformAiProviders',
        component: './ai-service-management/providers',
      },
      {
        path: '/ai-service-management/models',
        name: 'models',
        icon: 'robot',
        access: 'canViewPlatformAiModels',
        component: './ai-service-management/models',
      },
      {
        path: '/ai-service-management/billing',
        name: 'billing',
        icon: 'dollar',
        access: 'canViewModelBilling',
        component: './ai-service-management/billing',
      },
      {
        path: '/ai-service-management/logs',
        name: 'logs',
        icon: 'profile',
        access: 'canViewAiCallLogs',
        component: './ai-service-management/logs',
      },
      {
        path: '/ai-service-management/operations',
        name: 'operations',
        icon: 'dashboard',
        access: 'canViewPlatformAiProviders',
        component: './ai-service-management/operations',
      },
      {
        path: '/ai-service-management/agents',
        name: 'agents',
        icon: 'robot',
        access: 'canViewBuiltInAiAgents',
        component: './ai-service-management/agents',
      },
    ],
  },
  {
    path: '/video-script-decomposition',
    name: 'video-script-decomposition',
    icon: 'videoCamera',
    access: 'canUseVideoScriptDecomposition',
    component: './video-script-decomposition',
  },
  {
    path: '/style-library',
    name: 'style-library',
    icon: 'picture',
    access: 'canViewStyleLibrary',
    component: './style-library',
  },
  {
    path: '/short-drama-creation',
    name: 'short-drama-creation',
    icon: 'videoCamera',
    access: 'canUseProjectCenter',
    component: './short-drama-creation',
  },
  {
    path: '/script-review',
    name: 'script-review',
    icon: 'audit',
    access: 'canViewScriptReview',
    component: './script-review',
  },
  {
    path: '/projects/:id/production-workbench',
    hideInMenu: true,
    layout: false,
    component: './projects/production-workbench',
    routes: [
      {
        path: '/projects/:id/production-workbench',
        redirect: '/projects/:id/production-workbench/storyboard',
      },
      {
        path: '/projects/:id/production-workbench/ai-config',
        hideInMenu: true,
        component: './projects/production-workbench/ai-config',
      },
      {
        path: '/projects/:id/production-workbench/script',
        hideInMenu: true,
        component: './projects/production-workbench/script',
      },
      {
        path: '/projects/:id/production-workbench/settings',
        hideInMenu: true,
        component: './projects/production-workbench/settings',
      },
      {
        path: '/projects/:id/production-workbench/storyboard',
        hideInMenu: true,
        component: './projects/production-workbench/storyboard',
      },
      {
        path: '/projects/:id/production-workbench/video',
        hideInMenu: true,
        component: './projects/production-workbench/video',
      },
    ],
  },
  {
    path: '/projects',
    name: 'projects',
    icon: 'project',
    access: 'canUseProjectCenter',
    routes: [
      {
        path: '/projects',
        redirect: '/projects/list',
      },
      {
        path: '/projects/list',
        name: 'list',
        icon: 'profile',
        access: 'canViewProjects',
        component: './projects/list',
      },
    ],
  },
  {
    path: '/',
    redirect: '/team/my',
  },
  {
    component: './exception/404',
    path: '/*',
  },
];
