import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { access, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = join(root, '.temp', 'media-loading-qa');
const baseUrl = process.env.MEDIA_BASE_URL || 'http://127.0.0.1:8000';
const require = createRequire(import.meta.url);
const packageRoot = process.env.CODEX_RUNTIME_NODE_MODULES || join(homedir(), '.cache', 'codex-runtimes', 'codex-primary-runtime', 'dependencies', 'node', 'node_modules');
let playwright;
try { playwright = require('playwright'); } catch { playwright = require(join(packageRoot, 'playwright')); }

const findBrowser = async () => {
  if (process.env.PLAYWRIGHT_BROWSER_PATH) return process.env.PLAYWRIGHT_BROWSER_PATH;
  const cache = join(homedir(), 'AppData', 'Local', 'ms-playwright');
  for (const directory of (await readdir(cache).catch(() => [])).filter((name) => /^chromium-\d+$/.test(name)).sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]))) {
    const executable = join(cache, directory, 'chrome-win64', 'chrome.exe');
    try { await access(executable); return executable; } catch {}
  }
  return undefined;
};

const permissions = ['PROJECT:CREATE', 'STORYBOARD:VIEW', 'STORYBOARD:EDIT', 'AI_SERVICE:USE', 'AI_IMAGE_TASK:VIEW', 'AI_VIDEO_TASK:VIEW', 'AI_VIDEO_RESULT:DOWNLOAD', 'ELEMENT:VIEW', 'EPISODE_VERSION:VIEW'];
const tenant = { id: 1, code: 'QA', name: 'Media QA', type: 'STUDIO', status: 'ACTIVE', memberType: 'OWNER', memberId: 1 };
const bootstrap = { user: { id: 1, nickname: 'Media QA', mobile: '00000000000', status: 'ACTIVE' }, session: { sessionId: 'qa-only', expiresAt: '2099-01-01T00:00:00Z' }, platform: { roles: [], permissions: ['PLATFORM_INSPIRATION_MANAGE'] }, tenants: [tenant], selectedTenant: { tenant, membership: { id: 1, memberType: 'OWNER', status: 'ACTIVE' }, roles: [], permissions }, nextAction: 'ENTER_WORKSPACE' };
const entries = (make) => Array.from({ length: 200 }, (_, index) => make(index + 1));
const image = (domain, id) => `/qa/media/${domain}/${id}.png`;
const video = (id) => `/qa/media/video/${id}.webm`;
const inspirations = entries((id) => ({ id, title: `QA Inspiration ${id}`, externalId: String(id), url: video(id), thumbnailUrl: image('inspiration', id), creationType: 'VIDEO', mimeType: 'video/webm', promptText: `QA prompt ${id}`, sortOrder: id, authorName: 'QA', tags: ['QA'] }));
const styles = entries((id) => ({ id, externalId: String(id), name: `QA Style ${id}`, category: id === 200 ? 'Later category' : 'QA', description: `QA style ${id}`, imageUrl: image('style', id) }));
const projects = entries((id) => ({ id, name: `QA Project ${id}`, code: `QA_${id}`, ownerId: 1, ownerName: 'QA', tenantId: 1, memberCount: 1, status: 'IN_PROGRESS', coverUrl: `/api/projects/${id}/cover`, coverStatus: id === 1 ? 'PENDING' : id === 3 ? 'FAILED' : 'READY', coverSource: 'FIRST_FRAME', aspectRatio: '16:9', createdAt: '2026-10-02T00:00:00Z', capabilities: { canView: true, canEdit: true, canManageMembers: true, canManageRoles: true, canDelete: true }, effectivePermissions: permissions }));
const storyboards = entries((id) => ({ id, episodeNo: 1, episodeId: 1, shotNo: id, storyboardNo: id, shotType: 'MEDIUM', visualDescription: `QA storyboard ${id}`, characters: 'QA character', scene: 'QA scene', dialogue: `QA dialogue ${id}`, durationSeconds: 3, imagePrompt: 'QA image', videoPrompt: 'QA video', firstFrameUrl: image('original', id), firstFrameThumbnailUrl: image('storyboard', id), currentVideoResultId: id, currentVideoUrl: video(id), assetReferences: [] }));
const task = { taskKey: 'VIDEO:1', type: 'VIDEO', title: 'QA Video task', statusGroup: 'SUCCEEDED', childCounts: { total: 0 }, allowedActions: [], createdAt: '2026-10-02T00:00:00Z' };
const pageOf = (items, url, defaults = 20) => {
  const current = Number(url.searchParams.get('current') || url.searchParams.get('page') || 1);
  const pageSize = Number(url.searchParams.get('pageSize') || defaults);
  return { data: items.slice((current - 1) * pageSize, current * pageSize), current, pageSize, total: items.length };
};

await mkdir(output, { recursive: true });
const png = await readFile(join(root, 'frontend', 'public', 'juzhichuang-logo.png'));
const sharp = require(join(packageRoot, 'sharp'));
const jpeg = await sharp(png).resize(320, 180, { fit: 'contain', background: '#245e3d' }).jpeg().toBuffer();
const browserCache = join(homedir(), 'AppData', 'Local', 'ms-playwright');
const ffmpegDirectory = (await readdir(browserCache)).filter((name) => /^ffmpeg-\d+$/.test(name)).sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]))[0];
const fixturePath = join(output, 'fixture.webm');
await new Promise((done, reject) => {
  const encoder = spawn(join(browserCache, ffmpegDirectory, 'ffmpeg-win64.exe'), ['-y', '-f', 'image2pipe', '-vcodec', 'mjpeg', '-framerate', '10', '-i', 'pipe:0', '-c:v', 'libvpx', '-t', '1', '-pix_fmt', 'yuv420p', fixturePath], { stdio: ['pipe', 'ignore', 'pipe'] });
  let diagnostic = '';
  encoder.stderr.on('data', (chunk) => { diagnostic += chunk.toString(); });
  encoder.on('error', reject);
  encoder.stdin.on('error', () => {});
  encoder.on('close', (code) => code === 0 ? done() : reject(new Error(diagnostic)));
  for (let frame = 0; frame < 10; frame += 1) encoder.stdin.write(jpeg);
  encoder.stdin.end();
});
const webm = await readFile(fixturePath);
assert(webm.length > 1000, 'Generated video fixture has no encoded frames');
const browser = await playwright.chromium.launch({ headless: true, executablePath: await findBrowser(), args: ['--autoplay-policy=user-gesture-required'] });

const report = { baseUrl, fixtureRecords: 200, viewportChecks: [], consoleErrors: [] };
let activePage;
const pause = (ms) => new Promise((done) => setTimeout(done, ms));
const waitUntil = async (condition, description, limit = 10000) => {
  const deadline = Date.now() + limit;
  while (Date.now() < deadline) { if (await condition()) return; await pause(40); }
  throw new Error(`Timed out: ${description}`);
};

try {
  for (const viewport of [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
    await context.addInitScript(() => localStorage.setItem('currentTenantId', '1'));
    const page = await context.newPage();
    activePage = page;
    const requests = [];
    const unknownApis = [];
    const coverChecks = new Map();
    let pendingCoverState = 'PENDING';
    let videoDelay = 0;
    const results = { viewport: viewport.name, checks: {}, requests, unknownApis };
    report.viewportChecks.push(results);
    page.on('pageerror', (error) => report.consoleErrors.push({ viewport: viewport.name, message: error.message }));
    page.on('request', (request) => {
      const url = new URL(request.url());
      if (url.pathname.startsWith('/qa/media/') || url.pathname.startsWith('/api/')) requests.push({ path: url.pathname, method: request.method(), page: url.searchParams.get('page') || url.searchParams.get('current'), pageSize: url.searchParams.get('pageSize'), storyboardIds: url.searchParams.get('storyboardIds') });
    });
    const json = (route, data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
    await context.route('**/qa/media/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('.webm')) {
        if (videoDelay) await pause(videoDelay);
        const range = route.request().headers().range;
        const start = range ? Number(/^bytes=(\d+)-/.exec(range)?.[1] || 0) : 0;
        await route.fulfill({ status: range ? 206 : 200, contentType: 'video/webm', headers: { 'Accept-Ranges': 'bytes', ...(range ? { 'Content-Range': `bytes ${start}-${webm.length - 1}/${webm.length}` } : {}) }, body: webm.subarray(start) }).catch(() => {});
      } else await route.fulfill({ contentType: 'image/png', body: png });
    });
    await context.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const path = decodeURIComponent(url.pathname);
      if (path === '/api/auth/bootstrap') return json(route, bootstrap);
      if (/\/members$/.test(path)) return json(route, [{ id: 1, tenantId: 1, userId: 1, nickname: 'QA', status: 'ACTIVE' }]);
      if (/point|balance/.test(path)) return json(route, { balance: 1000, availableBalance: 1000, frozenBalance: 0 });
      if (path === '/api/style-library/categories') return json(route, ['QA', 'Later category']);
      if (path === '/api/style-library') return json(route, pageOf(styles.filter((item) => !url.searchParams.get('category') || item.category === url.searchParams.get('category')), url, 24));
      if (path === '/api/inspiration-creations') { const data = pageOf(inspirations, url, 8); return json(route, { ...data, records: data.data, data: undefined }); }
      if (/^\/api\/inspiration-creations\/\d+$/.test(path)) return json(route, inspirations[Number(path.split('/').at(-1)) - 1]);
      if (path === '/api/platform/inspiration-creations') { const data = pageOf(inspirations, url, 100); return json(route, { ...data, records: data.data.map((item) => ({ ...item, mediaType: 'VIDEO', sourceType: 'MANUAL', publishStatus: 'PUBLISHED' })), data: undefined }); }
      if (path === '/api/projects') return json(route, pageOf(projects, url));
      if (/^\/api\/projects\/\d+$/.test(path)) return json(route, projects[Number(path.split('/').at(-1)) - 1]);
      if (/\/cover\/status$/.test(path)) { coverChecks.set(path, (coverChecks.get(path) || 0) + 1); return json(route, { status: path.includes('/1/') ? pendingCoverState : 'READY', retryable: false, coverUrl: path.replace('/status', '') }); }
      if (/\/cover$/.test(path)) return route.fulfill({ contentType: 'image/png', body: png });
      if (/\/ai\/models$/.test(path)) return json(route, { imageModels: [], videoModels: [] });
      if (/\/ai\/config$/.test(path)) return json(route, {});
      if (/\/asset-settings-summary$/.test(path)) return json(route, { projectId: 1, characters: [], scenes: [], props: [] });
      if (/\/storyboard-batches\/latest$/.test(path)) return json(route, null);
      if (/\/storyboard-workspace$/.test(path)) { const data = pageOf(storyboards, url, 20); return json(route, { ...data, projectId: 1, episodes: [{ episodeId: 1, episodeNo: 1, title: 'QA Episode 1', content: '' }], episodeNo: 1, storyboards: data.data, data: undefined }); }
      if (/\/storyboard-media$/.test(path)) { const ids = (url.searchParams.get('storyboardIds') || '').split(',').map(Number); return json(route, { imageTasks: [], voiceTasks: [], videoTasks: ids.map((id) => ({ id, projectId: 1, storyboardId: id, status: 'SUCCEEDED', results: [{ id, taskId: id, videoUrl: video(id), coverUrl: image('storyboard', id), isSelected: true, status: 'SUCCEEDED' }] })) }); }
      if (/\/production-tasks$/.test(path)) return json(route, { items: [task], total: 1, page: 1, pageSize: 20, canViewTeamTasks: true });
      if (/\/production-tasks\/summary$/.test(path)) return json(route, { total: 1, counts: { SUCCEEDED: 1 } });
      if (/\/production-tasks\/VIDEO:1\/content$/.test(path)) return json(route, { schemaVersion: 1, taskKey: task.taskKey, contentRevision: '1', sections: [{ key: 'videos', title: 'QA task videos', kind: 'VIDEO', availability: 'AVAILABLE', fields: [], items: [{ id: 1, url: video(1), thumbnailUrl: image('task', 1) }, { id: 2, url: video(2), thumbnailUrl: image('task', 2) }], hasMore: false }] });
      if (/\/production-tasks\/VIDEO:1$/.test(path)) return json(route, task);
      if (/\/(ai-image-tasks|ai-video-tasks|ai-voice-tasks|storyboard-subtitles|shot-compose-tasks|episode-compose-tasks|episode-video-versions|episode-export-records)$/.test(path)) return json(route, { data: [], current: 1, pageSize: 20, total: 0 });
      unknownApis.push(path);
      return json(route, []);
    });
    const screenshot = async (name) => { await page.screenshot({ path: join(output, `${viewport.name}-${name}.png`), fullPage: false }); };
    const mediaRequests = () => requests.filter((item) => item.path.startsWith('/qa/media/video/'));
    const navigate = async (path) => { await page.goto(new URL(path, baseUrl).href, { waitUntil: 'domcontentloaded' }); };

    await navigate('/style-library');
    await page.getByText('QA Style 1', { exact: true }).waitFor();
    await pause(350);
    const offscreen = await page.locator('img[alt^="QA Style"]').evaluateAll((images) => images.map((image) => ({ alt: image.alt, src: image.getAttribute('src'), top: image.getBoundingClientRect().top, height: innerHeight })).filter((image) => image.top > image.height + 200));
    assert(offscreen.length > 0, 'Style fixture must include images beyond the preload margin');
    assert(offscreen.every((item) => !item.src), 'Offscreen style images received src before entering the margin');
    assert.equal(mediaRequests().length, 0, 'First screen fetched a video');
    results.checks.offscreenStyles = offscreen.length;
    await screenshot('styles-initial');
    await page.locator('.ant-pagination-item-2').click();
    await page.getByText('QA Style 25', { exact: true }).waitFor();
    results.checks.stylePage2 = true;
    await page.getByText('Later category', { exact: true }).click();
    await page.getByText('QA Style 200', { exact: true }).waitFor();
    assert.equal(Number(requests.filter((item) => item.path === '/api/style-library').at(-1).page), 1, 'Style filter did not reset pagination');
    results.checks.independentLaterCategory = true;

    await navigate('/short-drama-creation');
    await page.getByRole('button', { name: 'QA Inspiration 1', exact: true }).waitFor();
    assert.equal(mediaRequests().length, 0, 'Inspiration initial render fetched video bytes');
    await screenshot('inspirations-initial');
    await page.getByRole('button', { name: 'QA Inspiration 1', exact: true }).click();
    await page.getByRole('button', { name: '播放QA Inspiration 1', exact: true }).waitFor();
    assert.equal(await page.locator('video, source').count(), 0, 'Inspiration detail mounted video before play');
    await page.getByRole('button', { name: '播放QA Inspiration 1', exact: true }).click();
    await waitUntil(() => mediaRequests().length > 0, 'selected inspiration video request');
    assert.deepEqual([...new Set(mediaRequests().map((item) => item.path))], [video(1)], 'Click loaded an unselected video');
    await page.waitForFunction(() => document.querySelector('video')?.readyState >= 2);
    results.checks.playback = await page.locator('video').evaluate((element) => ({ readyState: element.readyState, controls: element.controls, preload: element.preload, paused: element.paused }));
    const pixelColors = await page.locator('video').evaluate((element) => {
      const canvas = document.createElement('canvas'); canvas.width = 64; canvas.height = 36;
      const drawing = canvas.getContext('2d'); drawing.drawImage(element, 0, 0, 64, 36);
      const pixels = drawing.getImageData(0, 0, 64, 36).data;
      return new Set(Array.from({ length: pixels.length / 4 }, (_, index) => `${pixels[index * 4]},${pixels[index * 4 + 1]},${pixels[index * 4 + 2]}`)).size;
    });
    assert(pixelColors > 10, 'Decoded video frame is blank');
    results.checks.playback.pixelColors = pixelColors;
    await screenshot('inspiration-playing');
    await page.evaluate(() => { globalThis.qaPreviousVideo = document.querySelector('video'); });
    await page.locator('.ant-modal-close').click();
    await page.waitForFunction(() => !globalThis.qaPreviousVideo?.getAttribute('src') && globalThis.qaPreviousVideo?.paused);
    results.checks.inspirationClose = true;
    let maximumCards = 0;
    for (let current = 2; current <= 25; current += 1) {
      await page.evaluate(() => scrollTo(0, document.documentElement.scrollHeight));
      await waitUntil(() => requests.some((item) => item.path === '/api/inspiration-creations' && Number(item.page) === current), `inspiration page ${current}`);
      await pause(80);
      maximumCards = Math.max(maximumCards, await page.getByRole('button', { name: /^QA Inspiration / }).count());
    }
    assert(maximumCards < 80, `Gallery mounted ${maximumCards} cards`);
    await waitUntil(() => page.locator('[class*="inspirationLoadMore"]').count().then((count) => count === 0), 'gallery terminal page');
    const finalHeight = await page.evaluate(() => document.documentElement.scrollHeight);
    await page.evaluate(() => scrollTo(0, 0));
    await page.getByRole('button', { name: 'QA Inspiration 1', exact: true }).waitFor();
    const restoredHeight = await page.evaluate(() => document.documentElement.scrollHeight);
    assert(Math.abs(finalHeight - restoredHeight) <= 2, `Gallery scroll height shifted ${finalHeight - restoredHeight}px`);
    results.checks.inspirationWindow = { loadedRecords: 200, maximumCards, scrollBackRestoredFirst: true, scrollHeightDelta: finalHeight - restoredHeight };
    await screenshot('inspirations-window');

    await page.getByRole('button', { name: '跳过上传，创建空白剧本', exact: true }).click();
    await page.locator('.ant-pagination-item-2').click();
    await page.getByRole('button', { name: 'QA Style 13', exact: true }).click();
    await page.locator('button[class*="selectedStyleCard"]').getByText('QA Style 13', { exact: true }).waitFor();
    const clippedStyles = await page.locator('[class*="styleStrip"]').evaluate((grid) => [...grid.querySelectorAll('button')].filter((button) => button.getBoundingClientRect().right > grid.getBoundingClientRect().right + 1).length);
    assert.equal(clippedStyles, 0, 'Platform style options exceed their grid container');
    results.checks.creationLaterStyleSelection = true;
    await screenshot('creation-later-style');

    const videosBeforeTask = mediaRequests().length;
    await navigate('/tasks');
    await page.getByRole('button', { name: 'QA Video task', exact: true }).click();
    await page.getByRole('button', { name: '播放QA task videos 1', exact: true }).waitFor();
    assert.equal(await page.locator('video').count(), 0, 'Task drawer mounted a player before click');
    assert.equal(mediaRequests().length, videosBeforeTask, 'Task drawer fetched video before click');
    await page.getByRole('button', { name: '播放QA task videos 1', exact: true }).click();
    await page.evaluate(() => { globalThis.qaPreviousVideo = document.querySelector('video'); });
    await page.getByRole('button', { name: '播放QA task videos 2', exact: true }).click();
    assert.equal(await page.locator('video').count(), 1, 'More than one task player mounted');
    assert.equal(await page.evaluate(() => globalThis.qaPreviousVideo.getAttribute('src')), null, 'Previous player retained source');
    await screenshot('task-player');
    await page.locator('.ant-drawer-close').click();
    await page.waitForFunction(() => document.querySelectorAll('video[src], source[src]').length === 0);
    videoDelay = 1000;
    await page.getByRole('button', { name: 'QA Video task', exact: true }).click();
    await page.getByRole('button', { name: '播放QA task videos 1', exact: true }).click();
    await page.locator('.ant-drawer-close').click();
    await pause(1200);
    assert.equal(await page.locator('video[src], source[src]').count(), 0, 'Late video response restored a closed player');
    results.checks.singlePlayerAndLateClose = true;
    videoDelay = 0;

    await navigate('/projects/list');
    await page.getByRole('button', { name: '进入QA Project 1', exact: true }).waitFor();
    await waitUntil(() => coverChecks.get('/api/projects/1/cover/status') === 1, 'initial visible cover status');
    const failedCoverRequests = requests.filter((item) => item.path === '/api/projects/3/cover');
    assert.equal(failedCoverRequests.length, 0, 'Failed compression fell back to a display or original source');
    await screenshot('project-covers');
    await page.clock.install();
    for (let attempt = 2; attempt <= 12; attempt += 1) {
      await page.clock.fastForward(5000);
      await waitUntil(() => coverChecks.get('/api/projects/1/cover/status') === attempt, `cover status attempt ${attempt}`);
    }
    await page.clock.fastForward(15000);
    await pause(120);
    assert.equal(coverChecks.get('/api/projects/1/cover/status'), 12, 'Cover exceeded polling bound');
    results.checks.coverPollingBound = 12;
    pendingCoverState = 'READY';
    await page.locator('.ant-pagination-item-2').click();
    await page.getByRole('button', { name: '进入QA Project 21', exact: true }).waitFor();
    await page.locator('.ant-pagination-item-1').click();
    await waitUntil(() => requests.some((item) => item.path === '/api/projects/1/cover'), 'compressed stable cover entry after readiness');
    results.checks.projectPage2AndReadyCover = true;

    const videosBeforeStoryboard = mediaRequests().length;
    await navigate('/projects/1/production-workbench/storyboard');
    await page.getByRole('textbox', { name: '分镜1剧本原文', exact: true }).waitFor();
    const summary = requests.filter((item) => item.path === '/api/projects/1/storyboard-media').at(-1);
    assert(summary?.storyboardIds && summary.storyboardIds.split(',').length <= 20, 'Storyboard summary exceeded its visible page');
    assert.equal(await page.locator('video').count(), 0, 'Storyboard mounted player before play');
    assert.equal(mediaRequests().length, videosBeforeStoryboard, 'Storyboard fetched video before play');
    await screenshot('storyboards-initial');
    results.checks.storyboardPageSummary = summary.storyboardIds.split(',').length;
    assert(!requests.some((item) => item.path.startsWith('/qa/media/original/')), 'Normal display requested an original image');
    assert.equal(report.consoleErrors.filter((item) => item.viewport === viewport.name).length, 0, 'Application emitted browser errors');
    await context.close();
  }
  await writeFile(join(output, 'evidence.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify({ result: 'passed', output, viewports: report.viewportChecks.map(({ viewport, checks }) => ({ viewport, checks })) }, null, 2));
} catch (error) {
  report.failure = error.message;
  if (activePage && !activePage.isClosed()) {
    report.failurePage = activePage.url();
    report.failureMedia = await activePage.locator('video').evaluateAll((elements) => elements.map((element) => ({ src: element.getAttribute('src'), preload: element.preload, readyState: element.readyState, paused: element.paused, error: element.error?.code }))).catch(() => []);
    await activePage.screenshot({ path: join(output, 'failure.png'), timeout: 5000 }).catch(() => {});
  }
  await writeFile(join(output, 'evidence.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.error(error);
  process.exitCode = 1;
} finally {
  await browser.close();
}
