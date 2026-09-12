import type { ReviewProject, ReviewProjectSummary } from './service';

export type LibraryStateKey = 'NOT_REVIEWED' | 'RUNNING' | 'COMPLETED';

export type LibraryState = {
  key: LibraryStateKey;
  label: string;
  actionLabel: string;
};

type ProjectReviewSnapshot = {
  project: LibraryProject;
  task?: {
    status: string;
    reportMarkdown?: string | null;
  };
};

export type LibraryProject = ReviewProjectSummary &
  Partial<
    Pick<
      ReviewProject,
      'versionCount' | 'latestRoundNo' | 'reviewState' | 'actionLabel'
    >
  >;

export const deriveLibraryState = ({
  task,
}: ProjectReviewSnapshot): LibraryState => {
  if (!task) {
    return {
      key: 'NOT_REVIEWED',
      label: '未审核',
      actionLabel: '发起审核',
    };
  }
  if (['PENDING', 'RUNNING'].includes(task.status)) {
    return {
      key: 'RUNNING',
      label: '审核中',
      actionLabel: '查看进度',
    };
  }

  if (task.status !== 'COMPLETED' || !task.reportMarkdown?.trim()) {
    return { key: 'NOT_REVIEWED', label: '未审核', actionLabel: '重试审核' };
  }
  return {
    key: 'COMPLETED',
    label: '审核完成',
    actionLabel: '查看报告',
  };
};

export const libraryStateFromProject = (
  project: LibraryProject,
): LibraryState => {
  if (project.reviewState) {
    const labels: Record<LibraryStateKey, string> = {
      NOT_REVIEWED: '未审核',
      RUNNING: '审核中',
      COMPLETED: '审核完成',
    };
    const actions: Record<LibraryStateKey, string> = {
      NOT_REVIEWED: '发起审核',
      RUNNING: '查看进度',
      COMPLETED: '查看报告',
    };
    return {
      key: project.reviewState,
      label: labels[project.reviewState],
      actionLabel: project.actionLabel ?? actions[project.reviewState],
    };
  }
  return deriveLibraryState({ project });
};

export const filterLibraryProjects = (
  projects: LibraryProject[],
  states: Map<number, Pick<LibraryState, 'key'>>,
  query: string,
  state?: LibraryStateKey,
) => {
  const normalizedQuery = query.trim().toLowerCase();
  return projects.filter((project) => {
    const matchesQuery =
      !normalizedQuery || project.name.toLowerCase().includes(normalizedQuery);
    return matchesQuery && (!state || states.get(project.id)?.key === state);
  });
};
