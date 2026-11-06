const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
let LocalVectorMatcher;
try {
  ({ LocalVectorMatcher } = require('../dist/vector-match'));
} catch {}

function source(id, name, type = 'CHARACTER') {
  return {
    id,
    name,
    type,
    active: true,
    sourceAssetId: `source-${id}`,
    sourceParentId: null,
    matchStatus: 'UNMATCHED',
    targetAssetId: null,
  };
}
function candidate(assetId, name, type = 'CHARACTER') {
  return {
    assetId,
    name,
    type,
    variantId: null,
    parentName: null,
    hasPrompt: true,
  };
}
function fixture() {
  assert.equal(typeof LocalVectorMatcher, 'function');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-vector-'));
  const calls = [];
  const encoder = {
    id: 'test-model-revision-cls-v1',
    dimensions: 3,
    async encode(texts) {
      calls.push(texts);
      return texts.map((name) =>
        name.includes('礼服')
          ? [0, 1, 0]
          : name.includes('海湾')
            ? [0, 0, 1]
            : [1, 0.1, 0],
      );
    },
  };
  const matcher = new LocalVectorMatcher(root, encoder);
  return {
    root,
    calls,
    encoder,
    matcher,
    close() {
      this.matcher.close();
      fs.rmSync(root, { recursive: true, force: true });
    },
  };
}
const signal = () => new AbortController().signal;

test('local vectors rank only supplied legal candidates and never preselect approximate matches', async () => {
  const f = fixture();
  try {
    const tasks = [
      {
        source: source(1, '汐月（日常装）'),
        candidates: [
          candidate(11, '汐月·常服'),
          candidate(12, '汐月·礼服'),
          { ...candidate(13, '汐月'), hasPrompt: false },
          candidate(14, '汐月', 'SCENE'),
        ],
      },
      {
        source: source(2, '潮汐海湾', 'SCENE'),
        candidates: [candidate(21, '潮汐海湾入口', 'SCENE')],
      },
    ];
    const result = await f.matcher.match(tasks, signal());
    assert.deepEqual(
      result.map((r) => r.sourceAssetId),
      [1, 2],
    );
    assert.deepEqual(
      result[0].options.map((c) => c.assetId),
      [11, 12],
    );
    assert.equal(result[0].status, 'ambiguous');
    assert.equal(result[1].status, 'ambiguous');
    assert.ok(
      result[0].options[0].similarity > result[0].options[1].similarity,
    );
    assert.ok(
      f.calls
        .flat()
        .every((text) => !text.includes('prompt') && !text.includes('Key')),
    );
  } finally {
    f.close();
  }
});

test('unchanged names reuse SQLite vectors across restarts and renamed names are encoded incrementally', async () => {
  const f = fixture();
  try {
    const tasks = [
      {
        source: source(1, '汐月（日常装）'),
        candidates: [candidate(11, '汐月·常服')],
      },
    ];
    await f.matcher.match(tasks, signal());
    assert.equal(f.calls.flat().length, 2);
    f.matcher.close();
    f.matcher = new LocalVectorMatcher(f.root, f.encoder);
    await f.matcher.match(tasks, signal());
    assert.equal(f.calls.length, 1);
    tasks[0].source.name = '汐月（礼服）';
    await f.matcher.match(tasks, signal());
    assert.equal(f.calls.flat().length, 3);
    assert.match(f.calls.at(-1)[0], /礼服/);
    const other = new LocalVectorMatcher(f.root, {
      ...f.encoder,
      id: 'different-model',
    });
    await other.match(tasks, signal());
    other.close();
    assert.equal(f.calls.flat().length, 5);
  } finally {
    f.close();
  }
});

test('unique exact names need no model and duplicate exact names require a choice', async () => {
  const f = fixture();
  try {
    const result = await f.matcher.match(
      [
        {
          source: source(1, '汐月'),
          candidates: [candidate(11, '汐月'), candidate(12, '汐月·礼服')],
        },
        {
          source: source(2, '汐月'),
          candidates: [candidate(21, '汐月'), candidate(22, '汐月')],
        },
      ],
      signal(),
    );
    assert.equal(f.calls.length, 0);
    assert.equal(result[0].status, 'suggested');
    assert.equal(result[0].options[0].assetId, 11);
    assert.equal(result[1].status, 'ambiguous');
  } finally {
    f.close();
  }
});

test('corrupt cached vectors are recomputed, and cancellation prevents a partial embedding batch commit', async () => {
  const f = fixture();
  try {
    const tasks = [
      {
        source: source(1, '汐月（日常装）'),
        candidates: [candidate(11, '汐月·常服')],
      },
    ];
    await f.matcher.match(tasks, signal());
    const { DatabaseSync } = require('node:sqlite');
    const db = new DatabaseSync(path.join(f.root, 'asset-vectors.sqlite'));
    db.exec("UPDATE embeddings SET vector = x'00'");
    db.close();
    await f.matcher.match(tasks, signal());
    assert.equal(f.calls.flat().length, 4);
    const controller = new AbortController();
    f.encoder.encode = async (texts) => {
      controller.abort();
      return texts.map(() => [1, 0, 0]);
    };
    tasks[0].source.name = '另一个待取消名称';
    await assert.rejects(f.matcher.match(tasks, controller.signal), /取消/);
    const check = new DatabaseSync(path.join(f.root, 'asset-vectors.sqlite'));
    assert.equal(
      check.prepare('SELECT count(*) AS n FROM embeddings').get().n,
      2,
    );
    check.close();
  } finally {
    f.close();
  }
});

test('bad encoder output cannot poison the vector cache', async () => {
  const f = fixture();
  try {
    const tasks = [
      {
        source: source(1, '汐月（日常装）'),
        candidates: [candidate(11, '汐月·常服')],
      },
    ];
    for (const bad of [
      [[0, 0, 0]],
      [
        [NaN, 1, 0],
        [1, 0, 0],
      ],
      [[1], [1]],
    ]) {
      f.encoder.encode = async () => bad;
      await assert.rejects(f.matcher.match(tasks, signal()), /向量/);
    }
  } finally {
    f.close();
  }
});
