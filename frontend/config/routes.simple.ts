export default [
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: 'login',
        path: '/user/login',
        component: './user/login',
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
        component: './team/my',
      },
    ],
  },
  {
    path: '/projects',
    name: 'projects',
    icon: 'project',
    routes: [
      {
        path: '/projects',
        redirect: '/projects/list',
      },
      {
        path: '/projects/list',
        name: 'list',
        component: './projects/list',
      },
    ],
  },
  {
    path: '/short-drama-creation',
    name: 'short-drama-creation',
    icon: 'videoCamera',
    component: './short-drama-creation',
  },
  {
    path: '/',
    redirect: '/team/my',
  },
  {
    component: './exception/404',
    layout: false,
    path: './*',
  },
];
