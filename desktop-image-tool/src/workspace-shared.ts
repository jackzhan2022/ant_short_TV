import type {
  AiApplyInput,
  AiApplyResult,
  AiMatchReview,
  AiModelInput,
  AiModelSettings,
} from './ai-match-types';
import type { Creation, Format, Quality, QueueState, Result } from './shared';

export interface Team {
  id: number;
  name: string;
  status: string;
}
export interface TargetProject {
  id: number;
  name: string;
}
export interface SourceProject {
  id: number;
  connectionId: string;
  name: string;
  sourceTeamId: string;
  sourceProjectId: string;
  sourceScriptId: string;
  targetProjectId: number | null;
  targetProjectName: string | null;
  active: boolean;
  syncedAt: string;
}
export type AssetType = 'CHARACTER' | 'SCENE' | 'PROP';
export interface SourceName {
  sourceAssetId: string;
  sourceParentId?: string | null;
  name: string;
  type: AssetType;
}
export interface SourceAsset extends SourceName {
  id: number;
  active: boolean;
  targetAssetId: number | null;
  targetVariantId: number | null;
  targetName: string | null;
  matchStatus: string;
  hasPrompt: boolean;
}
export interface Candidate {
  assetId: number;
  variantId: number | null;
  name: string;
  type: AssetType;
  parentName: string | null;
  hasPrompt: boolean;
}
export interface AssetPrompt {
  sourceProjectId: number;
  sourceProjectName: string;
  sourceParentName: string | null;
  sourceAssetId: number;
  name: string;
  type: AssetType;
  projectName: string;
  targetProjectId: number;
  targetAssetId: number;
  targetVariantId: number | null;
  prompt: string;
}
export interface SourceProjectIdentity {
  sourceTeamId: string;
  sourceProjectId: string;
  sourceScriptId: string;
  name: string;
}
export interface SourceSnapshot extends SourceProjectIdentity {
  connectionId: string;
  complete: boolean;
  assets: SourceName[];
}
export interface SyncStatus {
  phase: 'idle' | 'collecting' | 'uploading' | 'complete' | 'failed';
  totalScopes: number;
  uploadedScopes: number;
  failedScopes: number;
  uploadedProjects: number;
  automaticPaused: boolean;
  lastSuccessAt?: string;
  nextRunAt?: string;
  error?: string;
}
export interface LoginStatus {
  phase: 'unknown' | 'checking' | 'connected' | 'signedOut' | 'error';
  checkedAt?: string;
  message?: string;
  loginOpen?: boolean;
}
export interface WorkspaceState {
  cloudUrl: string;
  teams: Team[];
  tenantId?: number;
  teamName?: string;
  authenticated: boolean;
  cloudConnection: LoginStatus;
  error?: string;
  projects: SourceProject[];
  targets: TargetProject[];
  autoSyncMinutes: number;
  syncStatus: SyncStatus;
  source: {
    connection: LoginStatus;
    loggedIn: boolean;
    syncing: boolean;
    discoveredProjects: number;
    skippedUnnamedVariants?: number;
    lastSync?: string;
    error?: string;
  };
  queue: QueueState;
}
export interface BatchInput {
  assetIds: number[];
  model: string;
  size: string;
  quality: Quality;
  format: Format;
}
export interface WorkspaceAPI {
  aiSettings(): Promise<Result<AiModelSettings>>;
  configureAi(input: AiModelInput): Promise<Result<AiModelSettings>>;
  aiMatch(projectId: number): Promise<Result<AiMatchReview>>;
  aiApply(input: AiApplyInput): Promise<Result<AiApplyResult>>;
  aiCancel(reviewId?: string): Promise<Result<void>>;
  state(refresh?: boolean): Promise<Result<WorkspaceState>>;
  configure(input: {
    cloudUrl?: string;
    autoSyncMinutes: number;
  }): Promise<Result<WorkspaceState>>;
  loginCloud(): Promise<Result<void>>;
  openProject(id: number): Promise<Result<void>>;
  selectTeam(id: number): Promise<Result<WorkspaceState>>;
  loginSource(): Promise<Result<void>>;
  checkSource(): Promise<Result<WorkspaceState>>;
  sync(): Promise<Result<WorkspaceState>>;
  assets(id: number): Promise<Result<SourceAsset[]>>;
  bind(input: {
    id: number;
    projectId: number;
  }): Promise<Result<WorkspaceState>>;
  match(id: number): Promise<Result<SourceAsset[]>>;
  candidates(input: {
    id: number;
    type: AssetType;
    sourceParentId?: string;
  }): Promise<Result<Candidate[]>>;
  resolve(input: {
    id: number;
    assetId: number;
    variantId?: number;
  }): Promise<Result<SourceAsset>>;
  prompts(ids: number[]): Promise<Result<AssetPrompt[]>>;
  batch(input: BatchInput): Promise<Result<Creation[]>>;
  queue(input: {
    action: 'pause' | 'resume' | 'cancel' | 'retry' | 'limit';
    id?: string;
    limit?: number;
  }): Promise<Result<QueueState>>;
  onChanged(callback: () => void): () => void;
}
