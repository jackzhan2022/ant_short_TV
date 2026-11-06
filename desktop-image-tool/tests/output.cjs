const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  validateOutputName,
  validateAssetOutputName,
  copyWithoutOverwrite,
  copyAssetWithoutOverwrite,
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

test('asset names preserve the literal source stem and reject names Windows cannot save', () => {
  assert.equal(typeof validateAssetOutputName, 'function');
  for (const name of ['  角色.png', '角色'.repeat(100), 'a'.repeat(240)])
    assert.equal(validateAssetOutputName(name), name);
  for (const name of [
    undefined,
    '',
    'bad/name',
    'bad\\name',
    'name.',
    'name ',
    'CON',
    'nul.png',
    'COM¹',
    'x\u0000y',
    'a'.repeat(241),
  ])
    assert.throws(() => validateAssetOutputName(name), /资产名称/);
});

test('asset exports keep the exact basename by moving collisions into a new batch folder', (t) => {
  assert.equal(typeof copyAssetWithoutOverwrite, 'function');
  const root = folder(t);
  const source = path.join(root, 'source');
  const directory = path.join(root, 'batch');
  fs.writeFileSync(source, 'first');
  const first = copyAssetWithoutOverwrite(
    source,
    directory,
    '  角色.png',
    'png',
  );
  fs.writeFileSync(source, 'second');
  const second = copyAssetWithoutOverwrite(
    source,
    directory,
    '  角色.png',
    'png',
  );
  assert.equal(path.basename(first), '  角色.png.png');
  assert.equal(path.basename(second), '  角色.png.png');
  assert.equal(path.dirname(first), directory);
  assert.notEqual(path.dirname(second), directory);
  assert.match(path.basename(path.dirname(second)), /^导入批次-/);
  assert.equal(fs.readFileSync(first, 'utf8'), 'first');
  assert.equal(fs.readFileSync(second, 'utf8'), 'second');
});

test('asset collisions are case insensitive even on a case sensitive filesystem', (t) => {
  assert.equal(typeof copyAssetWithoutOverwrite, 'function');
  const root = folder(t);
  const source = path.join(root, 'source');
  const directory = path.join(root, 'batch');
  fs.writeFileSync(source, 'bytes');
  const first = copyAssetWithoutOverwrite(source, directory, 'Name', 'png');
  const second = copyAssetWithoutOverwrite(source, directory, 'name', 'png');
  assert.notEqual(path.dirname(first), path.dirname(second));
  assert.equal(path.basename(second), 'name.png');
});

test('concurrent asset exports reserve separate folders without changing names', async (t) => {
  assert.equal(typeof copyAssetWithoutOverwrite, 'function');
  const root = folder(t);
  const source = path.join(root, 'source');
  const directory = path.join(root, 'batch');
  fs.writeFileSync(source, 'bytes');
  const { execFile } = require('node:child_process');
  const outputModule = require.resolve('../dist/output');
  const results = await Promise.all(
    ['Name', 'name', 'Name', 'name'].map(
      (name) =>
        new Promise((resolve, reject) =>
          execFile(
            process.execPath,
            [
              '-e',
              'const [modulePath, source, directory, name] = process.argv.slice(1); process.stdout.write(require(modulePath).copyAssetWithoutOverwrite(source, directory, name, "png"));',
              outputModule,
              source,
              directory,
              name,
            ],
            (error, stdout) => (error ? reject(error) : resolve(stdout)),
          ),
        ),
    ),
  );
  assert.equal(
    new Set(results.map((file) => path.dirname(file).toLowerCase())).size,
    4,
  );
  for (const [index, file] of results.entries()) {
    assert.equal(path.basename(file), `${index % 2 ? 'name' : 'Name'}.png`);
    assert.equal(fs.readFileSync(file, 'utf8'), 'bytes');
  }
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
