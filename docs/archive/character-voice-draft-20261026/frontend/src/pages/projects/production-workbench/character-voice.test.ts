import { describe, expect, it } from 'vitest';
import { effectiveCharacterVoice, type CharacterVoiceSetting } from './character-voice';

const defaults: CharacterVoiceSetting = { characterId: 1, storyboardId: 0, variantId: 0, voiceId: 'role-voice', voiceName: '沉稳男声' };
const override: CharacterVoiceSetting = { characterId: 1, storyboardId: 10, variantId: 11, voiceId: 'shot-voice', voiceName: '少年男声' };

describe('character voice inheritance', () => {
  it('inherits the character voice for every look and uses an exact shot/look override first', () => {
    const settings = [defaults, override];
    expect(effectiveCharacterVoice(settings, 1, 10, 11)).toEqual({ ...override, inherited: false });
    expect(effectiveCharacterVoice(settings, 1, 10, 12)).toEqual({ ...defaults, inherited: true });
    expect(effectiveCharacterVoice(settings, 1, 20, 11)).toEqual({ ...defaults, inherited: true });
    expect(effectiveCharacterVoice(settings, 1, 10, null)).toEqual({ ...defaults, inherited: true });
    expect(effectiveCharacterVoice(settings, 2, 10, 11)).toBeUndefined();
  });
  it('restores inheritance after the override is removed and restores the same values after reload', () => {
    expect(effectiveCharacterVoice(JSON.parse(JSON.stringify([defaults, override])), 1, 10, 11)?.voiceId).toBe('shot-voice');
    expect(effectiveCharacterVoice([defaults], 1, 10, 11)?.voiceId).toBe('role-voice');
  });
});
