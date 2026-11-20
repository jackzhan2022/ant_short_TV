const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createClient, messageFor, ApiError } = require('../services/api');
const appid = 'wx0123456789abcdef';
const token = (n) => ({ accessToken: 'token-' + n, tokenType: 'Bearer', expiresAt: '2026-10-09T12:00:00' });
const ok = (data) => ({ statusCode: 200, data: { success: true, data, errorCode: null, errorMessage: null } });
const failure = (statusCode, errorCode) => ({ statusCode, data: { success: false, data: null, errorCode, errorMessage: '服务错误' } });
function mock(handler, envVersion = 'release') {
  const calls = [];
  let logins = 0;
  const wx = {
    getAccountInfoSync: () => ({ miniProgram: { appId: appid, envVersion } }),
    login(options) { logins += 1; queueMicrotask(() => options.success({ code: 'code-' + logins })); },
    request(options) {
      calls.push(options);
      queueMicrotask(() => {
        const result = handler(options);
        if (result === 'network') options.fail({ errMsg: '失败' });
        else options.success(result);
      });
    },
  };
  return { wx, calls, logins: () => logins };
}
function client(m, baseURL = 'https://java.example.test') { return createClient(m.wx, { baseURL }); }

test('空配置不登录、不请求，提示填写 Java 地址', async () => {
  const m = mock(() => ok([]));
  await assert.rejects(client(m, '').dramas(), (e) => e.code === 'CONFIG' && messageFor(e).includes('config/api.js'));
  assert.equal(m.logins(), 0);
  assert.equal(m.calls.length, 0);
});

test('正式版与体验版拒绝 HTTP，开发版允许 HTTP', async () => {
  for (const env of ['release', 'trial']) {
    const m = mock(() => ok([]), env);
    await assert.rejects(client(m, 'http://localhost:8080').dramas(), { code: 'CONFIG' });
    assert.equal(m.calls.length, 0);
  }
  const m = mock((o) => o.method === 'POST' ? ok(token(1)) : ok([]), 'develop');
  await client(m, 'http://localhost:8080').dramas();
  assert.equal(m.calls.length, 2);
});

test('游客 AppID 不可登录', async () => {
  const m = mock(() => ok([]));
  m.wx.getAccountInfoSync = () => ({ miniProgram: { appId: 'touristappid', envVersion: 'develop' } });
  await assert.rejects(client(m).dramas(), { code: 'CONFIG' });
  assert.equal(m.logins(), 0);
});

test('并发首次请求仅登录一次，POST 无 Bearer，其余请求带 Bearer', async () => {
  const m = mock((o) => o.method === 'POST' ? ok(token(1)) : ok([]));
  const api = client(m);
  await Promise.all([api.dramas(), api.episodes(7), api.dramas()]);
  assert.equal(m.logins(), 1);
  const login = m.calls.find((o) => o.method === 'POST');
  assert.equal(login.url, 'https://java.example.test/api/miniapp/auth/login');
  assert.deepEqual(login.data, { appid, code: 'code-1' });
  assert.equal(login.header.Authorization, undefined);
  for (const call of m.calls.filter((o) => o.method === 'GET')) assert.equal(call.header.Authorization, 'Bearer token-1');
});

test('列表 Release/ReleaseEpisode 和播放接口契约，使用 dramaId/episodeId', async () => {
  const drama = { id: 101, dramaId: 7, title: '真实剧名', description: '简介', published: true };
  const episode = { id: 202, releaseId: 101, episodeId: 9, episodeNo: 1, title: '第一集' };
  const play = { dramaId: 7, episodeId: 9, url: 'https://cdn.example.test/video?signature=test', expiresAt: '2026-10-09T12:05:00Z' };
  const m = mock((o) => {
    if (o.method === 'POST') return ok(token(1));
    if (o.url.endsWith('/play')) return ok(play);
    if (o.url.endsWith('/episodes')) return ok([episode]);
    return ok([drama]);
  });
  const api = client(m);
  assert.deepEqual(await api.dramas(), [drama]);
  assert.deepEqual(await api.episodes(drama.dramaId), [episode]);
  assert.deepEqual(await api.play(drama.dramaId, episode.episodeId), play);
  assert.equal(m.calls.at(-1).url, 'https://java.example.test/api/miniapp/dramas/7/episodes/9/play');
});

test('并发旧 token 的 401 仅触发一次重认证', async () => {
  let count = 0;
  const m = mock((o) => {
    if (o.method === 'POST') return ok(token(++count));
    return o.header.Authorization === 'Bearer token-1' ? failure(401, 'UNAUTHORIZED') : ok([]);
  });
  const api = client(m);
  await Promise.all([api.dramas(), api.episodes(7), api.dramas()]);
  assert.equal(count, 2);
  assert.equal(m.calls.filter((o) => o.method === 'GET').length, 6);
});

test('持续 401 仅重认证一次，不无限重试', async () => {
  let count = 0;
  const m = mock((o) => o.method === 'POST' ? ok(token(++count)) : failure(401, 'UNAUTHORIZED'));
  await assert.rejects(client(m).dramas(), { status: 401 });
  assert.equal(count, 2);
  assert.equal(m.calls.length, 4);
});

test('停用导致登录 401 直接终止', async () => {
  const m = mock(() => failure(401, 'UNAUTHORIZED'));
  await assert.rejects(client(m).dramas(), (e) => messageFor(e).includes('停用'));
  assert.equal(m.logins(), 1);
  assert.equal(m.calls.length, 1);
});

for (const [status, code, word] of [[403, 'FORBIDDEN', '停用'], [404, 'NOT_FOUND', '下架'], [500, 'HTTP', '稍后']]) {
  test(`${status} 不重认证、不自动重试，显示对应提示`, async () => {
    const m = mock((o) => o.method === 'POST' ? ok(token(1)) : failure(status, code));
    await assert.rejects(client(m).episodes(7), (e) => e.status === status && messageFor(e).includes(word));
    assert.equal(m.logins(), 1);
    assert.equal(m.calls.length, 2);
  });
}

test('200 业务 UNAUTHORIZED 同样只重认证一次', async () => {
  const m = mock((o) => o.method === 'POST' ? ok(token(1)) : failure(200, 'UNAUTHORIZED'));
  await assert.rejects(client(m).dramas(), { code: 'UNAUTHORIZED' });
  assert.equal(m.logins(), 2);
  assert.equal(m.calls.length, 4);
});

test('网络失败不自动重试，用户再次请求可以恢复', async () => {
  let offline = true;
  const m = mock((o) => o.method === 'POST' ? ok(token(1)) : offline ? 'network' : ok([]));
  const api = client(m);
  await assert.rejects(api.dramas(), (e) => e.code === 'NETWORK' && messageFor(e).includes('网络'));
  offline = false;
  await api.dramas();
  assert.equal(m.logins(), 1);
  assert.equal(m.calls.length, 3);
});

test('wx.login 失败或无 code 可由用户重试，不发空 code', async () => {
  for (const mode of ['fail', 'empty']) {
    const m = mock((o) => o.method === 'POST' ? ok(token(1)) : ok([]));
    m.wx.login = (o) => mode === 'fail' ? o.fail({}) : o.success({});
    const api = client(m);
    await assert.rejects(api.dramas(), (e) => e.code === 'WX_LOGIN' && messageFor(e).includes('微信登录失败'));
    assert.equal(m.calls.length, 0);
    m.wx.login = (o) => o.success({ code: 'recovered' });
    await api.dramas();
    assert.equal(m.calls.length, 2);
  }
});

test('响应包、登录、列表及播放格式不正确均报契约错误', async () => {
  const badLogin = mock(() => ok({ accessToken: 'x', tokenType: 'Basic' }));
  await assert.rejects(client(badLogin).dramas(), { code: 'CONTRACT' });
  for (const bad of [{ statusCode: 200, data: [] }, ok({ items: [] })]) {
    const m = mock((o) => o.method === 'POST' ? ok(token(1)) : bad);
    await assert.rejects(client(m).dramas(), { code: 'CONTRACT' });
  }
  for (const data of [{ url: 'http://cdn.example.test/v', expiresAt: '2026-10-09T12:00:00Z' }, { url: 'https://cdn.example.test/v', expiresAt: 'bad' }]) {
    const m = mock((o) => o.method === 'POST' ? ok(token(1)) : ok(data));
    await assert.rejects(client(m).play(7, 9), { code: 'CONTRACT' });
  }
});

test('平台失败分类、中文提示及日志只包含安全字段，不自动重试', async (t) => {
  const logs = [];
  t.mock.method(console, 'warn', (...args) => logs.push(args));
  const cases = [
    ['request:fail url not in domain list', 'REQUEST_DOMAIN', '域名不合法'],
    ['request:fail ssl hand shake error', 'TLS', '证书'],
    ['request:fail certificate verify failed', 'TLS', '证书'],
    ['request:fail timeout', 'TIMEOUT', '超时'],
    ['request:fail connection refused', 'NETWORK', '网络'],
    ['request:fail unknown', 'NETWORK', '网络'],
  ];
  for (const stage of ['login-request', 'GET']) {
    for (const [errMsg, code, word] of cases) {
      const m = mock(() => ok(token(1)));
      let requests = 0;
      m.wx.request = (o) => {
        requests += 1;
        if (stage === 'GET' && o.method === 'POST') return o.success(ok(token(1)));
        o.fail({ errMsg: errMsg + ' https://cdn.test/private?signature=SECRET#TOKEN code=LOGIN_SECRET', errCode: -7, token: 'TOKEN_SECRET', data: { code: 'BODY_SECRET' } });
      };
      await assert.rejects(client(m).dramas(), (e) => {
        assert.equal(e.code, code);
        assert.ok(messageFor(e).includes(word));
        assert.deepEqual(e.diagnostic, { source: 'request', category: code, reason: code, errMsg: code, nativeCode: -7 });
        assert.doesNotMatch(JSON.stringify(e), /SECRET|https:|signature/);
        return true;
      });
      assert.equal(requests, stage === 'GET' ? 2 : 1);
      assert.equal(m.logins(), 1);
    }
  }
  for (const errMsg of ['login:fail timeout', 'login:fail unknown']) {
    const m = mock(() => ok([]));
    m.wx.login = (o) => o.fail({ errMsg: errMsg + ' https://wx.test/?code=SECRET', code: 123456, token: 'SECRET' });
    await assert.rejects(client(m).dramas(), (e) => {
      assert.equal(e.code, 'WX_LOGIN');
      assert.equal(e.diagnostic.reason, errMsg.includes('timeout') ? 'TIMEOUT' : 'NETWORK');
      assert.equal(e.diagnostic.nativeCode, undefined);
      return true;
    });
    assert.equal(m.calls.length, 0);
  }
  assert.equal(logs.length, 14);
  assert.doesNotMatch(JSON.stringify(logs), /SECRET|https:|signature|123456|accessToken|Authorization/);
});

test('路径注入 ID 在发出请求前拒绝', async () => {
  const m = mock(() => ok([]));
  await assert.rejects(client(m).play('../auth', 9), { code: 'CONTRACT' });
  assert.equal(m.calls.length, 0);
  assert.ok(messageFor(new ApiError('EXPIRED', '')).includes('过期'));
});
