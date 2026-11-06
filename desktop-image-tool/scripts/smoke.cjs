const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');

(async () => {
  const root = path.resolve(__dirname, '..');
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-studio-smoke-'));
  const out = path.join(root, 'smoke-output');
  fs.mkdirSync(out, { recursive: true });
  const cloud = http.createServer((_request, response) => {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ success: true, data: [] }));
  });
  await new Promise((resolve) => cloud.listen(0, '127.0.0.1', resolve));
  fs.writeFileSync(
    path.join(data, 'workspace.json'),
    JSON.stringify({
      cloudUrl: `http://127.0.0.1:${cloud.address().port}`,
      autoSyncMinutes: 0,
    }),
  );
  const env = { ...process.env, ANTV_STUDIO_TEST_DATA: data };
  delete env.ELECTRON_RUN_AS_NODE;
  let application;
  let page;
  const errors = [];
  const launch = async () => {
    application = await electron.launch({
      executablePath:
        process.env.ANTV_ELECTRON_EXECUTABLE || require('electron'),
      args: [root],
      env,
    });
    page = await application.firstWindow();
    page.on('pageerror', (error) => errors.push(error.message));
    await page.locator('[data-view="create"]').click();
    await page.locator('#generate:not([disabled])').waitFor();
  };
  try {
    await launch();
    assert.equal(await page.evaluate(() => typeof window.require), 'undefined');
    await page.locator('#example').click();
    await page.screenshot({ path: path.join(out, 'workspace.png') });
    await page.locator('#connection-open').click();
    await page.locator('#connection-dialog').waitFor();
    await page.locator('#settings-open').click();
    await page.locator('#connection-dialog').waitFor({ state: 'hidden' });
    await page.locator('#settings-dialog').waitFor();
    assert.equal(
      await page.locator('#settings-dialog input[type=url]').count(),
      0,
    );
    const chosenDirectory = path.join(data, 'custom-images');
    fs.mkdirSync(chosenDirectory);
    await application.evaluate(({ dialog }, destination) => {
      dialog.showOpenDialog = async () => ({
        canceled: false,
        filePaths: [destination],
      });
    }, chosenDirectory);
    await page.locator('#choose-directory').click();
    await page.waitForFunction(
      (destination) =>
        document.querySelector('#output-directory').value === destination,
      chosenDirectory,
    );
    await page.screenshot({ path: path.join(out, 'settings.png') });
    await page.locator('#api-key').fill('smoke-only-key-not-valid');
    await page.locator('#save-settings').click();
    await page.locator('#settings-dialog').waitFor({ state: 'hidden' });
    assert.match(await page.locator('#key-status').textContent(), /已配置/);
    const saved = fs.readFileSync(path.join(data, 'settings.json'), 'utf8');
    assert.equal(saved.includes('smoke-only-key-not-valid'), false);
    await application.evaluate((_electron, directory) => {
      const load = process
        .getBuiltinModule('node:module')
        .createRequire(`${directory}/package.json`);
      const provider = load(
        load('node:path').join(directory, 'dist', 'provider.js'),
      );
      const bytes = _electron.nativeImage
        .createFromPath(load('node:path').join(directory, 'assets', 'icon.png'))
        .resize({ width: 512 })
        .toPNG();
      provider.generateImage = async (_draft, _key, _model, signal) => {
        await new Promise((resolve, reject) => {
          const timer = setTimeout(resolve, 1000);
          signal.addEventListener(
            'abort',
            () => {
              clearTimeout(timer);
              reject(new Error('aborted'));
            },
            { once: true },
          );
        });
        return {
          images: [{ bytes, format: 'png' }],
          responseId: 'smoke-response',
        };
      };
    }, root);
    await page
      .locator('#prompt')
      .fill('集成测试画面（本地图标模拟响应，非真实生成）');
    await page.locator('#output-name').fill('角色A_正面');
    await page.locator('#generate').click();
    await page.locator('#records .status.succeeded').waitFor();
    await page
      .locator('.thumbnail-button img')
      .evaluate((image) => image.decode());
    await page.locator('#prompt').fill('保留当前草稿文字');
    await page
      .locator('#reference-files')
      .setInputFiles(path.join(root, 'assets', 'icon.png'));
    await page.locator('.reference-tile').waitFor();
    await page.locator('.output-actions button').first().click();
    assert.equal(
      await page.locator('#prompt').inputValue(),
      '保留当前草稿文字',
    );
    assert.equal(
      await page.locator('.reference-tile').count(),
      2,
      'existing reference is retained',
    );
    await page.locator('.output-actions button').first().click();
    assert.equal(
      await page.locator('.reference-tile').count(),
      2,
      'duplicate reference is not added',
    );
    await page.locator('.reference-remove').nth(1).click();
    assert.equal(
      await page.locator('#prompt').inputValue(),
      '保留当前草稿文字',
    );
    assert.equal(await page.locator('#mode-label').textContent(), '参考图编辑');
    await page.locator('.thumbnail-button').click();
    await page.locator('#full-image').evaluate((image) => image.decode());
    await page.locator('#image-reference').click();
    await page.locator('#image-dialog').waitFor({ state: 'hidden' });
    assert.equal(await page.locator('.reference-tile').count(), 2);
    await page.screenshot({ path: path.join(out, 'history-reference.png') });
    const recordId = await page.locator('.record').getAttribute('data-id');
    const imageFile = path.join(data, 'creations', recordId, 'output-0.png');
    assert.ok(fs.existsSync(imageFile));
    assert.deepEqual(
      fs.readFileSync(path.join(chosenDirectory, '角色A_正面.png')),
      fs.readFileSync(imageFile),
    );
    assert.match(
      await page.locator('.saved-filename').textContent(),
      /角色A_正面.png/,
    );
    const exported = path.join(data, 'exported.png');
    await application.evaluate(({ dialog }, destination) => {
      dialog.showSaveDialog = async () => ({
        canceled: false,
        filePath: destination,
      });
    }, exported);
    await page.locator('.output-actions button').nth(1).click();
    await page.waitForFunction(() =>
      document.querySelector('#toast').textContent.includes('原图已保存'),
    );
    assert.deepEqual(fs.readFileSync(exported), fs.readFileSync(imageFile));
    const originalSize = await page.evaluate(() => ({
      width: innerWidth,
      height: innerHeight,
    }));
    await application.evaluate(({ BrowserWindow }) =>
      BrowserWindow.getAllWindows()[0].setSize(840, 680),
    );
    await page.waitForFunction(() => innerWidth < 900);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    );
    assert.equal(
      overflow,
      false,
      'no horizontal overflow at small window size',
    );
    await page.screenshot({ path: path.join(out, 'small-window.png') });
    await application.evaluate(
      ({ BrowserWindow }, dimensions) =>
        BrowserWindow.getAllWindows()[0].setContentSize(
          dimensions.width,
          dimensions.height,
        ),
      originalSize,
    );
    await page.locator('#generate').click();
    await page.locator('.pending').waitFor();
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('.pending button').click();
    await page.locator('#records .status.stopped').waitFor();
    await page.evaluate(async () => {
      const result = await window.studio.state();
      if (
        !result.ok ||
        JSON.stringify(result).includes('smoke-only-key-not-valid')
      )
        throw new Error('secret returned to renderer');
    });
    await application.close();
    await launch();
    assert.equal(await page.locator('.record').count(), 2);
    assert.match(await page.locator('#key-status').textContent(), /已配置/);
    assert.equal(await page.locator('#output-name').inputValue(), '角色A_正面');
    assert.match(
      await page.locator('#save-path').textContent(),
      /custom-images/,
    );
    assert.equal(
      await page.locator('#prompt').inputValue(),
      '保留当前草稿文字',
    );
    await page
      .locator('.thumbnail-button img')
      .evaluate((image) => image.decode());
    assert.deepEqual(errors, []);
    await application.evaluate(({ BrowserWindow }) => {
      new BrowserWindow({
        show: false,
        webPreferences: {
          sandbox: true,
          nodeIntegration: false,
          contextIsolation: true,
        },
      });
    });
    const exited = new Promise((resolve) =>
      application.process().once('exit', resolve),
    );
    await application
      .evaluate(({ BrowserWindow }) => {
        BrowserWindow.getAllWindows()
          .find((window) => window.isVisible())
          .close();
      })
      .catch(() => {});
    let exitTimer;
    try {
      await Promise.race([
        exited,
        new Promise((_resolve, reject) => {
          exitTimer = setTimeout(
            () =>
              reject(
                new Error('Main close left hidden browser/process running'),
              ),
            10000,
          );
        }),
      ]);
    } finally {
      clearTimeout(exitTimer);
    }
    console.log(
      'PASS Electron: encrypted key, generation, persistence, reference deduplication, preserved draft, full image, save, cancellation, narrow layout, isolated renderer.',
    );
    console.log(`Screenshots: ${out}`);
  } finally {
    if (application) await application.close().catch(() => {});
    await new Promise((resolve) => cloud.close(resolve));
    if (
      path.dirname(data) === os.tmpdir() &&
      path.basename(data).startsWith('antv-studio-smoke-')
    )
      fs.rmSync(data, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
