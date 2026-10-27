export type AvatarRecovery = {
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  phase: string;
  attempt?: number;
  maxAttempts?: number;
  retryCreationId?: number;
  retryStatus?: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED';
  errorMessage?: string;
};
export const avatarRecoveryPending = (recovery?: AvatarRecovery) =>
  recovery?.status === 'PENDING' ||
  recovery?.status === 'RUNNING' ||
  recovery?.retryStatus === 'QUEUED' ||
  recovery?.retryStatus === 'RUNNING';
