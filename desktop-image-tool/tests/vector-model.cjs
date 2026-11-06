const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { createHash } = require('node:crypto');
let LocalModelFiles;
try {
  ({ LocalModelFiles } = require('../dist/vector-model'));
} catch {}
const bytes = Buffer.from('test model');
const files = [
  {
    name: 'onnx/model_quantized.onnx',
    size: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex'),
  },
];
test('model preparation checks hashes and reuses intact downloads with no network', async () => {
  assert.equal(typeof LocalModelFiles, 'function');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-model-'));
  try {
    let calls = 0;
    const models = new LocalModelFiles(
      root,
      async (_url, init) => {
        calls++;
        assert.equal(init.credentials, 'omit');
        assert.equal(init.headers, undefined);
        return new Response(bytes);
      },
      files,
    );
    await models.ensure(new AbortController().signal);
    await models.ensure(new AbortController().signal);
    assert.equal(calls, 1);
    assert.deepEqual(
      fs.readFileSync(path.join(models.directory, files[0].name)),
      bytes,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
test('bad or cancelled model downloads cannot become cached models or leave partial files', async () => {
  assert.equal(typeof LocalModelFiles, 'function');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-model-'));
  try {
    const models = new LocalModelFiles(
      root,
      async () => new Response(Buffer.alloc(bytes.length)),
      files,
    );
    await assert.rejects(
      models.ensure(new AbortController().signal),
      /模型|下载/,
    );
    assert.equal(
      fs.existsSync(path.join(models.directory, files[0].name)),
      false,
    );
    const controller = new AbortController();
    const cancelled = new LocalModelFiles(
      root,
      async () => {
        controller.abort();
        return new Response(bytes);
      },
      files,
    );
    await assert.rejects(cancelled.ensure(controller.signal), /取消/);
    assert.equal(
      fs.existsSync(path.join(models.directory, files[0].name)),
      false,
    );
    assert.ok(
      !fs
        .readdirSync(models.directory, { recursive: true })
        .some((name) => name.endsWith('.part')),
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
