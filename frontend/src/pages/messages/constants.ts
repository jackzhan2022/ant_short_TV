export const categories: Record<string, string> = {
  SYSTEM: '系统消息',
  SECURITY: '安全消息',
  PRODUCT: '产品消息',
  FINANCE: '财务消息',
  OPERATION: '运维消息',
  ACTIVITY: '活动消息',
};
export const categoryOptions = Object.entries(categories).map(
  ([value, label]) => ({ value, label }),
);
export const errorText = (error: unknown) =>
  error instanceof Error ? error.message : '操作失败，请重试';
export const formatMessageTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour12: false,
  });
