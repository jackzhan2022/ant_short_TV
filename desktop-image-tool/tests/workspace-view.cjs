const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  groupProjects,
  targetOptions,
  queueCategory,
  initialAiChoices,
  aiChoiceKey,
} = require('../dist/workspace-view');

const scope = (id, team, project, target = null) => ({
  id,
  sourceTeamId: team,
  sourceProjectId: project,
  sourceScriptId: `s${id}`,
  name: '同名项目',
  active: true,
  targetProjectId: target,
});

test('source project cards combine script scopes but preserve team and project identity', () => {
  const groups = groupProjects([
    scope(1, 'team-a', 'p', 11),
    scope(2, 'team-a', 'p'),
    scope(3, 'team-b', 'p'),
    scope(4, 'team-a', 'another'),
    { ...scope(5, 'team-a', 'hidden'), active: false },
  ]);
  assert.equal(groups.length, 3);
  assert.deepEqual(
    groups[0].scopes.map((row) => row.id),
    [1, 2],
  );
  assert.equal(groups[0].bound, 1);
  assert.notEqual(groups[0].key, groups[1].key);
});

test('source search and binding filters operate on whole projects rather than hiding unbound scopes', () => {
  const rows = [
    scope(1, 'a', 'p', 11),
    scope(2, 'a', 'p'),
    scope(3, 'b', 'p', 11),
    { ...scope(4, 'c', 'p'), name: 'Other' },
  ];
  assert.equal(groupProjects(rows, '同名', 'partial').length, 1);
  assert.equal(groupProjects(rows, '', 'bound').length, 1);
  assert.equal(groupProjects(rows, '', 'unbound')[0].name, 'Other');
  assert.equal(groupProjects(rows, 'missing').length, 0);
  assert.equal(groupProjects(rows, 'other')[0].scopes[0].id, 4);
});

test('target search prioritizes exact names without discarding duplicate targets or selecting one', () => {
  const targets = [
    { id: 1, name: '其他' },
    { id: 2, name: '同名项目' },
    { id: 3, name: '同名项目' },
    { id: 4, name: '同名项目 第二季' },
  ];
  assert.deepEqual(
    targetOptions(targets, '同名项目')
      .slice(0, 2)
      .map((row) => row.id),
    [2, 3],
  );
  assert.deepEqual(
    targetOptions(targets, '同名项目', '第二季').map((row) => row.id),
    [4],
  );
  assert.equal(targetOptions(targets, '同名项目', 'missing').length, 0);
  assert.equal(targets[0].id, 1);
});

test('queue filters preserve stopped and interrupted outcomes in failed without inventing progress', () => {
  assert.equal(queueCategory('queued'), 'waiting');
  assert.equal(queueCategory('running'), 'running');
  assert.equal(queueCategory('succeeded'), 'done');
  for (const status of ['failed', 'stopped', 'interrupted'])
    assert.equal(queueCategory(status), 'failed');
  assert.equal(queueCategory('cancelled'), 'cancelled');
});

test('AI review only preselects a single valid suggestion and preserves asset and variant identity', () => {
  const candidate = { assetId: 10, variantId: null, hasPrompt: true };
  const suggestions = [
    { sourceAssetId: 1, status: 'suggested', options: [candidate] },
    { sourceAssetId: 2, status: 'ambiguous', options: [candidate] },
    {
      sourceAssetId: 3,
      status: 'suggested',
      options: [candidate, { ...candidate, variantId: 20 }],
    },
    { sourceAssetId: 4, status: 'parentRequired', options: [candidate] },
    {
      sourceAssetId: 5,
      status: 'suggested',
      options: [{ ...candidate, hasPrompt: false }],
    },
    { sourceAssetId: 6, status: 'unmatched', options: [] },
  ];
  assert.deepEqual([...initialAiChoices(suggestions)], [[1, '10:base']]);
  assert.notEqual(
    aiChoiceKey(candidate),
    aiChoiceKey({ ...candidate, variantId: 20 }),
  );
});
