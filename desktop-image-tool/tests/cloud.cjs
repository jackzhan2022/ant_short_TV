const { test } = require('node:test');
const assert = require('node:assert/strict');
let CloudClient, normalizeCloudUrl;
try {
  ({ CloudClient, normalizeCloudUrl } = require('../dist/cloud'));
} catch {}
test('cloud connection is explicit and never accepts embedded credentials or arbitrary protocols', () => {
  assert.equal(typeof normalizeCloudUrl, 'function');
  assert.equal(
    normalizeCloudUrl('https://studio.example.com/'),
    'https://studio.example.com',
  );
  for (const value of [
    'file:///secret',
    'https://key@studio.example.com',
    'https://studio.example.com/path',
    'https://studio.example.com?key=x',
  ])
    assert.throws(() => normalizeCloudUrl(value));
});
test('multiple memberships require an explicit choice and stale team selection is cleared', async () => {
  assert.equal(typeof CloudClient, 'function');
  let members = [
    { id: 1, name: 'A', status: 'ACTIVE' },
    { id: 2, name: 'B', status: 'ACTIVE' },
  ];
  const calls = [];
  const client = new CloudClient(
    'https://studio.example.com',
    async (url, init) => {
      calls.push({ url, init });
      return new Response(
        JSON.stringify({
          success: true,
          data: url.endsWith('/my') ? members : [],
        }),
      );
    },
  );
  await client.refreshTeams();
  assert.equal(client.tenantId, undefined);
  await assert.rejects(client.get('/api/desktop/source-projects'), /团队/);
  await client.selectTeam(2);
  await client.get('/api/desktop/source-projects');
  assert.equal(calls.at(-1).init.headers['X-Tenant-Id'], '2');
  members = [{ id: 1, name: 'A', status: 'ACTIVE' }];
  await client.refreshTeams();
  assert.notEqual(client.tenantId, 2);
  assert.equal(client.tenantId, undefined);
  await client.refreshTeams();
  assert.equal(client.tenantId, undefined);
  await assert.rejects(client.get('/api/desktop/source-projects'), /团队/);
  await assert.rejects(client.selectTeam(2), /团队/);
  assert.throws(() => client.get('https://evil.example/api/projects'));
});
test('cloud failures do not invent projects or silently cross origin redirects', async () => {
  assert.equal(typeof CloudClient, 'function');
  const client = new CloudClient(
    'https://studio.example.com',
    async () => new Response('{}', { status: 401 }),
  );
  await assert.rejects(client.refreshTeams(), /登录/);
  const calls = [];
  const good = new CloudClient(
    'https://studio.example.com',
    async (url, init) => {
      calls.push({ url, init });
      return new Response(
        JSON.stringify({
          success: true,
          data: [{ id: 1, name: 'A', status: 'ACTIVE' }],
        }),
      );
    },
  );
  await good.refreshTeams();
  assert.equal(good.tenantId, 1);
  assert.equal(calls[0].init.redirect, 'error');
});

test('a persisted revoked team cannot become another team after client restart', async () => {
  const client = new CloudClient(
    'https://studio.example.com',
    async () =>
      new Response(
        JSON.stringify({
          success: true,
          data: [{ id: 2, name: 'B', status: 'ACTIVE' }],
        }),
      ),
    1,
  );
  await client.refreshTeams();
  await client.refreshTeams();
  assert.equal(client.tenantId, undefined);
  await assert.rejects(client.selectTeam(1));
  assert.equal(client.tenantId, undefined);
  await client.selectTeam(2);
  assert.equal(client.tenantId, 2);
});

test('a scoped 401 cannot silently switch subsequent syncs to a different team', async () => {
  let members = [
    { id: 1, name: 'A', status: 'ACTIVE' },
    { id: 2, name: 'B', status: 'ACTIVE' },
  ];
  const client = new CloudClient('https://studio.example.com', async (url) =>
    url.endsWith('/my')
      ? new Response(JSON.stringify({ success: true, data: members }))
      : new Response('{}', { status: 401 }),
  );
  await client.selectTeam(1);
  await assert.rejects(client.get('/api/desktop/source-projects'), /登录/);
  members = members.filter((team) => team.id === 2);
  await client.refreshTeams();
  await client.refreshTeams();
  assert.equal(client.tenantId, undefined);
});

test('structured cloud validation failures retain actionable reason and status without exposing raw error pages', async () => {
  const client = new CloudClient(
    'https://studio.example.com',
    async () =>
      new Response(
        JSON.stringify({
          success: false,
          errorCode: 'VALIDATION_ERROR',
          errorMessage: 'invalid source name',
        }),
        { status: 400 },
      ),
    1,
  );
  await assert.rejects(
    client.send('/api/desktop/source-projects/sync', 'POST', {}),
    (error) => error.status === 400 && error.message === 'invalid source name',
  );
  const html = new CloudClient(
    'https://studio.example.com',
    async () => new Response('<html>SECRET</html>', { status: 502 }),
    1,
  );
  await assert.rejects(
    html.get('/api/desktop/source-projects'),
    (error) => !error.message.includes('SECRET') && error.status === 502,
  );
});
