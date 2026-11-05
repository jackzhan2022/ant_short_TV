const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const asar = require('@electron/asar');

(async () => {
  const root = path.resolve(__dirname, '..');
  const archive = path.join(
    root,
    'release-antv',
    'win-unpacked',
    'resources',
    'app.asar',
  );
  for (const name of [
    'main.js',
    'cloud.js',
    'workspace.js',
    'preload.js',
    'queue.js',
    'dramart.js',
    'source-data.js',
    'index.html',
    'renderer/renderer.js',
    'renderer/workspace-renderer.js',
  ])
    assert.deepEqual(
      asar.extractFile(archive, path.join('dist', ...name.split('/'))),
      fs.readFileSync(path.join(root, 'dist', name)),
      `packaged ${name} matches verified build`,
    );
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-packaged-'));
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  let application;
  try {
    application = await electron.launch({
      executablePath: path.join(
        root,
        'release-antv',
        'win-unpacked',
        'ANTV生图.exe',
      ),
      args: [`--user-data-dir=${data}`],
      env,
    });
    const runtime = await application.evaluate(({ app }) => ({
      version: app.getVersion(),
      packaged: app.isPackaged,
      data: app.getPath('userData'),
    }));
    assert.equal(path.resolve(runtime.data), path.resolve(data));
    assert.equal(runtime.packaged, true);
    assert.equal(runtime.version, '0.2.2');
    const page = await application.firstWindow();
    const errors = [];
    page.on('pageerror', (failure) => errors.push(failure.message));
    await page
      .locator('#generate:not([disabled])')
      .waitFor({ state: 'attached' });
    assert.equal(await page.evaluate(() => typeof window.require), 'undefined');
    const workspace = await page.evaluate(() => window.workspace.state());
    assert.equal(workspace.ok, true);
    assert.equal(workspace.value.cloudUrl, 'https://antv.aixmax.cn');
    await page.locator('#connection-open').click();
    assert.equal(
      await page.locator('#cloud-url').textContent(),
      'https://antv.aixmax.cn',
    );
    assert.equal(await page.locator('input#cloud-url').count(), 0);
    await page.locator('[data-close="connection-dialog"]').click();
    await page.locator('[data-view="queue"]').click();
    await page.locator('#queue-view').waitFor();
    await page.locator('[data-view="create"]').click();
    await page.locator('#create-view').waitFor();
    const state = await page.evaluate(() => window.studio.state());
    assert.equal(state.ok, true);
    assert.equal(state.value.settings.hasKey, false);
    assert.equal(state.value.records.length, 0);
    await page.locator('#settings-open').click();
    await page.locator('#settings-dialog').waitFor();
    await page.locator('#settings-close').click();
    await page.locator('[data-view="assets"]').click();
    await page.screenshot({
      path: path.join(root, 'smoke-output', 'packaged-client.png'),
    });
    assert.deepEqual(errors, []);
    console.log(
      'PASS packaged 0.2.2: compiled files match ASAR, isolated fresh profile, independent launch, renderer/preload, asset/create/queue views and settings. No credentials or model calls used.',
    );
  } finally {
    if (application) await application.close().catch(() => {});
    if (
      path.dirname(data) === os.tmpdir() &&
      path.basename(data).startsWith('antv-packaged-')
    )
      fs.rmSync(data, { recursive: true, force: true });
  }
})().catch((failure) => {
  console.error(failure);
  process.exitCode = 1;
});
