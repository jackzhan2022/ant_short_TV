import routes from '../../config/routes';
import simpleRoutes from '../../config/routes.simple';

describe('video script decomposition route', () => {
  it('is an independent first-level menu route', () => {
    const route = routes.find(
      (item) => item.path === '/video-script-decomposition',
    );

    expect(route).toMatchObject({
      path: '/video-script-decomposition',
      name: 'video-script-decomposition',
      component: './video-script-decomposition',
    });
    expect(route?.path).not.toContain('/projects/');
    expect(route?.hideInMenu).not.toBe(true);
  });
});

describe('short drama creation route', () => {
  it('is an independent first-level menu route', () => {
    const route = routes.find((item) => item.path === '/short-drama-creation');

    expect(route).toMatchObject({
      path: '/short-drama-creation',
      name: 'short-drama-creation',
      component: './short-drama-creation',
      access: 'canUseProjectCenter',
    });
    expect(route?.path).not.toContain('/projects/');
    expect(route?.hideInMenu).not.toBe(true);
  });

  it('is kept in the simple route table used by the simplification script', () => {
    const route = simpleRoutes.find(
      (item) => item.path === '/short-drama-creation',
    );

    expect(route).toMatchObject({
      path: '/short-drama-creation',
      name: 'short-drama-creation',
      component: './short-drama-creation',
    });
  });
});

describe('built-in Agent catalog route', () => {
  it('is a read-only AI management child route', () => {
    const parent = routes.find(
      (item) => item.path === '/ai-service-management',
    );
    const route = parent?.routes?.find(
      (item) => item.path === '/ai-service-management/agents',
    );

    expect(route).toMatchObject({
      path: '/ai-service-management/agents',
      name: 'agents',
      component: './ai-service-management/agents',
      access: 'canViewBuiltInAiAgents',
    });
  });
});
