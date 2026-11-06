const { _electron: electron } = require('playwright-core');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

(async () => {
  const root = path.resolve(__dirname, '..');
  const data = fs.mkdtempSync(path.join(os.tmpdir(), 'antv-workflow-'));
  const screenshots = path.join(root, 'smoke-output');
  fs.mkdirSync(screenshots, { recursive: true });
  let members = [
    { id: 11, name: '测试团队 A', status: 'ACTIVE' },
    { id: 22, name: '测试团队 B', status: 'ACTIVE' },
  ];
  let bound = false;
  let matched = false;
  let holdMatch = false;
  let releaseMatch;
  let matchStarted;
  const calls = [];
  const syncs = [];
  const syncedAt = new Date().toISOString();
  const project = () => ({
    id: 1,
    connectionId: 'fixture',
    name: '测试来源项目',
    sourceTeamId: 'source-team',
    sourceProjectId: 'source-project',
    sourceScriptId: 'source-script',
    targetProjectId: bound ? 100 : null,
    targetProjectName: bound ? '测试远程项目' : null,
    active: true,
    syncedAt,
  });
  const assets = () =>
    Array.from({ length: 12 }, (_, index) => ({
      id: index + 1,
      sourceAssetId: `c${index}`,
      name: `测试角色${index + 1}`,
      type: 'CHARACTER',
      active: true,
      targetAssetId: matched ? index + 100 : null,
      targetVariantId: null,
      targetName: matched ? `测试角色${index + 1}` : null,
      matchStatus: matched ? 'MATCHED' : 'UNBOUND',
      hasPrompt: matched,
    }));
  const server = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const input = Buffer.concat(chunks).toString();
    const body = input ? JSON.parse(input) : null;
    calls.push({
      url: req.url,
      tenant: req.headers['x-tenant-id'],
      method: req.method,
      body,
      csrf: req.headers['x-xsrf-token'],
    });
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Set-Cookie', 'XSRF-TOKEN=local-csrf; Path=/');
    if (
      req.url !== '/api/tenants/my' &&
      !members.some((team) => String(team.id) === req.headers['x-tenant-id'])
    ) {
      res.statusCode = 403;
      res.end('{}');
      return;
    }
    if (req.method !== 'GET' && req.headers['x-xsrf-token'] !== 'local-csrf') {
      res.statusCode = 403;
      res.end('{}');
      return;
    }
    let result;
    if (req.url === '/api/tenants/my') result = members;
    else if (req.url.startsWith('/api/projects?'))
      result = { data: [{ id: 100, name: '测试远程项目' }], total: 1 };
    else if (req.url === '/api/desktop/source-projects')
      result = [
        project(),
        {
          ...project(),
          id: 2,
          name: '测试项目 B',
          sourceScriptId: 'source-script-b',
        },
      ];
    else if (req.url === '/api/desktop/source-projects/sync') {
      syncs.push(body);
      result = project();
    } else if (req.url === '/api/desktop/source-projects/1/assets')
      result = assets();
    else if (req.url === '/api/desktop/source-projects/2/assets')
      result = [{ ...assets()[0], id: 101, name: '项目 B 专属角色' }];
    else if (req.url === '/api/desktop/source-projects/1/binding') {
      bound = true;
      result = project();
    } else if (req.url === '/api/desktop/source-projects/1/match') {
      if (holdMatch) {
        await new Promise((resolve) => {
          releaseMatch = resolve;
          matchStarted();
        });
        holdMatch = false;
      }
      matched = true;
      result = assets();
    } else if (req.url === '/api/desktop/source-projects/prompts')
      result = body.assetIds.map((id) => ({
        sourceProjectId: 1,
        sourceProjectName: '测试来源项目',
        sourceParentName: id === 1 ? '测试主体' : null,
        sourceAssetId: id,
        name: `测试角色${id}`,
        type: 'CHARACTER',
        projectName: '测试远程项目',
        targetProjectId: 100,
        targetAssetId: id + 100,
        targetVariantId: null,
        prompt: `existing-cloud-prompt-${id}`,
      }));
    else {
      res.statusCode = 404;
      res.end('{}');
      return;
    }
    res.end(JSON.stringify({ success: true, data: result }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = `http://127.0.0.1:${server.address().port}`;
  fs.writeFileSync(
    path.join(data, 'workspace.json'),
    JSON.stringify({
      cloudUrl: address,
      autoSyncMinutes: 0,
      connectionId: 'workflow-fixture',
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
    const page = await application.firstWindow();
    page.on('pageerror', (failure) => errors.push(failure.message));
    await page
      .locator('#generate:not([disabled])')
      .waitFor({ state: 'attached' });
    return page;
  };
  try {
    page = await launch();
    await application.evaluate(({ nativeImage }, directory) => {
      const load = process
        .getBuiltinModule('node:module')
        .createRequire(`${directory}/package.json`);
      const provider = load(`${directory}/dist/provider.js`);
      const source = load(`${directory}/dist/dramart.js`);
      source.DramartCollector.prototype.collect = async () => [
        {
          sourceTeamId: 'source-team',
          sourceProjectId: 'source-project',
          sourceScriptId: 'source-script',
          name: '测试来源项目',
          scriptText: 'MUST_NOT_UPLOAD',
          assets: [
            {
              sourceAssetId: 'c1',
              name: '测试角色',
              type: 'CHARACTER',
              description: 'MUST_NOT_UPLOAD',
              prompt: 'MUST_NOT_UPLOAD',
            },
          ],
        },
      ];
      const image = nativeImage
        .createFromPath(`${directory}/assets/icon.png`)
        .toPNG();
      globalThis.workflowProbe = { active: 0, peak: 0, starts: [], finish: [] };
      provider.generateImage = (draft, _key, _model, signal) =>
        new Promise((resolve, reject) => {
          const probe = globalThis.workflowProbe;
          probe.active++;
          probe.peak = Math.max(probe.peak, probe.active);
          probe.starts.push(draft.prompt);
          let done = false;
          const settle = (cancelled) => {
            if (done) return;
            done = true;
            probe.active--;
            if (cancelled) reject(new Error('cancelled'));
            else
              resolve({
                images: [{ bytes: image, format: 'png' }],
                responseId: 'local-only',
              });
          };
          probe.finish.push(() => settle(false));
          signal.addEventListener('abort', () => settle(true), { once: true });
        });
    }, root);
    await page.locator('#connection-open').click();
    await page.locator('#settings-open').click();
    await page.locator('#connection-dialog').waitFor({ state: 'hidden' });
    await page.locator('#api-key').fill('workflow-test-key');
    await page.locator('#save-settings').click();
    await page.locator('#settings-dialog').waitFor({ state: 'hidden' });
    await page.locator('#connection-open').click();
    assert.equal(await page.locator('#cloud-url').textContent(), address);
    assert.equal(await page.locator('input#cloud-url').count(), 0);
    await page.locator('#connection-form button[type=submit]').click();
    await page.waitForFunction(
      () => document.querySelector('#team-select').options.length === 3,
    );
    assert.equal(await page.locator('#team-select').inputValue(), '');
    assert.equal(
      calls.some((call) => call.url === '/api/desktop/source-projects'),
      false,
      'no tenant data before explicit choice',
    );
    await page.locator('#team-select').selectOption('11');
    await page.waitForFunction(() =>
      document.querySelector('#connection-open').title.includes('测试团队 A'),
    );
    await page.locator('#sync-source').click();
    await page.waitForFunction(() =>
      document.querySelector('#toast').textContent.includes('已同步'),
    );
    assert.equal(syncs.length, 1);
    assert.match(
      await page.locator('#source-status').textContent(),
      /同步完成：1 个项目 · 1 份资产清单/,
    );
    assert.match(
      await page.locator('#project-count').textContent(),
      /已同步 1 个项目 · 2 份剧本清单/,
    );
    assert.equal(JSON.stringify(syncs).includes('MUST_NOT_UPLOAD'), false);
    assert.deepEqual(Object.keys(syncs[0].assets[0]).sort(), [
      'name',
      'sourceAssetId',
      'type',
    ]);
    await page.locator('[data-close="connection-dialog"]').click();
    await page.locator('#source-project-open').click();
    await page.locator('[data-source-project-id="2"]').click();
    await page.locator('[data-close="source-project-dialog"]').first().click();
    assert.equal(
      await page.locator('#asset-project-name').textContent(),
      '测试来源项目',
      'cancelled source draft preserves the current scope',
    );
    await page.locator('#source-project-open').click();
    assert.equal(
      await page
        .locator('[data-source-project-id="1"]')
        .getAttribute('aria-pressed'),
      'true',
    );
    await page.locator('#source-project-confirm').click();
    await page.locator('#bind-project').click();
    await page.locator('[data-target-id="100"]').click();
    await page.locator('#binding-submit').click();
    await page.locator('#binding-dialog').waitFor({ state: 'hidden' });
    await page.locator('#match-assets:not([disabled])').waitFor();
    holdMatch = true;
    const requested = new Promise((resolve) => {
      matchStarted = resolve;
    });
    await page.locator('#match-assets').click();
    await requested;
    await page.locator('#source-project-open').click();
    await page.locator('[data-source-project-id="2"]').click();
    await page.locator('#source-project-confirm').click();
    await page.waitForFunction(() =>
      document
        .querySelector('#asset-rows')
        .textContent.includes('项目 B 专属角色'),
    );
    releaseMatch();
    await page.waitForFunction(() =>
      document.querySelector('#toast').textContent.includes('选择已改变'),
    );
    assert.match(
      await page.locator('#asset-rows').textContent(),
      /项目 B 专属角色/,
    );
    await page.locator('#source-project-open').click();
    await page.locator('[data-source-project-id="1"]').click();
    await page.locator('#source-project-confirm').click();
    await page.locator('#asset-rows .matched').first().waitFor();
    await page.locator('#match-assets').click();
    await page.locator('#asset-rows .matched').first().waitFor();
    await page.locator('#asset-rows button').first().click();
    await page.locator('#prompt-dialog').waitFor();
    assert.match(
      await page.locator('#asset-prompt').textContent(),
      /existing-cloud-prompt-1/,
    );
    await page.locator('[data-close="prompt-dialog"]').click();
    await page.screenshot({
      path: path.join(screenshots, 'asset-workflow.png'),
    });
    await page.locator('#select-assets').check();
    await page.locator('#batch-open').click();
    await page.locator('#batch-submit').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .running').length === 5,
    );
    assert.equal(await page.locator('.queue-row .queued').count(), 7);
    await page.locator('[data-queue-filter="running"]').click();
    assert.equal(await page.locator('.queue-row').count(), 5);
    assert.equal(await page.locator('.queue-row .running').count(), 5);
    await page.locator('[data-queue-filter="waiting"]').click();
    assert.equal(await page.locator('.queue-row').count(), 7);
    await page.locator('[data-queue-filter="done"]').click();
    assert.equal(await page.locator('.queue-row').count(), 0);
    assert.equal(await page.locator('#queue-empty').isVisible(), true);
    await page.locator('[data-queue-filter="all"]').click();
    assert.equal(await page.locator('.queue-row').count(), 12);
    const probe = await application.evaluate(() => ({
      active: globalThis.workflowProbe.active,
      peak: globalThis.workflowProbe.peak,
      starts: globalThis.workflowProbe.starts,
    }));
    assert.equal(probe.peak, 5);
    assert.ok(
      probe.starts.every((prompt) =>
        prompt.startsWith('existing-cloud-prompt-'),
      ),
    );
    await page.locator('[data-view="create"]').click();
    await page.locator('#prompt').fill('free-creation-same-queue');
    await page.locator('#generate').click();
    await page.locator('[data-view="queue"]').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .queued').length === 8,
    );
    assert.equal(
      await application.evaluate(() => globalThis.workflowProbe.starts.length),
      5,
    );
    await page.locator('#queue-pause').click();
    await application.evaluate(() => {
      for (const settle of globalThis.workflowProbe.finish.slice(0, 5))
        settle();
    });
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .succeeded').length === 5,
    );
    assert.equal(
      await application.evaluate(() => globalThis.workflowProbe.starts.length),
      5,
      'paused queue does not replace finished tasks',
    );
    const sample = fs
      .readdirSync(path.join(data, 'creations'))
      .map((id) =>
        JSON.parse(
          fs.readFileSync(
            path.join(data, 'creations', id, 'record.json'),
            'utf8',
          ),
        ),
      )
      .find((record) => record.status === 'succeeded');
    assert.ok(sample.outputDirectory.includes('测试团队 A_11'));
    assert.ok(sample.outputDirectory.includes('测试来源项目_1'));
    assert.ok(fs.existsSync(sample.savedFiles[0]));
    const firstTask = fs
      .readdirSync(path.join(data, 'creations'))
      .map((id) =>
        JSON.parse(
          fs.readFileSync(
            path.join(data, 'creations', id, 'record.json'),
            'utf8',
          ),
        ),
      )
      .find((record) => record.origin?.sourceAssetId === 1);
    assert.equal(firstTask.outputName, '测试角色1');
    assert.equal(path.basename(firstTask.savedFiles[0]), '测试角色1.png');
    assert.match(firstTask.outputDirectory, /导入批次-/);
    assert.equal(firstTask.origin.targetAssetId, 101);
    assert.equal(firstTask.origin.sourceProjectId, 1);
    await page.locator('#queue-limit').selectOption('3');
    await page.locator('#queue-resume').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .running').length === 3,
    );
    assert.equal(
      await application.evaluate(() => globalThis.workflowProbe.peak),
      5,
    );
    await page.screenshot({ path: path.join(screenshots, 'global-queue.png') });
    await page.locator('#queue-pause').click();
    await page.locator('#connection-open').click();
    await page.locator('#team-select').selectOption('22');
    await page.waitForFunction(() =>
      document.querySelector('#connection-open').title.includes('测试团队 B'),
    );
    await page.locator('[data-close="connection-dialog"]').click();
    members = members.filter((team) => team.id === 11);
    await page.locator('[data-view="assets"]').click();
    await page.locator('#refresh-cloud').click();
    await page.waitForFunction(
      () =>
        document.querySelector('#team-select').value === '' &&
        document.querySelector('#connection-open').title === '账号与同步',
    );
    await page.locator('#refresh-cloud').click();
    assert.equal(await page.locator('#team-select').inputValue(), '');
    assert.equal(
      await page.locator('#connection-open').getAttribute('title'),
      '账号与同步',
    );
    await page.locator('#connection-open').click();
    await page.locator('#team-select').selectOption('11');
    await page.waitForFunction(() =>
      document.querySelector('#connection-open').title.includes('测试团队 A'),
    );
    await page.locator('[data-close="connection-dialog"]').click();
    const persisted = fs.readFileSync(path.join(data, 'settings.json'), 'utf8');
    assert.equal(persisted.includes('workflow-test-key'), false);
    const state = await page.evaluate(
      async () => await window.workspace.state(),
    );
    assert.equal(JSON.stringify(state).includes('workflow-test-key'), false);
    assert.ok(
      calls
        .filter((call) => call.method !== 'GET')
        .every((call) => call.csrf === 'local-csrf'),
    );
    await application.evaluate(({ BrowserWindow }) => {
      for (const window of BrowserWindow.getAllWindows()) window.destroy();
    });
    await application.close();
    application = undefined;
    page = await launch();
    await page.locator('[data-view="queue"]').click();
    await page.waitForFunction(
      () => document.querySelectorAll('.queue-row .interrupted').length === 3,
    );
    assert.equal(await page.locator('.queue-row .queued').count(), 5);
    assert.match(await page.locator('#queue-summary').textContent(), /已暂停/);
    await page.locator('[data-queue-filter="failed"]').click();
    assert.equal(await page.locator('.queue-row .interrupted').count(), 3);
    assert.equal(await page.locator('.queue-row').count(), 3);
    assert.deepEqual(errors, []);
    console.log(
      'PASS workflow: actual cookie/CSRF cloud transport, explicit team and stale membership, names-only sync, bind/match/prompt, 12 batch + free task <=5 slots, pause/limit, local grouped save, restart recovery. All model responses are local fixtures.',
    );
  } catch (failure) {
    console.error('Workflow fixture calls:', calls.slice(-8));
    if (page) {
      console.error(
        'UI error:',
        await page
          .locator('#workspace-error')
          .textContent()
          .catch(() => 'unavailable'),
      );
      await page
        .screenshot({ path: path.join(screenshots, 'workflow-failure.png') })
        .catch(() => {});
    }
    throw failure;
  } finally {
    if (application) {
      await application
        .evaluate(({ BrowserWindow }) => {
          for (const window of BrowserWindow.getAllWindows()) window.destroy();
        })
        .catch(() => {});
      await application.close().catch(() => {});
    }
    await new Promise((resolve) => server.close(resolve));
    if (
      path.dirname(data) === os.tmpdir() &&
      path.basename(data).startsWith('antv-workflow-')
    )
      fs.rmSync(data, { recursive: true, force: true });
  }
})().catch((failure) => {
  console.error(failure);
  process.exitCode = 1;
});
