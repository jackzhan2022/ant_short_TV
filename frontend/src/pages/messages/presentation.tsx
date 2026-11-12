import {
  BellOutlined,
  GiftOutlined,
  InboxOutlined,
  NotificationOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { Button, Pagination, Tabs, Tag, Tooltip } from 'antd';
import { categories, formatMessageTime } from './constants';
import { useStyles } from './index.style';
import type { MessageItem } from './service';

const categoryIcons: Record<string, typeof BellOutlined> = {
  SYSTEM: BellOutlined,
  SECURITY: SafetyCertificateOutlined,
  PRODUCT: NotificationOutlined,
  FINANCE: WalletOutlined,
  OPERATION: ToolOutlined,
  ACTIVITY: GiftOutlined,
};

export function CategoryTag({ category }: { category: string }) {
  const { styles } = useStyles();
  return (
    <Tag className={styles.categoryTag}>{categories[category] || category}</Tag>
  );
}

export function ReadTag({ read }: { read: boolean }) {
  const { styles } = useStyles();
  return (
    <Tag className={styles.readTag} data-read={read}>
      {read ? '已读' : '未读'}
    </Tag>
  );
}

export function MessageIdentity({
  item,
  onOpen,
  disabled,
  unread = false,
}: {
  item: Omit<MessageItem, 'read'>;
  onOpen: () => void;
  disabled?: boolean;
  unread?: boolean;
}) {
  const { styles } = useStyles();
  const Icon = categoryIcons[item.category] || BellOutlined;
  return (
    <div className={styles.identity}>
      <span
        className={styles.messageIcon}
        data-type={item.category}
        aria-hidden
      >
        <Icon />
      </span>
      <div>
        <button
          type="button"
          className={styles.titleButton}
          disabled={disabled}
          onClick={onOpen}
        >
          {unread && <span className={styles.unreadDot} aria-hidden />}
          <span title={item.title}>{item.title}</span>
        </button>
        <div className={styles.source}>
          {item.source === 'SYSTEM' ? '系统流程' : '平台管理员'} ·{' '}
          {item.senderName}
        </div>
      </div>
    </div>
  );
}

export function MessageDate({ value }: { value: string }) {
  const { styles } = useStyles();
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return <span>—</span>;
  const options = { timeZone: 'Asia/Shanghai' };
  return (
    <time
      className={styles.date}
      dateTime={value}
      title={`${formatMessageTime(value)}（UTC+8）`}
    >
      {date
        .toLocaleDateString('zh-CN', {
          ...options,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
        })
        .replaceAll('/', '-')}
      <small>
        {date.toLocaleTimeString('zh-CN', {
          ...options,
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
        })}
      </small>
    </time>
  );
}

export function CategoryNav({
  category,
  onChange,
  total,
  unread,
  counts,
  onRefresh,
  loading,
}: {
  category?: string;
  onChange: (category?: string) => void;
  total: number;
  unread?: number;
  counts?: Record<string, number>;
  onRefresh: () => void;
  loading: boolean;
}) {
  const { styles } = useStyles();
  return (
    <div className={styles.categories}>
      <Tabs
        activeKey={category || 'ALL'}
        onChange={(key) => onChange(key === 'ALL' ? undefined : key)}
        items={[['ALL', '全部消息'], ...Object.entries(categories)].map(
          ([key, label]) => {
            const count = key === 'ALL' ? unread : counts?.[key];
            return {
              key,
              label: (
                <span>
                  {label}
                  {!!count && (
                    <small className={styles.tabCount}>
                      {count > 99 ? '99+' : count}
                    </small>
                  )}
                </span>
              ),
            };
          },
        )}
      />
      <span className={styles.count}>共 {total} 条消息</span>
      <Tooltip title="刷新消息">
        <Button
          type="text"
          aria-label="刷新消息"
          icon={<ReloadOutlined aria-hidden spin={loading} />}
          disabled={loading}
          onClick={onRefresh}
        />
      </Tooltip>
    </div>
  );
}

export function MessagePagination({
  page,
  pageSize,
  total,
  onChange,
  disabled,
}: {
  page: number;
  pageSize: number;
  total: number;
  onChange: (page: number, pageSize: number) => void;
  disabled: boolean;
}) {
  const { styles } = useStyles();
  return (
    <div className={styles.pagination}>
      <span>共 {total} 条</span>
      <Pagination
        current={page}
        pageSize={pageSize}
        total={total}
        showSizeChanger
        pageSizeOptions={[10, 20, 50, 100]}
        onChange={onChange}
        disabled={disabled}
        size="small"
        responsive
      />
    </div>
  );
}

export function MessageEmpty({
  filtered,
  sent = false,
  onClear,
}: {
  filtered: boolean;
  sent?: boolean;
  onClear: () => void;
}) {
  const { styles } = useStyles();
  return (
    <div className={styles.empty}>
      <span aria-hidden>
        <InboxOutlined />
      </span>
      <h3>
        {filtered ? '没有找到相关消息' : sent ? '暂无发送记录' : '暂无消息'}
      </h3>
      <p>
        {filtered
          ? '试试其他关键词或清除筛选'
          : sent
            ? '发送的消息将在这里展示'
            : '平台公告与系统通知将在这里展示'}
      </p>
      {filtered && <Button onClick={onClear}>清除筛选</Button>}
    </div>
  );
}

export function ReadingHeader({
  item,
  read,
}: {
  item: Omit<MessageItem, 'read'>;
  read?: boolean;
}) {
  const { styles } = useStyles();
  return (
    <>
      <div className={styles.readingMeta}>
        <CategoryTag category={item.category} />
        {read !== undefined && <ReadTag read={read} />}
      </div>
      <h2 className={styles.readingTitle}>{item.title}</h2>
      <div className={styles.byline}>
        <UserOutlined aria-hidden />
        <span>{item.senderName}</span>
        <span>·</span>
        <span>{formatMessageTime(item.createdAt)}（UTC+8）</span>
      </div>
    </>
  );
}
