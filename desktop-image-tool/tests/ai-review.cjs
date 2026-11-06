const { test } = require('node:test');
const assert = require('node:assert/strict');
let AiReviewManager;
try {
  ({ AiReviewManager } = require('../dist/ai-review'));
} catch {}
function fixture(matcher) {
  let identity = 'team-7-version-1';
  const project = {
    id: 8,
    active: true,
    targetProjectId: 18,
    name: '人鱼故事',
    syncedAt: '2026-11-06T10:00:00',
  };
  const assets = [
    {
      id: 1,
      sourceAssetId: 'role-1',
      sourceParentId: null,
      name: '汐月',
      type: 'CHARACTER',
      active: true,
      matchStatus: 'UNMATCHED',
      targetAssetId: null,
      targetVariantId: null,
      targetName: null,
      hasPrompt: false,
    },
    {
      id: 2,
      sourceAssetId: 'role-2',
      sourceParentId: null,
      name: '沈澈',
      type: 'CHARACTER',
      active: true,
      matchStatus: 'MATCHED',
      targetAssetId: 42,
      targetVariantId: null,
      targetName: '沈澈',
      hasPrompt: true,
    },
    {
      id: 3,
      sourceAssetId: 'variant-1',
      sourceParentId: 'role-1',
      name: '日常装',
      type: 'CHARACTER',
      active: true,
      matchStatus: 'UNMATCHED',
      targetAssetId: null,
      targetVariantId: null,
      targetName: null,
      hasPrompt: false,
    },
  ];
  const candidates = [
    {
      assetId: 41,
      variantId: null,
      name: '汐月（成人）',
      type: 'CHARACTER',
      parentName: null,
      hasPrompt: true,
    },
  ];
  const writes = [];
  const client = {
    async get(url) {
      if (url.endsWith('/assets')) return structuredClone(assets);
      if (url.includes('/candidates')) return structuredClone(candidates);
      return [structuredClone(project)];
    },
    async send(url, method, body) {
      writes.push({ url, method, body });
      const a = assets.find((a) => url.includes(`/assets/${a.id}/`));
      a.targetAssetId = body.assetId;
      a.targetVariantId = body.variantId;
      a.targetName = candidates[0].name;
      a.matchStatus = 'MATCHED';
      a.hasPrompt = true;
      return structuredClone(a);
    },
  };
  const manager = new AiReviewManager(
    () => ({ client, identity }),
    () => ({
      settings: { baseUrl: 'https://example.test', model: 'test-model' },
      key: 'fake-key',
    }),
    matcher ??
      (async (tasks) =>
        tasks.map((t) => ({
          sourceAssetId: t.source.id,
          sourceName: t.source.name,
          type: t.source.type,
          options: t.candidates,
          reason: '候选名称相近',
          status: 'suggested',
        }))),
  );
  return {
    manager,
    client,
    assets,
    project,
    candidates,
    writes,
    changeIdentity() {
      identity = 'team-8-version-2';
    },
  };
}
test('AI review does not write; matched sources are preserved and variants require resolved parents', async () => {
  assert.equal(typeof AiReviewManager, 'function');
  const f = fixture();
  const review = await f.manager.match(8);
  assert.equal(f.writes.length, 0);
  assert.equal(review.suggestions.length, 2);
  assert.equal(
    review.suggestions.find((s) => s.sourceAssetId === 3).status,
    'parentRequired',
  );
  const result = await f.manager.apply({
    reviewId: review.reviewId,
    choices: [{ sourceAssetId: 1, assetId: 41 }],
  });
  assert.equal(result.applied, 1);
  assert.equal(f.writes.length, 1);
  assert.equal(f.assets[1].targetAssetId, 42);
  await assert.rejects(
    f.manager.apply({
      reviewId: review.reviewId,
      choices: [{ sourceAssetId: 1, assetId: 41 }],
    }),
    /失效|重新/,
  );
});
test('stale team/project/source/candidate snapshots cannot be applied', async () => {
  assert.equal(typeof AiReviewManager, 'function');
  for (const change of [
    (f) => f.changeIdentity(),
    (f) => (f.project.targetProjectId = 19),
    (f) => (f.assets[0].name = '重命名角色'),
    (f) => (f.candidates[0].name = '重命名候选'),
    (f) => (f.candidates[0].hasPrompt = false),
  ]) {
    const f = fixture();
    const review = await f.manager.match(8);
    change(f);
    await assert.rejects(
      f.manager.apply({
        reviewId: review.reviewId,
        choices: [{ sourceAssetId: 1, assetId: 41 }],
      }),
      /变化|失效|重新/,
    );
    assert.equal(f.writes.length, 0);
  }
});
test('forged choices and duplicates are rejected before any save', async () => {
  assert.equal(typeof AiReviewManager, 'function');
  for (const choices of [
    [{ sourceAssetId: 1, assetId: 999 }],
    [{ sourceAssetId: 2, assetId: 41 }],
    [{ sourceAssetId: 3, assetId: 41 }],
    [
      { sourceAssetId: 1, assetId: 41 },
      { sourceAssetId: 1, assetId: 41 },
    ],
  ]) {
    const f = fixture();
    const review = await f.manager.match(8);
    await assert.rejects(
      f.manager.apply({ reviewId: review.reviewId, choices }),
      /候选|重复|选择/,
    );
    assert.equal(f.writes.length, 0);
  }
});
test('cancelled reviews cannot save', async () => {
  assert.equal(typeof AiReviewManager, 'function');
  const f = fixture();
  const review = await f.manager.match(8);
  f.manager.cancel(review.reviewId);
  await assert.rejects(
    f.manager.apply({
      reviewId: review.reviewId,
      choices: [{ sourceAssetId: 1, assetId: 41 }],
    }),
    /失效|重新/,
  );
});

for (const parentStatus of ['MATCHED', 'NO_PROMPT'])
  test(`a resolved ${parentStatus} parent permits only its own prompted variant and preserves variantId on confirmation`, async () => {
    const f = fixture();
    Object.assign(f.assets[0], {
      matchStatus: parentStatus,
      targetAssetId: 41,
      targetName: '汐月（成人）',
      hasPrompt: true,
    });
    f.candidates.push(
      {
        ...f.candidates[0],
        variantId: 101,
        name: '日常服',
        parentName: '汐月（成人）',
      },
      {
        ...f.candidates[0],
        assetId: 42,
        variantId: 102,
        name: '日常服',
        parentName: '另一个角色',
      },
      {
        ...f.candidates[0],
        variantId: 103,
        name: '无提示词',
        hasPrompt: false,
      },
    );
    const review = await f.manager.match(8);
    assert.equal(review.suggestions.length, 1);
    assert.deepEqual(
      review.suggestions[0].options.map((o) => [o.assetId, o.variantId]),
      [[41, 101]],
    );
    const result = await f.manager.apply({
      reviewId: review.reviewId,
      choices: [{ sourceAssetId: 3, assetId: 41, variantId: 101 }],
    });
    assert.equal(result.applied, 1);
    assert.equal(f.writes[0].body.variantId, 101);
    assert.equal(f.writes[0].body.expected.sourceParentId, 'role-1');
  });

test('a changed full candidate set invalidates recommendations', async () => {
  const f = fixture();
  const review = await f.manager.match(8);
  f.candidates.push({
    ...f.candidates[0],
    assetId: 99,
    name: '另一个近似候选',
  });
  await assert.rejects(
    f.manager.apply({
      reviewId: review.reviewId,
      choices: [{ sourceAssetId: 1, assetId: 41 }],
    }),
    /变化|重新/,
  );
  assert.equal(f.writes.length, 0);
});

test('cancelling while applying prevents all not-yet-submitted writes', async () => {
  const f = fixture();
  const review = await f.manager.match(8);
  const get = f.client.get.bind(f.client);
  f.client.get = async (url) => {
    const result = await get(url);
    if (url.includes('/candidates')) f.manager.cancel(review.reviewId);
    return result;
  };
  await assert.rejects(
    f.manager.apply({
      reviewId: review.reviewId,
      choices: [{ sourceAssetId: 1, assetId: 41 }],
    }),
    /取消/,
  );
  assert.equal(f.writes.length, 0);
});

test('a pending model request receives cancellation signal', async () => {
  let started,
    aborted = false;
  const ready = new Promise((resolve) => {
    started = resolve;
  });
  const f = fixture(async (_tasks, _settings, _key, signal) => {
    started();
    return new Promise((_resolve, reject) =>
      signal.addEventListener(
        'abort',
        () => {
          aborted = true;
          reject(new Error('AI 匹配已取消'));
        },
        { once: true },
      ),
    );
  });
  const job = f.manager.match(8);
  await ready;
  f.manager.cancel();
  await assert.rejects(job, /取消/);
  assert.equal(aborted, true);
  assert.equal(f.writes.length, 0);
});

test('partial save failure reports confirmed saves and does not retry uncertain requests', async () => {
  const f = fixture();
  f.assets.push({
    ...f.assets[0],
    id: 4,
    sourceAssetId: 'role-4',
    name: '另一个角色',
  });
  const review = await f.manager.match(8);
  const send = f.client.send.bind(f.client);
  let calls = 0;
  f.client.send = async (...args) => {
    calls++;
    if (calls === 2) throw Error('disconnected');
    return send(...args);
  };
  const result = await f.manager.apply({
    reviewId: review.reviewId,
    choices: [
      { sourceAssetId: 1, assetId: 41 },
      { sourceAssetId: 4, assetId: 41 },
    ],
  });
  assert.equal(result.applied, 1);
  assert.equal(result.failures.length, 1);
  assert.equal(calls, 2);
  assert.ok(f.writes[0].url.endsWith('/ai-match'));
  assert.equal(f.writes[0].body.expected.sourceName, '汐月');
  assert.equal(f.writes[0].body.expected.targetProjectId, 18);
});
