import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { homedir } from 'node:os';
import { join, resolve, extname, sep } from 'node:path';
const root = resolve(import.meta.dirname, '..');
const output = join(root, 'outputs/manual-asset-preview/implementation-browser');
await mkdir(output, { recursive: true });
const dist = join(root, 'frontend/dist');
const server = createServer(async (req, res) => {
  let file = resolve(dist, '.' + decodeURIComponent(new URL(req.url, 'http://localhost').pathname));
  if (file !== dist && !file.startsWith(dist + sep)) { res.writeHead(403).end(); return; }
  try { if (!extname(file)) file = join(dist, 'index.html');
    const content = await readFile(file);
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' })[extname(file)] || 'application/octet-stream');
    res.end(content);
  } catch { res.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
const require = createRequire(import.meta.url);
const runtime = join(homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules');
const { chromium } = require(join(runtime, 'playwright'));
const browser = await chromium.launch({ headless: true, executablePath: join(homedir(), 'AppData/Local/ms-playwright/chromium-1243/chrome-win64/chrome.exe') });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const report = { checks: [], errors: [], requests: [], saves: [] };
page.on('pageerror', error => report.errors.push(error.message));
const definitions = JSON.parse(await readFile(join(root, 'backend/src/main/resources/manual-assets/configuration.json'), 'utf8'));
const permissions = ['PROJECT:VIEW', 'PROJECT:CREATE', 'ELEMENT:VIEW', 'ELEMENT:EDIT', 'STORYBOARD:VIEW', 'STORYBOARD:EDIT', 'AI_SERVICE:USE', 'AI_IMAGE_TASK:VIEW'];
const tenant = { id: 1, code: 'QA', name: '资产验收工作室', type: 'STUDIO', status: 'ACTIVE', memberType: 'OWNER', memberId: 1 };
const bootstrap = { user: { id: 1, nickname: '资产验收', mobile: '00000000000', status: 'ACTIVE' }, session: { sessionId: 'qa-only', expiresAt: '2099-01-01T00:00:00Z' }, platform: { roles: [], permissions: [] }, tenants: [tenant], selectedTenant: { tenant, membership: { id: 1, memberType: 'OWNER', status: 'ACTIVE' }, roles: [], permissions }, nextAction: 'ENTER_WORKSPACE' };
const project = { id: 1, tenantId: 1, name: '时光里的书店', status: 'IN_PROGRESS', aspectRatio: '16:9', effectivePermissions: permissions, capabilities: { canView: true, canEdit: true } };
const workspace = { projectId: 1, characters: [], scenes: [], props: [] };
let failing = false;
const json = (route, data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
const arrays = { CHARACTER: 'characters', SCENE: 'scenes', PROP: 'props' };
await context.route('**/api/**', async route => {
  const path = new URL(route.request().url()).pathname; report.requests.push(path);
  if (path === '/api/auth/bootstrap') return json(route, bootstrap);
  if (path === '/api/projects/1' || path.endsWith('/metadata')) return json(route, project);
  if (/point|balance/.test(path)) return json(route, { balance: 1000, availableBalance: 1000, frozenBalance: 0 });
  if (path.endsWith('/asset-settings-summary')) return json(route, workspace);
  if (path.endsWith('/asset-normalization')) return json(route, { status: 'IDLE', blocked: false });
  if (path.endsWith('/manual-assets/configuration')) return json(route, { scriptId: 7, canCreate: true, disabledReason: '', definitions });
  if (path.endsWith('/manual-assets')) {
    const command = route.request().postDataJSON(); report.saves.push(command);
    if (failing) { failing = false; return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: false, errorMessage: '测试：名称与已有资产重复，请修改后重试' }) }); }
    const id = 100 + report.saves.length;
    workspace[arrays[command.assetType]].push({ id, name: command.name, status: 'CONFIRMED', prompt: '已生成的完整提示词', appearance: '手动填写外貌', description: '手动填写场景', roleType: 'SUPPORTING', sceneType: 'LOCATION', propType: 'KEY_PROP' });
    return json(route, { assetType: command.assetType, assetId: id, variantId: id + 100 });
  }
  if (path.endsWith('/visual-workspace')) {
    const match = /elements\/([^/]+)\/(\d+)/.exec(path) || /assets\/([^/]+)\/(\d+)/.exec(path);
    const id = match ? Number(match[2]) : 101;
    const variant = { id: id + 100, assetId: id, assetType: match?.[1] || 'CHARACTER', name: '主形象', prompt: '已生成的完整提示词', sourceType: 'MANUAL', generationStatus: 'NOT_STARTED', primary: true, usable: false };
    return json(route, { variantCount: 1, variants: [variant], selectedVariant: variant, primaryVariant: variant, episodeBindings: [], generationSummary: {}, total: 1, current: 1, pageSize: 20 });
  }
  if (path.endsWith('/ai/models')) return json(route, { imageModels: [], videoModels: [], textModels: [], audioModels: [] });
  if (path.endsWith('/ai/config')) return json(route, {});
  if (path.endsWith('/script-content')) return json(route, { id: 7, title: '时光里的书店', content: '测试剧本' });
  return json(route, []);
});
const check = (name, result) => { assert(result, name); report.checks.push(name); };
try {
  await page.goto(`${base}/projects/1/production-workbench/settings`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '新增角色', exact: true }).waitFor();
  await page.screenshot({ path: join(output, '01-settings.png'), fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: '新增角色', exact: true }).click();
  await page.getByRole('dialog').waitFor();
  await page.getByRole('button', { name: /^保\s*存$/ }).click();
  await page.getByText('脸型不能为空且不能超过 200 字符。', { exact: true }).waitFor();
  check('角色只填名称不能保存', report.saves.length === 0);
  const sample = { era: '现代', country: '中国', ethnicity: '未明确', genre: '写实', gender: '女', age: '25—30 岁', face: '鹅蛋脸，下颌线清晰', hair: '黑色齐肩短发，发尾内扣', earrings: '无', body: '体态修长，肩背挺直', ratio: '7.5 头身', upper: '米白色棉麻衬衫', lower: '深蓝色直筒长裤', shoes: '白色帆布鞋' };
  for (const [type, label, name] of [['CHARACTER', '角色', '苏禾'], ['SCENE', '场景', '旧城书店'], ['PROP', '道具', '苏禾的怀表']]) {
    if (type !== 'CHARACTER') {
      await page.getByRole('tab', { name: new RegExp(`^${label}`) }).click();
      await page.getByRole('button', { name: `新增${label}`, exact: true }).click();
    }
    await page.getByLabel(`${label}名称`, { exact: true }).fill(name);
    for (const field of definitions[type].fields) await page.getByLabel(field.label, { exact: true }).fill(sample[field.key] || field.placeholder.replace(/^例如：/, '').split('；')[0]);
    const preview = await page.getByLabel('自动生成的提示词').textContent();
    check(`${label}预览包含固定AI任务`, preview.replace(/\s/g, '').includes(definitions[type].task.replace(/\s/g, '')));
    await page.getByLabel(`${label}名称`, { exact: true }).focus();
    await page.locator('.ant-form-item-explain-error').waitFor({ state: 'hidden' }).catch(() => {});
    await page.screenshot({ path: join(output, `02-${type.toLowerCase()}-form.png`), fullPage: true, animations: 'disabled' });
    if (type === 'CHARACTER') {
      failing = true;
      await page.getByRole('button', { name: /保存并配置形象/ }).click();
      await page.getByText('测试：名称与已有资产重复，请修改后重试', { exact: true }).waitFor();
      check('保存失败保留表单', await page.getByLabel('角色名称', { exact: true }).inputValue() === name);
      await page.getByRole('button', { name: /保存并配置形象/ }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
      check('失败重试复用幂等标识', report.saves[0].requestId === report.saves[1].requestId);
      await page.getByText('主形象', { exact: true }).first().waitFor();
      await page.screenshot({ path: join(output, '03-created-inline-panel.png'), fullPage: true, animations: 'disabled' });
      check('保存并配置形象进入现有内联面板', (await page.getByText('主形象', { exact: true }).count()) > 0);
    } else {
      await page.getByRole('button', { name: /^保\s*存$/ }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
    }
    await page.getByText(name, { exact: true }).first().waitFor();
    check(`${label}创建后出现在列表`, workspace[arrays[type]].length === 1);
  }
  check('没有调用生成和扣费接口', report.requests.every(path => !/ai-image-tasks|point-estimate|asset-image-batches/.test(path)));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '新增道具', exact: true }).click();
  await page.getByRole('dialog').waitFor();
  await page.locator('.ant-message-notice').last().waitFor({ state: 'hidden' });
  await page.screenshot({ path: join(output, '04-mobile-form.png'), fullPage: true, animations: 'disabled' });
  check('窄屏页面没有横向溢出', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await page.getByLabel('道具名称', { exact: true }).fill('未保存道具');
  await page.getByRole('button', { name: /^取\s*消$/ }).click();
  await page.getByText('放弃本次新增？', { exact: true }).last().waitFor();
  check('取消编辑需确认放弃', true);
  await page.getByRole('button', { name: '继续填写', exact: true }).click();
  check('继续填写保留内容', await page.getByLabel('道具名称', { exact: true }).inputValue() === '未保存道具');
  check('没有浏览器运行错误', report.errors.length === 0);
} catch (error) { report.failure = error.stack; await page.screenshot({ path: join(output, 'failure.png'), fullPage: true, animations: 'disabled' }); throw error; }
finally { await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2)); await browser.close(); await new Promise(resolve => server.close(resolve)); }
console.log(`Passed ${report.checks.length} browser checks. Report: ${output}`);
