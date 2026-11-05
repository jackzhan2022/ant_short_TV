const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { StudioStore } = require('../dist/store');
const key = crypto.randomBytes(32);
const codec = {
  encrypt(value) {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const bytes = Buffer.concat([cipher.update(value), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), bytes]);
  },
  decrypt(value) {
    const cipher = crypto.createDecipheriv(
      'aes-256-gcm',
      key,
      value.subarray(0, 12),
    );
    cipher.setAuthTag(value.subarray(12, 28));
    return Buffer.concat([
      cipher.update(value.subarray(28)),
      cipher.final(),
    ]).toString();
  },
};
const png = fs.readFileSync('assets/icon.png');
const reference = {
  name: 'ref.png',
  dataUrl: `data:image/png;base64,${png.toString('base64')}`,
};
const draft = {
  prompt: '画一只熊',
  model: 'gpt-image-2',
  size: '1024x1024',
  quality: 'auto',
  format: 'png',
  references: [reference],
};
function setup(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bear-store-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return new StudioStore(root, codec);
}

test('keys stay encrypted on disk and are never returned to the renderer', (t) => {
  const store = setup(t);
  store.updateSettings({
    key: 'private-test-secret',
    responseModel: 'gpt-6-astra',
  });
  assert.equal(store.key(), 'private-test-secret');
  assert.equal(store.settings().hasKey, true);
  assert.equal(
    JSON.stringify(store.settings()).includes('private-test-secret'),
    false,
  );
  assert.equal(
    fs
      .readFileSync(path.join(store.root, 'settings.json'), 'utf8')
      .includes('private-test-secret'),
    false,
  );
  store.updateSettings({ key: '', responseModel: 'other-model' });
  assert.equal(store.key(), 'private-test-secret');
  const reloaded = new StudioStore(store.root, codec);
  assert.equal(reloaded.key(), 'private-test-secret');
  reloaded.updateSettings({ clearKey: true, responseModel: 'other-model' });
  assert.throws(() => reloaded.key());
});

test('failed credential encryption does not overwrite an existing configuration', (t) => {
  const store = setup(t);
  store.updateSettings({ key: 'original', responseModel: 'gpt-6-astra' });
  const denied = new StudioStore(store.root, {
    ...codec,
    encrypt() {
      throw new Error('unavailable');
    },
  });
  assert.throws(() =>
    denied.updateSettings({ key: 'replacement', responseModel: 'gpt-6-astra' }),
  );
  assert.equal(new StudioStore(store.root, codec).key(), 'original');
});

test('saved creation can be reopened, reused and added as a reference without mutating its source', (t) => {
  const store = setup(t);
  const record = store.create(draft);
  store.finish(record.id, { status: 'succeeded' }, [
    { bytes: png, format: 'png', thumbnail: png },
  ]);
  const reopened = new StudioStore(store.root, codec);
  assert.equal(reopened.list()[0].imageCount, 1);
  assert.deepEqual(reopened.reuse(record.id), draft);
  const ref = reopened.reference(record.id, 0);
  assert.equal(ref.dataUrl, reference.dataUrl);
  assert.equal(reopened.get(record.id).prompt, draft.prompt);
  assert.throws(() => reopened.imagePath('../settings.json', 0));
  assert.throws(() => reopened.imagePath(record.id, -1));
  assert.throws(() => reopened.imagePath(record.id, 1));
});

test('restart marks unfinished requests interrupted rather than submitting them again', (t) => {
  const store = setup(t);
  const record = store.create(draft);
  const reopened = new StudioStore(store.root, codec);
  assert.equal(reopened.get(record.id).status, 'interrupted');
  assert.equal(reopened.get(record.id).imageCount, 0);
  assert.match(reopened.get(record.id).error, /不会自动重试/);
});

test('unsubmitted tasks survive restart queued, dispatch transitions before provider calls', (t) => {
  const store = setup(t);
  const record = store.create(draft, { queued: true });
  assert.equal(record.status, 'queued');
  const reopened = new StudioStore(store.root, codec);
  assert.equal(reopened.get(record.id).status, 'queued');
  assert.equal(reopened.start(record.id).status, 'running');
  assert.throws(() => reopened.start(record.id));
  assert.equal(
    new StudioStore(store.root, codec).get(record.id).status,
    'interrupted',
  );
});

test('batch output groups stay below selected directory and concurrency cannot exceed five', (t) => {
  const store = setup(t);
  const record = store.create(draft, {
    queued: true,
    outputGroup: ['项目A', '角色'],
  });
  assert.equal(
    record.outputDirectory,
    path.join(store.settings().outputDirectory, '项目A', '角色'),
  );
  assert.throws(() => store.create(draft, { outputGroup: ['..', 'secret'] }));
  assert.throws(() =>
    store.updateSettings({ responseModel: 'gpt-6-astra', concurrency: 6 }),
  );
  store.updateSettings({ responseModel: 'gpt-6-astra', concurrency: 3 });
  assert.equal(new StudioStore(store.root, codec).settings().concurrency, 3);
});

test('explicit retry preserves original prompt, destination and request model after settings change', (t) => {
  const store = setup(t);
  const original = store.create(draft, {
    queued: true,
    outputGroup: ['源项目', '角色'],
    origin: {
      tenantId: 1,
      teamName: 'A',
      sourceAssetId: 10,
      projectName: '项目',
      assetName: '角色',
      type: 'CHARACTER',
      targetProjectId: 20,
      targetAssetId: 30,
    },
  });
  assert.throws(() => store.retry(original.id));
  store.finish(original.id, { status: 'interrupted' });
  store.updateSettings({ responseModel: 'new-model' });
  const retry = store.retry(original.id);
  assert.notEqual(retry.id, original.id);
  assert.equal(retry.status, 'queued');
  assert.equal(retry.outputDirectory, original.outputDirectory);
  assert.equal(retry.responseModel, original.responseModel);
  assert.deepEqual(retry.origin, original.origin);
  assert.equal(retry.prompt, original.prompt);
});
