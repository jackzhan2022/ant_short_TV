const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
if (!process.versions.electron) {
  const { spawnSync } = require('node:child_process');
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-source-smoke-'));
  const env = { ...process.env, ANTV_SOURCE_SMOKE_DATA: data };
  delete env.ELECTRON_RUN_AS_NODE;
  try {
    const run = spawnSync(
      process.env.ANTV_ELECTRON_EXECUTABLE || require('electron'),
      [__filename, ...process.argv.slice(2)],
      { env, timeout: 55000, encoding: 'utf8' },
    );
    const report = path.join(data, 'report.json');
    const result = fs.existsSync(report)
      ? JSON.parse(fs.readFileSync(report, 'utf8'))
      : { error: run.error?.message || 'Browser exited without a result' };
    console.log(JSON.stringify(result));
    assert.equal(run.status, 0, result.error);
    assert.equal(result.ok, true);
  } finally {
    fs.rmSync(data, { recursive: true, force: true });
  }
} else {
  const { app, BrowserWindow } = require('electron');
  const data = process.env.ANTV_SOURCE_SMOKE_DATA;
  app.setPath('userData', data);
  const compiledRoot = process.argv.includes('--packaged')
    ? path.join(root, 'release-antv/win-unpacked/resources/app.asar/dist')
    : path.join(root, 'dist');
  const { DramartCollector } = require(path.join(compiledRoot, 'dramart.js'));
  let collector;
  const finish = (result) => {
    fs.writeFileSync(path.join(data, 'report.json'), JSON.stringify(result));
    collector?.close();
    app.exit(result.ok ? 0 : 1);
  };
  const deadline = setTimeout(
    () =>
      finish({ ok: false, error: 'Source login form did not render in 45s' }),
    45000,
  );
  app.whenReady().then(async () => {
    try {
      collector = new DramartCollector(() => {});
      await collector.login();
      const [window] = BrowserWindow.getAllWindows();
      let document;
      while (true) {
        document = await window.webContents.executeJavaScript(`({
          title: document.title,
          characters: document.body.innerText.length,
          inputs: [...document.querySelectorAll('input')].filter(input =>
            input.getBoundingClientRect().width > 0).length
        })`);
        if (document.inputs > 0 && document.characters > 50) break;
        await new Promise((resolve) => setTimeout(resolve, 250));
      }
      const url = new URL(window.webContents.getURL());
      assert.equal(url.protocol, 'https:');
      assert.ok(url.hostname.endsWith('.volcengine.com'));
      assert.match(document.title, /登录/);
      assert.equal(window.webContents.debugger.isAttached(), true);
      await new Promise((resolve) => setTimeout(resolve, 1500));
      const output = path.join(root, 'smoke-output');
      fs.mkdirSync(output, { recursive: true });
      const screenshot = await window.webContents.capturePage();
      const pixels = screenshot.toBitmap();
      const colors = new Set();
      for (let offset = 0; offset < pixels.length; offset += 64)
        colors.add(pixels.readUInt32LE(offset));
      assert.ok(colors.size > 20, 'Login window must paint visible content');
      fs.writeFileSync(
        path.join(output, 'source-login.png'),
        screenshot.toPNG(),
      );
      window.close();
      assert.equal(window.isDestroyed(), false);
      assert.equal(window.isVisible(), false);
      await collector.login();
      assert.equal(window.isVisible(), true);
      assert.equal(BrowserWindow.getAllWindows().length, 1);
      clearTimeout(deadline);
      finish({ ok: true, host: url.hostname, ...document });
    } catch (error) {
      finish({
        ok: false,
        error: error.message.replace(/https?:\/\/\S+/g, '[URL]'),
      });
    }
  });
}
