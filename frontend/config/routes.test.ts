import { describe, expect, it } from 'vitest';
import routes from './routes';

const aiServiceManagement = routes.find(
  (route) => route.path === '/ai-service-management',
);

describe('AI service management routes', () => {
  it('exposes a single model-management menu entry and keeps legacy routes hidden', () => {
    const children = aiServiceManagement?.routes ?? [];
    const modelManagement = children.find(
      (route) => route.path === '/ai-service-management/model-management',
    );

    expect(modelManagement).toMatchObject({ name: 'model-management' });
    for (const path of ['billing', 'providers', 'models', 'logs']) {
      expect(children.find((route) => route.path === `/ai-service-management/${path}`)).toMatchObject({
        hideInMenu: true,
        redirect: '/ai-service-management/model-management',
      });
    }
  });

  it('guards AI operations with the platform accounting permission used by its API', () => {
    const operations = (aiServiceManagement?.routes ?? []).find(
      (route) => route.path === '/ai-service-management/operations',
    );

    expect(operations).toMatchObject({
      name: 'operations',
      access: 'canViewModelBilling',
    });
  });
});

describe('commercial management routes', () => {
  it('guards package and tenant pages with independent permissions', () => {
    const commercial = routes.find(
      (route) => route.path === '/commercial-management',
    );
    const children = commercial?.routes ?? [];

    expect(commercial?.access).toBeUndefined();
    expect(
      children.find(
        (route) => route.path === '/commercial-management/packages',
      ),
    ).toMatchObject({ access: 'canViewCommercialPackages' });
    expect(
      children.find(
        (route) => route.path === '/commercial-management/tenants',
      ),
    ).toMatchObject({
      name: 'tenants',
      access: 'canViewPlatformTenants',
      component: './commercial-management/tenants',
    });
  });
});
