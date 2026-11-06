const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const asar = require('@electron/asar');
const crypto = require('node:crypto');

(async () => {
  const root = path.resolve(__dirname, '..');
  const config = require('../package.json');
  const packagedDirectory = path.join(root, 'release-antv', 'win-unpacked');
  const installedIndex = process.argv.indexOf('--installed');
  const appDirectory =
    installedIndex < 0
      ? packagedDirectory
      : path.resolve(process.argv[installedIndex + 1]);
  const executableName = `${config.build.win.executableName || config.build.productName}.exe`;
  const executablePath = path.join(appDirectory, executableName);
  assert.ok(
    fs.existsSync(executablePath),
    `Installed executable is missing: ${executablePath}`,
  );
  if (installedIndex >= 0) {
    for (const entry of fs.readdirSync(packagedDirectory, {
      recursive: true,
      withFileTypes: true,
    })) {
      if (!entry.isFile()) continue;
      const source = path.join(entry.parentPath, entry.name);
      const destination = path.join(
        appDirectory,
        path.relative(packagedDirectory, source),
      );
      assert.ok(
        fs.existsSync(destination),
        `Installed file is missing: ${destination}`,
      );
      const digest = (file) =>
        crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
      assert.equal(
        digest(destination),
        digest(source),
        `Installed file differs: ${destination}`,
      );
    }
  }
  const shortcutIndex = process.argv.indexOf('--shortcut');
  if (shortcutIndex >= 0) {
    const shortcut = fs.readFileSync(process.argv[shortcutIndex + 1]);
    assert.ok(
      shortcut.includes(Buffer.from(executablePath, 'utf16le')),
      'Shortcut must reference the installed executable',
    );
  }
  const archive = path.join(appDirectory, 'resources', 'app.asar');
  for (const name of [
    'main.js',
    'cloud.js',
    'workspace.js',
    'preload.js',
    'queue.js',
    'dramart.js',
    'source-data.js',
    'source-page.js',
    'ai-match.js',
    'ai-review.js',
    'vector-match.js',
    'vector-model.js',
    'vector-runtime.js',
    'vector-preload.js',
    'vector-page.mjs',
    'vector.html',
    'vector-vendor/transformers.min.js',
    'vector-vendor/ort-wasm-simd-threaded.jsep.mjs',
    'vector-vendor/ort-wasm-simd-threaded.jsep.wasm',
    'vector-vendor/TRANSFORMERS-LICENSE',
    'vector-vendor/ONNX-RUNTIME-LICENSE',
    'vector-vendor/NOTICE.txt',
    'index.html',
    'renderer/renderer.js',
    'renderer/workspace-renderer.js',
    'renderer/workspace-view.js',
  ])
    assert.deepEqual(
      asar.extractFile(archive, path.join('dist', ...name.split('/'))),
      fs.readFileSync(path.join(root, 'dist', name)),
      `packaged ${name} matches verified build`,
    );
  assert.ok(
    !asar
      .listPackage(archive)
      .some((name) =>
        /(?:node_modules|model_quantized\.onnx|\.node$)/.test(name),
      ),
    'Native inference packages and model weights must stay outside the archive',
  );
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-packaged-'));
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  let application;
  try {
    application = await electron.launch({
      executablePath,
      args: [`--user-data-dir=${data}`],
      env,
    });
    const runtime = await application.evaluate(({ app }) => ({
      version: app.getVersion(),
      name: app.getName(),
      packaged: app.isPackaged,
      data: app.getPath('userData'),
    }));
    assert.equal(path.resolve(runtime.data), path.resolve(data));
    assert.equal(runtime.packaged, true);
    assert.equal(runtime.name, config.name);
    assert.equal(runtime.version, require('../package.json').version);
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
    await page.locator('#connection-open').click();
    await page.locator('#settings-open').click();
    await page.locator('#settings-dialog').waitFor();
    await page.locator('#settings-close').click();
    await page.locator('[data-view="assets"]').click();
    await page.screenshot({
      path: path.join(root, 'smoke-output', 'packaged-client.png'),
    });
    assert.deepEqual(errors, []);
    console.log(
      `PASS ${installedIndex < 0 ? 'packaged' : 'installed'} ${runtime.version}: compiled files match ASAR, isolated fresh profile, independent launch, renderer/preload, asset/create/queue views and settings. No credentials or model calls used.`,
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
