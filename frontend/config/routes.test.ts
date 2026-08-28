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
});
