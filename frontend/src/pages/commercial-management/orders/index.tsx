import { FileDoneOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, Button, Empty, Input, Select, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useState } from 'react';
import OrderDetailsDrawer from './OrderDetailsDrawer';
import { useStyles } from './index.style';
import { DateCell, OrderAmount, OrderStatus, orderStatuses, packageTypeText } from './presentation';
import { queryPlatformCommercialOrders, type CommercialOrderStatus, type CommercialPackageType, type PlatformCommercialOrderQuery, type PlatformCommercialOrderSummary } from './service';

const PlatformCommercialOrderPage = () => {
  const access = useAccess();
  const { styles } = useStyles();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<PlatformCommercialOrderQuery>({ current: 1, pageSize: 20 });
  const [data, setData] = useState<PlatformCommercialOrderSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  const [selectedOrder, setSelectedOrder] = useState<number>();
  const filtered = Boolean(keyword.trim() || query.status || query.packageType);

  useEffect(() => {
    const value = keyword.trim();
    if (value === (query.keyword ?? '')) return;
    const timer = setTimeout(() => setQuery((previous) => ({ ...previous, current: 1, keyword: value || undefined })), 300);
    return () => clearTimeout(timer);
  }, [keyword, query.keyword]);

  useEffect(() => {
    if (!access.canViewCommercialOrders) { setData([]); setTotal(0); setLoading(false); return; }
    let active = true;
    setLoading(true);
    setFailed(false);
    const params: PlatformCommercialOrderQuery = {
      current: query.current, pageSize: query.pageSize,
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(query.status ? { status: query.status } : {}),
      ...(query.packageType ? { packageType: query.packageType } : {}),
    };
    void queryPlatformCommercialOrders(params).then((response) => {
      if (!response.success) throw new Error('订单加载失败');
      if (active) { setData(response.data.records); setTotal(response.data.total); }
    }).catch(() => { if (active) { setFailed(true); setData([]); setTotal(0); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [access.canViewCommercialOrders, query, revision]);

  const reset = () => { setKeyword(''); setQuery({ current: 1, pageSize: query.pageSize }); };
  const columns: TableColumnsType<PlatformCommercialOrderSummary> = [
    { title: '订单号', dataIndex: 'merchantOrderNo', width: '24%', render: (value: string, record) => <Typography.Text className={styles.orderNumber} copyable={{ text: value, tooltips: ['复制订单号', '已复制'] }}><Button type="text" title={value} onClick={() => setSelectedOrder(record.id)}>{value}</Button></Typography.Text> },
    { title: '租户', key: 'tenant', width: '12%', render: (_, record) => <div className={styles.identity}><strong>{record.tenantName || '—'}</strong><code>{record.tenantCode || '—'}</code></div> },
    { title: '套餐 / 版本', key: 'package', width: '15%', render: (_, record) => <div className={styles.identity}><strong>{record.packageName}</strong><div className={styles.packageMeta}><span className={styles.version}>V{record.packageVersionNo}</span><span>{packageTypeText(record.packageType)}</span></div></div> },
    { title: '实付金额', key: 'amount', width: '10%', align: 'right', render: (_, record) => <OrderAmount amount={record.amount} currency={record.currency} /> },
    { title: '订单状态', dataIndex: 'status', width: '10%', render: (value: CommercialOrderStatus) => <OrderStatus status={value} /> },
    { title: '支付时间', dataIndex: 'paidAt', width: '11%', render: (value?: string) => <DateCell value={value} /> },
    { title: '创建时间', dataIndex: 'createdAt', width: '11%', render: (value: string) => <DateCell value={value} /> },
    { title: '操作', key: 'actions', width: '7%', align: 'right', render: (_, record) => <Button type="link" aria-label="查看详情" onClick={() => setSelectedOrder(record.id)}>详情</Button> },
  ];

  return <PageContainer pageHeaderRender={false}>
    <div className={styles.page}>
      <header className={styles.heading}><div><h1>订单管理</h1><p>查看订单、支付记录与套餐版本</p></div><span className={styles.headingMark}><FileDoneOutlined />商业管理</span></header>
      <section className={styles.surface} aria-label="订单列表">
        <div className={styles.toolbar}>
          <Input variant="outlined" prefix={<SearchOutlined />} allowClear aria-label="搜索订单号或租户名称" placeholder="搜索订单号或租户名称" value={keyword} disabled={!access.canViewCommercialOrders} onChange={(event) => { const value = event.target.value; setKeyword(value); if (!value.trim()) setQuery((previous) => ({ ...previous, current: 1, keyword: undefined })); }} onPressEnter={() => setQuery((previous) => ({ ...previous, current: 1, keyword: keyword.trim() || undefined }))} />
          <Select variant="outlined" aria-label="套餐类型" value={query.packageType ?? 'all'} disabled={!access.canViewCommercialOrders} options={[{ value: 'all', label: '全部套餐类型' }, { value: 'POINT_PACKAGE', label: '积分包' }, { value: 'SUBSCRIPTION', label: '会员订阅' }]} onChange={(value) => setQuery((previous) => ({ ...previous, current: 1, packageType: value === 'all' ? undefined : value as CommercialPackageType }))} />
          <div className={styles.spacer} />
          {filtered && <Button type="link" onClick={reset}>清除筛选</Button>}
          {!failed && <span className={styles.count}>{total} 笔订单</span>}
          <Button type="text" icon={<ReloadOutlined spin={loading} />} aria-label="刷新订单" title="刷新订单" disabled={loading || !access.canViewCommercialOrders} onClick={() => setRevision((value) => value + 1)} />
        </div>
        <fieldset className={styles.filters} aria-label="订单状态">{[{ value: undefined, label: '全部订单' }, ...orderStatuses].map((item) => <Button type="text" key={item.value ?? 'all'} aria-pressed={query.status === item.value} disabled={!access.canViewCommercialOrders} onClick={() => setQuery((previous) => ({ ...previous, current: 1, status: item.value }))}>{item.label}</Button>)}</fieldset>
        {failed ? <div className={styles.error}><Alert type="error" showIcon title="订单加载失败" action={<Button onClick={() => setRevision((value) => value + 1)}>重试</Button>} /></div> : <Table<PlatformCommercialOrderSummary>
          className={styles.table} rowKey="id" columns={columns} dataSource={data} loading={loading}
          rowClassName={(record) => record.id === selectedOrder ? styles.selectedRow : ''}
          scroll={{ x: 1110, y: 'calc(100dvh - 390px)', scrollToFirstRowOnChange: true }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={access.canViewCommercialOrders ? '没有匹配的订单' : '暂无订单查看权限'}>{filtered && <Button onClick={reset}>清除筛选</Button>}</Empty> }}
          pagination={{ current: query.current, pageSize: query.pageSize, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (count, range) => `第 ${range[0]}–${range[1]} 条，共 ${count} 条`, onChange: (current, pageSize) => setQuery((previous) => ({ ...previous, current: pageSize === previous.pageSize ? current : 1, pageSize })) }}
        />}
      </section>
    </div>
    <OrderDetailsDrawer orderId={access.canViewCommercialOrders ? selectedOrder : undefined} onClose={() => setSelectedOrder(undefined)} />
  </PageContainer>;
};

export default PlatformCommercialOrderPage;
