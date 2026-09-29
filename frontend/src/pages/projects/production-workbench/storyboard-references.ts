import type { StoryboardPromptDocument, StoryboardPromptNode } from './service';

type Mention = Extract<StoryboardPromptNode, { type: 'mention' }>;
export type NamedImageReference = Mention & { assetName: string };

export const promptText = (document: StoryboardPromptDocument) =>
  document.nodes.map((node) => node.type === 'text' ? node.text : node.displayName).join('');

export const hasImageReference = (document: StoryboardPromptDocument) =>
  document.nodes.some((node) => node.type === 'mention' && node.mediaType === 'IMAGE');

export const hydratePromptDocument = (
  document: StoryboardPromptDocument | null | undefined,
  fallbackText: string,
  references: NamedImageReference[],
): StoryboardPromptDocument => {
  const nodes = document?.version === 2 && document.nodes?.length
    ? document.nodes
    : [{ type: 'text' as const, text: fallbackText }];
  const byName = new Map<string, NamedImageReference[]>();
  for (const reference of references) {
    byName.set(reference.assetName, [...(byName.get(reference.assetName) || []), reference]);
  }
  const hydrated = nodes.flatMap((node): StoryboardPromptNode[] => {
    if (node.type !== 'text') return [node];
    const result: StoryboardPromptNode[] = [];
    let cursor = 0;
    for (const match of node.text.matchAll(/<([^<>\n]+)>/g)) {
      const candidates = byName.get(match[1].trim()) || [];
      if (candidates.length !== 1) continue;
      if (match.index > cursor) result.push({ type: 'text', text: node.text.slice(cursor, match.index) });
      const { assetName: _assetName, ...mention } = candidates[0];
      result.push(mention);
      cursor = match.index + match[0].length;
    }
    if (cursor < node.text.length) result.push({ type: 'text', text: node.text.slice(cursor) });
    return result;
  });
  return { version: 2, nodes: hydrated };
};

export const withFirstFrameReference = (
  document: StoryboardPromptDocument,
  storyboardId: number,
  displayName: string,
): StoryboardPromptDocument => {
  if (hasImageReference(document)) return document;
  const mention: Mention = {
    type: 'mention', mediaType: 'IMAGE', sourceType: 'STORYBOARD_FIRST_FRAME',
    sourceId: storyboardId, displayName,
  };
  const nodes = [...document.nodes];
  const index = nodes.findIndex((node) => node.type === 'text' && node.text.includes('### 素材引用'));
  if (index >= 0) {
    const text = (nodes[index] as Extract<StoryboardPromptNode, { type: 'text' }>).text;
    const markerEnd = text.indexOf('### 素材引用') + '### 素材引用'.length;
    nodes.splice(index, 1,
      { type: 'text', text: `${text.slice(0, markerEnd)}\n` }, mention,
      { type: 'text', text: text.slice(markerEnd) },
    );
  } else {
    nodes.unshift(mention, { type: 'text', text: '\n' });
  }
  return { version: 2, nodes };
};
