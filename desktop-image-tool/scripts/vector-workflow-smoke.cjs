const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const { LocalModelFiles } = require('../dist/vector-model');

(async () => {
  const root = path.resolve(__dirname, '..');
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-vector-workflow-'));
  const modelIndex = process.argv.indexOf('--models');
  const modelSource =
    modelIndex >= 0
      ? path.resolve(process.argv[modelIndex + 1])
      : path.resolve(root, '../.release/bge-model');
  const installedIndex = process.argv.indexOf('--packaged');
  const packaged =
    installedIndex >= 0
      ? path.resolve(process.argv[installedIndex + 1])
      : undefined;
  const models = new LocalModelFiles(data);
  fs.cpSync(modelSource, models.directory, { recursive: true });
  await models.ensure(new AbortController().signal);
  const base = {
    sourceParentId: null,
    active: true,
    matchStatus: 'UNMATCHED',
    targetAssetId: null,
    targetVariantId: null,
    targetName: null,
    hasPrompt: false,
  };
  const assets = [
    {
      ...base,
      id: 1,
      sourceAssetId: 'role-1',
      name: '汐月（日常装）',
      type: 'CHARACTER',
    },
    {
      ...base,
      id: 2,
      sourceAssetId: 'role-2',
      name: '沈澈',
      type: 'CHARACTER',
      matchStatus: 'MATCHED',
      targetAssetId: 102,
      targetName: '沈澈',
      hasPrompt: true,
    },
    {
      ...base,
      id: 3,
      sourceAssetId: 'scene-1',
      name: '潮汐海湾',
      type: 'SCENE',
    },
    { ...base, id: 4, sourceAssetId: 'prop-1', name: '银色发簪', type: 'PROP' },
    {
      ...base,
      id: 5,
      sourceAssetId: 'prop-2',
      name: '珍珠项链',
      type: 'PROP',
      matchStatus: 'CONFLICT',
    },
    {
      ...base,
      id: 6,
      sourceAssetId: 'role-1-variant',
      sourceParentId: 'role-1',
      name: '日常装',
      type: 'CHARACTER',
    },
  ];
  const candidates = [
    { assetId: 101, name: '汐月·常服', type: 'CHARACTER' },
    { assetId: 102, name: '沈澈·盔甲', type: 'CHARACTER' },
    { assetId: 103, name: '海边的潮汐湾', type: 'SCENE' },
    { assetId: 104, name: '银质发簪', type: 'PROP' },
  ].map((c) => ({ ...c, hasPrompt: true, variantId: null, parentName: null }));
  const project = {
    id: 1,
    name: '人鱼故事',
    active: true,
    sourceTeamId: 'source-team',
    sourceProjectId: 'source-project',
    sourceScriptId: 'current-script',
    targetProjectId: 100,
    targetProjectName: '人鱼故事',
    syncedAt: '2026-11-06T09:00:00',
    connectionId: 'test-connection',
  };
  const calls = [];
  const writes = [];
  const server = http.createServer(async (req, res) => {
    let text = '';
    for await (const chunk of req) text += chunk;
    const body = text ? JSON.parse(text) : undefined;
    calls.push(req.url);
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Set-Cookie', 'XSRF-TOKEN=test-csrf; Path=/');
    let value;
    if (req.url === '/api/tenants/my')
      value = [{ id: 11, name: '测试团队', status: 'ACTIVE' }];
    else if (req.url.startsWith('/api/projects?'))
      value = { data: [{ id: 100, name: '人鱼故事' }], total: 1 };
    else if (req.url === '/api/desktop/source-projects')
      value = [
        project,
        {
          ...project,
          id: 2,
          sourceProjectId: 'other-project',
          name: '其他项目',
        },
      ];
    else if (req.url === '/api/desktop/source-projects/1/assets')
      value = assets;
    else if (req.url.includes('/candidates?')) {
      assert.ok(req.url.startsWith('/api/desktop/source-projects/1/'));
      const query = new URL(req.url, 'http://localhost').searchParams;
      value = query.has('sourceParentId')
        ? []
        : candidates.filter((c) => c.type === query.get('type'));
    } else if (/\/assets\/\d+\/ai-match$/.test(req.url)) {
      const asset = assets.find(
        (a) => a.id === Number(req.url.match(/assets\/(\d+)/)[1]),
      );
      assert.equal(asset.matchStatus, 'UNMATCHED');
      assert.equal(body.expected.sourceName, asset.name);
      assert.equal(body.expected.targetProjectId, 100);
      const candidate = candidates.find((c) => c.assetId === body.assetId);
      assert.equal(candidate.type, asset.type);
      writes.push(body);
      Object.assign(asset, {
        targetAssetId: candidate.assetId,
        targetName: candidate.name,
        matchStatus: 'MATCHED',
        hasPrompt: true,
      });
      value = asset;
    } else {
      res.statusCode = 404;
      res.end('{}');
      return;
    }
    res.end(JSON.stringify({ success: true, data: value }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  fs.writeFileSync(
    path.join(data, 'workspace.json'),
    JSON.stringify({
      cloudUrl: `http://127.0.0.1:${server.address().port}`,
      connectionId: 'test-connection',
      tenantId: 11,
    }),
  );
  const env = { ...process.env, ANTV_STUDIO_TEST_DATA: data };
  delete env.ELECTRON_RUN_AS_NODE;
  let application;
  try {
    application = await electron.launch({
      executablePath: packaged
        ? path.join(packaged, 'ANTVImageStudio.exe')
        : process.env.ANTV_ELECTRON_EXECUTABLE || require('electron'),
      args: packaged ? [`--user-data-dir=${data}`] : [root],
      env,
    });
    const page = await application.firstWindow();
    const errors = [];
    page.on('pageerror', (failure) => errors.push(failure.message));
    await page
      .locator('#asset-rows')
      .getByText('汐月（日常装）', { exact: true })
      .waitFor();
    const initial = await page.evaluate(() => window.studio.state());
    assert.equal(initial.value.settings.hasKey, false);
    await application.evaluate(({ ipcMain }) => {
      globalThis.vectorBatches = 0;
      ipcMain.on('vector:result', () => {
        globalThis.vectorBatches++;
      });
    });
    const cancelled = await page.evaluate(async () => {
      const unsubscribe = window.workspace.onAiProgress((p) => {
        if (p.detail?.includes('正在加载本地中文模型'))
          void window.workspace.aiCancel();
      });
      try {
        return await window.workspace.aiMatch(1, 'vector');
      } finally {
        unsubscribe();
      }
    });
    assert.equal(cancelled.ok, false);
    assert.match(cancelled.error, /取消/);
    await page.locator('#ai-match-open').click();
    assert.equal(await page.locator('#ai-method').inputValue(), 'vector');
    assert.equal(await page.locator('#ai-model-settings').isVisible(), false);
    const start = Date.now();
    await page.locator('#ai-start').click();
    await page.locator('[data-ai-source-id="1"] select').waitFor();
    const coldMs = Date.now() - start;
    assert.equal(await page.locator('#ai-results input:checked').count(), 0);
    assert.equal(await page.locator('[data-ai-source-id="2"]').count(), 0);
    assert.equal(await page.locator('[data-ai-source-id="5"]').count(), 0);
    assert.equal(
      await page.locator('[data-ai-source-id="6"] select').count(),
      0,
    );
    const option = await page
      .locator('[data-ai-source-id="1"] option')
      .nth(1)
      .innerText();
    assert.match(option, /汐月·常服.*相似度/);
    assert.equal(writes.length, 0);
    const batches = await application.evaluate(() => globalThis.vectorBatches);
    const screenshotRoot = path.join(root, 'smoke-output');
    fs.mkdirSync(screenshotRoot, { recursive: true });
    await page.screenshot({
      path: path.join(screenshotRoot, 'local-vector-review.png'),
    });
    await page.locator('[data-close="ai-match-dialog"]').last().click();
    await page.locator('#ai-match-open').click();
    const warmStart = Date.now();
    await page.locator('#ai-start').click();
    await page.locator('[data-ai-source-id="1"] select').waitFor();
    const warmMs = Date.now() - warmStart;
    assert.equal(
      await application.evaluate(() => globalThis.vectorBatches),
      batches,
    );
    for (const [sourceId, assetId] of [
      [1, 101],
      [3, 103],
      [4, 104],
    ])
      await page
        .locator(`[data-ai-source-id="${sourceId}"] select`)
        .selectOption(`${assetId}:base`);
    await page.locator('#ai-apply').click();
    await page.locator('#ai-match-dialog').waitFor({ state: 'hidden' });
    assert.equal(writes.length, 3);
    assert.equal(assets[1].targetAssetId, 102);
    assert.equal(assets[4].matchStatus, 'CONFLICT');
    assert.ok(
      calls.every(
        (url) =>
          !url.includes('chat/completions') && !url.includes('embeddings'),
      ),
    );
    assert.equal(
      (await page.evaluate(() => window.studio.state())).value.settings.hasKey,
      false,
    );
    const subsequent = await page.evaluate(() =>
      window.workspace.aiMatch(1, 'vector'),
    );
    assert.equal(subsequent.ok, true);
    assert.deepEqual(
      subsequent.value.suggestions.map((s) => s.sourceAssetId),
      [6],
    );
    assert.deepEqual(errors, []);
    console.log(
      JSON.stringify({
        result: 'PASS local vector workflow',
        packaged: Boolean(packaged),
        coldMs,
        warmMs,
        saved: writes.length,
        apiKeyConfigured: false,
        paidModelCalls: 0,
        modelBatches: batches,
        cancellation: true,
        cacheReused: true,
      }),
    );
  } catch (error) {
    const page = application?.windows()[0];
    if (page && !page.isClosed()) {
      console.error(
        await page.evaluate(() => ({
          dialogs: [...document.querySelectorAll('dialog')].map((dialog) => ({
            id: dialog.id,
            open: dialog.open,
          })),
          error: document.querySelector('#ai-error')?.textContent,
          progress: document.querySelector('#ai-progress')?.textContent,
          results: document.querySelector('#ai-results')?.textContent,
        })),
      );
      await page.screenshot({
        path: path.join(root, 'smoke-output', 'local-vector-failure.png'),
      });
    }
    throw error;
  } finally {
    await application?.close();
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(data, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
