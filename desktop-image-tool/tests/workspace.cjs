const { test } = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const flush = () => new Promise((resolve) => setImmediate(resolve));
function setup(t, transport, config) {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), 'antv-workspace-test-'),
  );
  const original = Module._load;
  if (config)
    fs.writeFileSync(
      path.join(directory, 'workspace.json'),
      JSON.stringify(config),
    );
  Module._load = function (name, ...args) {
    if (name === 'electron')
      return {
        session: {
          fromPartition: () => ({
            setPermissionRequestHandler() {},
            setPermissionCheckHandler() {},
            fetch: transport,
            cookies: { get: async () => [] },
          }),
        },
      };
    return original.call(this, name, ...args);
  };
  delete require.cache[require.resolve('../dist/workspace')];
  const { WorkspaceController } = require('../dist/workspace');
  Module._load = original;
  const source = {
    state: () => ({ loggedIn: true, syncing: false, discoveredProjects: 1 }),
    collect: async () => [],
    close() {},
  };
  const workspace = new WorkspaceController(
    directory,
    source,
    () => ({ limit: 5, paused: true, waiting: [], running: [] }),
    () => {},
  );
  t.after(() => {
    workspace.close();
    fs.rmSync(directory, { recursive: true, force: true });
  });
  return { workspace, directory, source };
}
const response = (data) =>
  new Response(JSON.stringify({ success: true, data }));
test('a fresh client connects to production without configuring a platform URL and persists its identity', async (t) => {
  const requests = [];
  const { workspace, directory } = setup(t, async (url) => {
    requests.push(url);
    return response([]);
  });
  assert.equal(workspace.state().cloudUrl, 'https://antv.aixmax.cn');
  const saved = JSON.parse(
    fs.readFileSync(path.join(directory, 'workspace.json'), 'utf8'),
  );
  assert.ok(saved.connectionId);
  await workspace.configure({ autoSyncMinutes: 15 });
  assert.equal(workspace.state().cloudUrl, 'https://antv.aixmax.cn');
  assert.equal(workspace.state().autoSyncMinutes, 15);
  assert.ok(requests.length > 0);
  assert.ok(
    requests.every((url) => new URL(url).origin === 'https://antv.aixmax.cn'),
  );
  assert.equal(
    JSON.parse(fs.readFileSync(path.join(directory, 'workspace.json'), 'utf8'))
      .connectionId,
    saved.connectionId,
  );
});
test('legacy blank platform settings migrate to production while preserving connection identity and interval', (t) => {
  const { workspace, directory } = setup(t, async () => response([]), {
    cloudUrl: '',
    autoSyncMinutes: 30,
    connectionId: 'existing-connection',
  });
  assert.equal(workspace.state().cloudUrl, 'https://antv.aixmax.cn');
  assert.equal(workspace.state().autoSyncMinutes, 30);
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(directory, 'workspace.json'), 'utf8')),
    {
      cloudUrl: 'https://antv.aixmax.cn',
      autoSyncMinutes: 30,
      connectionId: 'existing-connection',
    },
  );
});
test('a late response from an old platform cannot overwrite the selected platform inventory', async (t) => {
  let release;
  const { workspace } = setup(t, async (url) => {
    const parsed = new URL(url);
    if (parsed.pathname === '/api/tenants/my')
      return response([{ id: 1, name: 'team', status: 'ACTIVE' }]);
    if (parsed.pathname === '/api/projects')
      return response({ total: 1, data: [{ id: 1, name: parsed.hostname }] });
    if (parsed.hostname === 'a.example')
      return new Promise((resolve) => {
        release = () => resolve(response([{ id: 1, name: 'A' }]));
      });
    return response([{ id: 2, name: 'B' }]);
  });
  const old = workspace.configure({
    cloudUrl: 'https://a.example',
    autoSyncMinutes: 0,
  });
  await flush();
  assert.equal(typeof release, 'function');
  await workspace.configure({
    cloudUrl: 'https://b.example',
    autoSyncMinutes: 0,
  });
  release();
  await old;
  assert.equal(workspace.state().cloudUrl, 'https://b.example');
  assert.equal(workspace.state().projects[0].name, 'B');
  assert.equal(workspace.state().targets[0].name, 'b.example');
});
test('cloud project pagination rejects duplicate IDs instead of claiming complete coverage', async (t) => {
  const { workspace } = setup(t, async (url) => {
    const parsed = new URL(url);
    if (parsed.pathname.endsWith('/my'))
      return response([{ id: 1, name: 'team', status: 'ACTIVE' }]);
    if (parsed.pathname === '/api/projects')
      return response({
        total: 3,
        data:
          parsed.searchParams.get('current') === '1'
            ? [
                { id: 1, name: 'A' },
                { id: 2, name: 'B' },
              ]
            : [{ id: 1, name: 'A' }],
      });
    return response([]);
  });
  const state = await workspace.configure({
    cloudUrl: 'https://a.example',
    autoSyncMinutes: 0,
  });
  assert.match(state.error, /分页/);
  assert.equal(state.targets.length, 0);
});

const basicTransport = async (url) => {
  const pathname = new URL(url).pathname;
  if (pathname === '/api/tenants/my')
    return response([{ id: 1, name: 'Team', status: 'ACTIVE' }]);
  if (pathname === '/api/projects') return response({ total: 0, data: [] });
  return response([]);
};
const collection = (id) => ({
  sourceTeamId: 't',
  sourceProjectId: id,
  sourceScriptId: `s-${id}`,
  name: id,
  assets: [],
});

test('cloud state distinguishes a logged-out session from an unavailable server', async (t) => {
  for (const [status, phase] of [
    [401, 'signedOut'],
    [503, 'error'],
  ]) {
    const { workspace } = setup(t, async () => new Response('{}', { status }));
    const state = await workspace.refresh();
    assert.equal(state.cloudConnection.phase, phase);
    assert.ok(state.cloudConnection.checkedAt);
    assert.equal(state.authenticated, false);
  }
});

test('background source sync permits batch creation in the same team while still preventing team changes', async (t) => {
  const { workspace, source } = setup(t, basicTransport);
  await workspace.refresh();
  let release;
  source.collect = () =>
    new Promise((resolve) => {
      release = () => resolve([]);
    });
  const sync = workspace.sync();
  await flush();
  try {
    assert.equal(workspace.state().source.syncing, true);
    const result = await workspace.withBatch(
      {},
      async (_client, _input, teamId) => teamId,
    );
    assert.equal(result, 1);
    await assert.rejects(workspace.selectTeam(2), /同步/);
    await workspace.configure({ autoSyncMinutes: 0 });
    assert.equal(workspace.state().autoSyncMinutes, 0);
  } finally {
    release();
    await sync;
  }
});

test('one rejected project does not hide later projects and refresh cannot erase sync failure or claim success', async (t) => {
  const saved = [];
  const { workspace, source } = setup(t, async (url, init) => {
    if (url.endsWith('/source-projects/sync-current')) {
      const input = JSON.parse(init.body);
      if (input.sourceProjectId === 'bad')
        return new Response(
          JSON.stringify({
            success: false,
            errorCode: 'VALIDATION_ERROR',
            errorMessage: 'invalid source name',
          }),
          { status: 400 },
        );
      saved.push(input);
      return response(input);
    }
    if (url.endsWith('/source-projects'))
      return response(
        saved.map((p, index) => ({ ...p, id: index + 1, active: true })),
      );
    return basicTransport(url);
  });
  source.collect = async () => [
    collection('first'),
    collection('bad'),
    collection('last'),
  ];
  await workspace.refresh();
  await assert.rejects(workspace.sync(), /bad|失败/);
  assert.deepEqual(
    saved.map((p) => p.sourceProjectId),
    ['first', 'last'],
  );
  assert.equal(workspace.state().projects.length, 2);
  assert.equal(workspace.state().syncStatus.phase, 'failed');
  assert.equal(workspace.state().syncStatus.uploadedScopes, 2);
  assert.equal(workspace.state().syncStatus.failedScopes, 1);
  assert.equal(workspace.state().syncStatus.lastSuccessAt, undefined);
  await workspace.refresh();
  assert.equal(workspace.state().syncStatus.phase, 'failed');
  assert.match(workspace.state().syncStatus.error, /bad/);
});

test('sync uses only the current-inventory endpoint and never falls back when the backend is old', async (t) => {
  const writes = [];
  const { workspace, source } = setup(t, async (url, init) => {
    if (init?.method === 'POST') {
      writes.push(new URL(url).pathname);
      return new Response('{}', { status: 404 });
    }
    return basicTransport(url);
  });
  source.collect = async () => [collection('p')];
  await workspace.refresh();
  await assert.rejects(workspace.sync(), /后端|部署|接口/);
  assert.deepEqual(writes, ['/api/desktop/source-projects/sync-current']);
  assert.equal(workspace.state().syncStatus.automaticPaused, true);
});

test('a collector returning multiple scopes for one project cannot retire history in arbitrary order', async (t) => {
  let writes = 0;
  const { workspace, source } = setup(t, async (url, init) => {
    if (init?.method === 'POST') writes++;
    return basicTransport(url);
  });
  source.collect = async () => [
    collection('p'),
    { ...collection('p'), sourceScriptId: 'other' },
  ];
  await workspace.refresh();
  await assert.rejects(workspace.sync(), /多个|重复|当前/);
  assert.equal(writes, 0);
});

test('automatic sync checks a restored source session and schedules from completion without blocking batches', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { workspace, source } = setup(t, basicTransport, {
    cloudUrl: 'https://antv.aixmax.cn',
    autoSyncMinutes: 1,
    connectionId: 'c',
    tenantId: 1,
  });
  await workspace.refresh();
  let reads = 0,
    release;
  source.state = () => ({
    loggedIn: false,
    syncing: false,
    discoveredProjects: 0,
  });
  source.collect = () => {
    reads++;
    return new Promise((resolve) => {
      release = () => resolve([]);
    });
  };
  t.mock.timers.tick(60000);
  await flush();
  assert.equal(reads, 1);
  t.mock.timers.tick(90000);
  await flush();
  assert.equal(reads, 1);
  release();
  await flush();
  t.mock.timers.tick(59999);
  await flush();
  assert.equal(reads, 1);
  t.mock.timers.tick(1);
  await flush();
  assert.equal(reads, 2);
  release();
  await flush();
});

test('failed automatic sync pauses instead of repeatedly occupying the client until an explicit retry', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { workspace, source } = setup(t, basicTransport, {
    cloudUrl: 'https://antv.aixmax.cn',
    autoSyncMinutes: 1,
    connectionId: 'c',
    tenantId: 1,
  });
  await workspace.refresh();
  let reads = 0;
  source.collect = async () => {
    reads++;
    throw new Error('source unavailable');
  };
  t.mock.timers.tick(60000);
  await flush();
  t.mock.timers.tick(600000);
  await flush();
  assert.equal(reads, 1);
  assert.equal(workspace.state().syncStatus.automaticPaused, true);
  assert.equal(await workspace.withBatch({}, async () => true), true);
  source.collect = async () => {
    reads++;
    return [];
  };
  await workspace.sync();
  assert.equal(workspace.state().syncStatus.automaticPaused, false);
  t.mock.timers.tick(60000);
  await flush();
  assert.equal(reads, 3);
});
