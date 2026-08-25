import { describe, expect, it } from 'vitest';
import routes from '@root/config/routes';
import zhCNMenu from './locales/zh-CN/menu';

type Route = {
  path?: string;
  hideInMenu?: boolean;
  layout?: boolean;
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
  it('removes template demo pages from the route table', () => {
    const demoPaths = [
      '/welcome',
      '/admin',
      '/dashboard',
      '/form',
      '/list',
      '/profile',
      '/result',
      '/account',
    ];

    for (const path of demoPaths) {
      expect(findRoute(path, routes)).toBeUndefined();
    }
  });

  it('keeps exception pages available but hidden from the sidebar menu', () => {
    expect(findRoute('/exception', routes)).toMatchObject({
      hideInMenu: true,
    });
  });

  it('hides duplicated team and AI assistant entries from the sidebar menu', () => {
    const hiddenPaths = [
      '/team/select',
      '/team/members',
      '/team/settings',
      '/chatbot',
      '/projects/:id/production-workbench/ai-config',
    ];

    for (const path of hiddenPaths) {
      expect(findRoute(path, routes)).toMatchObject({ hideInMenu: true });
    }
  });

  it('renames my teams menu entry to team management', () => {
    expect(zhCNMenu['menu.team.my']).toBe('团队管理');
  });

  it('removes organization management from the route table and menu catalog', () => {
    expect(findRoute('/team/organizations', routes)).toBeUndefined();
    expect((zhCNMenu as Record<string, string>)['menu.team.organizations']).toBeUndefined();
  });

  it('shows the public style library as a first-level menu entry', () => {
    expect(findRoute('/style-library', routes)).toMatchObject({
      path: '/style-library',
      name: 'style-library',
      access: 'canViewStyleLibrary',
    });
    expect(zhCNMenu['menu.style-library']).toBe('风格库');
  });

  it('shows the short drama creation entry as a first-level menu route', () => {
    expect(findRoute('/short-drama-creation', routes)).toMatchObject({
      path: '/short-drama-creation',
      name: 'short-drama-creation',
      access: 'canUseProjectCenter',
    });
    expect(zhCNMenu['menu.short-drama-creation']).toBe('短剧创作');
  });

  it('keeps the production workbench as an independent hidden project page', () => {
    expect(findRoute('/projects/:id/production-workbench', routes)).toMatchObject({
      hideInMenu: true,
      layout: false,
    });
    expect(findRoute('/projects/:id/production-workbench', routes)).not.toHaveProperty(
      'access',
    );

    const hiddenPaths = [
      '/projects/:id/production-workbench/script',
      '/projects/:id/production-workbench/settings',
      '/projects/:id/production-workbench/storyboard',
      '/projects/:id/production-workbench/video',
    ];

    for (const path of hiddenPaths) {
      expect(findRoute(path, routes)).toMatchObject({
        hideInMenu: true,
      });
    }
  });

  it('removes the legacy project detail page', () => {
    expect(findRoute('/projects/:id', routes)).toBeUndefined();
  });
});
