import { describe, expect, it } from 'vitest';
import type { CreationRecord } from './api';
import { newDraft, requestFromDraft, reuseDraft } from './draft';
import type { CreationModel } from './service';

const video: CreationModel = {
  id: 1,
  name: '视频',
  serviceType: 'VIDEO',
  isDefault: true,
  configStatus: 'configured',
  references: {},
  resolutions: [],
};
const constraint = '视频画面中禁止出现任何形式的字幕';

describe('subtitle constraint', () => {
  it('defaults on and prepends only to video submissions without mutating the draft', () => {
    const draft = { ...newDraft(video), prompt: '  雨夜街景  ' };
    expect(draft.suppressSubtitles).toBe(true);
    expect(requestFromDraft(draft, 'VIDEO', 'one').prompt).toBe(
      `${constraint}\n雨夜街景`,
    );
    expect(requestFromDraft(draft, 'IMAGE', 'two').prompt).toBe('雨夜街景');
    expect(draft.prompt).toBe('  雨夜街景  ');
    expect(
      requestFromDraft({ ...draft, suppressSubtitles: false }, 'VIDEO', 'three')
        .prompt,
    ).toBe('雨夜街景');
  });

  it('does not duplicate an existing prefix', () => {
    const draft = { ...newDraft(video), prompt: `${constraint}\n雨夜街景` };
    expect(requestFromDraft(draft, 'VIDEO', 'one').prompt).toBe(draft.prompt);
  });

  it.each([
    true,
    false,
  ])('restores the constraint state on reuse: %s', (enabled) => {
    const record: CreationRecord = {
      id: 9,
      mediaType: 'VIDEO',
      status: 'SUCCEEDED',
      prompt: enabled ? `${constraint}\n雨夜街景` : '雨夜街景',
      creatorId: 8,
      creatorName: '同事',
      createdAt: '',
      modelId: 1,
      modelName: '视频',
      parameters: {},
      references: [],
      outputs: [],
    };
    const draft = reuseDraft(record, [video]);
    expect(draft.prompt).toBe('雨夜街景');
    expect(draft.suppressSubtitles).toBe(enabled);
    expect(requestFromDraft(draft, 'VIDEO', 'reuse').prompt).toBe(
      record.prompt,
    );
    expect(
      requestFromDraft({ ...draft, suppressSubtitles: false }, 'VIDEO', 'off')
        .prompt,
    ).toBe('雨夜街景');
  });
});
