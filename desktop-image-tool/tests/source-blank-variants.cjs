const { test } = require('node:test');
const assert = require('node:assert/strict');
const { projectAssetNames } = require('../dist/source-data');
test('blank-named variants are counted and excluded from name matching without inventing names', () => {
  let skipped = 0;
  const rows = [
    {
      SceneId: 'base',
      Meta: { Name: 'room' },
      Variants: [
        { SceneId: 'blank', Meta: { Name: ' ' } },
        { SceneId: 'v', Meta: { Name: 'night' } },
      ],
    },
  ];
  const result = projectAssetNames('SCENE', rows, () => skipped++);
  assert.equal(skipped, 1);
  assert.deepEqual(
    result.map((row) => row.sourceAssetId),
    ['base', 'v'],
  );
  assert.equal(result[1].sourceParentId, 'base');
  assert.throws(
    () => projectAssetNames('SCENE', [{ SceneId: 'bad', Meta: { Name: 42 } }]),
    /名称/,
  );
});
