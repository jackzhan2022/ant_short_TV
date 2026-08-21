import routes from '../../config/routes';

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
