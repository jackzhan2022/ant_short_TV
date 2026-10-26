import { readFileSync } from 'node:fs';
import { describe, expect, it, vi } from 'vitest';
import {
  type AssetDefinition,
  buildManualAssetPrompt,
  fieldIssue,
  normalizeAssetName,
} from './manual-assets';

vi.mock('@/services/point-estimate', () => ({
  pointEstimatedRequest: vi.fn(),
}));
const definitions = JSON.parse(
  readFileSync(
    '../backend/src/main/resources/manual-assets/configuration.json',
    'utf8',
  ),
) as Record<string, AssetDefinition>;
describe('manual asset configuration', () => {
  it('matches Java letter/digit identity normalization', () => {
    expect(normalizeAssetName('A-Ｂ·十二３Ⅷ½')).toBe('aｂ十二３');
  });

  it('uses the exact AI task, 14 ordered character fields and neutral display', () => {
    const definition = definitions.CHARACTER;
    const values = Object.fromEntries(
      definition.fields.map((f) => [f.key, f.unknown || '具体外貌']),
    );
    const prompt = buildManualAssetPrompt(
      'CHARACTER',
      '苏禾',
      definition,
      values,
    );
    expect(definition.fields).toHaveLength(14);
    expect(prompt).toContain(definition.task);
    expect(prompt).toContain('- **脸型**: 具体外貌');
    expect(prompt.indexOf('**鞋子**')).toBeLessThan(prompt.indexOf('**性别**'));
    expect(prompt).toContain('自然站立、中性表情、双手自然下垂');
  });
  it('preserves fixed scene and prop requirements and previews unfilled fields', () => {
    for (const type of ['SCENE', 'PROP'] as const) {
      const prompt = buildManualAssetPrompt(
        type,
        '测试资产',
        definitions[type],
        {},
      );
      expect(prompt).toContain(definitions[type].task);
      expect(prompt).toContain('[待填写：');
    }
  });
  it('allows unknown only for designated fields and preserves explicit absence', () => {
    const fields = definitions.CHARACTER.fields;
    expect(fieldIssue(fields[0], '未明确')).toBeUndefined();
    const face = fields.find((f) => f.key === 'face');
    const earrings = fields.find((f) => f.key === 'earrings');
    if (!face || !earrings) throw new Error('缺少角色字段');
    for (const bad of [
      '',
      '未知',
      '未明确（待确认）',
      '待补充',
      '{值}',
      '...',
      '无依据',
      '未明确(待确认)',
    ])
      expect(fieldIssue(face, bad)).toBeTruthy();
    expect(
      fieldIssue(earrings, '无'),
    ).toBeUndefined();
    expect(fieldIssue(face, '长'.repeat(201))).toBeTruthy();
  });
});
