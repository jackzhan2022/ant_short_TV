import { describe, expect, it } from 'vitest';
import { hydratePromptDocument, withFirstFrameReference } from './storyboard-references';

describe('storyboard prompt references', () => {
  it('binds exact legacy asset markers and leaves unresolved names as text', () => {
    const document = hydratePromptDocument(
      { version: 2, nodes: [{ type: 'text', text: '【场景】\n<走廊>参考走廊。\n【道具】\n<手机>对应手机。' }] },
      '',
      [{ assetName: '走廊', type: 'mention', mediaType: 'IMAGE', sourceType: 'ASSET_VISUAL_VARIANT', sourceId: 12, displayName: '走廊主形象' }],
    );

    expect(document.nodes).toEqual([
      { type: 'text', text: '【场景】\n' },
      { type: 'mention', mediaType: 'IMAGE', sourceType: 'ASSET_VISUAL_VARIANT', sourceId: 12, displayName: '走廊主形象' },
      { type: 'text', text: '参考走廊。\n【道具】\n<手机>对应手机。' },
    ]);
  });

  it('uses a generated storyboard first frame when no image is bound', () => {
    const document = withFirstFrameReference(
      { version: 2, nodes: [{ type: 'text', text: '开头\n### 素材引用\n\n### 画面描写' }] },
      101,
      '分镜1首帧',
    );

    expect(document.nodes).toContainEqual({
      type: 'mention', mediaType: 'IMAGE', sourceType: 'STORYBOARD_FIRST_FRAME', sourceId: 101, displayName: '分镜1首帧',
    });
  });
});
