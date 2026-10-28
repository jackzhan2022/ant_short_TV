const enumLabels: Record<string, string> = {
  QUEUED: '排队中', PENDING: '待处理', RUNNING: '处理中', GENERATING: '生成中', PROCESSING: '处理中',
  SUBMITTING: '提交中', SETTLING: '结算中', RENDERING: '渲染中', ANALYZING: '分析中',
  RETRYING: '重试中', DRAFT: '草稿', CONFIRMED: '已确认',
  WAITING_USER: '待处理', SUCCEEDED: '已完成', SUCCESS: '已完成', COMPLETED: '已完成',
  PARTIAL: '部分完成', FAILED: '已失败', CANCELED: '已取消', CANCELLED: '已取消', TIMED_OUT: '已超时',
  NOT_STARTED: '未开始', IN_PROGRESS: '进行中', PAUSED: '已暂停', ARCHIVED: '已归档',
  ACTIVE: '有效', DELETED: '已删除', DISABLED: '已停用', ENABLED: '已启用', READY: '已就绪',
  IMAGE: '图片', VIDEO: '视频', AUDIO: '音频', CHARACTER: '角色', SCENE: '场景', PROP: '道具',
  REFERENCE_IMAGE: '参考图片', REFERENCE_VIDEO: '参考视频', REFERENCE_AUDIO: '参考音频',
  FIRST_FRAME: '首帧', LAST_FRAME: '尾帧', INPUT: '输入', OUTPUT: '输出',
  ASSET_VISUAL_VARIANT: '资产形态', STORYBOARD_FIRST_FRAME: '分镜首帧',
  IMAGE_MATERIAL: '图片素材', VIDEO_MATERIAL: '视频素材', AUDIO_MATERIAL: '音频素材',
  PROJECT_AUDIO_ASSET: '项目音频', AI_VOICE_RESULT: '生成音频', SYSTEM_AUDIO_ASSET: '系统音频',
  VOLCENGINE_ARK: '火山引擎', VOLCENGINE: '火山引擎', ALIYUN: '阿里云', DASHSCOPE: '阿里云百炼',
  TENCENT: '腾讯云', BAIDU: '百度智能云', KLING: '可灵', MINIMAX: '海螺',
  LOW: '低', MEDIUM: '中', HIGH: '高', WEAK: '弱', STRONG: '强',
  STATIC: '固定', FIXED: '固定', PAN: '平移', TILT: '俯仰', ZOOM: '变焦',
  ZOOM_IN: '推近', ZOOM_OUT: '拉远', TRACKING: '跟随', ORBIT: '环绕',
  STANDARD: '标准', HD: '高清', AUTO: '自动', NONE: '无',
  REALISTIC: '写实', ANIME: '动漫', CARTOON: '卡通',
  TRUE: '是', FALSE: '否', YES: '是', NO: '否',
};

const sourceTypes: Record<string, string> = {
  IMAGE: '图片生成', VIDEO: '视频生成', REVIEW: '剧本审核', SCRIPT_ANALYSIS: '剧本分析',
  SCRIPT_OPERATION: '剧本与资产处理', STORYBOARD_BATCH: '分镜批次', STORYBOARD_ITEM: '分镜任务',
  VIDEO_DECOMPOSITION: '视频拆剧', VIDEO_EPISODE: '拆剧分集', FREE_CREATION: '自由创作',
  VIRTUAL_AVATAR: '虚拟人像', SUBTITLE_ERASE: '字幕擦除',
};

export function displayEnum(value: unknown): string {
  if (value == null || value === '') return '—';
  const text = String(value);
  return enumLabels[text.toUpperCase()] ?? text;
}

export function displayField(label: string, value: unknown): string {
  // Free text and identifiers must not be rewritten just because they resemble an enum.
  if (['模型', '名称', '负向提示词', '错误', '失败原因', '角色与形态'].includes(label) || /编号|种子|ID$/.test(label)) {
    return value == null || value === '' ? '—' : String(value);
  }
  return displayEnum(value);
}

export const displayTime = (value?: string) => value ? value.replace('T', ' ') : '—';

export function displaySourceVersion(value: string): string {
  const match = /^([^:]+):([^@]+)@(.+)$/.exec(value);
  return match ? `${sourceTypes[match[1]] ?? match[1]} #${match[2]} · ${displayTime(match[3])}` : value;
}
