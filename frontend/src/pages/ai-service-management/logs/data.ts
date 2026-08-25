export type AiCallLogServiceType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'VOICE';

export type AiCallLogStatus = 'SUCCESS' | 'FAILED' | 'CANCELED';

export interface AiCallLog {
  id: number;
  tenantId: number;
  userId: number;
  taskId?: number;
  modelId?: number;
  providerId?: number;
  provider?: string;
  serviceType: AiCallLogServiceType;
  model?: string;
  businessScene: string;
  requestSummary?: string;
  responseSummary?: string;
  status: AiCallLogStatus;
  errorMessage?: string;
  durationMs: number;
  createdAt: string;
}

export interface AiCallLogQueryParams {
  current?: number;
  pageSize?: number;
  serviceType?: AiCallLogServiceType;
  status?: AiCallLogStatus;
  businessScene?: string;
}

export interface AiCallLogPage {
  records: AiCallLog[];
  total: number;
  current: number;
  pageSize: number;
}

export const SERVICE_TYPE_TEXT: Record<AiCallLogServiceType, string> = {
  TEXT: '文本',
  IMAGE: '图片',
  VIDEO: '视频',
  VOICE: '语音',
};

export const STATUS_TEXT: Record<AiCallLogStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
};
