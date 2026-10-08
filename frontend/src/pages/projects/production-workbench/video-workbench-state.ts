import {
  type AiVideoTask,
  queryStoryboardWorkspace,
  type StoryboardShot,
  type StoryboardWorkspacePage,
} from './service';

export const STORYBOARD_PAGE_SIZE = 20;
const positiveInteger = (value: string | null) => {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
};

export function readStoryboardTarget(search: string) {
  const params = new URLSearchParams(search);
  const episodeNo = positiveInteger(params.get('episodeNo'));
  if (!episodeNo) return {};
  return {
    episodeNo,
    current: positiveInteger(params.get('current')) || 1,
    storyboardId: positiveInteger(params.get('storyboardId')),
  };
}

export const storyboardEditPath = (
  projectId: number,
  shot: StoryboardShot,
  index: number,
) =>
  `/projects/${projectId}/production-workbench/storyboard?episodeNo=${shot.episodeNo}&current=${Math.floor(index / STORYBOARD_PAGE_SIZE) + 1}&storyboardId=${shot.id}`;

export async function loadVideoEpisode(
  projectId: number,
  episodeNo?: number,
  obsolete: () => boolean = () => false,
): Promise<StoryboardWorkspacePage> {
  const first = await queryStoryboardWorkspace(projectId, {
    episodeNo,
    current: 1,
    pageSize: 100,
  });
  if (obsolete())
    throw new DOMException('Obsolete episode request', 'AbortError');
  if (!first.data) throw new Error('分镜加载失败');
  const data = first.data;
  const shots = [...data.storyboards];
  let current = 1;
  while (shots.length < data.total) {
    if (!shots.length) throw new Error('分镜分页不完整，请重新加载');
    const next = await queryStoryboardWorkspace(projectId, {
      episodeNo: data.episodeNo,
      current: ++current,
      pageSize: data.pageSize || 100,
    });
    if (obsolete())
      throw new DOMException('Obsolete episode request', 'AbortError');
    const ids = new Set(shots.map((shot) => shot.id));
    const additional =
      next.data?.storyboards?.filter((shot) => !ids.has(shot.id)) || [];
    if (!additional.length) throw new Error('分镜分页不完整，请重新加载');
    shots.push(...additional);
  }
  return { ...data, storyboards: shots };
}

export function getShotState(shot: StoryboardShot, tasks: AiVideoTask[]) {
  const src = shot.currentShotVideoUrl || shot.currentVideoUrl || undefined;
  const task = tasks
    .filter((item) => item.storyboardId === shot.id)
    .sort((a, b) => b.id - a.id)[0];
  const kind = src
    ? 'ready'
    : task && ['PENDING', 'SUBMITTING', 'GENERATING'].includes(task.status)
      ? 'generating'
      : task?.status === 'FAILED'
        ? 'failed'
        : 'pending';
  const labels = {
    ready: '已就绪',
    generating: '生成中',
    failed: '生成失败',
    pending: '待生成',
  };
  return {
    src,
    kind,
    label: labels[kind],
    error: task?.errorMessage,
    sourceLabel: shot.currentShotVideoUrl ? '单镜头合成结果' : '分镜原始视频',
  };
}

export const formatVideoTime = (seconds: number) => {
  const safe = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
  return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`;
};
