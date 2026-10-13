import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@umijs/max', () => ({ request }));

import access from '../../access';
import {
  bindMiniapp,
  canUseMiniapp,
  createAuthorizationRequest,
  deleteDrama,
  deleteEpisode,
  enableMiniapp,
  getAuthorizationAvailability,
  getAuthorizationRequest,
  getDrama,
  importEpisode,
  listDramas,
  listEpisodes,
  listMiniapps,
  listReleases,
  publishDrama,
  saveDrama,
  unpublishDrama,
} from './service';

describe('剧库接口契约', () => {
  beforeEach(() => {
    request.mockReset();
    request.mockResolvedValue({ success: true, data: [] });
  });
  it('直接解包列表，不虚构分页或剧与项目绑定', async () => {
    expect(await listDramas()).toEqual([]);
    await getDrama(3);
    await listEpisodes(3);
    await listMiniapps();
    await listReleases(8);
    expect(request.mock.calls.map(([url]) => url)).toEqual([
      '/api/drama-library',
      '/api/drama-library/3',
      '/api/drama-library/3/episodes',
      '/api/team-miniapps',
      '/api/team-miniapps/8/dramas',
    ]);
  });
  it('官方授权匹配真实路径且创建不发送 body', async () => {
    await getAuthorizationAvailability();
    await createAuthorizationRequest();
    await getAuthorizationRequest('request/id');
    expect(request.mock.calls).toEqual([
      [
        '/api/team-miniapps/authorization/status',
        { method: 'GET', data: undefined },
      ],
      [
        '/api/team-miniapps/authorization/requests',
        { method: 'POST', data: undefined },
      ],
      [
        '/api/team-miniapps/authorization/requests/request%2Fid',
        { method: 'GET', data: undefined },
      ],
    ]);
  });
  it.each([
    'REVOKED',
    'UNKNOWN',
    undefined,
  ])('官方授权 %s 禁止再启用与发布', (authStatus) => {
    expect(
      canUseMiniapp({
        id: 1,
        appid: 'wx1',
        name: '历史',
        bindingType: 'OFFICIAL',
        enabled: true,
        authStatus,
        createdAt: '',
      }),
    ).toBe(false);
  });
  it.each(['OFFICIAL', 'MANUAL'])('保留 %s 有效历史记录', (bindingType) => {
    expect(
      canUseMiniapp({
        id: 1,
        appid: 'wx1',
        name: '历史',
        bindingType,
        enabled: true,
        authStatus: 'AUTHORIZED',
        createdAt: '',
      }),
    ).toBe(true);
  });
  it('创建编辑仅传标题与简介', async () => {
    const data = { title: '独立剧集', description: '简介' };
    await saveDrama(data);
    await saveDrama(data, 3);
    expect(request).toHaveBeenCalledWith('/api/drama-library', {
      method: 'POST',
      data,
    });
    expect(request).toHaveBeenCalledWith('/api/drama-library/3', {
      method: 'PUT',
      data,
    });
  });
  it('两个导入来源使用互斥的真实契约', async () => {
    const local = {
      episodeNo: 1,
      title: '第一集',
      uploadSessionToken: 'verified-session',
    };
    const project = {
      episodeNo: 2,
      title: '第二集',
      projectId: 4,
      sourceVersionId: 12,
    };
    await importEpisode(3, local);
    await importEpisode(3, project);
    expect(request).toHaveBeenCalledWith('/api/drama-library/3/episodes', {
      method: 'POST',
      data: local,
    });
    expect(request).toHaveBeenCalledWith('/api/drama-library/3/episodes', {
      method: 'POST',
      data: project,
    });
  });
  it('手工绑定不请求官方授权或密钥，上架明确传单集 ID', async () => {
    const data = { appid: 'wx0123456789abcdef', name: '团队小程序' };
    await bindMiniapp(data);
    await enableMiniapp(8, false);
    await publishDrama(8, 3, [11, 12]);
    await unpublishDrama(8, 3);
    expect(request).toHaveBeenCalledWith('/api/team-miniapps', {
      method: 'POST',
      data,
    });
    expect(request).toHaveBeenCalledWith('/api/team-miniapps/8', {
      method: 'PATCH',
      data: { enabled: false },
    });
    expect(request).toHaveBeenCalledWith('/api/team-miniapps/8/dramas/3', {
      method: 'PUT',
      data: { episodeIds: [11, 12] },
    });
    expect(request).toHaveBeenCalledWith('/api/team-miniapps/8/dramas/3', {
      method: 'DELETE',
      data: undefined,
    });
  });
  it('删除和业务失败遵循响应契约', async () => {
    await deleteDrama(3);
    await deleteEpisode(3, 11);
    expect(request).toHaveBeenCalledWith('/api/drama-library/3/episodes/11', {
      method: 'DELETE',
      data: undefined,
    });
    request.mockResolvedValue({
      success: false,
      errorMessage: '已发布资源须先下架',
    });
    await expect(deleteDrama(3)).rejects.toThrow('已发布资源须先下架');
  });
});

describe('剧集权限', () => {
  it('分别控制查看、管理、发布与绑定', () => {
    const permissions = access({
      currentUser: { userid: '1' },
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      tenantPermissions: ['DRAMA:VIEW', 'DRAMA:PUBLISH'],
    });
    expect(permissions.canViewDramas).toBe(true);
    expect(permissions.canPublishDramas).toBe(true);
    expect(permissions.canManageDramas).toBe(false);
    expect(permissions.canManageMiniapps).toBe(false);
  });
  it('无活跃团队时禁止全部剧集操作', () => {
    const permissions = access({
      currentUser: { userid: '1' },
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
      tenantPermissions: [
        'DRAMA:VIEW',
        'DRAMA:MANAGE',
        'DRAMA:PUBLISH',
        'MINIAPP:MANAGE',
      ],
    });
    expect(permissions.canViewDramas).toBe(false);
    expect(permissions.canManageDramas).toBe(false);
    expect(permissions.canPublishDramas).toBe(false);
    expect(permissions.canManageMiniapps).toBe(false);
  });
});
