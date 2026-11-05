import type { SourceName, SourceProjectIdentity } from './workspace-shared';

export interface SourceCollection extends SourceProjectIdentity {
  assets: SourceName[];
}
export interface SourceStatus {
  loggedIn: boolean;
  syncing: boolean;
  discoveredProjects: number;
  skippedUnnamedVariants?: number;
  lastSync?: string;
  error?: string;
}
export interface SourceCollector {
  state(): SourceStatus;
  login(): Promise<void>;
  collect(): Promise<SourceCollection[]>;
  close(): void;
}
