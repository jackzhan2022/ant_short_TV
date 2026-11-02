const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  validateOutputName,
  copyWithoutOverwrite,
  automaticName,
} = require('../dist/output');
const { StudioStore } = require('../dist/store');

function folder(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bear-output-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}
test('custom filenames accept Chinese and strip image extension, reject traversal and Windows reserved names', () => {
  assert.equal(validateOutputName('角色A_正面.png'), '角色A_正面');
  assert.equal(validateOutputName(undefined), '');
  for (const name of [
    '../secret',
    'C:\\temp\\x',
    'CON',
    'nul.png',
    'LPT1.foo',
    'name.',
    'x*y',
    '\u0000',
    'a'.repeat(81),
  ])
    assert.throws(() => validateOutputName(name));
  assert.match(
    automaticName('2026-10-31T12:00:00+08:00'),
    /^ANTV生图_\d{8}_\d{6}$/,
  );
});
test('exclusive saves append a suffix without overwriting existing files', (t) => {
  const root = folder(t);
  const source = path.join(root, 'source.png');
  fs.writeFileSync(source, 'original');
  const first = copyWithoutOverwrite(source, root, '角色', 'png');
  fs.writeFileSync(source, 'second');
  const second = copyWithoutOverwrite(source, root, '角色', 'png');
  assert.equal(path.basename(first), '角色.png');
  assert.equal(path.basename(second), '角色_2.png');
  assert.equal(fs.readFileSync(first, 'utf8'), 'original');
  assert.equal(fs.readFileSync(second, 'utf8'), 'second');
});
test('configured directory persists, each generation retains its chosen location, failed export preserves history', (t) => {
  const root = folder(t);
  const codec = {
    encrypt: (value) => Buffer.from(value),
    decrypt: (value) => value.toString(),
  };
  const store = new StudioStore(root, codec);
  const selected = path.join(root, 'selected');
  fs.mkdirSync(selected);
  store.setOutputDirectory(selected);
  assert.equal(
    new StudioStore(root, codec).settings().outputDirectory,
    selected,
  );
  const draft = {
    outputName: '我的角色',
    prompt: '熊',
    model: 'gpt-image-2',
    size: 'auto',
    quality: 'auto',
    format: 'png',
    references: [],
  };
  const creation = store.create(draft);
  const next = path.join(root, 'next');
  fs.mkdirSync(next);
  store.setOutputDirectory(next);
  const bytes = fs.readFileSync('assets/icon.png');
  store.finish(creation.id, { status: 'succeeded' }, [
    { bytes, format: 'png', thumbnail: bytes },
  ]);
  const exported = store.exportImages(creation.id);
  assert.equal(exported.savedFiles[0], path.join(selected, '我的角色.png'));
  assert.deepEqual(fs.readFileSync(exported.savedFiles[0]), bytes);
  const other = store.create(draft);
  store.finish(other.id, { status: 'succeeded' }, [
    { bytes, format: 'png', thumbnail: bytes },
  ]);
  fs.rmdirSync(next);
  fs.writeFileSync(next, 'not a folder');
  const failedExport = store.exportImages(other.id);
  assert.equal(failedExport.status, 'succeeded');
  assert.match(failedExport.saveError, /写入失败/);
  assert.deepEqual(fs.readFileSync(store.imagePath(other.id, 0)), bytes);
});
