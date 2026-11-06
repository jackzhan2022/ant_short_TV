const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
let implementation = {};
try {
  implementation = require('../dist/ai-match');
} catch {}

const source = {
  id: 1,
  sourceAssetId: 'role-1',
  name: '汐月（日常装）',
  type: 'CHARACTER',
  active: true,
  sourceParentId: null,
  matchStatus: 'UNMATCHED',
  targetAssetId: null,
  targetVariantId: null,
  hasPrompt: false,
  targetName: null,
};
const candidate = {
  assetId: 41,
  variantId: null,
  name: '汐月·常服',
  type: 'CHARACTER',
  parentName: null,
  hasPrompt: true,
};
const task = { source, parentName: undefined, candidates: [candidate] };
const settings = { baseUrl: 'https://models.example/v1', model: 'text-model' };
const reply = (matches) =>
  Response.json({
    choices: [
      {
        finish_reason: 'stop',
        message: { content: JSON.stringify({ matches }) },
      },
    ],
  });
const match = {
  sourceId: 1,
  candidateIds: ['41:0'],
  reason: '服装描述相近，请核对',
};

test('AI name matching performs a text-only model request with scoped names and a bearer key', async () => {
  assert.equal(typeof implementation.matchAssetNames, 'function');
  let calls = 0;
  const result = await implementation.matchAssetNames(
    [task],
    settings,
    'local-test-key',
    new AbortController().signal,
    async (url, init) => {
      calls++;
      assert.equal(url, 'https://models.example/v1/chat/completions');
      assert.equal(init.headers.Authorization, 'Bearer local-test-key');
      assert.equal(init.redirect, 'error');
      const body = JSON.parse(init.body);
      assert.equal(body.model, 'text-model');
      assert.equal(body.tools, undefined);
      const payload = JSON.parse(body.messages.at(-1).content);
      assert.equal(payload.sources[0].name, source.name);
      assert.equal(payload.candidateGroups[0].options[0].name, candidate.name);
      assert.equal(JSON.stringify(body).includes('local-test-key'), false);
      assert.equal(JSON.stringify(body).includes('prompt'), false);
      return reply([match]);
    },
  );
  assert.equal(calls, 1);
  assert.equal(result[0].options[0].assetId, 41);
  assert.equal(result[0].sourceName, source.name);
});

test('AI cannot invent candidates, duplicate sources or exceed three options', async () => {
  assert.equal(typeof implementation.matchAssetNames, 'function');
  for (const matches of [
    [{ ...match, candidateIds: ['999:0'] }],
    [match, match],
    [{ ...match, sourceId: 99 }],
    [{ ...match, candidateIds: ['41:0', '41:0'] }],
  ]) {
    await assert.rejects(
      implementation.matchAssetNames(
        [task],
        settings,
        'test',
        new AbortController().signal,
        async () => reply(matches),
      ),
      /结果|候选|重复/,
    );
  }
});

test('ambiguous and absent options stay explicit, and missing-prompt candidates are excluded', async () => {
  assert.equal(typeof implementation.matchAssetNames, 'function');
  const other = { ...candidate, assetId: 42, name: '汐月·礼服' };
  const results = await implementation.matchAssetNames(
    [
      { ...task, candidates: [candidate, other] },
      { ...task, source: { ...source, id: 2 } },
    ],
    settings,
    'test',
    new AbortController().signal,
    async () => reply([{ ...match, candidateIds: ['41:0', '42:0'] }]),
  );
  assert.equal(results[0].status, 'ambiguous');
  assert.equal(results[1].status, 'unmatched');
  await assert.rejects(
    implementation.matchAssetNames(
      [{ ...task, candidates: [{ ...candidate, hasPrompt: false }] }],
      settings,
      'test',
      new AbortController().signal,
      async () => reply([match]),
    ),
    /候选|结果/,
  );
});

test('model HTTP and malformed/truncated responses fail without automatic retry or secret disclosure', async () => {
  assert.equal(typeof implementation.matchAssetNames, 'function');
  for (const response of [
    new Response('key-secret remote diagnostics', { status: 401 }),
    Response.json({
      choices: [{ finish_reason: 'length', message: { content: '{}' } }],
    }),
    Response.json({ choices: [{ message: { content: 'not-json' } }] }),
  ]) {
    let calls = 0;
    await assert.rejects(
      implementation.matchAssetNames(
        [task],
        settings,
        'key-secret',
        new AbortController().signal,
        async () => {
          calls++;
          return response;
        },
      ),
      (e) => !e.message.includes('key-secret'),
    );
    assert.equal(calls, 1);
  }
});

test('text settings preserve encrypted dedicated key and fall back to image key', () => {
  assert.equal(typeof implementation.AiSettingsStore, 'function');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-ai-settings-'));
  const codec = {
    encrypt: (value) => Buffer.from(`cipher:${value}`),
    decrypt: (bytes) => bytes.toString().slice(7),
  };
  try {
    const store = new implementation.AiSettingsStore(root, codec, () => ({
      model: 'image-response-model',
      hasKey: true,
      key: () => 'image-key',
    }));
    assert.equal(store.settings().usingImageKey, true);
    assert.equal(store.key(), 'image-key');
    store.update({ ...settings, key: 'private-model-key' });
    const content = fs.readFileSync(path.join(root, 'text-model.json'), 'utf8');
    assert.equal(content.includes('private-model-key'), false);
    const restored = new implementation.AiSettingsStore(root, codec, () => ({
      model: 'ignored',
      hasKey: true,
      key: () => 'image-key',
    }));
    assert.equal(restored.key(), 'private-model-key');
    assert.equal(restored.settings().hasKey, true);
    assert.equal(restored.settings().usingImageKey, false);
    restored.update({ ...settings, clearKey: true });
    assert.equal(restored.key(), 'image-key');
    assert.throws(
      () =>
        restored.update({
          ...settings,
          baseUrl: 'https://user:password@models.example/v1',
        }),
      /地址/,
    );
    assert.throws(() => restored.update({ ...settings, model: '' }), /模型/);
    assert.throws(
      () =>
        restored.update({ ...settings, baseUrl: 'http://models.example/v1' }),
      /HTTPS/,
    );
    restored.update({ ...settings, baseUrl: 'http://127.0.0.1:8888/v1' });
    restored.update({
      ...settings,
      baseUrl: require('../dist/shared').BASE_URL,
    });
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
