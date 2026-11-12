import { ArrowRightOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Input, Select, Table } from 'antd';
import { useEffect, useState } from 'react';
import { errorText, formatMessageTime } from './constants';
import { useStyles } from './index.style';
import {
  CategoryNav,
  CategoryTag,
  MessageDate,
  MessageEmpty,
  MessageIdentity,
  MessagePagination,
  ReadingHeader,
  ReadTag,
} from './presentation';
import {
  type Delivery,
  getSentMessage,
  listDeliveries,
  listSentMessages,
  type SentMessageItem,
} from './service';

function SentDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { styles } = useStyles();
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
    <Drawer
      open
      title="发送详情"
      size={720}
      className={styles.drawer}
      onClose={onClose}
      footer={
        <div className={styles.drawerFooter}>
          <span />
          <Button onClick={onClose}>关闭</Button>
        </div>
      }
    >
      {error && (
        <Alert
          type="error"
          title={error}
          action={<Button onClick={() => setRetry((v) => v + 1)}>重试</Button>}
        />
      )}
      {detail && (
        <article>
          <ReadingHeader item={detail} />
          <p className={styles.source}>
            发送范围：
            {detail.audience === 'ALL' ? '全部用户（发送时）' : '指定用户'}
          </p>
          <section aria-label="送达统计">
            <dl className={styles.receiptSummary}>
              <div>
                <dt>收件人数</dt>
                <dd>{detail.recipientCount}</dd>
              </div>
              <div>
                <dt>已读</dt>
                <dd>{detail.readCount}</dd>
              </div>
              <div>
                <dt>未读</dt>
                <dd>{detail.unreadCount}</dd>
              </div>
            </dl>
          </section>
          <div className={styles.content}>{detail.content}</div>
          <section className={styles.receipts} aria-label="收件明细">
            <h3>收件明细</h3>
            <Table<Delivery>
              rowKey="userId"
              loading={loading}
              dataSource={receipts.items}
              size="small"
              scroll={{ x: 570 }}
              columns={[
                { title: '收件人', dataIndex: 'nickname' },
                { title: '用户编号', dataIndex: 'userId', width: 100 },
                {
                  title: '阅读状态',
                  render: (_, r) => <ReadTag read={!!r.readAt} />,
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
          </section>
        </article>
      )}
      {!detail && loading && <p>正在加载发送详情…</p>}
    </Drawer>
  );
}

export default function SentMessages({ revision }: { revision: number }) {
  const { styles } = useStyles();
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
        if (!disposed) {
          if (query.page > 1 && !result.items.length) {
            setQuery((q) => ({
              ...q,
              page: Math.max(1, Math.ceil(result.total / q.pageSize)),
            }));
          } else setData(result);
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
  }, [query, revision, retry]);
  const filtered = !!(query.category || query.source || query.keyword);
  const clearFilters = () => {
    setKeyword('');
    setQuery({ page: 1, pageSize: query.pageSize });
  };
  return (
    <section className={styles.panel} aria-label="已发送消息">
      {error && (
        <Alert
          title={error}
          type="error"
          action={<Button onClick={() => setRetry((v) => v + 1)}>重试</Button>}
        />
      )}
      <CategoryNav
        category={query.category}
        onChange={(category) => setQuery((q) => ({ ...q, category, page: 1 }))}
        total={data.total}
        loading={loading}
        onRefresh={() => setRetry((v) => v + 1)}
      />
      <div className={styles.toolbar}>
        <Input
          aria-label="已发送标题搜索"
          placeholder="搜索已发送消息标题"
          prefix={<SearchOutlined aria-hidden />}
          variant="outlined"
          value={keyword}
          maxLength={200}
          allowClear
          onChange={(e) => {
            setKeyword(e.target.value);
            if (!e.target.value && query.keyword)
              setQuery((q) => ({ ...q, keyword: undefined, page: 1 }));
          }}
          onPressEnter={() =>
            setQuery((q) => ({
              ...q,
              keyword: keyword.trim() || undefined,
              page: 1,
            }))
          }
        />
        <Select
          aria-label="发送消息来源"
          placeholder="全部来源"
          allowClear
          value={query.source}
          options={[
            { value: 'ADMIN', label: '平台管理员' },
            { value: 'SYSTEM', label: '系统流程' },
          ]}
          onChange={(source) => setQuery((q) => ({ ...q, source, page: 1 }))}
        />
        {(filtered || keyword) && (
          <Button
            type="text"
            className={styles.quietButton}
            onClick={clearFilters}
          >
            清除筛选
          </Button>
        )}
      </div>
      <Table<SentMessageItem>
        className={styles.table}
        rowKey="id"
        dataSource={data.items}
        loading={loading}
        scroll={{ x: 1080, y: 'max(280px, calc(100dvh - 365px))' }}
        pagination={false}
        locale={{
          emptyText: (
            <MessageEmpty sent filtered={filtered} onClear={clearFilters} />
          ),
        }}
        columns={[
          {
            title: '消息标题',
            dataIndex: 'title',
            render: (_, item) => (
              <MessageIdentity
                item={item}
                onOpen={() => setDetailId(item.id)}
              />
            ),
          },
          {
            title: '分类',
            dataIndex: 'category',
            width: 115,
            render: (category: string) => <CategoryTag category={category} />,
          },
          {
            title: '发送范围',
            dataIndex: 'audience',
            width: 110,
            render: (a: string) => (a === 'ALL' ? '全部用户' : '指定用户'),
          },
          { title: '收件人数', dataIndex: 'recipientCount', width: 100 },
          {
            title: '阅读情况',
            key: 'read',
            width: 180,
            render: (_, item) => (
              <div className={styles.readSummary}>
                <div>
                  <span>
                    {item.readCount} / {item.recipientCount}
                  </span>
                  <span className={styles.miniTrack} aria-hidden>
                    <i
                      style={{
                        width: `${item.recipientCount ? Math.min(100, (item.readCount / item.recipientCount) * 100) : 0}%`,
                      }}
                    />
                  </span>
                </div>
                <small>未读 {item.unreadCount} 人</small>
              </div>
            ),
          },
          {
            title: '发送时间',
            dataIndex: 'createdAt',
            width: 150,
            render: (value: string) => <MessageDate value={value} />,
          },
          {
            key: 'detail',
            width: 60,
            render: (_, item) => (
              <Button
                type="text"
                className="message-detail-link"
                aria-label={`查看发送详情：${item.title}`}
                icon={<ArrowRightOutlined aria-hidden />}
                onClick={() => setDetailId(item.id)}
              />
            ),
          },
        ]}
      />
      <MessagePagination
        page={query.page}
        pageSize={query.pageSize}
        total={data.total}
        disabled={loading}
        onChange={(page, pageSize) =>
          setQuery((q) => ({
            ...q,
            page: pageSize === q.pageSize ? page : 1,
            pageSize,
          }))
        }
      />
      {detailId && (
        <SentDetail id={detailId} onClose={() => setDetailId(undefined)} />
      )}
    </section>
  );
}
