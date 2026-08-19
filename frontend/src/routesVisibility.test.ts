import { describe, expect, it } from 'vitest';
import routes from '@root/config/routes';
import zhCNMenu from './locales/zh-CN/menu';

type Route = {
  path?: string;
  hideInMenu?: boolean;
  routes?: Route[];
};

const findRoute = (path: string, routeList: Route[]): Route | undefined => {
  for (const route of routeList) {
    if (route.path === path) return route;
    const child = route.routes ? findRoute(path, route.routes) : undefined;
    if (child) return child;
  }
  return undefined;
};

describe('menu routes visibility', () => {
  it('hides Ant Design Pro demo pages from the sidebar menu', () => {
    const demoPaths = [
      '/welcome',
      '/admin',
      '/dashboard',
      '/form',
      '/list',
      '/profile',
      '/result',
      '/exception',
      '/account',
    ];

    for (const path of demoPaths) {
      expect(findRoute(path, routes)).toMatchObject({ hideInMenu: true });
    }
  });

  it('hides duplicated team and AI assistant entries from the sidebar menu', () => {
    const hiddenPaths = [
      '/team/select',
      '/team/members',
      '/team/settings',
      '/chatbot',
    ];

    for (const path of hiddenPaths) {
      expect(findRoute(path, routes)).toMatchObject({ hideInMenu: true });
    }
  });

  it('renames my teams menu entry to team management', () => {
    expect(zhCNMenu['menu.team.my']).toBe('团队管理');
  });

  it('keeps the production workbench as an independent hidden project page', () => {
    expect(findRoute('/projects/:id/production-workbench', routes)).toMatchObject({
      hideInMenu: true,
    });
  });
});
