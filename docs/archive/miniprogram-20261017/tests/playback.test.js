const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createPlayback } = require('../services/playback');
const { ApiError } = require('../services/api');
function setup(handler) {
  let clock = 1000;
  const calls = [];
  const states = [];
  const api = { play(dramaId, episodeId) { calls.push({ dramaId, episodeId }); return handler(calls.length, dramaId, episodeId); } };
  const player = createPlayback(api, (s) => states.push(s), () => clock);
  return { player, calls, states, tick: (value) => { clock = value; } };
}
const signed = (n, expires = 5000) => ({ url: 'https://cdn.example.test/' + n, expiresAt: new Date(expires).toISOString() });

test('选择集数调用真实 ID；未过期视频错误不自动重试', async () => {
  const s = setup(async () => signed(1));
  await s.player.select(7, 9);
  assert.deepEqual(s.calls, [{ dramaId: 7, episodeId: 9 }]);
  await s.player.check(true);
  await s.player.check(true);
  assert.equal(s.calls.length, 1);
  assert.equal(s.states.at(-1).playError.code, 'PLAYBACK');
  assert.equal(s.states.at(-1).videoUrl, '');
});

test('两次正常到期均可续签，每轮合并并发事件并保留进度', async () => {
  const s = setup(async (n) => signed(n, n * 5000));
  await s.player.select(7, 9);
  s.player.timeUpdate(38);
  s.tick(5000);
  await Promise.all([s.player.check(true), s.player.check(true), s.player.check()]);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).initialTime, 38);
  assert.equal(s.states.at(-1).videoUrl, signed(2).url);
  s.player.timeUpdate(96);
  s.tick(10000);
  await Promise.all([s.player.check(true), s.player.check(), s.player.check(true)]);
  assert.equal(s.calls.length, 3);
  assert.equal(s.states.at(-1).videoUrl, signed(3).url);
  assert.equal(s.states.at(-1).initialTime, 96);
  assert.equal(s.states.at(-1).playError, '');
});

test('到期恢复返回过期签名时停止，连续错误事件不触发无界请求', async () => {
  const s = setup(async (n) => signed(n, 5000));
  await s.player.select(7, 9);
  s.player.timeUpdate(42);
  s.tick(5000);
  await s.player.check(true);
  for (let i = 0; i < 10; i += 1) await s.player.check(true);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).videoUrl, '');
  assert.equal(s.states.at(-1).playError.code, 'EXPIRED');
  assert.equal(s.states.filter((state) => state.playLoading).at(-1).initialTime, 42);
  await s.player.retry();
  assert.equal(s.states.filter((state) => state.playLoading).at(-1).initialTime, 42);
  assert.equal(s.calls.length, 4);
  assert.equal(s.states.at(-1).playError.code, 'EXPIRED');
});

test('续签成功后连续未过期播放错误不再次自动请求', async () => {
  const s = setup(async (n) => signed(n, n * 5000));
  await s.player.select(7, 9);
  s.tick(5000);
  await s.player.check(true);
  for (let i = 0; i < 10; i += 1) await s.player.check(true);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).playError.code, 'PLAYBACK');
});

test('服务端连续返回过期签名最多请求两次', async () => {
  const s = setup(async (n) => signed(n, 500));
  await s.player.select(7, 9);
  await s.player.check(true);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).playError.code, 'EXPIRED');
});

test('第一条已过期签名可续签为有效地址', async () => {
  const s = setup(async (n) => signed(n, n === 1 ? 500 : 5000));
  await s.player.select(7, 9);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).videoUrl, signed(2).url);
});

for (const [code, status] of [['NOT_FOUND', 404], ['FORBIDDEN', 403], ['UNAUTHORIZED', 401], ['NETWORK', 0]]) {
  test(`播放 ${code} 清空旧地址且不自动重试`, async () => {
    const s = setup(async () => { throw new ApiError(code, '错误', status); });
    await s.player.select(7, 9);
    await s.player.check(true);
    assert.equal(s.calls.length, 1);
    assert.equal(s.states.at(-1).videoUrl, '');
    assert.equal(s.states.at(-1).playError.code, code);
  });
}

test('续签失败不循环，用户重试可恢复', async () => {
  const s = setup(async (n) => {
    if (n === 2) throw new ApiError('NOT_FOUND', '下架', 404);
    return signed(n, n === 1 ? 5000 : 10000);
  });
  await s.player.select(7, 9);
  s.tick(5000);
  await s.player.check(true);
  await s.player.check(true);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).playError.status, 404);
  await s.player.retry();
  assert.equal(s.calls.length, 3);
  assert.equal(s.states.at(-1).videoUrl, signed(3).url);
});

test('快速切集的迟到响应不能覆盖当前集', async () => {
  let resolveOld;
  const s = setup((n) => n === 1 ? new Promise((resolve) => { resolveOld = resolve; }) : Promise.resolve(signed(n)));
  const old = s.player.select(7, 9);
  await s.player.select(7, 10);
  resolveOld(signed(1));
  await old;
  assert.equal(s.states.at(-1).videoUrl, signed(2).url);
  assert.equal(s.calls.at(-1).episodeId, 10);
});

test('续签中的切集不被迟到过期响应覆盖或触发额外恢复', async () => {
  let resolveRenewal;
  const s = setup((n) => n === 2 ? new Promise((resolve) => { resolveRenewal = resolve; }) : Promise.resolve(signed(n, n * 5000)));
  await s.player.select(7, 9);
  s.player.timeUpdate(38);
  s.tick(5000);
  const renewal = s.player.check(true);
  await s.player.select(7, 10);
  resolveRenewal(signed(2, 500));
  await renewal;
  assert.equal(s.calls.length, 3);
  assert.equal(s.calls.at(-1).episodeId, 10);
  assert.equal(s.states.at(-1).videoUrl, signed(3).url);
  assert.equal(s.states.at(-1).initialTime, 0);
});

test('真实视频事件保留安全诊断，不泄露签名或凭据，手动重试保留进度', async (t) => {
  const logs = [];
  t.mock.method(console, 'warn', (...args) => logs.push(args));
  for (const [errMsg, category] of [
    ['video:fail TLS certificate error', 'TLS'],
    ['video:fail timed out', 'TIMEOUT'],
    ['video:fail network error', 'NETWORK'],
    ['video:fail decode error', 'PLAYBACK'],
    ['video:fail https://cdn.test/v?token=timeout', 'PLAYBACK'],
  ]) {
    const s = setup(async () => ({ ...signed(1), url: 'https://cdn.test/v?signature=SIGN_SECRET' }));
    await s.player.select(7, 9);
    s.player.timeUpdate(42);
    const event = { detail: { errCode: -1004, errMsg: errMsg + ' https://cdn.test/v?signature=SIGN_SECRET&code=LOGIN_SECRET token=TOKEN_SECRET', url: 'https://cdn.test/?secret=SECRET' }, token: 'EVENT_SECRET' };
    await s.player.check(event);
    const error = s.states.at(-1).playError;
    assert.equal(error.code, category);
    assert.deepEqual(error.diagnostic, { source: 'video', category, reason: category, errMsg: category, nativeCode: -1004 });
    assert.doesNotMatch(JSON.stringify(error), /SECRET|https:|signature/);
    await s.player.check(event);
    assert.equal(s.calls.length, 1);
    await s.player.retry();
    assert.equal(s.calls.length, 2);
    assert.equal(s.states.at(-1).initialTime, 42);
  }
  for (const detail of [undefined, { errMsg: {}, errCode: 'SECRET' }, { code: 'https://cdn.test/?token=SECRET' }]) {
    const s = setup(async () => signed(1));
    await s.player.select(7, 9);
    await s.player.check({ detail });
    assert.equal(s.states.at(-1).playError.code, 'PLAYBACK');
    assert.equal(s.states.at(-1).playError.diagnostic.nativeCode, undefined);
  }
  assert.equal(logs.length, 8);
  assert.doesNotMatch(JSON.stringify(logs), /SECRET|https:|signature|token=/);
});

test('携带真实错误事件的到期恢复优先于错误分类且不新增重试', async (t) => {
  const logs = [];
  t.mock.method(console, 'warn', (...args) => logs.push(args));
  const s = setup(async (n) => signed(n, n * 5000));
  await s.player.select(7, 9);
  s.player.timeUpdate(38);
  s.tick(5000);
  const event = { detail: { code: -1, errMsg: 'video:fail timeout https://cdn.test/?sign=SECRET' } };
  await Promise.all([s.player.check(event), s.player.check(event)]);
  assert.equal(s.calls.length, 2);
  assert.equal(s.states.at(-1).playError, '');
  assert.equal(s.states.at(-1).initialTime, 38);
  assert.equal(logs.length, 1);
  assert.equal(logs[0][1].nativeCode, -1);
  assert.doesNotMatch(JSON.stringify(logs), /SECRET|https:/);
});

test('页面卸载后迟到响应不更新页面或续签', async () => {
  let resolve;
  const s = setup(() => new Promise((done) => { resolve = done; }));
  const pending = s.player.select(7, 9);
  const count = s.states.length;
  s.player.dispose();
  resolve(signed(1));
  await pending;
  await s.player.check(true);
  await s.player.retry();
  assert.equal(s.states.length, count);
  assert.equal(s.calls.length, 1);
});
