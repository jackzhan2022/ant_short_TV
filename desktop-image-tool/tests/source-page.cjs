const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
let readSourcePage;
try {
  ({ readSourcePage } = require('../dist/source-page'));
} catch {}

async function read(action, fixture, failure, options = {}) {
  assert.equal(typeof readSourcePage, 'function');
  const calls = [];
  const sdk = {};
  for (const name of [
    'DescribeUser',
    'ListProjects',
    'ListScripts',
    'ListCharacters',
    'ListScenes',
    'ListProps',
  ])
    sdk[name] = async (body) => {
      calls.push({ action: name, body });
      if (failure) throw failure;
      return fixture;
    };
  const runtime = () => ({ A: sdk });
  runtime.m = {
    987: function serviceModule() {
      return 'DescribeUser ListProjects ListScripts';
    },
  };
  const chunks = [];
  chunks.push = (chunk) => {
    chunk[2](runtime);
    return Array.prototype.push.call(chunks, chunk);
  };
  const document = {};
  Object.defineProperty(document, 'cookie', {
    get() {
      throw new Error('Cookies must not be read');
    },
  });
  const context = {
    window: {
      webpackChunkshort_drama_fe: chunks,
      location: { origin: options.origin || 'https://console.volcengine.com' },
    },
    document,
    setTimeout,
    clearTimeout,
  };
  const result = await vm.runInNewContext(
    `(${readSourcePage.toString()})(${JSON.stringify(action)}, {}, ${options.limit ?? 100000})`,
    context,
  );
  return { result: JSON.parse(JSON.stringify(result)), calls };
}

test('source SDK transport keeps credentials in the page and returns only accessible team IDs', async () => {
  const { result, calls } = await read('DescribeUser', {
    Result: {
      AccountId: 'SECRET',
      MemDescription: {
        UserName: 'SECRET',
        Teams: [{ TeamID: 't', TeamDesc: 'SECRET' }],
      },
    },
  });
  assert.deepEqual(result, {
    status: 200,
    value: { Result: { MemDescription: { Teams: [{ TeamID: 't' }] } } },
  });
  assert.equal(calls.length, 1);
  assert.ok(!JSON.stringify(result).includes('SECRET'));
});

test('script discovery returns identity fields without reading episodes or script metadata', async () => {
  const script = { ScriptId: 's', ProjectId: 'p', TeamId: 't' };
  for (const key of ['Episodes', 'Meta', 'ScriptContent', 'ScriptUrl'])
    Object.defineProperty(script, key, {
      get() {
        throw new Error(`Must not read ${key}`);
      },
    });
  const { result } = await read('ListScripts', {
    Result: { Items: [script], Total: 1 },
  });
  assert.deepEqual(result.value, {
    Result: {
      Items: [{ ScriptId: 's', ProjectId: 'p', TeamId: 't' }],
      Total: 1,
    },
  });
});

test('project discovery retains the current script pointer without script content or project description', async () => {
  const { result } = await read('ListProjects', {
    Result: {
      Items: [
        {
          ProjectId: 'p',
          TeamId: 't',
          ProjectName: 'P',
          ScriptId: 'current',
          ProjectDesc: 'SECRET',
          ScriptContent: 'SECRET',
        },
      ],
      Total: 1,
    },
  });
  assert.equal(result.value.Result.Items[0].ScriptId, 'current');
  assert.ok(!JSON.stringify(result).includes('SECRET'));
});

test('asset projections retain variant identities and names without prompts, images or descriptions', async () => {
  const { result } = await read('ListCharacters', {
    Result: {
      Items: [
        {
          CharacterId: 'c',
          Meta: { Name: '主角', Prompt: 'SECRET' },
          ImageInfo: 'SECRET',
          Variants: [
            { CharacterId: 'v', Meta: { Name: '雨衣', Description: 'SECRET' } },
          ],
        },
      ],
      Total: 1,
    },
  });
  assert.equal(result.value.Result.Items[0].Variants[0].CharacterId, 'v');
  assert.equal(result.value.Result.Items[0].Variants[0].Meta.Name, '雨衣');
  assert.ok(!JSON.stringify(result).includes('SECRET'));
});

test('source SDK transport rejects write operations and sanitizes expired-session failures', async () => {
  const blocked = await read('UpdateScript', {});
  assert.equal(blocked.result.unsupported, true);
  assert.equal(blocked.calls.length, 0);
  const expired = await read(
    'ListProjects',
    {},
    {
      response: {
        status: 401,
        data: {
          ResponseMetadata: {
            Error: { Code: 'SessionExpired', Message: 'SECRET' },
          },
        },
      },
    },
  );
  assert.equal(expired.result.status, 401);
  assert.ok(!JSON.stringify(expired.result).includes('SECRET'));
});

test('source transport rejects unexpected origins, malformed rows and oversized projections', async () => {
  const blocked = await read('DescribeUser', {}, undefined, {
    origin: 'https://example.com',
  });
  assert.equal(blocked.result.unsupported, true);
  assert.equal(blocked.calls.length, 0);
  const malformed = await read('ListProjects', { Result: { Items: null } });
  assert.equal(malformed.result.malformed, true);
  const oversized = await read(
    'ListProjects',
    {
      Result: {
        Items: [{ ProjectId: 'p', ProjectName: 'x'.repeat(200) }],
        Total: 1,
      },
    },
    undefined,
    { limit: 100 },
  );
  assert.equal(oversized.result.oversized, true);
});
