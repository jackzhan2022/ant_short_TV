export type AiServiceType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'VOICE';

export type AiProviderCode = 'OpenAI' | 'Gemini' | '火山' | 'MiniMax';

export type AiServiceStatus = 'ENABLED' | 'DISABLED';

export type AiServiceTestStatus = 'UNTESTED' | 'SUCCESS' | 'FAILED';

export interface AiServiceConfig {
  id: number;
  tenantId: number;
  name: string;
  provider: AiProviderCode;
  serviceType: AiServiceType;
  baseUrl: string;
  apiKey: string;
  model: string;
  endpoint?: string;
  queryEndpoint?: string;
  priority: number;
  isDefault: boolean;
  enabled: boolean;
  lastTestStatus: AiServiceTestStatus;
  lastTestMessage?: string;
  lastTestAt?: string;
  remark?: string;
  updatedAt: string;
}

export interface AiServiceConfigFormValues {
  name: string;
  provider: AiProviderCode;
  serviceType: AiServiceType;
  baseUrl: string;
  apiKey?: string;
  model: string;
  endpoint?: string;
  queryEndpoint?: string;
  priority: number;
  isDefault: boolean;
  enabled: boolean;
  remark?: string;
}

export interface AiProvider {
  id: number;
  name: string;
  code: AiProviderCode;
  supportedTypes: string;
  defaultBaseUrl?: string;
  recommendedModels?: string;
  description?: string;
  status: 'ENABLED' | 'DISABLED';
}

export const SERVICE_TYPE_TEXT: Record<AiServiceType, string> = {
  TEXT: '文本',
  IMAGE: '图片',
  VIDEO: '视频',
  VOICE: '语音',
};

export const PROVIDER_TEXT: Record<AiProviderCode, string> = {
  OpenAI: 'OpenAI',
  Gemini: 'Gemini',
  火山: '火山',
  MiniMax: 'MiniMax',
};

export const STATUS_TEXT: Record<AiServiceStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

export const TEST_STATUS_TEXT: Record<AiServiceTestStatus, string> = {
  UNTESTED: '未测试',
  SUCCESS: '测试成功',
  FAILED: '测试失败',
};
