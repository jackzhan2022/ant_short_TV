const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
function page(file, api) {
  const apiPath = require.resolve('../services/api');
  const actual = require(apiPath);
  const target = require.resolve(file);
  let definition;
  const previousPage = global.Page;
  const previousWx = global.wx;
  global.Page = (value) => { definition = value; };
  global.wx = { navigateTo(value) { global.wx.lastNavigation = value; }, stopPullDownRefresh() {}, createVideoContext: () => ({ pause() {} }) };
  require.cache[apiPath].exports = { ...actual, getClient: () => api };
  delete require.cache[target];
  try { require(target); } finally {
    require.cache[apiPath].exports = actual;
    global.Page = previousPage;
  }
  return {
    instance: { ...definition, data: { ...definition.data }, setData(value) { Object.assign(this.data, value); } },
    restore() { global.wx = previousWx; delete require.cache[target]; },
  };
}

test('首页以 dramaId 而非上架记录 id 导航，无假数据', async () => {
  const p = page('../pages/index/index', { dramas: async () => [{ id: 101, dramaId: 7, title: '后台剧集' }] });
  try {
    await p.instance.load();
    p.instance.openDrama({ currentTarget: { dataset: { index: 0 } } });
    assert.equal(global.wx.lastNavigation.url, '/pages/detail/index?dramaId=7');
    assert.equal(p.instance.data.dramas.length, 1);
  } finally { p.restore(); }
});

test('首页空列表及失败重试不保留失效剧库', async () => {
  let failed = true;
  const p = page('../pages/index/index', { dramas: async () => { if (failed) throw { status: 403 }; return []; } });
  try {
    await p.instance.load();
    assert.ok(p.instance.data.error.includes('停用'));
    assert.deepEqual(p.instance.data.dramas, []);
    failed = false;
    await p.instance.load();
    assert.equal(p.instance.data.error, '');
    assert.equal(p.instance.data.loading, false);
  } finally { p.restore(); }
});

test('详情用真实目录元数据和 episodeId 选集播放', async () => {
  const calls = [];
  const api = {
    dramas: async () => [{ id: 101, dramaId: 7, title: '后台标题' }],
    episodes: async (id) => { calls.push(['episodes', id]); return [{ id: 202, episodeId: 9, episodeNo: 1 }]; },
    play: async (dramaId, episodeId) => {
      calls.push(['play', dramaId, episodeId]);
      return { url: 'https://cdn.example.test/v', expiresAt: '2099-01-01T00:00:00Z' };
    },
  };
  const p = page('../pages/detail/index', api);
  try {
    p.instance.onLoad({ dramaId: '7' });
    await new Promise(setImmediate);
    assert.equal(p.instance.data.drama.title, '后台标题');
    p.instance.selectEpisode({ currentTarget: { dataset: { index: 0 } } });
    await new Promise(setImmediate);
    assert.deepEqual(calls, [['episodes', '7'], ['play', '7', 9]]);
    assert.equal(p.instance.data.selectedId, '9');
    assert.equal(p.instance.data.videoUrl, 'https://cdn.example.test/v');
    p.instance.onUnload();
  } finally { p.restore(); }
});

test('详情 binderror 真实事件传入 playback，显示对应提示且日志无敏感内容', async (t) => {
  const logs = [];
  t.mock.method(console, 'warn', (...args) => logs.push(args));
  let plays = 0;
  const p = page('../pages/detail/index', {
    dramas: async () => [{ dramaId: 7 }],
    episodes: async () => [{ episodeId: 9 }],
    play: async () => { plays += 1; return { url: 'https://cdn.test/v?signature=SECRET', expiresAt: '2099-01-01T00:00:00Z' }; },
  });
  try {
    p.instance.onLoad({ dramaId: '7' });
    await new Promise(setImmediate);
    p.instance.selectEpisode({ currentTarget: { dataset: { index: 0 } } });
    await new Promise(setImmediate);
    p.instance.onTimeUpdate({ detail: { currentTime: 25 } });
    const event = { type: 'error', detail: { errCode: -123, errMsg: 'video:fail certificate error https://cdn.test/v?signature=SECRET code=LOGIN_SECRET' } };
    const originalCheck = p.instance.player.check;
    let received;
    p.instance.player.check = (value) => { received = value; return originalCheck(value); };
    await p.instance.onVideoError(event);
    assert.equal(received, event);
    assert.ok(p.instance.data.playError.includes('证书'));
    assert.equal(p.instance.data.videoUrl, '');
    assert.equal(plays, 1);
    assert.equal(logs.length, 1);
    assert.deepEqual(logs[0][1], { source: 'video', category: 'TLS', reason: 'TLS', errMsg: 'TLS', nativeCode: -123 });
    assert.doesNotMatch(JSON.stringify(logs), /SECRET|https:|signature|code=/);
    await p.instance.retryPlay();
    assert.equal(plays, 2);
    assert.equal(p.instance.data.initialTime, 25);
    assert.equal(p.instance.data.playError, '');
  } finally { p.instance.onUnload(); p.restore(); }
});

test('详情剧集已下架或路由无效时显示错误，不播放', async () => {
  for (const id of ['7', '../bad']) {
    const p = page('../pages/detail/index', { dramas: async () => [], episodes: async () => [] });
    try {
      p.instance.onLoad({ dramaId: id });
      await new Promise(setImmediate);
      assert.ok(p.instance.data.error.includes('下架'));
      assert.equal(p.instance.data.videoUrl, '');
      p.instance.onUnload();
    } finally { p.restore(); }
  }
});

test('配置采用独立原生根目录、按需加载且不打包测试', () => {
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
  const project = JSON.parse(fs.readFileSync(path.join(root, 'project.config.json'), 'utf8'));
  assert.equal(app.lazyCodeLoading, 'requiredComponents');
  assert.equal(project.miniprogramRoot, './');
  assert.equal(project.appid, 'wx2a250e3fe6b31ce4');
  assert.equal(project.setting.urlCheck, true);
  assert.ok(project.packOptions.ignore.some((item) => item.value === 'tests'));
  for (const name of app.pages) {
    for (const ext of ['js', 'json', 'wxml', 'wxss']) assert.ok(fs.existsSync(path.join(root, name + '.' + ext)));
  }
  const wxml = fs.readFileSync(path.join(root, 'pages/detail/index.wxml'), 'utf8');
  assert.ok(wxml.includes('<video '));
  assert.ok(wxml.includes('binderror="onVideoError"'));
  assert.equal(require('../config/api').baseURL, 'https://antv.aixmax.cn');
});
