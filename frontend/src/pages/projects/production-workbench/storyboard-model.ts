import type { StoryboardShot, StoryboardAssetReference, SaveStoryboardValues } from './service';
type StoryboardWithProps = StoryboardShot & { props?: string };
export type StoryboardReferenceDisplayFields = Pick<StoryboardShot, 'characters' | 'scene'> & { props: string };
type ClientKeyedStoryboardAssetReference = StoryboardAssetReference & { clientKey?: string };

export const splitNames = (value?: string | null) =>
  String(value || '')
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean);

export const getStoryboardPropNames = (storyboard: StoryboardShot) =>
  splitNames((storyboard as StoryboardWithProps).props);

export const getStoryboardProps = (storyboard: StoryboardShot) =>
  (storyboard as StoryboardWithProps).props || '';

export const getReferenceDisplayFields = (
  references: StoryboardAssetReference[],
): StoryboardReferenceDisplayFields => ({
  characters: references
    .filter((reference) => reference.assetType === 'CHARACTER')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
  scene: references
    .filter((reference) => reference.assetType === 'SCENE')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
  props: references
    .filter((reference) => reference.assetType === 'PROP')
    .map((reference) => reference.assetName || reference.sourceName)
    .filter(Boolean).join('、'),
});

const referencePersistenceIdentity = (reference: StoryboardAssetReference) => JSON.stringify([
  reference.assetType,
  reference.sortOrder,
]);

export const preserveReferenceClientKeys = (
  submitted: StoryboardAssetReference[],
  saved: StoryboardAssetReference[],
) => {
  const clientKeys = new Map<string, string[]>();
  for (const reference of submitted) {
    const clientKey = (reference as ClientKeyedStoryboardAssetReference).clientKey;
    if (!clientKey) continue;
    const identity = referencePersistenceIdentity(reference);
    clientKeys.set(identity, [...(clientKeys.get(identity) || []), clientKey]);
  }
  return saved.map((reference) => {
    const keys = clientKeys.get(referencePersistenceIdentity(reference));
    const clientKey = keys?.shift();
    return clientKey ? { ...reference, clientKey } : reference;
  });
};

export const getStoryboardScriptText = (storyboard: StoryboardShot) =>
  [
    storyboard.scene ? `▲${storyboard.scene}` : '',
    storyboard.visualDescription,
    storyboard.dialogue,
  ]
    .filter(Boolean)
    .join('\n');

export const parseStoryboardScriptText = (
  storyboard: StoryboardShot,
  scriptText: string,
) => {
  const lines = scriptText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  let scene = storyboard.scene;
  if (lines[0]?.startsWith('▲')) {
    scene =
      lines
        .shift()
        ?.replace(/^▲+\s*/, '')
        .trim() || storyboard.scene;
  }

  let dialogue = storyboard.dialogue;
  const dialogueIndex = lines.findLastIndex((line) =>
    /(?:VO|旁白)?[:：]|[“"].+[”"]/.test(line),
  );
  if (dialogueIndex >= 0) {
    dialogue = lines.splice(dialogueIndex, 1)[0];
  }

  return {
    scene,
    dialogue,
    visualDescription: lines.join('\n') || storyboard.visualDescription,
  };
};

export const getStoryboardPrompt = (storyboard: StoryboardShot) => {
  if (storyboard.videoPrompt?.trim()) {
    return storyboard.videoPrompt;
  }
  return [
    '画风：写实都市',
    '视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。',
    '### 素材引用',
    storyboard.characters ? `【人物】${storyboard.characters}` : '',
    storyboard.scene ? `【场景】${storyboard.scene}` : '',
    getStoryboardPropNames(storyboard).length
      ? `【道具】${getStoryboardPropNames(storyboard).join('、')}`
      : '',
    '### 画面描写',
    `镜头${storyboard.shotNo} ${storyboard.durationSeconds || 5}s`,
    storyboard.visualDescription,
    storyboard.dialogue,
  ]
    .filter(Boolean)
    .join('\n');
};

export const getStoryboardSavePayload = (
  storyboard: StoryboardShot,
  overrides: Partial<SaveStoryboardValues> = {},
): SaveStoryboardValues => ({
  visualDescription: storyboard.visualDescription,
  characters: storyboard.characters,
  dialogue: storyboard.dialogue,
  scene: storyboard.scene,
  props: getStoryboardProps(storyboard),
  durationSeconds: storyboard.durationSeconds,
  imagePrompt: storyboard.imagePrompt,
  videoPrompt: storyboard.videoPrompt,
  promptDocument: storyboard.promptDocument || undefined,
  ...overrides,
});
