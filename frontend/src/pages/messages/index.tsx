import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckOutlined,
  DeleteOutlined,
  EllipsisOutlined,
  InboxOutlined,
  PlusOutlined,
  SearchOutlined,
  SendOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Drawer,
  Dropdown,
  Input,
  Modal,
  Select,
  Table,
  Tabs,
} from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { errorText } from './constants';
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

export default function MessageCenter() {
  const { styles } = useStyles();
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
  const [detailItems, setDetailItems] = useState<MessageItem[]>([]);
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
  const openMessage = async (item: MessageItem, navigating = false) => {
    if (!navigating) setDetailItems(data.items);
    const id = ++detailId.current;
    setOpening(true);
    setError('');
    try {
      const result = await getMessage(item.id);
      if (id !== detailId.current) return;
      setDetail(result);
      if (!result.read) {
        await updateMessages('read', { ids: [result.id] });
        if (id === detailId.current) setDetail({ ...result, read: true });
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
  const filtered = !!(query.keyword || query.category || query.status);
  const clearFilters = () => {
    setKeyword('');
    setQuery({ page: 1, pageSize: query.pageSize });
  };
  const closeDetail = () => {
    detailId.current++;
    setDetail(undefined);
    setOpening(false);
  };
  const detailIndex = detailItems.findIndex((item) => item.id === detail?.id);

  return (
    <main className={styles.page}>
      {contextHolder}
      <header className={styles.heading}>
        <div>
          <h1>
            消息中心
            {!!summary.unread && (
              <span className={styles.unreadCount}>
                {summary.unread} 条未读
              </span>
            )}
          </h1>
          <p>查看平台公告与系统通知</p>
        </div>
        <div className={styles.headingActions}>
          {summary.canSend && (
            <>
              <Tabs
                className={styles.boxSwitch}
                activeKey={box}
                onChange={setBox}
                items={[
                  {
                    key: 'inbox',
                    label: (
                      <>
                        <InboxOutlined aria-hidden />
                        收件箱
                      </>
                    ),
                  },
                  {
                    key: 'sent',
                    label: (
                      <>
                        <SendOutlined aria-hidden />
                        已发送
                      </>
                    ),
                  },
                ]}
              />
              <span className={styles.headingDivider} aria-hidden />
            </>
          )}
          {summary.canSend && (
            <Button
              type="primary"
              icon={<PlusOutlined aria-hidden />}
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
      {box === 'sent' && summary.canSend ? (
        <SentMessages revision={sentRevision} />
      ) : (
        <section className={styles.panel} aria-label="消息列表">
          <CategoryNav
            category={query.category}
            onChange={(category) => changeFilter({ category })}
            total={data.total}
            unread={summary.unread}
            counts={summary.categories}
            onRefresh={() => void load()}
            loading={disabled}
          />
          <div className={styles.toolbar}>
            <Input
              aria-label="标题搜索"
              placeholder="搜索消息标题"
              prefix={<SearchOutlined aria-hidden />}
              variant="outlined"
              value={keyword}
              maxLength={200}
              allowClear
              onChange={(e) => {
                setKeyword(e.target.value);
                if (!e.target.value && query.keyword)
                  changeFilter({ keyword: undefined });
              }}
              onPressEnter={() =>
                changeFilter({ keyword: keyword.trim() || undefined })
              }
            />
            <Select
              id="message-status"
              aria-label="消息状态"
              value={query.status || 'ALL'}
              options={[
                { value: 'ALL', label: '全部状态' },
                { value: 'UNREAD', label: '未读' },
                { value: 'READ', label: '已读' },
              ]}
              onChange={(value) =>
                changeFilter({ status: value === 'ALL' ? undefined : value })
              }
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
            <span className={styles.spacer} />
            <Button
              type="text"
              className={styles.quietButton}
              icon={<CheckOutlined aria-hidden />}
              disabled={!data.total || disabled}
              onClick={() => act('read', true)}
            >
              全部标为已读
            </Button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  {
                    key: 'delete-all',
                    label: '删除当前筛选全部消息',
                    icon: <DeleteOutlined aria-hidden />,
                    danger: true,
                    disabled: !data.total || disabled,
                  },
                ],
                onClick: ({ key }) => {
                  if (key === 'delete-all') act('delete', true);
                },
              }}
            >
              <Button
                type="text"
                aria-label="更多消息操作"
                icon={<EllipsisOutlined aria-hidden />}
              />
            </Dropdown>
          </div>
          <Table<MessageItem>
            className={styles.table}
            rowKey="id"
            loading={loading || opening}
            dataSource={data.items}
            scroll={{ x: 820, y: 'max(280px, calc(100dvh - 365px))' }}
            pagination={false}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys.map(String)),
              getCheckboxProps: () => ({ disabled }),
            }}
            rowClassName={(item) => (item.read ? styles.readRow : '')}
            locale={{
              emptyText: (
                <MessageEmpty filtered={filtered} onClear={clearFilters} />
              ),
            }}
            columns={[
              {
                title: '消息标题',
                dataIndex: 'title',
                render: (_, item) => (
                  <MessageIdentity
                    item={item}
                    unread={!item.read}
                    disabled={disabled}
                    onOpen={() => void openMessage(item)}
                  />
                ),
              },
              {
                title: '分类',
                dataIndex: 'category',
                width: 130,
                render: (category: string) => (
                  <CategoryTag category={category} />
                ),
              },
              {
                title: '状态',
                dataIndex: 'read',
                width: 100,
                render: (read: boolean) => <ReadTag read={read} />,
              },
              {
                title: '收到时间',
                dataIndex: 'createdAt',
                width: 160,
                render: (value: string) => <MessageDate value={value} />,
              },
              {
                key: 'detail',
                width: 60,
                render: (_, item) => (
                  <Button
                    type="text"
                    className="message-detail-link"
                    aria-label={`查看消息：${item.title}`}
                    icon={<ArrowRightOutlined aria-hidden />}
                    disabled={disabled}
                    onClick={() => void openMessage(item)}
                  />
                ),
              },
            ]}
          />
          <MessagePagination
            page={query.page}
            pageSize={query.pageSize}
            total={data.total}
            disabled={disabled}
            onChange={(page, pageSize) =>
              setQuery((q) => ({
                ...q,
                page: pageSize === q.pageSize ? page : 1,
                pageSize,
              }))
            }
          />
        </section>
      )}
      {box === 'inbox' && selected.length > 0 && (
        <section className={styles.batch} aria-label="批量操作">
          <div>
            <span>已选 {selected.length} 条</span>
            <Button
              type="link"
              disabled={!selected.length || disabled}
              onClick={() => setSelected([])}
            >
              取消选择
            </Button>
          </div>
          <div>
            <Button
              disabled={disabled}
              icon={<CheckOutlined aria-hidden />}
              onClick={() => act('read')}
            >
              标为已读
            </Button>
            <Button
              danger
              aria-label="删除"
              icon={<DeleteOutlined aria-hidden />}
              disabled={disabled}
              onClick={() => act('delete')}
            >
              删除
            </Button>
          </div>
        </section>
      )}
      <Drawer
        className={styles.drawer}
        open={!!detail}
        title="消息详情"
        onClose={closeDetail}
        size={720}
        footer={
          <div className={styles.drawerFooter}>
            <div>
              <Button
                type="text"
                icon={<ArrowLeftOutlined aria-hidden />}
                disabled={opening || detailIndex <= 0}
                onClick={() => void openMessage(detailItems[detailIndex - 1], true)}
              >
                上一条
              </Button>
              <Button
                type="text"
                icon={<ArrowRightOutlined aria-hidden />}
                disabled={
                  opening ||
                  detailIndex < 0 ||
                  detailIndex >= detailItems.length - 1
                }
                onClick={() => void openMessage(detailItems[detailIndex + 1], true)}
              >
                下一条
              </Button>
            </div>
            <Button onClick={closeDetail}>关闭</Button>
          </div>
        }
      >
        {detail && (
          <article>
            <ReadingHeader item={detail} read={detail.read} />
            <div className={styles.content}>{detail.content}</div>
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
