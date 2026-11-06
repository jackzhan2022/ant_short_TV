import type { AiMatchOption, AiSuggestion } from './ai-match-types';
import type { Creation } from './shared';
import type { SourceProject, TargetProject } from './workspace-shared';

export function nextSourceProject(
  rows: SourceProject[],
  previous?: SourceProject,
  remembered?: number,
) {
  const active = rows.filter((row) => row.active);
  return (
    active.find((row) => row.id === previous?.id)?.id ??
    active.find(
      (row) =>
        previous &&
        row.connectionId === previous.connectionId &&
        row.sourceTeamId === previous.sourceTeamId &&
        row.sourceProjectId === previous.sourceProjectId,
    )?.id ??
    active.find((row) => row.id === remembered)?.id ??
    active[0]?.id
  );
}

export function queueCategory(status: Creation['status']) {
  if (status === 'queued') return 'waiting';
  if (status === 'succeeded') return 'done';
  if (status === 'stopped' || status === 'interrupted') return 'failed';
  return status;
}

export function aiChoiceKey(
  option: Pick<AiMatchOption, 'assetId' | 'variantId'>,
) {
  return `${option.assetId}:${option.variantId ?? 'base'}`;
}

export function initialAiChoices(suggestions: AiSuggestion[]) {
  const choices = new Map<number, string>();
  for (const suggestion of suggestions) {
    if (
      suggestion.status === 'suggested' &&
      suggestion.options.length === 1 &&
      suggestion.options[0].hasPrompt
    )
      choices.set(suggestion.sourceAssetId, aiChoiceKey(suggestion.options[0]));
  }
  return choices;
}

export function groupProjects(
  rows: SourceProject[],
  search = '',
  filter = 'all',
) {
  const groups = new Map<
    string,
    {
      key: string;
      name: string;
      scopes: SourceProject[];
      bound: number;
    }
  >();
  for (const row of rows) {
    if (!row.active) continue;
    const key = JSON.stringify([row.sourceTeamId, row.sourceProjectId]);
    let group = groups.get(key);
    if (!group) {
      group = { key, name: row.name, scopes: [], bound: 0 };
      groups.set(key, group);
    }
    group.scopes.push(row);
    if (row.targetProjectId) group.bound++;
  }
  const query = search.trim().toLocaleLowerCase();
  return [...groups.values()].filter(
    (group) =>
      group.name.toLocaleLowerCase().includes(query) &&
      (filter === 'all' ||
        (filter === 'unbound' && group.bound === 0) ||
        (filter === 'partial' &&
          group.bound > 0 &&
          group.bound < group.scopes.length) ||
        (filter === 'bound' && group.bound === group.scopes.length)),
  );
}

export function targetOptions(
  targets: TargetProject[],
  sourceName: string,
  search = '',
) {
  const query = search.trim().toLocaleLowerCase();
  return targets
    .filter((target) => target.name.toLocaleLowerCase().includes(query))
    .sort(
      (a, b) =>
        Number(b.name.trim() === sourceName.trim()) -
          Number(a.name.trim() === sourceName.trim()) ||
        a.name.localeCompare(b.name, 'zh-CN') ||
        a.id - b.id,
    );
}
