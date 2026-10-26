import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
const root = resolve(import.meta.dirname, '..');
const output = join(root, '.temp/video-frame-browser'); await mkdir(output, { recursive: true });
const require = createRequire(import.meta.url);
const runtime = process.env.CODEX_RUNTIME_NODE_MODULES || join(homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules');
const { chromium } = require(join(runtime, 'playwright'));
const sharp = require(join(runtime, 'sharp'));
const cache = join(homedir(), 'AppData/Local/ms-playwright');
const packages = await readdir(cache);
const encoderDir = packages.filter(name => /^ffmpeg-\d+$/.test(name)).sort().at(-1);
const chromeDir = packages.filter(name => /^chromium-\d+$/.test(name)).sort().at(-1);
const pictures = await Promise.all(['#bb3850', '#258d9d'].map((color, i) => sharp(Buffer.from(`<svg width="360" height="640" xmlns="http://www.w3.org/2000/svg"><rect width="360" height="640" fill="${color}"/><circle cx="180" cy="250" r="100" fill="#ffffff22"/><text x="180" y="270" text-anchor="middle" font-size="70" fill="white">0${i + 1}</text><text x="180" y="470" text-anchor="middle" font-size="24" fill="white">VIDEO FRAME</text></svg>`)).jpeg().toBuffer()));
const fixturePath = join(output, 'fixture.webm');
await new Promise((done, reject) => {
  const encoder = spawn(join(cache, encoderDir, 'ffmpeg-win64.exe'), ['-y', '-f', 'image2pipe', '-vcodec', 'mjpeg', '-framerate', '10', '-i', 'pipe:0', '-c:v', 'libvpx', '-t', '2', '-pix_fmt', 'yuv420p', fixturePath], { stdio: ['pipe', 'ignore', 'pipe'] });
  let diagnostic = ''; encoder.stderr.on('data', chunk => { diagnostic += chunk; }); encoder.on('error', reject);
  encoder.on('close', code => code === 0 ? done() : reject(new Error(diagnostic))); encoder.stdin.on('error', () => {});
  for (let frame = 0; frame < 20; frame++) encoder.stdin.write(pictures[frame < 10 ? 0 : 1]); encoder.stdin.end();
});
const fixture = await readFile(fixturePath);
const permissions = ['PROJECT:CREATE', 'STORYBOARD:VIEW', 'STORYBOARD:EDIT', 'AI_SERVICE:USE', 'AI_IMAGE_TASK:VIEW', 'AI_VIDEO_TASK:VIEW', 'ELEMENT:VIEW'];
const tenant = { id: 1, code: 'QA', name: 'Frame QA', type: 'STUDIO', status: 'ACTIVE', memberType: 'OWNER', memberId: 1 };
const bootstrap = { user: { id: 1, nickname: 'Frame QA', mobile: '00000000000', status: 'ACTIVE' }, session: { sessionId: 'qa-only', expiresAt: '2099-01-01T00:00:00Z' }, platform: { roles: [], permissions: [] }, tenants: [tenant], selectedTenant: { tenant, membership: { id: 1, memberType: 'OWNER', status: 'ACTIVE' }, roles: [], permissions }, nextAction: 'ENTER_WORKSPACE' };
const project = { id: 1, tenantId: 1, name: '截帧验收项目', status: 'IN_PROGRESS', aspectRatio: '9:16', effectivePermissions: permissions, capabilities: { canView: true, canEdit: true } };
const shot = { id: 101, episodeNo: 1, episodeId: 1, storyboardNo: 1, shotNo: 1, visualDescription: '视频画面截取测试', videoPrompt: '测试分镜', durationSeconds: 2, currentVideoResultId: 22, currentVideoUrl: '/qa/video.webm', assetReferences: [] };
const result = id => ({ id, taskId: 2, storyboardId: 101, videoUrl: '/qa/video.webm', coverUrl: '/qa/cover.jpg', isSelected: id === 22, status: 'ACTIVE', createdAt: `2026-10-26T10:0${id === 22 ? 2 : 1}:00` });
const browser = await chromium.launch({ headless: true, executablePath: join(cache, chromeDir, 'chrome-win64/chrome.exe') });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
await context.addInitScript(() => localStorage.setItem('currentTenantId', '1'));
const page = await context.newPage(); page.setDefaultTimeout(20000);
const report = { requests: [], captures: [], consoleErrors: [], checks: [] };
page.on('pageerror', error => report.consoleErrors.push(error.message));
let session = 0, uploads = [], bindings = [], failSave = false;
const json = (route, data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
const serveVideo = route => {
  const range = route.request().headers().range;
  const start = Number(/^bytes=(\d+)-/.exec(range || '')?.[1] || 0);
  return route.fulfill({ status: range ? 206 : 200, contentType: 'video/webm', headers: { 'Accept-Ranges': 'bytes', ...(range ? { 'Content-Range': `bytes ${start}-${fixture.length - 1}/${fixture.length}` } : {}) }, body: fixture.subarray(start) });
};
await context.route('**/qa/**', route => route.request().url().endsWith('.webm') ? serveVideo(route) : route.fulfill({ contentType: 'image/jpeg', body: pictures[0] }));
await context.route('**://qa-frame-bucket-1250000000.cos.ap-shanghai.myqcloud.com/**', async route => {
  if (route.request().method() === 'PUT') uploads.push(route.request().postDataBuffer());
  await route.fulfill({ status: 200, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Expose-Headers': 'ETag', ETag: '"qa-etag"' }, body: '' });
});
await context.route('**/api/**', async route => {
  const path = new URL(route.request().url()).pathname; report.requests.push(path);
  if (path === '/api/auth/bootstrap') return json(route, bootstrap);
  if (path === '/api/projects/1' || path === '/api/projects/1/metadata') return json(route, project);
  if (/point|balance/.test(path)) return json(route, { balance: 1000, availableBalance: 1000, frozenBalance: 0 });
  if (path.endsWith('/storyboard-editor-page')) return json(route, { projectId: 1, episodeNo: 1, current: 1, pageSize: 20, total: 1, episodes: [{ episodeId: 1, episodeNo: 1, title: '测试剧集' }], storyboards: [shot] });
  if (path.endsWith('/details')) return json(route, { episodeId: 1, episodeNo: 1, title: '测试剧集' });
  if (path.endsWith('/storyboard-media')) return json(route, { imageTasks: [], voiceTasks: [], videoTasks: [{ id: 2, storyboardId: 101, status: 'SUCCEEDED', results: [result(22), result(21)] }], videoHistory: [{ storyboardId: 101, data: [result(22), result(21)], current: 1, pageSize: 8, total: 2 }] });
  if (path.endsWith('/asset-settings-summary')) return json(route, { characters: [], scenes: [], props: [] });
  if (path.endsWith('/ai/models')) return json(route, { imageModels: [], videoModels: [] });
  if (path.endsWith('/ai/config')) return json(route, {});
  if (path.endsWith('/storyboard-batches/latest')) return json(route, null);
  if (path.endsWith('/frame-source')) return serveVideo(route);
  if (path === '/api/media-uploads') { session++; return json(route, { sessionToken: `qa-${session}`, bucket: 'qa-frame-bucket-1250000000', region: 'ap-shanghai', objectKey: `frames/${session}.png`, storageClass: 'STANDARD', status: 'PENDING' }); }
  if (path.endsWith('/authorization')) return json(route, { authorization: 'q-sign-algorithm=sha1&q-ak=qa&q-sign-time=1;9999999999&q-key-time=1;9999999999&q-header-list=&q-url-param-list=&q-signature=qa', securityToken: 'qa', expiresAt: 9999999999 });
  if (path.endsWith('/complete')) return json(route, { sessionToken: path.split('/')[3], objectKey: `frames/${session}.png`, contentType: 'image/png', size: uploads.at(-1)?.length, eTag: 'qa-etag' });
  if (path.endsWith('/frames')) {
    const body = route.request().postDataJSON(); bindings.push({ path, ...body });
    if (failSave) { failSave = false; return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: false, errorMessage: '模拟保存失败' }) }); }
    return json(route, { id: bindings.length, sourceVideoResultId: Number(path.split('/').at(-2)), timeSeconds: body.timeSeconds });
  }
  return json(route, []);
});
try {
  await page.goto('http://localhost:8000/projects/1/production-workbench/storyboard', { waitUntil: 'domcontentloaded', timeout: 90000 });
  const stage = page.locator('.storyboard-preview-stage').first(); await stage.waitFor();
  await page.getByRole('button', { name: '预览分镜1视频版本21', exact: true }).click();
  await stage.hover();
  await page.getByRole('button', { name: '取视频帧', exact: true }).click();
  const modal = page.getByRole('dialog'); await modal.waitFor();
  await page.waitForFunction(() => document.querySelector('video[aria-label="截帧视频预览"]')?.readyState >= 2);
  await page.waitForFunction(() => document.querySelectorAll('.video-frame-strip img').length === 6);
  assert(report.requests.some(path => path.endsWith('/21/frame-source')), 'Capture must use selected historic version');
  const capture = page.getByRole('button', { name: '截取当前帧并保存', exact: true }); await capture.click();
  await page.getByText('已保存至项目素材库，本次已保存 1 张', { exact: true }).waitFor();
  const seek = page.getByRole('slider', { name: '定位视频帧' }); await seek.fill('1.2');
  await page.waitForFunction(() => { const v=document.querySelector('video[aria-label="截帧视频预览"]'); return !v.seeking && v.currentTime >= 1.19; });
  failSave = true; await capture.click(); await page.getByRole('alert').filter({ hasText: '模拟保存失败' }).waitFor();
  await page.getByRole('button', { name: '重试保存当前截图' }).click();
  await page.getByText('已保存至项目素材库，本次已保存 2 张', { exact: true }).waitFor();
  assert.equal(uploads.length, 2, 'Save retry must not re-upload image');
  assert.equal(bindings[1].sessionToken, bindings[2].sessionToken, 'Save retry must reuse idempotency key');
  assert.equal(bindings[1].timeSeconds, bindings[2].timeSeconds, 'Retry must keep frozen timestamp');
  for (let i=0;i<uploads.length;i++) {
    const metadata=await sharp(uploads[i]).metadata(); assert.equal(metadata.width,360); assert.equal(metadata.height,640); assert.equal(metadata.format,'png');
    await writeFile(join(output,`capture-${i+1}.png`),uploads[i]); report.captures.push({width:metadata.width,height:metadata.height,time:bindings[i].timeSeconds});
  }
  assert(!uploads[0].equals(uploads[1]), 'Scrubbing must capture a different image');
  const [firstPixel,secondPixel]=await Promise.all(uploads.map(buffer=>sharp(buffer).extract({left:10,top:10,width:1,height:1}).raw().toBuffer()));
  assert(firstPixel[0]>firstPixel[2], 'First capture must be red'); assert(secondPixel[2]>secondPixel[0], 'Second capture must be blue');
  await page.waitForFunction(() => !document.querySelector('.video-frame-save button')?.disabled);
  await page.waitForTimeout(350);
  await page.screenshot({path:join(output,'desktop.png')});
  await page.setViewportSize({width:390,height:844}); await page.screenshot({path:join(output,'mobile.png')});
  assert(await modal.evaluate(el=>el.getBoundingClientRect().width<=innerWidth), 'Modal must fit mobile viewport');
  await page.keyboard.press('Escape'); await modal.waitFor({state:'hidden'});
  assert.equal(await page.locator('video').count(),0,'Closing modal must remove the player');
  report.checks=['current preview version','six real thumbnails','original PNG dimensions','seek matches captured pixels','retry reuses upload','continuous capture','responsive modal','media released'];
  assert.deepEqual(report.consoleErrors,[]);
  console.log(JSON.stringify({result:'passed',checks:report.checks,captures:report.captures,output},null,2));
} catch(error) { report.failure=error.stack; await page.screenshot({path:join(output,'failure.png')}).catch(()=>{}); console.error(error); process.exitCode=1; }
finally {await writeFile(join(output,'evidence.json'),JSON.stringify(report,null,2)); await browser.close();}
