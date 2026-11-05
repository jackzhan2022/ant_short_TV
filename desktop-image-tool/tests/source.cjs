const { test } = require('node:test');
const assert = require('node:assert/strict');
let source = {};
try {
  source = require('../dist/source-data');
} catch {}
const base =
  'https://console.volcengine.com/api/top/dramart/cn-beijing/2026-01-12/';
const template = (action, scope = {}) =>
  source.learnListRequest(`${base}${action}`, 'POST', {
    PageIndex: 1,
    PageSize: 2,
    ...scope,
  });
const result = (Items, Total = Items.length) => ({ Result: { Items, Total } });

test('pagination proves total coverage and rejects repeating or uncounted pages', async () => {
  assert.equal(typeof source.readAllPages, 'function');
  const calls = [];
  const rows = await source.readAllPages(
    template('ListTeams'),
    {},
    async (_url, body) => {
      calls.push(body);
      return result(
        body.PageIndex === 1
          ? [{ TeamId: 't1' }, { TeamId: 't2' }]
          : [{ TeamId: 't3' }],
        3,
      );
    },
    'TeamId',
  );
  assert.equal(rows.length, 3);
  assert.deepEqual(
    calls.map((body) => body.PageIndex),
    [1, 2],
  );
  await assert.rejects(
    source.readAllPages(
      template('ListTeams'),
      {},
      async () => result([{ TeamId: 't1' }], 2),
      'TeamId',
    ),
    /重复|完整/,
  );
  await assert.rejects(
    source.readAllPages(
      template('ListTeams'),
      {},
      async () => ({ Result: { Items: [] } }),
      'TeamId',
    ),
    /Total|总数/,
  );
});

test('only observed read-only contracts may be replayed, with all search filters removed', () => {
  assert.equal(typeof source.learnListRequest, 'function');
  assert.equal(
    source.learnListRequest(`${base}GetScript`, 'POST', {}),
    undefined,
  );
  assert.equal(
    source.learnListRequest('https://evil.example/ListTeams', 'POST', {}),
    undefined,
  );
  assert.equal(
    source.learnListRequest(`${base}ListTeams`, 'GET', {}),
    undefined,
  );
  assert.equal(
    source.learnListRequest(`${base}ListTeams`, 'POST', { Page: 1 }),
    undefined,
  );
  assert.equal(
    source.learnListRequest(`${base}ListTeams`, 'POST', {
      PageIndex: 1,
      PageSize: 10,
      UnknownScope: 'x',
    }),
    undefined,
  );
  const learned = template('ListProjects', {
    TeamId: 'old',
    Filters: { Name: 'search' },
  });
  assert.deepEqual(source.pageBody(learned, { TeamId: 't' }, 1), {
    PageIndex: 1,
    PageSize: 2,
    TeamId: 't',
    Filters: {},
  });
});

test('names-only projection retains distinct variants and parent IDs without prompt or images', () => {
  assert.equal(typeof source.projectAssetNames, 'function');
  const names = source.projectAssetNames('CHARACTER', [
    {
      CharacterId: 'a',
      Meta: { Name: '主角', Prompt: 'SECRET' },
      Images: ['SECRET'],
      Variants: [
        { CharacterId: 'v', Meta: { Name: '雨衣' }, Prompt: 'SECRET' },
      ],
    },
    { CharacterId: 'w', ParentCharacterId: 'a', Meta: { Name: '冬装' } },
  ]);
  assert.deepEqual(names, [
    { sourceAssetId: 'a', name: '主角', type: 'CHARACTER' },
    {
      sourceAssetId: 'v',
      sourceParentId: 'a',
      name: '雨衣',
      type: 'CHARACTER',
    },
    {
      sourceAssetId: 'w',
      sourceParentId: 'a',
      name: '冬装',
      type: 'CHARACTER',
    },
  ]);
  assert.ok(!JSON.stringify(names).includes('SECRET'));
  assert.throws(
    () =>
      source.projectAssetNames('CHARACTER', [{ Meta: { Name: 'missing id' } }]),
    /ID|身份/,
  );
});

test('visited project and script observations cannot substitute for all-team discovery', async () => {
  assert.equal(typeof source.collectAllProjects, 'function');
  await assert.rejects(
    source.collectAllProjects(
      [template('ListProjects', { TeamId: 'visited' })],
      async () => result([]),
    ),
    /团队|ListTeams/,
  );
});

test('all teams and their projects are enumerated before assets, failure never returns a partial collection', async () => {
  assert.equal(typeof source.collectAllProjects, 'function');
  const templates = [
    template('ListTeams'),
    template('ListProjects', { TeamId: 'observed' }),
    template('ListScripts', { TeamId: 'observed', ProjectId: 'observed' }),
  ];
  const requests = [];
  const fetcher = async (url, body) => {
    const action = url.split('/').at(-1);
    requests.push({ action, body });
    if (action === 'ListTeams')
      return result([{ TeamId: 't1' }, { TeamId: 't2' }]);
    if (action === 'ListProjects')
      return result([
        {
          ProjectId: `p-${body.TeamId}`,
          ScriptId: `s-${body.TeamId}`,
          Meta: { Name: body.TeamId },
          ScriptContent: 'SECRET',
        },
      ]);
    if (action === 'ListCharacters')
      return result([
        { CharacterId: `c-${body.TeamId}`, Meta: { Name: '角色' } },
      ]);
    if (action === 'ListScripts')
      return result([{ ScriptId: `s-${body.TeamId}` }]);
    return result([]);
  };
  const projects = await source.collectAllProjects(templates, fetcher);
  assert.equal(projects.length, 2);
  assert.equal(projects[1].sourceTeamId, 't2');
  assert.equal(projects[1].assets[0].name, '角色');
  assert.ok(!JSON.stringify(projects).includes('SECRET'));
  assert.equal(requests.filter((r) => r.action === 'ListProjects').length, 2);
  assert.equal(requests.filter((r) => r.action === 'ListScripts').length, 2);
  await assert.rejects(
    source.collectAllProjects(templates, async (url, body) =>
      url.endsWith('ListScenes')
        ? Promise.reject(new Error('expired'))
        : fetcher(url, body),
    ),
    /expired/,
  );
});

test('unknown project identity and missing script enumeration fail with an actionable contract error', async () => {
  assert.equal(typeof source.collectAllProjects, 'function');
  const templates = [
    template('ListTeams'),
    template('ListProjects', { TeamId: 't' }),
  ];
  await assert.rejects(
    source.collectAllProjects(templates, async (url) =>
      result(
        url.endsWith('ListTeams')
          ? [{ TeamId: 't' }]
          : [{ ProjectId: 'p', ScriptId: 'default', Name: 'P' }],
      ),
    ),
    /ListScripts|ScriptId/,
  );
});

test('expired source sessions are recognized even when the server returns HTTP 200', () => {
  assert.equal(typeof source.authenticationExpired, 'function');
  assert.equal(
    source.authenticationExpired({
      ResponseMetadata: {
        Error: { Code: 'SessionExpired', Message: 'SECRET' },
      },
    }),
    true,
  );
  assert.equal(
    source.authenticationExpired({ Error: { Code: 'Unauthorized' } }),
    true,
  );
  assert.equal(source.authenticationExpired(result([])), false);
});

test('script lists are fully enumerated per project and scopes never cross teams', async () => {
  const discovered = [];
  const templates = [
    template('ListTeams'),
    template('ListProjects', {
      Filters: { TeamId: 'observed', Name: 'filtered' },
    }),
    template('ListScripts', {
      TeamId: 'observed',
      Filters: { ProjectId: 'observed' },
    }),
  ];
  const projects = await source.collectAllProjects(
    templates,
    async (url, body) => {
      if (url.endsWith('ListTeams')) return result([{ TeamId: 't' }]);
      if (url.endsWith('ListProjects')) {
        assert.deepEqual(body.Filters, { TeamId: 't' });
        return result([{ ProjectId: 'p', ScriptId: 'default', Name: 'P' }]);
      }
      if (url.endsWith('ListScripts')) {
        assert.equal(body.TeamId, 't');
        assert.equal(body.Filters.ProjectId, 'p');
        return result(
          body.PageIndex === 1
            ? [{ ScriptId: 's1' }, { ScriptId: 's2' }]
            : [{ ScriptId: 's3' }],
          3,
        );
      }
      return result([]);
    },
    (count) => discovered.push(count),
  );
  assert.deepEqual(
    projects.map((project) => project.sourceScriptId),
    ['s1', 's2', 's3'],
  );
  assert.deepEqual(discovered, [1]);
  await assert.rejects(
    source.collectAllProjects(templates, async (url) =>
      result(
        url.endsWith('ListTeams')
          ? [{ TeamId: 't' }]
          : [{ ProjectId: 'p', TeamId: 'different', ScriptId: 's', Name: 'P' }],
      ),
    ),
    /团队/,
  );
});

test('changing totals and early termination cannot be mistaken for a complete snapshot', async () => {
  await assert.rejects(
    source.readAllPages(
      template('ListTeams'),
      {},
      async (_url, body) =>
        body.PageIndex === 1
          ? result([{ TeamId: 't1' }], 2)
          : result([{ TeamId: 't2' }], 3),
      'TeamId',
    ),
    /变化/,
  );
  await assert.rejects(
    source.readAllPages(
      template('ListTeams'),
      {},
      async () => result([], 1),
      'TeamId',
    ),
    /不完整/,
  );
});

test('independent secure browser login and session expiry update collector state without exposing credentials', async () => {
  const { EventEmitter } = require('node:events');
  const Module = require('node:module');
  const original = Module._load;
  let browser;
  class FakeWindow extends EventEmitter {
    constructor(options) {
      super();
      browser = this;
      this.options = options;
      this.webContents = new EventEmitter();
      this.webContents.session = {
        setPermissionRequestHandler: (handler) => {
          this.permissionRequest = handler;
        },
        setPermissionCheckHandler: (handler) => {
          this.permissionCheck = handler;
        },
      };
      this.webContents.debugger = new EventEmitter();
      this.webContents.debugger.attach = () => {};
      this.webContents.debugger.sendCommand = async (command) =>
        command === 'Network.getResponseBody'
          ? { body: JSON.stringify(result([])) }
          : {};
      this.webContents.setWindowOpenHandler = () => {};
      this.webContents.getURL = () =>
        'https://console.volcengine.com/dramart/projectlist/';
      this.webContents.executeJavaScript = async (script) => {
        assert.ok(script.includes("credentials: 'include'"));
        assert.ok(!script.includes('document.cookie'));
        return { status: 401 };
      };
    }
    async loadURL() {}
    isDestroyed() {
      return false;
    }
    show() {}
    focus() {}
    destroy() {
      this.destroyed = true;
    }
  }
  let DramartCollector;
  try {
    Module._load = function (request, ...args) {
      return request === 'electron'
        ? { BrowserWindow: FakeWindow }
        : original.call(this, request, ...args);
    };
    delete require.cache[require.resolve('../dist/dramart')];
    ({ DramartCollector } = require('../dist/dramart'));
  } finally {
    Module._load = original;
  }
  const collector = new DramartCollector(() => {});
  await collector.login();
  assert.equal(browser.permissionCheck(), false);
  let permissionAllowed;
  browser.permissionRequest({}, 'camera', (allowed) => {
    permissionAllowed = allowed;
  });
  assert.equal(permissionAllowed, false);
  let redirected = false;
  browser.webContents.emit(
    'will-redirect',
    {
      preventDefault: () => {
        redirected = true;
      },
    },
    'file:///secret',
  );
  assert.equal(redirected, true);
  assert.deepEqual(browser.options.webPreferences, {
    partition: 'persist:dramart-source-v1',
    sandbox: true,
    contextIsolation: true,
    nodeIntegration: false,
    webSecurity: true,
  });
  for (const [url, method] of [
    [`${base}SaveScript`, 'POST'],
    ['https://unrelated.example/api/ListTeams', 'POST'],
    [`${base}ListTeams`, 'GET'],
  ]) {
    let bodyReads = 0;
    const request = { url, method };
    Object.defineProperty(request, 'postData', {
      get() {
        bodyReads++;
        throw new Error('Unrelated screenplay body must never be inspected');
      },
    });
    browser.webContents.debugger.emit(
      'message',
      {},
      'Network.requestWillBeSent',
      { requestId: url, request },
    );
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(bodyReads, 0);
    assert.equal(collector.state().error, undefined);
  }
  for (const action of ['ListTeams', 'ListProjects', 'ListScripts']) {
    browser.webContents.debugger.emit(
      'message',
      {},
      'Network.requestWillBeSent',
      {
        requestId: action,
        request: {
          url: `${base}${action}`,
          method: 'POST',
          postData: JSON.stringify({
            PageIndex: 1,
            PageSize: 2,
            ...(action === 'ListProjects' ? { TeamId: 't' } : {}),
            ...(action === 'ListScripts'
              ? { TeamId: 't', ProjectId: 'p' }
              : {}),
          }),
        },
      },
    );
    browser.webContents.debugger.emit(
      'message',
      {},
      'Network.loadingFinished',
      { requestId: action, encodedDataLength: 10 },
    );
    await new Promise((resolve) => setImmediate(resolve));
  }
  assert.equal(collector.state().loggedIn, true);
  await assert.rejects(collector.collect(), /登录已失效/);
  assert.equal(collector.state().loggedIn, false);
  assert.equal(collector.state().syncing, false);
  assert.equal(collector.state().lastSync, undefined);
  collector.close();
  assert.equal(browser.destroyed, true);
});
