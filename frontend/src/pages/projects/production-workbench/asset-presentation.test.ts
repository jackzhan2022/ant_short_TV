import { describe, expect, it } from 'vitest';
import {
  assetAppearanceSummary,
  assetMatchesGeneration,
} from './asset-presentation';
import type { CharacterAsset } from './service';

const character = (fields: Partial<CharacterAsset>) =>
  ({ id: 1, name: '角色', prompt: '', ...fields }) as CharacterAsset;

describe('asset presentation', () => {
  it('extracts appearance fields without exposing rendering instructions', () => {
    const text =
      '## 任务：生成正面及三视图\n角色描述:\n- **脸型**: 宽短脸\n- **发型**: 黑色短发\n- **上身着装**: 深蓝夹克\n- **下身着装**: 灰色长裤\n- **性别**: 男';
    expect(assetAppearanceSummary(text)).toBe('深蓝夹克；灰色长裤；黑色短发');
    expect(assetAppearanceSummary('圆脸，黄色上衣')).toBe('圆脸，黄色上衣');
    expect(assetAppearanceSummary('## 任务：画三视图\n请使用白色背景')).toBe(
      '',
    );
  });

  it('supports alternative markdown labels, CRLF and empty values', () => {
    expect(
      assetAppearanceSummary(
        '- **外观**：银色金属，边缘磨损\r\n- **用途**：开锁',
      ),
    ).toBe('银色金属，边缘磨损');
    expect(assetAppearanceSummary(undefined)).toBe('');
    expect(assetAppearanceSummary('  ')).toBe('');
  });

  it('keeps confirmation separate from image completion', () => {
    const pending = character({
      status: 'CONFIRMED',
      imagePreview: {
        generationStatus: 'NOT_STARTED',
      } as CharacterAsset['imagePreview'],
    });
    expect(assetMatchesGeneration(pending, 'NOT_STARTED')).toBe(true);
    expect(assetMatchesGeneration(pending, 'COMPLETED')).toBe(false);
    expect(assetMatchesGeneration(character({}), 'NOT_STARTED')).toBe(true);
  });

  it('uses the summary API priority for mixed variants without changing counts when details load', () => {
    const mixed = character({
      visual: {
        generationSummary: { COMPLETED: 1, FAILED: 1, NOT_STARTED: 1 },
        variants: [],
      } as unknown as CharacterAsset['visual'],
    });
    expect(assetMatchesGeneration(mixed, 'COMPLETED')).toBe(false);
    expect(assetMatchesGeneration(mixed, 'FAILED')).toBe(true);
    expect(assetMatchesGeneration(mixed, 'NOT_STARTED')).toBe(false);
    const summary = character({
      imagePreview: { generationStatus: 'FAILED' },
      mainImageThumbnailUrl: '/image.png',
    });
    expect(assetMatchesGeneration(summary, 'FAILED')).toBe(true);
    expect(assetMatchesGeneration(summary, 'COMPLETED')).toBe(false);
    expect(assetMatchesGeneration({ ...mixed, ...summary }, 'FAILED')).toBe(
      true,
    );
    expect(assetMatchesGeneration(mixed, 'GENERATING')).toBe(false);
    expect(
      assetMatchesGeneration(
        character({ mainImageThumbnailUrl: '/image.png' }),
        'COMPLETED',
      ),
    ).toBe(true);
    expect(assetMatchesGeneration(mixed, 'ALL')).toBe(true);
  });
});
