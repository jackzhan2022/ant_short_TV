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
  return { workspace, directory };
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
