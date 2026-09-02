import type { ReviewIssue, ReviewProject } from './service';

export type LibraryStateKey =
  | 'NOT_REVIEWED'
  | 'RUNNING'
  | 'ACTION_REQUIRED'
  | 'READY_FOR_REVIEW'
  | 'COMPLETED';

export type LibraryState = {
  key: LibraryStateKey;
  label: string;
  actionLabel: string;
  outstandingIssueCount: number;
};

type ProjectReviewSnapshot = {
  project: ReviewProject;
  task?: {
    status: string;
    issues: Array<Pick<ReviewIssue, 'manuallyResolved'>>;
  };
};

export const deriveLibraryState = ({ task }: ProjectReviewSnapshot): LibraryState => {
  if (!task) {
    return {
      key: 'NOT_REVIEWED',
      label: '未审核',
      actionLabel: '发起审核',
      outstandingIssueCount: 0,
    };
  }
  if (['PENDING', 'RUNNING'].includes(task.status)) {
    return {
      key: 'RUNNING',
      label: '审核中',
      actionLabel: '查看进度',
      outstandingIssueCount: 0,
    };
  }

  const outstandingIssueCount = task.issues.filter(
    (issue) => !issue.manuallyResolved,
  ).length;
  if (outstandingIssueCount > 0) {
    return {
      key: 'ACTION_REQUIRED',
      label: '待处理',
      actionLabel: '处理问题',
      outstandingIssueCount,
    };
  }
  if (task.issues.length > 0) {
    return {
      key: 'READY_FOR_REVIEW',
      label: '待复审',
      actionLabel: '发起复审',
      outstandingIssueCount: 0,
    };
  }
  return {
    key: 'COMPLETED',
    label: '审核完成',
    actionLabel: '查看报告',
    outstandingIssueCount: 0,
  };
};

export const filterLibraryProjects = (
  projects: ReviewProject[],
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
