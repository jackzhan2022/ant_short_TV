import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  listMessages: vi.fn(),
  messageSummary: vi.fn(),
  getMessage: vi.fn(),
  updateMessages: vi.fn(),
  sendMessage: vi.fn(),
  findRecipients: vi.fn(),
  notifyMessageChange: vi.fn(),
  listSentMessages: vi.fn(),
  getSentMessage: vi.fn(),
  listDeliveries: vi.fn(),
}));
vi.mock('./service', () => ({ ...mocks }));

import MessageCenter from './index';

describe('MessageCenter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listMessages.mockResolvedValue({
      items: [
        {
          id: 'message-1',
          title: '产品升级通知',
          category: 'PRODUCT',
          source: 'ADMIN',
          senderName: '管理员',
          createdAt: '2026-10-21T08:00:00Z',
          read: false,
        },
      ],
      total: 1,
    });
    mocks.messageSummary.mockResolvedValue({
      unread: 1,
      categories: { PRODUCT: 1 },
      canSend: false,
    });
    mocks.getMessage.mockResolvedValue({
      id: 'message-1',
      title: '产品升级通知',
      content: '升级详情正文',
      category: 'PRODUCT',
      source: 'ADMIN',
      senderName: '管理员',
      createdAt: '2026-10-21T08:00:00Z',
      read: false,
    });
    mocks.updateMessages.mockResolvedValue(undefined);
  });
  it('shows received messages without a send action for ordinary members, and marks opened messages read', async () => {
    render(<MessageCenter />);
    fireEvent.click(
      await screen.findByRole('button', { name: '产品升级通知' }),
    );
    expect(await screen.findByText('升级详情正文')).toBeInTheDocument();
    await waitFor(() =>
      expect(mocks.updateMessages).toHaveBeenCalledWith('read', {
        ids: ['message-1'],
      }),
    );
    expect(
      screen.queryByRole('button', { name: /发送消息/ }),
    ).not.toBeInTheDocument();
  });
  it('allows only platform admins to open the send dialog', async () => {
    mocks.messageSummary.mockResolvedValue({
      unread: 0,
      categories: {},
      canSend: true,
    });
    mocks.findRecipients.mockResolvedValue([]);
    render(<MessageCenter />);
    fireEvent.click(await screen.findByRole('button', { name: /发送消息/ }));
    expect(await screen.findByLabelText('消息标题')).toBeInTheDocument();
    expect(screen.getByLabelText('消息正文')).toBeInTheDocument();
  });
  it('shows administrator sent records and delivery counts without marking recipient messages read', async () => {
    mocks.messageSummary.mockResolvedValue({
      unread: 0,
      categories: {},
      canSend: true,
    });
    const sent = {
      id: 'sent-1',
      title: '已发送公告',
      content: '公告正文',
      category: 'PRODUCT',
      source: 'ADMIN',
      audience: 'ALL',
      senderName: '平台管理员',
      createdAt: '2026-10-21T08:00:00Z',
      recipientCount: 12,
      readCount: 5,
      unreadCount: 7,
    };
    mocks.listSentMessages.mockResolvedValue({ items: [sent], total: 1 });
    mocks.getSentMessage.mockResolvedValue(sent);
    mocks.listDeliveries.mockResolvedValue({
      items: [
        { userId: 3, nickname: '测试收件人', readAt: null, deleted: false },
      ],
      total: 12,
    });
    render(<MessageCenter />);
    fireEvent.click(await screen.findByRole('tab', { name: '已发送' }));
    fireEvent.click(await screen.findByRole('button', { name: '已发送公告' }));
    expect(await screen.findByText('公告正文')).toBeInTheDocument();
    expect(await screen.findByText('测试收件人')).toBeInTheDocument();
    expect(
      screen.getByText('收件 12 人 · 已读 5 人 · 未读 7 人'),
    ).toBeInTheDocument();
    expect(mocks.updateMessages).not.toHaveBeenCalled();
  });
  it('applies title search and category changes to the server query', async () => {
    render(<MessageCenter />);
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.change(screen.getByPlaceholderText('请输入标题搜索'), {
      target: { value: '升级' },
    });
    fireEvent.click(screen.getByRole('button', { name: /搜.*索/ }));
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: '升级', page: 1 }),
      ),
    );
    fireEvent.click(screen.getByRole('tab', { name: /安全消息/ }));
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'SECURITY', page: 1 }),
      ),
    );
  });
  it('marks all messages read within the current filter only', async () => {
    render(<MessageCenter />);
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.click(screen.getByRole('tab', { name: /产品消息/ }));
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'PRODUCT' }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: '当前筛选全部已读' }));
    await waitFor(() =>
      expect(mocks.updateMessages).toHaveBeenCalledWith(
        'read',
        expect.objectContaining({ all: true, category: 'PRODUCT' }),
      ),
    );
  });
  it('previews the broadcast audience before sending and leaves source assignment to the server', async () => {
    mocks.messageSummary.mockResolvedValue({
      unread: 0,
      categories: {},
      canSend: true,
    });
    mocks.findRecipients.mockResolvedValue([]);
    mocks.sendMessage.mockResolvedValue({ id: 'sent', recipientCount: 3 });
    render(<MessageCenter />);
    fireEvent.click(await screen.findByRole('button', { name: /发送消息/ }));
    fireEvent.mouseDown(screen.getByLabelText('发送范围'));
    fireEvent.click(await screen.findByText('全部当前有效用户'));
    await waitFor(() =>
      expect(screen.queryByLabelText('收件人')).not.toBeInTheDocument(),
    );
    fireEvent.change(screen.getByLabelText('消息标题'), {
      target: { value: '维护通知' },
    });
    fireEvent.change(screen.getByLabelText('消息正文'), {
      target: { value: '平台将进行维护。' },
    });
    fireEvent.click(screen.getByRole('button', { name: /下一步/ }));
    expect(
      await screen.findByRole('button', { name: /确认发送/ }),
    ).toBeInTheDocument();
    expect(mocks.sendMessage).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /确认发送/ }));
    await waitFor(() =>
      expect(mocks.sendMessage).toHaveBeenCalledWith({
        title: '维护通知',
        content: '平台将进行维护。',
        category: 'SYSTEM',
        allUsers: true,
        recipientIds: undefined,
      }),
    );
    expect(
      await screen.findByText('消息已发送给 3 位用户'),
    ).toBeInTheDocument();
  });
});
