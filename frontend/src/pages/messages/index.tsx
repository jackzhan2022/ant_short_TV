import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Badge,
  Button,
  Drawer,
  Input,
  Modal,
  Select,
  Table,
  Tabs,
} from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { categories, errorText, formatMessageTime } from './constants';
import SendMessageModal from './SendMessageModal';
import SentMessages from './SentMessages';
import {
  getMessage,
  listMessages,
  type MessageAction,
  type MessageItem,
  type MessageQuery,
  type MessageSummary,
  messageSummary,
  notifyMessageChange,
  updateMessages,
} from './service';
import './index.less';

export default function MessageCenter() {
  const [box, setBox] = useState('inbox');
  const [sentRevision, setSentRevision] = useState(0);
  const [query, setQuery] = useState<MessageQuery>({ page: 1, pageSize: 10 });
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<{ items: MessageItem[]; total: number }>({
    items: [],
    total: 0,
  });
  const [summary, setSummary] = useState<MessageSummary>({
    unread: 0,
    categories: {},
    canSend: false,
  });
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [detail, setDetail] = useState<MessageItem>();
  const [opening, setOpening] = useState(false);
  const [sending, setSending] = useState(false);
  const requestId = useRef(0);
  const detailId = useRef(0);
  const [modal, contextHolder] = Modal.useModal();

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const [page, counts] = await Promise.all([
        listMessages(query),
        messageSummary(),
      ]);
      if (id !== requestId.current) return;
      if (query.page > 1 && page.items.length === 0) {
        setQuery((q) => ({
          ...q,
          page: Math.max(1, Math.ceil(page.total / q.pageSize)),
        }));
        return;
      }
      setData(page);
      setSummary(counts);
    } catch (e) {
      if (id === requestId.current) setError(errorText(e));
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [query]);
  useEffect(() => {
    setSelected([]);
    void load();
    return () => {
      requestId.current++;
    };
  }, [load]);
  useEffect(
    () => () => {
      detailId.current++;
    },
    [],
  );

  const perform = async (action: 'read' | 'delete', payload: MessageAction) => {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await updateMessages(action, payload);
      setSelected([]);
      notifyMessageChange();
      await load();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  const act = (action: 'read' | 'delete', all = false) => {
    const payload = all
      ? {
          all: true,
          category: query.category,
          status: query.status,
          keyword: query.keyword,
        }
      : { ids: selected };
    if (action === 'delete') {
      modal.confirm({
        title: all
          ? '删除当前筛选下的全部消息？'
          : `删除已选的 ${selected.length} 条消息？`,
        content: '删除后，这些消息将从你的消息中心移除。',
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: () => perform(action, payload),
      });
    } else void perform(action, payload);
  };
  const openMessage = async (item: MessageItem) => {
    const id = ++detailId.current;
    setOpening(true);
    setError('');
    try {
      const result = await getMessage(item.id);
      if (id !== detailId.current) return;
      setDetail(result);
      if (!result.read) {
        await updateMessages('read', { ids: [result.id] });
        notifyMessageChange();
        await load();
      }
    } catch (e) {
      if (id === detailId.current) setError(errorText(e));
    } finally {
      if (id === detailId.current) setOpening(false);
    }
  };
  const changeFilter = (patch: Partial<MessageQuery>) => {
    setSuccess('');
    setQuery((q) => ({ ...q, ...patch, page: 1 }));
  };
  const disabled = loading || busy || opening;

  return (
    <main className="message-center">
      {contextHolder}
      <header className="message-center-header">
        <div>
          <h1>消息中心</h1>
          <p>查看平台公告与系统通知</p>
        </div>
        <div className="message-center-actions">
          <Button
            aria-label="刷新消息"
            icon={<ReloadOutlined />}
            disabled={disabled}
            onClick={() => {
              void load();
              setSentRevision((v) => v + 1);
            }}
          />
          {summary.canSend && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setSending(true)}
            >
              发送消息
            </Button>
          )}
        </div>
      </header>
      {error && (
        <Alert
          title={error}
          type="error"
          showIcon
          action={<Button onClick={() => void load()}>重试</Button>}
        />
      )}
      {success && <Alert title={success} type="success" showIcon />}
      {summary.canSend && (
        <Tabs
          activeKey={box}
          onChange={setBox}
          items={[
            { key: 'inbox', label: '收件箱' },
            { key: 'sent', label: '已发送' },
          ]}
        />
      )}
      {box === 'sent' && summary.canSend ? (
        <SentMessages revision={sentRevision} />
      ) : (
        <section className="message-center-panel" aria-label="消息列表">
          <Tabs
            activeKey={query.category || 'ALL'}
            onChange={(key) =>
              changeFilter({ category: key === 'ALL' ? undefined : key })
            }
            items={[['ALL', '全部消息'], ...Object.entries(categories)].map(
              ([key, label]) => ({
                key,
                label: (
                  <span className="message-tab">
                    {label}
                    <Badge
                      count={
                        key === 'ALL' ? summary.unread : summary.categories[key]
                      }
                      overflowCount={99}
                    />
                  </span>
                ),
              }),
            )}
          />
          <div className="message-center-filters">
            <label htmlFor="message-status">状态</label>
            <Select
              id="message-status"
              aria-label="消息状态"
              value={query.status || 'ALL'}
              style={{ width: 180 }}
              options={[
                { value: 'ALL', label: '全部消息' },
                { value: 'UNREAD', label: '未读' },
                { value: 'READ', label: '已读' },
              ]}
              onChange={(value) =>
                changeFilter({ status: value === 'ALL' ? undefined : value })
              }
            />
            <Input
              aria-label="标题搜索"
              placeholder="请输入标题搜索"
              value={keyword}
              maxLength={200}
              allowClear
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() =>
                changeFilter({ keyword: keyword.trim() || undefined })
              }
              style={{ width: 280 }}
            />
            <Button
              type="primary"
              onClick={() =>
                changeFilter({ keyword: keyword.trim() || undefined })
              }
            >
              搜索
            </Button>
          </div>
          <Table<MessageItem>
            rowKey="id"
            loading={loading || opening}
            dataSource={data.items}
            scroll={{ x: 760 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys.map(String)),
              getCheckboxProps: () => ({ disabled }),
            }}
            rowClassName={(item) =>
              item.read ? 'message-row-read' : 'message-row-unread'
            }
            locale={{
              emptyText:
                query.keyword || query.category || query.status
                  ? '暂无符合条件的消息'
                  : '暂无消息',
            }}
            columns={[
              {
                title: '标题',
                dataIndex: 'title',
                render: (_, item) => (
                  <button
                    type="button"
                    className="message-title-button"
                    disabled={disabled}
                    onClick={() => void openMessage(item)}
                  >
                    {item.title}
                  </button>
                ),
              },
              {
                title: '状态',
                dataIndex: 'read',
                width: 100,
                render: (read: boolean) => (
                  <Badge
                    status={read ? 'default' : 'processing'}
                    text={read ? '已读' : '未读'}
                  />
                ),
              },
              {
                title: '时间（UTC+8）',
                dataIndex: 'createdAt',
                width: 210,
                render: formatMessageTime,
              },
              {
                title: '消息分类',
                dataIndex: 'category',
                width: 130,
                render: (value: string) => categories[value] || value,
              },
              {
                title: '来源',
                dataIndex: 'source',
                width: 120,
                render: (value: string) =>
                  value === 'SYSTEM' ? '系统流程' : '平台管理员',
              },
            ]}
            pagination={{
              current: query.page,
              pageSize: query.pageSize,
              total: data.total,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, pageSize) =>
                setQuery((q) => ({
                  ...q,
                  page: pageSize === q.pageSize ? page : 1,
                  pageSize,
                })),
            }}
          />
          <div className="message-center-batch">
            <span>已选 {selected.length} 条</span>
            <Button
              type="link"
              disabled={!selected.length || disabled}
              onClick={() => setSelected([])}
            >
              取消选择
            </Button>
            <Button
              disabled={!selected.length || disabled}
              onClick={() => act('read')}
            >
              标记为已读
            </Button>
            <Button
              danger
              disabled={!selected.length || disabled}
              onClick={() => act('delete')}
            >
              删除
            </Button>
            <Button
              disabled={!data.total || disabled}
              onClick={() => act('read', true)}
            >
              当前筛选全部已读
            </Button>
            <Button
              danger
              disabled={!data.total || disabled}
              onClick={() => act('delete', true)}
            >
              当前筛选全部删除
            </Button>
          </div>
        </section>
      )}
      <Drawer
        open={!!detail}
        title="消息详情"
        onClose={() => {
          detailId.current++;
          setDetail(undefined);
          setOpening(false);
        }}
        size="large"
      >
        {detail && (
          <article>
            <h2>{detail.title}</h2>
            <p className="message-detail-meta">
              {categories[detail.category]} · {detail.senderName} ·{' '}
              {formatMessageTime(detail.createdAt)}
            </p>
            <div className="message-content">{detail.content}</div>
          </article>
        )}
      </Drawer>
      {sending && (
        <SendMessageModal
          onClose={() => setSending(false)}
          onSent={(count) => {
            setSending(false);
            setSentRevision((v) => v + 1);
            setSuccess(`消息已发送给 ${count} 位用户`);
            notifyMessageChange();
            void load();
          }}
        />
      )}
    </main>
  );
}
