const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || 'playwright-core');

const outputDir = path.join(__dirname, 'screenshots');
fs.mkdirSync(outputDir, { recursive: true });

const team = {
  id: 10, code: 'VISUAL', name: '新禾文创', type: 'STUDIO', status: 'ACTIVE',
  memberType: 'OWNER', memberId: 1,
};
const bootstrap = {
  user: { id: 1, mobile: '13800138000', nickname: '验证用户', status: 'ACTIVE' },
  session: { sessionId: 'visual-check', expiresAt: '2099-01-01T00:00:00' },
  platform: { roles: [], permissions: [] },
  tenants: [team],
  selectedTenant: {
    tenant: team, membership: { id: 1, memberType: 'OWNER', status: 'ACTIVE' },
    roles: [], permissions: ['BILLING:MANAGE'],
  },
};
const offer = (id, packageType, name, price, entitlements, periodMonths) => ({
  packageId: id, packageVersionId: id, code: `VISUAL-${id}`, packageType,
  name, price, currency: 'CNY', entitlements,
  ...(periodMonths ? { periodMonths } : {}),
});
const catalog = [
  offer(11, 'SUBSCRIPTION', '专业创作月度会员', 99, [
    { type: 'PERIODIC_POINTS', value: 3500 },
    { type: 'ONE_TIME_POINTS', value: 200 },
    { type: 'GLOBAL_DISCOUNT', value: 0.85 },
    { type: 'DISPLAY', name: '商用授权' },
  ], 1),
  offer(12, 'SUBSCRIPTION', '超长名称用于验证在窄屏与多权益内容下仍能完整阅读的团队创作会员套餐', 199, [
    { type: 'PERIODIC_POINTS', value: 8000 },
    { type: 'DISPLAY', name: '多人协作与商用素材权益说明' },
  ], 1),
  offer(13, 'SUBSCRIPTION', '季度创作会员', 269, [{ type: 'PERIODIC_POINTS', value: 3500 }], 3),
  offer(14, 'SUBSCRIPTION', '半年创作会员', 499, [{ type: 'PERIODIC_POINTS', value: 3500 }], 6),
  offer(15, 'SUBSCRIPTION', '年度创作会员', 899, [{ type: 'PERIODIC_POINTS', value: 3500 }], 12),
  offer(16, 'POINT_PACKAGE', '积分补给包', 59, [{ type: 'ONE_TIME_POINTS', value: 2000 }]),
];

function response(data) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) };
}

async function openScenario(browser, scenario, viewport, colorScheme) {
  const context = await browser.newContext({ viewport, colorScheme, locale: 'zh-CN' });
  const page = await context.newPage();
  const requests = { create: 0, refresh: 0 };
  await page.addInitScript(() => localStorage.setItem('currentTenantId', '10'));
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    if (pathname === '/api/auth/bootstrap') return route.fulfill(response(bootstrap));
    if (pathname.endsWith('/commercial/catalog')) {
      if (scenario === 'catalog-error') {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, errorMessage: '目录服务暂不可用' }) });
      }
      return route.fulfill(response(scenario === 'empty' ? [] : scenario === 'points-only' ? catalog.slice(-1) : catalog));
    }
    if (pathname.endsWith('/commercial/subscription/current')) return route.fulfill(response(null));
    if (pathname.endsWith('/commercial/subscription/queued')) return route.fulfill(response([]));
    if (pathname.endsWith('/commercial/orders') && request.method() === 'GET') {
      return route.fulfill(response(scenario === 'pending' ? [{
        id: 51, merchantOrderNo: 'VISUAL-51', amount: 39, currency: 'CNY',
        status: 'PENDING_PAYMENT', codeUrl: 'weixin://visual-pending',
        expiresAt: '2099-01-01T00:00:00',
      }] : []));
    }
    if (pathname.endsWith('/commercial/orders') && request.method() === 'POST') {
      requests.create += 1;
      return route.fulfill(response({
        id: 52, merchantOrderNo: 'VISUAL-52', amount: 99, currency: 'CNY',
        status: 'PENDING_PAYMENT', codeUrl: 'weixin://visual-created',
        expiresAt: new Date(Date.now() + 30000).toISOString(),
      }));
    }
    if (pathname.endsWith('/refresh')) {
      requests.refresh += 1;
      const status = scenario === 'payment-expired' ? 'EXPIRED'
        : scenario === 'fulfillment-pending' && requests.refresh === 1 ? 'ENTITLEMENT_PENDING'
          : scenario === 'fulfillment-pending' ? 'COMPLETED' : 'PENDING_PAYMENT';
      return route.fulfill(response({
        id: 52, merchantOrderNo: 'VISUAL-52', amount: 99, currency: 'CNY',
        status,
        codeUrl: 'weixin://visual-created', expiresAt: new Date(Date.now() + 30000).toISOString(),
      }));
    }
    if (pathname.endsWith('/points/account')) return route.fulfill(response({ balance: 2140 }));
    if (pathname.endsWith('/points/transactions')) return route.fulfill(response({ records: [], total: 0, current: 1, pageSize: 20 }));
    return route.fulfill(response(null));
  });
  await page.goto('http://localhost:8001/recharge', { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: '为下一场创作，备好灵感。' }).waitFor();
  await page.getByText('套餐加载中').waitFor({ state: 'hidden' });
  return { context, page, requests };
}

async function capture(page, name) {
  await page.screenshot({ path: path.join(outputDir, `${name}.png`), fullPage: true });
  return page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    background: getComputedStyle(document.querySelector('main')).backgroundColor,
    cards: document.querySelectorAll('button[aria-label^="选择"]').length,
    tabs: [...document.querySelectorAll('[role="tab"]')].map((tab) => tab.textContent.trim()),
  }));
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM_PATH });
  const results = {};
  try {
    for (const width of process.env.VISUAL_ONLY_DARK_PAYMENT ? [] : [320, 390, 768, 1440]) {
      for (const colorScheme of ['light', 'dark']) {
        const { context, page, requests } = await openScenario(browser, 'full', { width, height: 900 }, colorScheme);
        results[`${width}-${colorScheme}`] = await capture(page, `${width}-${colorScheme}-membership`);
        const keyboardCard = page.getByRole('button', { name: `选择${catalog[1].name}` });
        await keyboardCard.focus();
        await page.keyboard.press('Enter');
        if (await keyboardCard.getAttribute('aria-pressed') !== 'true') throw new Error('Keyboard card selection failed');
        for (const period of ['季度', '半年', '年度']) {
          await page.locator('.ant-radio-button-wrapper').filter({ hasText: period }).click();
          await page.getByRole('button', { name: `选择${period === '季度' ? '季度' : period === '半年' ? '半年' : '年度'}创作会员` }).waitFor();
        }
        await page.getByRole('tab', { name: '会员套餐' }).focus();
        await page.keyboard.press('ArrowRight');
        await page.keyboard.press('Enter');
        await page.waitForFunction(() => document.querySelector('[role="tab"][aria-selected="true"]')?.textContent?.includes('积分包'));
        results[`${width}-${colorScheme}-points`] = await capture(page, `${width}-${colorScheme}-points`);
        if (requests.create !== 0) throw new Error('Browsing or selection created a payment order');
        await context.close();
      }
    }
    for (const scenario of process.env.VISUAL_ONLY_DARK_PAYMENT ? [] : ['points-only', 'empty', 'catalog-error', 'pending', 'payment-expired']) {
      const { context, page, requests } = await openScenario(browser, scenario, { width: 390, height: 900 }, 'light');
      if (scenario === 'points-only') {
        await page.getByText('当前周期暂无可售会员套餐').waitFor();
        await page.getByRole('tab', { name: '积分包' }).click();
        await page.getByRole('button', { name: '选择积分补给包' }).waitFor();
      }
      if (scenario === 'empty') await page.getByText('当前周期暂无可售会员套餐').waitFor();
      if (scenario === 'catalog-error') await page.getByRole('alert').getByText('目录服务暂不可用').waitFor();
      if (scenario === 'pending') await page.getByRole('button', { name: '继续支付' }).click();
      if (scenario === 'payment-expired') {
        await page.getByRole('button', { name: '确认付款' }).click();
        await page.getByText('请使用微信扫码付款').waitFor();
      }
      if (scenario === 'pending' || scenario === 'payment-expired') await page.waitForTimeout(350);
      results[scenario] = await capture(page, `390-light-${scenario}`);
      if (scenario === 'pending') {
        await page.keyboard.press('Escape');
        await page.getByRole('dialog', { name: '扫码支付' }).waitFor({ state: 'hidden' });
        await page.waitForTimeout(350);
        const focusReturned = await page.evaluate(() => document.activeElement?.textContent?.includes('继续支付'));
        if (!focusReturned) throw new Error('Payment focus did not return to the continuation button');
      }
      if (scenario === 'payment-expired') {
        await page.getByText('请使用微信扫码付款').waitFor({ state: 'hidden', timeout: 7000 });
        results['payment-expired-after-poll'] = await capture(page, '390-light-payment-expired-after-poll');
        if (requests.create !== 1 || requests.refresh !== 1) throw new Error('Payment stub request count mismatch');
      } else if (requests.create !== 0) throw new Error(`${scenario} created a payment order`);
      await context.close();
    }
    const darkPayment = await openScenario(browser, 'pending', { width: 390, height: 900 }, 'dark');
    await darkPayment.page.getByRole('button', { name: '继续支付' }).click();
    await darkPayment.page.waitForTimeout(350);
    results['pending-dark'] = await capture(darkPayment.page, '390-dark-pending');
    await darkPayment.context.close();
    if (!process.env.VISUAL_ONLY_DARK_PAYMENT) {
      const fulfillment = await openScenario(browser, 'fulfillment-pending', { width: 390, height: 900 }, 'light');
      await fulfillment.page.getByRole('button', { name: '确认付款' }).click();
      await fulfillment.page.getByText('权益发放中', { exact: true }).waitFor({ timeout: 7000 });
      results['fulfillment-pending'] = await capture(fulfillment.page, '390-light-fulfillment-pending');
      await fulfillment.page.getByText('权益发放中', { exact: true }).waitFor({ state: 'hidden', timeout: 7000 });
      results['fulfillment-complete'] = await capture(fulfillment.page, '390-light-fulfillment-complete');
      if (fulfillment.requests.create !== 1 || fulfillment.requests.refresh !== 2) throw new Error('Fulfillment stub request count mismatch');
      await fulfillment.context.close();
    }
    fs.writeFileSync(path.join(__dirname, 'visual-check-results.json'), JSON.stringify(results, null, 2));
    for (const [name, result] of Object.entries(results)) {
      if (result.documentWidth > result.viewport + 1) throw new Error(`${name} has horizontal overflow: ${result.documentWidth} > ${result.viewport}`);
    }
    process.stdout.write(JSON.stringify(results, null, 2));
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
