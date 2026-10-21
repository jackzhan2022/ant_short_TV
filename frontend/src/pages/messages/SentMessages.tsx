import { Alert, Button, Drawer, Input, Select, Table } from 'antd';
import { useEffect, useState } from 'react';
import {
  categories,
  categoryOptions,
  errorText,
  formatMessageTime,
} from './constants';
import {
  type Delivery,
  getSentMessage,
  listDeliveries,
  listSentMessages,
  type SentMessageItem,
} from './service';

function SentDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const [detail, setDetail] = useState<SentMessageItem>();
  const [receipts, setReceipts] = useState<{
    items: Delivery[];
    total: number;
  }>({ items: [], total: 0 });
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let disposed = false;
    setLoading(true);
    setError('');
    void Promise.all([getSentMessage(id), listDeliveries(id, page, 10)])
      .then(([message, records]) => {
        if (!disposed) {
          setDetail(message);
          setReceipts(records);
        }
      })
      .catch((e) => {
        if (!disposed) setError(errorText(e));
      })
      .finally(() => {
        if (!disposed) setLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, [id, page, retry]);
  return (
    <Drawer open title="发送详情" size="large" onClose={onClose}>
      {error && (
        <Alert
          type="error"
          title={error}
          action={<Button onClick={() => setRetry((v) => v + 1)}>重试</Button>}
        />
      )}
      {detail && (
        <article>
          <h2>{detail.title}</h2>
          <p className="message-detail-meta">
            {categories[detail.category]} · {detail.senderName} ·{' '}
            {formatMessageTime(detail.createdAt)}
          </p>
          <p>
            发送范围：
            {detail.audience === 'ALL' ? '全部用户（发送时）' : '指定用户'}
          </p>
          <p>
            收件 {detail.recipientCount} 人 · 已读 {detail.readCount} 人 · 未读{' '}
            {detail.unreadCount} 人
          </p>
          <div className="message-content">{detail.content}</div>
          <h3>收件明细</h3>
          <Table<Delivery>
            rowKey="userId"
            loading={loading}
            dataSource={receipts.items}
            size="small"
            columns={[
              { title: '收件人', dataIndex: 'nickname' },
              { title: '用户编号', dataIndex: 'userId', width: 100 },
              {
                title: '阅读状态',
                render: (_, r) => (r.readAt ? '已读' : '未读'),
                width: 90,
              },
              {
                title: '阅读时间（UTC+8）',
                dataIndex: 'readAt',
                render: (t?: string) => (t ? formatMessageTime(t) : '—'),
              },
              {
                title: '收件箱状态',
                render: (_, r) => (r.deleted ? '已删除' : '正常'),
                width: 100,
              },
            ]}
            pagination={{
              current: page,
              pageSize: 10,
              total: receipts.total,
              showSizeChanger: false,
              onChange: setPage,
            }}
          />
        </article>
      )}
      {!detail && loading && <p>正在加载发送详情…</p>}
    </Drawer>
  );
}

export default function SentMessages({ revision }: { revision: number }) {
  const [query, setQuery] = useState<{
    page: number;
    pageSize: number;
    category?: string;
    source?: string;
    keyword?: string;
  }>({ page: 1, pageSize: 10 });
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<{ items: SentMessageItem[]; total: number }>(
    { items: [], total: 0 },
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [detailId, setDetailId] = useState<string>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let disposed = false;
    setLoading(true);
    setError('');
    void listSentMessages(query)
      .then((result) => {
        if (!disposed) setData(result);
      })
      .catch((e) => {
        if (!disposed) setError(errorText(e));
      })
      .finally(() => {
        if (!disposed) setLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, [query, revision, retry]);
  return (
    <section className="message-center-panel" aria-label="已发送消息">
      {error && (
        <Alert
          title={error}
          type="error"
          action={<Button onClick={() => setRetry((v) => v + 1)}>重试</Button>}
        />
      )}
      <div className="message-center-filters">
        <Select
          aria-label="发送消息分类"
          placeholder="全部分类"
          allowClear
          style={{ width: 160 }}
          options={categoryOptions}
          onChange={(category) =>
            setQuery((q) => ({ ...q, category, page: 1 }))
          }
        />
        <Select
          aria-label="发送消息来源"
          placeholder="全部来源"
          allowClear
          style={{ width: 160 }}
          options={[
            { value: 'ADMIN', label: '平台管理员' },
            { value: 'SYSTEM', label: '系统流程' },
          ]}
          onChange={(source) => setQuery((q) => ({ ...q, source, page: 1 }))}
        />
        <Input
          placeholder="搜索已发送消息标题"
          value={keyword}
          maxLength={200}
          allowClear
          style={{ width: 280 }}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={() =>
            setQuery((q) => ({
              ...q,
              keyword: keyword.trim() || undefined,
              page: 1,
            }))
          }
        />
        <Button
          type="primary"
          onClick={() =>
            setQuery((q) => ({
              ...q,
              keyword: keyword.trim() || undefined,
              page: 1,
            }))
          }
        >
          搜索
        </Button>
      </div>
      <Table<SentMessageItem>
        rowKey="id"
        dataSource={data.items}
        loading={loading}
        scroll={{ x: 1000 }}
        locale={{ emptyText: '暂无发送记录' }}
        columns={[
          {
            title: '标题',
            dataIndex: 'title',
            render: (_, item) => (
              <button
                type="button"
                className="message-title-button"
                onClick={() => setDetailId(item.id)}
              >
                {item.title}
              </button>
            ),
          },
          {
            title: '分类',
            dataIndex: 'category',
            width: 100,
            render: (c: string) => categories[c],
          },
          { title: '发送人', dataIndex: 'senderName', width: 120 },
          {
            title: '来源',
            dataIndex: 'source',
            width: 110,
            render: (s: string) => (s === 'ADMIN' ? '平台管理员' : '系统流程'),
          },
          {
            title: '发送范围',
            dataIndex: 'audience',
            width: 110,
            render: (a: string) => (a === 'ALL' ? '全部用户' : '指定用户'),
          },
          { title: '收件人数', dataIndex: 'recipientCount', width: 90 },
          { title: '已读', dataIndex: 'readCount', width: 70 },
          { title: '未读', dataIndex: 'unreadCount', width: 70 },
          {
            title: '发送时间（UTC+8）',
            dataIndex: 'createdAt',
            width: 200,
            render: formatMessageTime,
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
      {detailId && (
        <SentDetail id={detailId} onClose={() => setDetailId(undefined)} />
      )}
    </section>
  );
}
