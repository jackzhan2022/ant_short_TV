import { request } from '@umijs/max';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type ReviewProject = {
  id: number;
  name: string;
  sourceFileName?: string | null;
  sourceType: string;
  currentVersionId?: number | null;
  lastTaskId?: number | null;
  status: string;
  versionCount: number;
  latestRoundNo: number;
  createdAt?: string;
  updatedAt?: string;
};

export type ReviewVersion = {
  id: number;
  projectId: number;
  versionNo: number;
  sourceType: string;
  fileName?: string | null;
  content: string;
  createdAt?: string;
};

export type ReviewHit = {
  id: number;
  hitNo: number;
  episodeNo?: number | null;
  sceneNo?: string | null;
  shotNo?: number | null;
  lineNo?: number | null;
  anchorLabel?: string | null;
  excerpt: string;
  entityName?: string | null;
  selected: boolean;
  replacementText?: string | null;
};

export type ReviewIssue = {
  id: number;
  taskId: number;
  scriptVersionId: number;
  roundNo: number;
  issueNo: string;
  dimension: string;
  severity: string;
  title: string;
  position: Record<string, unknown>;
  excerpt: string;
  problem: string;
  evidence: string[];
  suggestion: string;
  status: string;
  relatedIssueNo?: string | null;
  manuallyResolved: boolean;
  manuallyResolvedAt?: string | null;
  hits: ReviewHit[];
};

export type ReviewTask = {
  id: number;
  projectId: number;
  scriptVersionId: number;
  roundNo: number;
  reviewMode: string;
  selectedDimensions: string[];
  reviewScopeType: string;
  reviewScope: Record<string, unknown>;
  status: string;
  currentStage?: string | null;
  overallProgress: number;
  currentAction?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  workflowAgentCode?: string | null;
  workflowAgentRevision?: number | null;
  workflowAgentRunId?: number | null;
  workflowPhase?: 'QUICK' | 'DEEP_CHILD' | 'DEEP_AGGREGATION' | null;
  workflowAttemptNo?: number | null;
  fanoutSnapshotId?: number | null;
  aggregationRunId?: number | null;
  retryKind?: 'WHOLE_TASK' | 'FAILED_UNITS' | 'AGGREGATION_ONLY' | null;
  stale?: boolean;
  fanout?: {
    status: string;
    totalUnits: number;
    completedUnits: number;
    failedUnits: number;
    currentUnitId?: number | null;
    aggregationStatus?: string | null;
    units: Array<{
      id: number;
      unitNo: number;
      unitKey: string;
      status: string;
      candidateSaved: boolean;
      errorCode?: string | null;
      errorMessage?: string | null;
    }>;
  } | null;
  completedAt?: string | null;
  canceledAt?: string | null;
  summary?: {
    overallConclusion: string;
    overallScore: number;
    summary: string;
  } | null;
  issues: ReviewIssue[];
};

export type ReviewProjectDetail = {
  project: ReviewProject;
  versions: ReviewVersion[];
  tasks: ReviewTask[];
};

export type ReviewVersionDiffLine = {
  type: string;
  lineNo: number;
  beforeText?: string | null;
  afterText?: string | null;
};

export type ReviewVersionDiff = {
  fromVersionId: number;
  toVersionId: number;
  addedLines: number;
  removedLines: number;
  lines: ReviewVersionDiffLine[];
};

export type ReviewRoundHistory = {
  taskId: number;
  roundNo: number;
  status: string;
  reviewMode: string;
  issueCount: number;
  processedIssueCount: number;
  summary?: {
    overallConclusion: string;
    overallScore: number;
    summary: string;
  } | null;
  completedAt?: string | null;
};

export type ReviewIssueMapping = {
  issueId: number;
  issueNo: string;
  roundNo: number;
  status: string;
  relatedIssueNo?: string | null;
  dimension: string;
  title: string;
  hitCount: number;
  hitIds: number[];
};

export type ReviewVersionHistory = {
  project: ReviewProject;
  selectedVersion: ReviewVersion;
  versions: ReviewVersion[];
  diffLines: ReviewVersionDiff[];
  roundHistory: ReviewRoundHistory[];
  issueMappings: ReviewIssueMapping[];
};

export const queryReviewProjects = () =>
  request<ApiResponse<ReviewProject[]>>('/api/script-review/projects');

export const importReviewProject = async (
  name: string,
  content: string,
  file?: File,
) => {
  const data = new FormData();
  if (name.trim()) data.append('name', name.trim());
  if (content.trim()) data.append('content', content);
  if (file) data.append('file', file);
  return request<ApiResponse<ReviewProjectDetail>>(
    '/api/script-review/projects',
    {
      method: 'POST',
      data,
    },
  );
};

export const queryReviewProject = (projectId: number) =>
  request<ApiResponse<ReviewProjectDetail>>(
    `/api/script-review/projects/${projectId}`,
  );

export const saveReviewVersion = (
  projectId: number,
  content: string,
  fileName?: string,
) =>
  request<ApiResponse<ReviewVersion>>(
    `/api/script-review/projects/${projectId}/versions`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { content, fileName, sourceType: 'MANUAL_EDIT' },
    },
  );

export const createReviewTask = (
  projectId: number,
  values: {
    versionId?: number;
    reviewMode: string;
    selectedDimensions: string[];
    reviewScopeType: string;
    reviewScope?: Record<string, unknown>;
  },
) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    `/api/script-review/projects/${projectId}/tasks`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const cancelReviewTask = (taskId: number) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    `/api/script-review/tasks/${taskId}/cancel`,
    {
      method: 'POST',
    },
  );

export const retryReviewTask = (taskId: number, fullRegeneration = false) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    `/api/script-review/tasks/${taskId}/retry`,
    {
      method: 'POST',
      params: { fullRegeneration },
    },
  );

export const resolveReviewIssue = (issueId: number, note?: string) =>
  request<ApiResponse<ReviewIssue>>(
    `/api/script-review/issues/${issueId}/resolve`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: note ? { note } : {},
    },
  );

export const batchRepairReview = (
  taskId: number,
  values: {
    actionType: string;
    replacementFrom?: string;
    replacementTo?: string;
    insertionText?: string;
    deletionText?: string;
    selectedHitIds?: number[];
  },
) =>
  request<ApiResponse<ReviewTask>>(
    `/api/script-review/tasks/${taskId}/batch-repair`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const rollbackReviewVersion = (projectId: number, versionId: number) =>
  request<ApiResponse<ReviewVersion>>(
    `/api/script-review/projects/${projectId}/rollback`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { versionId },
    },
  );

export const queryReviewVersionHistory = (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<ReviewVersionHistory>>(
    `/api/script-review/projects/${projectId}/versions/${versionId}/history`,
  );

export const updateReviewTaskConfig = (
  taskId: number,
  values: {
    reviewMode?: string;
    selectedDimensions?: string[];
    reviewScopeType?: string;
    reviewScope?: Record<string, unknown>;
  },
) =>
  request<ApiResponse<ReviewTask>>(
    `/api/script-review/tasks/${taskId}/config`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const exportReviewReport = (
  projectId: number,
  versionId: number,
  exportType: string,
) =>
  request<ApiResponse<{ fileName: string; downloadUrl?: string | null }>>(
    `/api/script-review/projects/${projectId}/exports`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { versionId, exportType },
    },
  );
