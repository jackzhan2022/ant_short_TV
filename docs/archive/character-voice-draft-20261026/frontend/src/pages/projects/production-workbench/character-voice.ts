export type CharacterVoiceSetting = {
  characterId: number;
  storyboardId: number;
  variantId: number;
  voiceId: string;
  voiceName?: string | null;
};

export const effectiveCharacterVoice = (
  settings: readonly CharacterVoiceSetting[], characterId: number | null | undefined,
  storyboardId: number, variantId?: number | null,
) => {
  const override = settings.find((setting) => setting.characterId === characterId
    && setting.storyboardId === storyboardId && setting.variantId === (variantId || 0));
  if (override) return { ...override, inherited: false };
  const defaults = settings.find((setting) => setting.characterId === characterId
    && setting.storyboardId === 0 && setting.variantId === 0);
  return defaults ? { ...defaults, inherited: true } : undefined;
};
