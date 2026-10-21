import { request } from '@umijs/max';

export type MessageItem = {
  id: string;
  title: string;
  content?: string;
  category: string;
  source: 'ADMIN' | 'SYSTEM';
  senderName: string;
  createdAt: string;
  read: boolean;
};
export type MessageQuery = {
  category?: string;
  status?: string;
  keyword?: string;
  page: number;
  pageSize: number;
};
export type MessageSummary = {
  unread: number;
  categories: Record<string, number>;
  canSend: boolean;
};
export type Recipient = { id: number; nickname: string };
export type SendMessage = {
  title: string;
  content: string;
  category: string;
  allUsers: boolean;
  recipientIds?: number[];
};
export type MessageAction = {
  ids?: string[];
  all?: boolean;
  category?: string;
  status?: string;
  keyword?: string;
};

async function read<T>(
  url: string,
  options: { method?: string; data?: unknown; params?: object } = {},
): Promise<T> {
  const response = await request<{
    success: boolean;
    data: T;
    errorMessage?: string;
  }>(url, options);
  if (!response.success)
    throw new Error(response.errorMessage || '消息操作失败，请重试');
  return response.data;
}
export const listMessages = (query: MessageQuery) =>
  read<{ items: MessageItem[]; total: number }>('/api/messages', {
    params: query,
  });
export const messageSummary = () =>
  read<MessageSummary>('/api/messages/summary');
export const getMessage = (id: string) =>
  read<MessageItem>(`/api/messages/${encodeURIComponent(id)}`);
export const updateMessages = (
  action: 'read' | 'delete',
  data: MessageAction,
) => read<void>(`/api/messages/${action}`, { method: 'POST', data });
export const findRecipients = (keyword: string) =>
  read<Recipient[]>('/api/messages/recipients', { params: { keyword } });
export const sendMessage = (data: SendMessage) =>
  read<{ id: string; recipientCount: number }>('/api/messages/send', {
    method: 'POST',
    data,
  });
export const notifyMessageChange = () =>
  window.dispatchEvent(new Event('message-center-change'));

export type SentMessageItem = Omit<MessageItem, 'read'> & {
  audience: 'ALL' | 'SELECTED';
  recipientCount: number;
  readCount: number;
  unreadCount: number;
};
export type Delivery = {
  userId: number;
  nickname: string;
  readAt?: string;
  deleted: boolean;
};
export const listSentMessages = (
  query: Omit<MessageQuery, 'status'> & { source?: string },
) =>
  read<{ items: SentMessageItem[]; total: number }>('/api/messages/sent', {
    params: query,
  });
export const getSentMessage = (id: string) =>
  read<SentMessageItem>(`/api/messages/sent/${encodeURIComponent(id)}`);
export const listDeliveries = (id: string, page: number, pageSize: number) =>
  read<{ items: Delivery[]; total: number }>(
    `/api/messages/sent/${encodeURIComponent(id)}/recipients`,
    { params: { page, pageSize } },
  );
