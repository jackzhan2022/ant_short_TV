const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');

(async () => {
  const root = path.resolve(__dirname, '..');
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-ai-workflow-'));
  const screenshots = path.join(root, 'smoke-output');
  fs.mkdirSync(screenshots, { recursive: true });
  const names = [
    '汐月（日常装）',
    '沈澈',
    '潮汐海湾',
    '灯塔',
    '珍珠项链',
    '贝壳',
    '需人工处理的冲突资产',
  ];
  const assets = names.map((name, index) => ({
    id: index + 1,
    sourceAssetId: `source-${index}`,
    sourceParentId: null,
    name,
    type: index < 2 ? 'CHARACTER' : index < 4 ? 'SCENE' : 'PROP',
    active: true,
    matchStatus: [1, 3].includes(index)
      ? 'MATCHED'
      : index === 6
        ? 'CONFLICT'
        : 'UNMATCHED',
    targetAssetId: [1, 3].includes(index) ? index + 100 : null,
    targetVariantId: null,
    targetName: [1, 3].includes(index) ? name : null,
    hasPrompt: [1, 3].includes(index),
  }));
  const candidates = [
    {
      assetId: 101,
      variantId: null,
      name: '汐月·常服',
      type: 'CHARACTER',
      parentName: null,
      hasPrompt: true,
    },
    {
      assetId: 103,
      variantId: null,
      name: '潮汐湾',
      type: 'SCENE',
      parentName: null,
      hasPrompt: true,
    },
    {
      assetId: 105,
      variantId: null,
      name: '珍珠项链（白色）',
      type: 'PROP',
      parentName: null,
      hasPrompt: true,
    },
    {
      assetId: 106,
      variantId: null,
      name: '珍珠项链（金色）',
      type: 'PROP',
      parentName: null,
      hasPrompt: true,
    },
  ];
  const project = {
    id: 1,
    name: '人鱼故事',
    sourceTeamId: 'source-team',
    sourceProjectId: 'source-project',
    sourceScriptId: 'main-script',
    targetProjectId: 100,
    targetProjectName: '人鱼故事',
    active: true,
    syncedAt: '2026-11-06T09:00:00',
    connectionId: 'test-connection',
  };
  const calls = [],
    textCalls = [],
    writes = [];
  let releaseModel;
  const modelReady = new Promise((resolve) => {
    releaseModel = resolve;
  });
  const server = http.createServer(async (req, res) => {
    let text = '';
    for await (const chunk of req) text += chunk;
    const body = text ? JSON.parse(text) : undefined;
    calls.push(req.url);
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Set-Cookie', 'XSRF-TOKEN=test-csrf; Path=/');
    if (req.url === '/v1/chat/completions') {
      assert.equal(req.headers.authorization, 'Bearer ai-only-test-key');
      assert.equal(body.model, 'local-text-test');
      textCalls.push(body);
      await modelReady;
      const payload = JSON.parse(body.messages.at(-1).content);
      assert.ok(payload.sources.every((s) => ![2, 4, 7].includes(s.id)));
      assert.equal(
        JSON.stringify(payload).includes('existing-secret-prompt'),
        false,
      );
      const matches = payload.sources.map((s) => ({
        sourceId: s.id,
        candidateIds:
          s.id === 1
            ? ['101:0']
            : s.id === 3
              ? ['103:0']
              : s.id === 5
                ? ['105:0', '106:0']
                : [],
        reason: s.id === 5 ? '款式无法仅凭名称确定' : '名称语义相近，请核对',
      }));
      res.end(
        JSON.stringify({
          choices: [
            {
              finish_reason: 'stop',
              message: { content: JSON.stringify({ matches }) },
            },
          ],
        }),
      );
      return;
    }
    let value;
    if (req.url === '/api/tenants/my')
      value = [{ id: 11, name: '测试团队', status: 'ACTIVE' }];
    else if (req.url.startsWith('/api/projects?'))
      value = {
        data: [
          { id: 100, name: '人鱼故事' },
          { id: 200, name: '人鱼故事' },
        ],
        total: 2,
      };
    else if (req.url === '/api/desktop/source-projects')
      value = [
        project,
        {
          ...project,
          id: 2,
          sourceProjectId: 'other-project',
          name: '第二个项目',
        },
      ];
    else if (/\/source-projects\/[12]\/assets$/.test(req.url))
      value = req.url.includes('/2/')
        ? [{ ...assets[0], id: 20, name: '其他项目角色' }]
        : assets;
    else if (req.url.includes('/candidates?'))
      value = candidates.filter(
        (c) =>
          c.type ===
          new URL(req.url, 'http://localhost').searchParams.get('type'),
      );
    else if (/\/assets\/\d+\/ai-match$/.test(req.url)) {
      const asset = assets.find(
        (a) => a.id === Number(req.url.match(/assets\/(\d+)/)[1]),
      );
      assert.equal(body.expected.sourceName, asset.name);
      assert.equal(body.expected.targetProjectId, 100);
      assert.equal(body.expected.sourceMatchStatus, asset.matchStatus);
      const candidate = candidates.find((c) => c.assetId === body.assetId);
      assert.ok(candidate && candidate.type === asset.type);
      writes.push(body);
      Object.assign(asset, {
        targetAssetId: candidate.assetId,
        targetName: candidate.name,
        matchStatus: 'MATCHED',
        hasPrompt: true,
      });
      value = asset;
    } else if (req.url === '/api/desktop/source-projects/prompts')
      value = body.assetIds.map((id) => ({
        sourceAssetId: id,
        name: assets[id - 1].name,
        type: assets[id - 1].type,
        sourceProjectId: 1,
        sourceProjectName: '人鱼故事',
        sourceParentName: id === 1 ? '主体名称不应进入文件名' : null,
        targetProjectId: 100,
        targetAssetId: assets[id - 1].targetAssetId,
        targetVariantId: null,
        projectName: '人鱼故事',
        prompt: `existing-secret-prompt-${id}`,
      }));
    else {
      res.statusCode = 404;
      res.end('{}');
      return;
    }
    res.end(JSON.stringify({ success: true, data: value }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = `http://127.0.0.1:${server.address().port}`;
  fs.writeFileSync(
    path.join(data, 'workspace.json'),
    JSON.stringify({
      cloudUrl: address,
      autoSyncMinutes: 0,
      connectionId: 'local-only',
      tenantId: 11,
    }),
  );
  const env = { ...process.env, ANTV_STUDIO_TEST_DATA: data };
  delete env.ELECTRON_RUN_AS_NODE;
  let app;
  try {
    app = await electron.launch({
      executablePath:
        process.env.ANTV_ELECTRON_EXECUTABLE || require('electron'),
      args: [root],
      env,
    });
    const page = await app.firstWindow();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('#asset-rows input:not([disabled])').first().waitFor();
    assert.equal(await page.evaluate(() => typeof window.require), 'undefined');
    await app.evaluate(({ nativeImage }, directory) => {
      const load = process
        .getBuiltinModule('node:module')
        .createRequire(`${directory}/package.json`);
      const provider = load(`${directory}/dist/provider.js`);
      const bytes = nativeImage
        .createFromPath(`${directory}/assets/icon.png`)
        .toPNG();
      globalThis.aiSmoke = { active: 0, peak: 0, prompts: [], finish: [] };
      provider.generateImage = (draft) =>
        new Promise((resolve) => {
          const p = globalThis.aiSmoke;
          p.active++;
          p.peak = Math.max(p.peak, p.active);
          p.prompts.push(draft.prompt);
          p.finish.push(() => {
            p.active--;
            resolve({
              images: [{ bytes, format: 'png' }],
              responseId: 'local-mock-image',
            });
          });
        });
    }, root);
    await page.locator('#connection-open').click();
    await page.locator('#settings-open').click();
    await page.locator('#api-key').fill('image-test-key');
    await page.locator('#save-settings').click();
    await page.locator('#settings-dialog').waitFor({ state: 'hidden' });
    await page.locator('#connection-open').click();
    await page.locator('#settings-open').click();
    await page.locator('#ai-settings-details summary').click();
    await page.locator('#ai-settings-save:not([disabled])').waitFor();
    await page.locator('#ai-base-url').fill(`${address}/v1`);
    await page.locator('#ai-model').fill('local-text-test');
    await page.locator('#ai-key').fill('ai-only-test-key');
    await page.locator('#ai-settings-save').click();
    await page.waitForFunction(
      () => document.querySelector('#ai-key').value === '',
    );
    assert.equal(
      fs
        .readFileSync(path.join(data, 'text-model.json'), 'utf8')
        .includes('ai-only-test-key'),
      false,
    );
    await page.locator('#settings-close').click();
    await page.locator('#source-project-open').click();
    await page.locator('[data-source-project-id="2"]').click();
    assert.match(
      await page.locator('#asset-project-name').innerText(),
      /人鱼故事/,
    );
    await page.locator('[data-close="source-project-dialog"]').last().click();
    await page.locator('#ai-match-open').click();
    await page.locator('#ai-method').selectOption('llm');
    await page.locator('#ai-start').click();
    await page.waitForFunction(() =>
      document.querySelector('#ai-progress').textContent.includes('已完成 0 /'),
    );
    assert.equal(await page.locator('#ai-progress').isVisible(), true);
    releaseModel();
    await page.locator('#ai-apply:not([disabled])').waitFor();
    assert.equal(textCalls.length, 1);
    assert.equal(writes.length, 0);
    assert.equal(await page.locator('#ai-results input:checked').count(), 2);
    await page.screenshot({ path: path.join(screenshots, 'ai-review.png') });
    await page.getByLabel('珍珠项链的对应资产').selectOption('106:base');
    await page.locator('#ai-apply').click();
    await page.locator('#ai-match-dialog').waitFor({ state: 'hidden' });
    assert.equal(writes.length, 3);
    assert.equal(
      await page.locator('#asset-rows input:not([disabled])').count(),
      5,
    );
    await page.screenshot({
      path: path.join(screenshots, 'clean-workspace.png'),
    });
    await page.locator('#select-assets').check();
    await page.locator('#batch-open').click();
    await page.locator('#batch-submit').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .running').length === 5,
    );
    await page.locator('[data-view="assets"]').click();
    await page.locator('#select-assets').check();
    await page.locator('#batch-open').click();
    await page.locator('#batch-submit').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .queued').length === 5,
    );
    assert.equal(await app.evaluate(() => globalThis.aiSmoke.peak), 5);
    assert.ok(
      (await app.evaluate(() => globalThis.aiSmoke.prompts)).every((p) =>
        p.startsWith('existing-secret-prompt-'),
      ),
    );
    await page.screenshot({ path: path.join(screenshots, 'clean-queue.png') });
    await page.locator('#queue-pause').click();
    await app.evaluate(() =>
      globalThis.aiSmoke.finish.slice(0, 5).forEach((f) => {
        f();
      }),
    );
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .succeeded').length === 5,
    );
    const records = () =>
      fs
        .readdirSync(path.join(data, 'creations'))
        .map((id) =>
          JSON.parse(
            fs.readFileSync(
              path.join(data, 'creations', id, 'record.json'),
              'utf8',
            ),
          ),
        );
    for (const record of records().filter((r) => r.status === 'succeeded')) {
      assert.equal(
        path.basename(record.savedFiles[0]),
        `${record.origin.assetName}.png`,
      );
      assert.equal(fs.existsSync(record.savedFiles[0]), true);
      assert.match(record.savedFiles[0], /导入批次-/);
    }
    await page.locator('[data-queue-filter="done"]').click();
    assert.equal(await page.locator('.queue-row').count(), 5);
    await page.locator('[data-queue-filter="all"]').click();
    await page.locator('#queue-resume').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .running').length === 5,
    );
    await app.evaluate(() =>
      globalThis.aiSmoke.finish.slice(5).forEach((f) => {
        f();
      }),
    );
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .succeeded').length === 10,
    );
    const outputs = records().filter(
      (r) => r.status === 'succeeded' && r.origin.assetName === names[0],
    );
    assert.equal(outputs.length, 2);
    assert.notEqual(
      path.dirname(outputs[0].savedFiles[0]),
      path.dirname(outputs[1].savedFiles[0]),
    );
    assert.equal(path.basename(outputs[0].savedFiles[0]), `${names[0]}.png`);
    assert.deepEqual(errors, []);
    console.log(
      'AI workflow PASS: actual HTTP text request, encrypted local key, scoped human review, exact Dramart names, distinct batch folders, 5 global image slots, queue filters, no paid calls.',
    );
  } finally {
    await app?.close();
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(data, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
