import { Button, Card, Empty, Space, Statistic, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ReactNode } from 'react';
import type { HistogramBin, Page, Snapshot } from './service';

export const time = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat('zh-CN', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }).format(new Date(value))
    : '无数据';
export function value(value: unknown): ReactNode {
  if (value == null || value === '') return '无数据';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'number')
    return Number.isFinite(value)
      ? value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
      : '无数据';
  return String(value);
}
export function columns<T extends object>(
  fields: [keyof T, string][],
): ColumnsType<T> {
  return fields.map(([field, title]) => ({
    key: String(field),
    dataIndex: String(field),
    title,
    ellipsis: true,
    width: 160,
    render: (item: unknown) =>
      String(field).endsWith('At') ||
      String(field) === 'at' ||
      String(field) === 'silenceUntil'
        ? time(typeof item === 'string' ? item : null)
        : value(item),
  }));
}
export function Metrics({ items }: { items: [string, unknown, string?][] }) {
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {items.map(([title, item, suffix]) => (
        <Card key={title} size="small">
          <Statistic
            title={title}
            value={
              item == null
                ? '无数据'
                : typeof item === 'number' || typeof item === 'string'
                  ? item
                  : String(item)
            }
            precision={
              typeof item === 'number' && !Number.isInteger(item)
                ? 2
                : undefined
            }
            suffix={item == null ? undefined : suffix}
          />
        </Card>
      ))}
    </div>
  );
}
export function DataTable<T extends object>({
  title,
  data,
  columns: definitions,
  rowKey,
  loading,
  next,
  previous,
  hasPrevious,
  extra,
}: {
  title: string;
  data?: Page<T>;
  columns: ColumnsType<T>;
  rowKey: (row: T) => string | number;
  loading?: boolean;
  next?: () => void;
  previous?: () => void;
  hasPrevious?: boolean;
  extra?: ReactNode;
}) {
  return (
    <Card title={title} size="small" extra={extra}>
      <Table<T>
        size="small"
        columns={definitions}
        dataSource={data?.items ?? []}
        rowKey={rowKey}
        pagination={false}
        loading={loading}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: '保留期内无明细；历史聚合可能仍可查询' }}
      />
      <Space className="mt-3" wrap>
        <Typography.Text type="secondary">
          匹配记录 {data?.total ?? '无数据'} · 每页20条
        </Typography.Text>
        {previous && (
          <Button disabled={!hasPrevious || loading} onClick={previous}>
            上一页
          </Button>
        )}
        {next && (
          <Button disabled={!data?.nextCursor || loading} onClick={next}>
            下一页
          </Button>
        )}
      </Space>
    </Card>
  );
}

export function Trend({ trend }: { trend: Snapshot['trend'] }) {
  const series = [
    ['requests', '请求数', '#1677ff'],
    ['errors', '技术错误数', '#ff4d4f'],
    ['p95Ms', '近似P95（ms）', '#722ed1'],
  ] as const;
  return (
    <Card title="请求与延迟趋势" size="small">
      {!trend.length ? (
        <Empty description="此范围没有趋势样本" />
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          {series.map(([key, label, color]) => {
            const observed = trend.flatMap((point) =>
              point[key] == null ? [] : [point[key]],
            );
            const max = observed.length ? Math.max(...observed) : null;
            const points = trend.map((point, index) =>
              point[key] == null
                ? null
                : `${20 + (index * 440) / Math.max(1, trend.length - 1)},${130 - ((point[key] ?? 0) * 110) / Math.max(1, max ?? 0)}`,
            );
            const segments: string[][] = [[]];
            for (const point of points) {
              if (point === null) segments.push([]);
              else segments[segments.length - 1].push(point);
            }
            return (
              <div key={key}>
                <Typography.Text>
                  {label} · 最大 {value(max)}
                </Typography.Text>
                <svg
                  role="img"
                  aria-label={label}
                  viewBox="0 0 480 160"
                  style={{ width: '100%' }}
                >
                  <title>{label}，空窗不连接</title>
                  <path d="M20 20V130H460" fill="none" stroke="#d9d9d9" />
                  {segments
                    .filter((segment) => segment.length)
                    .map((segment) => (
                      <polyline
                        key={segment[0]}
                        points={segment.join(' ')}
                        fill="none"
                        stroke={color}
                        strokeWidth="2"
                      />
                    ))}
                </svg>
                <Typography.Text type="secondary">
                  {time(trend[0].at)} — {time(trend[trend.length - 1].at)}
                </Typography.Text>
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );
}

export function Histogram({ bins }: { bins?: HistogramBin[] }) {
  const maximum = Math.max(1, ...(bins ?? []).map((bin) => bin.count));
  return (
    <Card title="延迟分布（固定桶，上界ms）" size="small">
      {!bins?.length ? (
        <Empty description="没有直方图样本" />
      ) : (
        <div className="flex flex-wrap items-end gap-2">
          {bins.map((bin) => (
            <div key={bin.upperBoundMs ?? 'overflow'} style={{ minWidth: 44 }}>
              <Typography.Text>{value(bin.count)}</Typography.Text>
              <div
                style={{
                  background: '#1677ff',
                  height: Math.max(2, (bin.count / maximum) * 100),
                }}
              />
              <Typography.Text type="secondary">
                {bin.upperBoundMs == null ? '>1800000' : `≤${bin.upperBoundMs}`}
              </Typography.Text>
            </div>
          ))}
        </div>
      )}
      <Typography.Paragraph type="secondary" className="mt-3">
        分位数由固定桶近似计算；最后一个桶表示超过30分钟的请求。缺样本时不推算延迟。
      </Typography.Paragraph>
    </Card>
  );
}
