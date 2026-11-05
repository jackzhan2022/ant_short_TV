const { test } = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const flush = () => new Promise((resolve) => setImmediate(resolve));
function setup(t, transport) {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), 'antv-workspace-test-'),
  );
  const original = Module._load;
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
  return workspace;
}
const response = (data) =>
  new Response(JSON.stringify({ success: true, data }));
test('a late response from an old platform cannot overwrite the selected platform inventory', async (t) => {
  let release;
  const workspace = setup(t, async (url) => {
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
  const workspace = setup(t, async (url) => {
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
