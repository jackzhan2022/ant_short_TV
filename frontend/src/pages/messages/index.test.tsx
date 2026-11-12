import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { ConfigProvider } from 'antd';
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

const renderPage = () =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <MessageCenter />
    </ConfigProvider>,
  );

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
    renderPage();
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
    renderPage();
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
    renderPage();
    fireEvent.click(await screen.findByRole('tab', { name: '已发送' }));
    fireEvent.click(await screen.findByRole('button', { name: '已发送公告' }));
    expect(await screen.findByText('公告正文')).toBeInTheDocument();
    expect(await screen.findByText('测试收件人')).toBeInTheDocument();
    const counts = screen.getByRole('region', { name: '送达统计' });
    expect(within(counts).getByText('12')).toBeInTheDocument();
    expect(within(counts).getByText('5')).toBeInTheDocument();
    expect(within(counts).getByText('7')).toBeInTheDocument();
    fireEvent.click(within(screen.getByRole('dialog')).getByTitle('2'));
    await waitFor(() =>
      expect(mocks.listDeliveries).toHaveBeenLastCalledWith('sent-1', 2, 10),
    );
    expect(mocks.updateMessages).not.toHaveBeenCalled();
  });
  it('applies title search and category changes to the server query', async () => {
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.change(screen.getByPlaceholderText('搜索消息标题'), {
      target: { value: '升级' },
    });
    fireEvent.keyDown(screen.getByPlaceholderText('搜索消息标题'), {
      key: 'Enter',
      code: 'Enter',
      charCode: 13,
    });
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
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.click(screen.getByRole('tab', { name: /产品消息/ }));
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'PRODUCT' }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: '全部标为已读' }));
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
    renderPage();
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

  it('shows batch actions only after selection and clears selection with the filter', async () => {
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    expect(
      screen.queryByRole('region', { name: '批量操作' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    const bar = screen.getByRole('region', { name: '批量操作' });
    expect(within(bar).getByText('已选 1 条')).toBeInTheDocument();
    fireEvent.click(within(bar).getByRole('button', { name: '标为已读' }));
    await waitFor(() =>
      expect(mocks.updateMessages).toHaveBeenCalledWith('read', {
        ids: ['message-1'],
      }),
    );
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: '批量操作' }),
      ).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    fireEvent.click(screen.getByRole('tab', { name: /安全消息/ }));
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: '批量操作' }),
      ).not.toBeInTheDocument(),
    );
  });

  it('clears a title/category filter back to the first page', async () => {
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.click(screen.getByRole('tab', { name: /产品消息/ }));
    const input = screen.getByPlaceholderText('搜索消息标题');
    fireEvent.change(input, { target: { value: '升级' } });
    fireEvent.keyDown(input, { key: 'Enter', charCode: 13 });
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: '升级' }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: '清除筛选' }));
    await waitFor(() =>
      expect(mocks.listMessages).toHaveBeenLastCalledWith({
        page: 1,
        pageSize: 10,
      }),
    );
    expect(input).toHaveValue('');
  });

  it('requires confirmation before deleting selected messages', async () => {
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    fireEvent.click(
      within(screen.getByRole('region', { name: '批量操作' })).getByRole(
        'button',
        { name: '删除' },
      ),
    );
    expect(await screen.findByRole('dialog')).toHaveTextContent(
      '删除已选的 1 条消息？',
    );
    expect(mocks.updateMessages).not.toHaveBeenCalled();
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: /删.*除/,
      }),
    );
    await waitFor(() =>
      expect(mocks.updateMessages).toHaveBeenCalledWith('delete', {
        ids: ['message-1'],
      }),
    );
  });

  it('navigates between messages in the reading drawer', async () => {
    const first = (await mocks.listMessages()).items[0];
    mocks.listMessages.mockResolvedValue({
      items: [
        first,
        { ...first, id: 'message-2', title: '第二条通知', read: true },
      ],
      total: 2,
    });
    mocks.getMessage.mockImplementation(async (id: string) => ({
      ...first,
      id,
      read: true,
      title: id === 'message-1' ? first.title : '第二条通知',
      content: id === 'message-1' ? '第一条正文' : '第二条正文',
    }));
    renderPage();
    fireEvent.click(
      await screen.findByRole('button', { name: '产品升级通知' }),
    );
    expect(await screen.findByText('第一条正文')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一条' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '下一条' }));
    expect(await screen.findByText('第二条正文')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下一条' })).toBeDisabled();
    expect(mocks.updateMessages).not.toHaveBeenCalled();
  });

  it('preserves detail navigation when marking an unread message removes it from the filtered list', async () => {
    const first = { id: 'message-1', title: '产品升级通知', category: 'PRODUCT', source: 'ADMIN', senderName: '管理员', createdAt: '2026-10-21T08:00:00Z', read: false };
    const second = { ...first, id: 'message-2', title: '第二条通知' };
    let marked = false;
    mocks.listMessages.mockImplementation(async (query) => ({ items: query.status === 'UNREAD' && marked ? [second] : [first, second], total: marked ? 1 : 2 }));
    mocks.updateMessages.mockImplementation(async () => { marked = true; });
    mocks.getMessage.mockImplementation(async (id) => ({ ...(id === first.id ? first : second), content: id === first.id ? '第一条未读正文' : '第二条未读正文' }));
    renderPage();
    await screen.findByRole('button', { name: first.title });
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '消息状态' }));
    fireEvent.click(await screen.findByText('未读', { selector: '.ant-select-item-option-content' }));
    await waitFor(() => expect(mocks.listMessages).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'UNREAD' })));
    fireEvent.click(screen.getByRole('button', { name: first.title }));
    await screen.findByText('第一条未读正文');
    await waitFor(() => expect(screen.queryByRole('button', { name: first.title })).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole('button', { name: '下一条' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: '下一条' }));
    expect(await screen.findByText('第二条未读正文')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '上一条' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: '上一条' }));
    expect(await screen.findByText('第一条未读正文')).toBeInTheDocument();
  });

  it('keeps the current filters when confirming deletion from the more menu', async () => {
    renderPage();
    await screen.findByRole('button', { name: '产品升级通知' });
    fireEvent.click(screen.getByRole('tab', { name: /产品消息/ }));
    const input = screen.getByPlaceholderText('搜索消息标题');
    fireEvent.change(input, { target: { value: '升级' } });
    fireEvent.keyDown(input, { key: 'Enter', charCode: 13 });
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '全部标为已读' }),
      ).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: '更多消息操作' }));
    fireEvent.click(
      await screen.findByRole('menuitem', { name: '删除当前筛选全部消息' }),
    );
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('删除当前筛选下的全部消息？');
    expect(mocks.updateMessages).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: /删.*除/ }));
    await waitFor(() =>
      expect(mocks.updateMessages).toHaveBeenCalledWith('delete', {
        all: true,
        category: 'PRODUCT',
        keyword: '升级',
        status: undefined,
      }),
    );
  });
});
