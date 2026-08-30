type Dictionary = Record<string, string>;

const STATUS: Dictionary = {
  ACTIVE: '正常',
  DISABLED: '已停用',
  ENABLED: '已启用',
  PENDING: '待处理',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
  CANCELLED: '已取消',
  TIMED_OUT: '已超时',
  PENDING_REVIEW: '待审核',
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  PROCESSING: '处理中',
  READY: '待提交',
  DELETED: '已删除',
  PUBLISHED: '已发布',
  OFF_SALE: '已下架',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  REFUNDED: '已退款',
  GRANTED: '已发放',
  UNTESTED: '未测试',
  REMOVED: '已移除',
  PARTIAL_FAILED: '部分失败',
  PENDING_ANALYSIS: '等待生成',
  ANALYZING: '生成中',
  ANALYSIS_SUCCEEDED: '解析完成',
  PENDING_DRAFT: '等待历史草稿',
  DRAFT_GENERATING: '历史草稿生成中',
};

const SERVICE_TYPE: Dictionary = {
  TEXT: '文本',
  IMAGE: '图片',
  VIDEO: '视频',
  AUDIO: '音频',
  VOICE: '语音',
  VIDEO_UNDERSTANDING: '视频理解',
};

const METRIC: Dictionary = {
  CALL: '调用次数',
  INPUT_TOKEN: '输入 Token 数',
  OUTPUT_TOKEN: '输出 Token 数',
  IMAGE: '图片数',
  VIDEO_SECOND: '视频秒数',
  AUDIO_SECOND: '音频秒数',
  CHARACTER: '字符数',
};

const ENTITLEMENT: Dictionary = {
  ONE_TIME_POINTS: '一次性积分',
  PERIODIC_POINTS: '周期积分',
  GLOBAL_DISCOUNT: '全局折扣',
};

const FIELD: Dictionary = {
  code: '编码',
  modelCode: '真实模型编码',
  providerCode: '服务商编码',
  agentCode: '智能体编码',
  skillCode: '技能编码',
  baseUrl: '接口地址',
  defaultBaseUrl: '默认接口地址',
  apiKey: '访问密钥',
  configJson: '扩展配置',
  projectId: '项目编号',
  tenantId: '团队编号',
  userId: '用户编号',
  taskId: '任务编号',
  executionId: '执行编号',
  errorCode: '错误码',
};

const lookup = (dictionary: Dictionary, value?: string | null) =>
  value && dictionary[value] ? dictionary[value] : '其他';

export const statusText = (value?: string | null) => lookup(STATUS, value);
export const serviceTypeText = (value?: string | null) =>
  lookup(SERVICE_TYPE, value);
export const metricText = (value?: string | null) => lookup(METRIC, value);
export const entitlementTypeText = (value?: string | null) =>
  lookup(ENTITLEMENT, value);
export const fieldText = (value?: string | null) => lookup(FIELD, value);

export const displayFieldValue = (
  kind: 'status' | 'serviceType' | 'metric' | 'entitlement' | 'field',
  value?: string | null,
) => {
  if (kind === 'status') return statusText(value);
  if (kind === 'serviceType') return serviceTypeText(value);
  if (kind === 'metric') return metricText(value);
  if (kind === 'entitlement') return entitlementTypeText(value);
  return fieldText(value);
};
