import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@umijs/max', () => ({ request }));

import access from '../../access';
import {
  bindMiniapp,
  canManageCode,
  canUseMiniapp,
  commitCode,
  createAuthorizationRequest,
  deleteDrama,
  deleteEpisode,
  enableMiniapp,
  getAuthorizationAvailability,
  getAuthorizationRequest,
  getCodeTemplates,
  getDrama,
  getTrialQrCode,
  importEpisode,
  listDramas,
  listEpisodes,
  listMiniapps,
  listReleases,
  publishDrama,
  saveDrama,
  unpublishDrama,
} from './service';

describe('体验版接口契约', () => {
  const signal = new AbortController().signal;
  beforeEach(() => {
    request.mockReset();
  });
  it('模板使用响应体并固定团队头', async () => {
    request.mockResolvedValue({ success: true, data: [{ templateId: 0 }] });
    expect(await getCodeTemplates(7, signal)).toEqual([{ templateId: 0 }]);
    expect(request).toHaveBeenCalledWith(
      '/api/team-miniapps/code/templates',
      expect.objectContaining({ headers: { 'X-Tenant-Id': '7' }, signal }),
    );
  });
  it('模板0单次提交，不传凭据和AppID', async () => {
    request.mockResolvedValue({ success: true, data: { status: 'SUCCEEDED' } });
    const data = { templateId: 0, version: '1', description: '测试' };
    expect(await commitCode(7, 3, data, signal)).toBe('SUCCEEDED');
    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith(
      '/api/team-miniapps/3/code/commit',
      expect.objectContaining({ method: 'POST', data, skipErrorHandler: true }),
    );
  });
  it.each([
    { response: { data: { errorCode: 'WECHAT_COMMIT_UNKNOWN' } } },
    { info: { errorCode: 'WECHAT_COMMIT_UNKNOWN' } },
    new Error('Network Error'),
    { code: 'ECONNABORTED' },
  ])('UNKNOWN及网络超时从不重试', async (error) => {
    request.mockRejectedValue(error);
    expect(
      await commitCode(
        7,
        3,
        { templateId: 0, version: '1', description: '测试' },
        signal,
      ),
    ).toBe('UNKNOWN');
    expect(request).toHaveBeenCalledTimes(1);
  });
  it('二维码直接返回blob且path可选', async () => {
    const blob = new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], {
      type: 'image/png',
    });
    request.mockResolvedValue(blob);
    expect(await getTrialQrCode(7, 3, 'pages/index', signal)).toBe(blob);
    expect(request).toHaveBeenLastCalledWith(
      '/api/team-miniapps/3/code/trial-qrcode',
      expect.objectContaining({
        responseType: 'blob',
        params: { path: 'pages/index' },
      }),
    );
    await getTrialQrCode(7, 3, '', signal);
    expect(request.mock.calls[1][1].params).toBeUndefined();
  });
  it.each([
    new Blob(['{"errorCode":"FORBIDDEN"}'], { type: 'application/json' }),
    new Blob(['{"errorCode":"FORBIDDEN"}'], { type: 'image/png' }),
    new Blob([], { type: 'image/png' }),
    new Blob([new Uint8Array(2 * 1024 * 1024 + 1)], { type: 'image/png' }),
    new Blob(['<svg>bad</svg>'], { type: 'image/svg+xml' }),
  ])('拒绝JSON、伪图片、空和超大响应', async (blob) => {
    request.mockResolvedValue(blob);
    await expect(getTrialQrCode(7, 3, '', signal)).rejects.toThrow();
  });
  it.each([
    false,
    true,
  ])('JSON blob错误安全解析，HTTP错误=%s', async (rejected) => {
    const blob = new Blob(
      [
        JSON.stringify({
          errorCode: 'FORBIDDEN',
          errorMessage: '<script>敏感上游信息</script>',
        }),
      ],
      { type: 'application/json; charset=utf-8' },
    );
    if (rejected) request.mockRejectedValue({ response: { data: blob } });
    else request.mockResolvedValue(blob);
    await expect(getTrialQrCode(7, 3, '', signal)).rejects.toThrow(
      '二维码获取失败 (FORBIDDEN)',
    );
    expect(request).toHaveBeenCalledTimes(1);
  });
  it.each([
    '{',
    'null',
    '[]',
    '{"errorCode":"<script>"}',
  ])('畸形或未知错误体不透传：%s', async (body) => {
    request.mockResolvedValue(new Blob([body], { type: 'application/json' }));
    await expect(getTrialQrCode(7, 3, '', signal)).rejects.toThrow(
      '二维码响应无效',
    );
  });
  it.each([
    'WECHAT_CODE_REJECTED',
    'WECHAT_COMMIT_IN_PROGRESS',
    'FORBIDDEN',
  ])('明确拒绝 %s 不重试', async (errorCode) => {
    const failure = { response: { data: { errorCode } } };
    request.mockRejectedValue(failure);
    await expect(
      commitCode(
        7,
        3,
        { templateId: 0, version: '1', description: '测试' },
        signal,
      ),
    ).rejects.toBe(failure);
    expect(request).toHaveBeenCalledTimes(1);
  });
  it('只有启用的官方有效授权目标可管理', () => {
    const app = {
      id: 1,
      name: '目标',
      appid: 'wx0123456789abcdef',
      bindingType: 'OFFICIAL',
      authStatus: 'AUTHORIZED',
      enabled: true,
      createdAt: '',
    };
    expect(canManageCode(app)).toBe(true);
    for (const change of [
      { bindingType: 'MANUAL' },
      { enabled: false },
      { authStatus: 'REVOKED' },
      { appid: 'wx1' },
    ])
      expect(canManageCode({ ...app, ...change })).toBe(false);
  });
});

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
  it('剧集菜单统一授权功能并保留平台管理员限制', () => {
    const permissions = access({
      currentUser: { userid: '1' },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
      tenantPermissions: ['MENU:DRAMA_LIBRARY'],
    });
    expect(permissions.canViewDramas).toBe(true);
    expect(permissions.canPublishDramas).toBe(true);
    expect(permissions.canManageDramas).toBe(true);
    expect(permissions.canManageMiniapps).toBe(true);
  });
  it('无活跃团队时禁止全部剧集操作', () => {
    const permissions = access({
      currentUser: { userid: '1' },
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
