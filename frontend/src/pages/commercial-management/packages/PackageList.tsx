import { AppstoreOutlined, InfoCircleOutlined, MoreOutlined, ReloadOutlined, SearchOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Button, Dropdown, Empty, Input, Pagination, Select, Spin, Table } from 'antd';
import type { TableColumnsType } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useStyles } from './index.style';
import { PackageBenefits, PackageMark, PackagePrice, PackageStatus } from './presentation';
import { listCommercialPackages, type CommercialPackageSummary } from './service';
import PackageVersionsDrawer from './PackageVersionsDrawer';

const statuses = [
  { value: 'all', label: '全部套餐' },
  { value: 'PUBLISHED', label: '销售中' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'OFF_SALE', label: '已下架' },
];

export default function PackageList({ revision, canCreateVersion, onCreateVersion }: {
  revision: number;
  canCreateVersion: boolean;
  onCreateVersion: (pack: CommercialPackageSummary) => void;
}) {
  const { styles } = useStyles();
  const access = useAccess();
  const [packages, setPackages] = useState<CommercialPackageSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [packageType, setPackageType] = useState('all');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('sale');
  const [view, setView] = useState<'table' | 'cards'>('table');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [selected, setSelected] = useState<CommercialPackageSummary>();
  const requestId = useRef(0);
  const reload = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setFailed(false);
    try {
      const result = await listCommercialPackages();
      if (id === requestId.current) setPackages(result.data ?? []);
    } catch {
      if (id === requestId.current) setFailed(true);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void revision;
    void reload();
    return () => { requestId.current += 1; };
  }, [revision, reload]);

  const matching = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    return packages.filter((pack) => (!query || [pack.code, pack.latestName].some((value) => value?.toLowerCase().includes(query))) && (packageType === 'all' || pack.packageType === packageType));
  }, [packages, keyword, packageType]);
  const filtered = useMemo(() => {
    const result = matching.filter((pack) => status === 'all' || pack.latestStatus === status);
    if (sort === 'sale') result.sort((a, b) => Number(b.latestStatus === 'PUBLISHED') - Number(a.latestStatus === 'PUBLISHED'));
    if (sort === 'price') result.sort((a, b) => (a.latestPrice ?? Number.POSITIVE_INFINITY) - (b.latestPrice ?? Number.POSITIVE_INFINITY));
    return result;
  }, [matching, status, sort]);
  const resetPage = (change: () => void) => { change(); setPage(1); };
  const visible = filtered.slice((page - 1) * pageSize, page * pageSize);
  const actions = (record: CommercialPackageSummary) => <div className={styles.rowActions}>
    <Button type="link" onClick={() => setSelected(record)}>查看版本</Button>
    {access.canEditCommercialPackages && <Dropdown trigger={['click']} menu={{ items: [{ key: 'create', label: '新增版本', disabled: !canCreateVersion }], onClick: () => onCreateVersion(record) }}><Button type="text" aria-label="更多操作" icon={<MoreOutlined />} /></Dropdown>}
  </div>;
  const columns: TableColumnsType<CommercialPackageSummary> = [
    { title: '套餐名称 / 编码', key: 'name', width: '28%', render: (_, record) => <div className={styles.identity}><PackageMark subscription={record.packageType === 'SUBSCRIPTION'} /><div><strong>{record.latestName ?? '未命名草稿'}</strong><code>{record.code}</code></div></div> },
    { title: '套餐类型', dataIndex: 'packageType', width: '12%', render: (value) => <span className={styles.muted}>{value === 'SUBSCRIPTION' ? '会员订阅' : '积分包'}</span> },
    { title: '售价', key: 'price', width: '12%', render: (_, record) => <PackagePrice value={record.latestPrice} currency={record.latestCurrency} /> },
    { title: '套餐权益', key: 'benefits', width: '17%', render: (_, record) => <PackageBenefits items={record.latestEntitlements} /> },
    { title: '版本', key: 'version', width: '7%', render: (_, record) => <span className={styles.version}>{record.latestVersionNo ? `V${record.latestVersionNo}` : '—'}</span> },
    { title: '销售状态', key: 'status', width: '10%', render: (_, record) => <PackageStatus status={record.latestStatus} /> },
    { title: '操作', key: 'actions', align: 'right', width: '14%', render: (_, record) => actions(record) },
  ];

  return <>
    <div className={styles.surface}>
      <div className={styles.toolbar}>
        <Input prefix={<SearchOutlined />} allowClear aria-label="搜索套餐名称或编码" placeholder="搜索套餐名称或编码" value={keyword} onChange={(event) => resetPage(() => setKeyword(event.target.value))} />
        <Select aria-label="套餐类型" style={{ width: 125 }} value={packageType} onChange={(value) => resetPage(() => setPackageType(value))} options={[{ label: '全部类型', value: 'all' }, { label: '积分包', value: 'POINT_PACKAGE' }, { label: '会员订阅', value: 'SUBSCRIPTION' }]} />
        <div className={styles.spacer} />
        <Select aria-label="套餐排序" style={{ width: 145 }} value={sort} onChange={(value) => resetPage(() => setSort(value))} options={[{ label: '销售中优先', value: 'sale' }, { label: '默认排序', value: 'original' }, { label: '价格从低到高', value: 'price' }]} />
        <fieldset className={styles.views} aria-label="展示方式"><Button type="text" aria-label="表格展示" aria-pressed={view === 'table'} icon={<UnorderedListOutlined />} onClick={() => setView('table')} /><Button type="text" aria-label="轻卡片展示" aria-pressed={view === 'cards'} icon={<AppstoreOutlined />} onClick={() => setView('cards')} /></fieldset>
        <Button type="text" aria-label="刷新列表" icon={<ReloadOutlined spin={loading} />} onClick={() => void reload()} disabled={loading} />
      </div>
      <fieldset className={styles.filters} aria-label="销售状态">{statuses.map((item) => <Button key={item.value} type="text" size="small" aria-pressed={status === item.value} onClick={() => resetPage(() => setStatus(item.value))}><span>{item.label}</span><span className="filter-count">{item.value === 'all' ? matching.length : matching.filter((pack) => pack.latestStatus === item.value).length}</span></Button>)}</fieldset>
      {failed ? <div className={styles.error}><p>套餐加载失败</p><Button onClick={() => void reload()}>重新加载</Button></div> : view === 'table' ? <Table<CommercialPackageSummary> className={styles.table} rowKey="id" columns={columns} dataSource={visible} loading={loading} pagination={false} scroll={{ x: 1000 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配套餐" /> }} /> : <Spin spinning={loading}>{visible.length ? <div className={styles.grid}>{visible.map((pack) => <article className={styles.card} key={pack.id}>
        <header><PackageMark subscription={pack.packageType === 'SUBSCRIPTION'} /><PackageStatus status={pack.latestStatus} /></header><h3>{pack.latestName ?? '未命名草稿'}</h3><code>{pack.code}</code><p>{pack.packageType === 'SUBSCRIPTION' ? '会员订阅' : '积分包'}</p>
        <div className={styles.cardPrice}><PackagePrice value={pack.latestPrice} currency={pack.latestCurrency} /></div><div className={styles.cardBenefits}><PackageBenefits items={pack.latestEntitlements} /></div>
        <footer><span className={styles.version}>{pack.latestVersionNo ? `V${pack.latestVersionNo}` : '—'}</span>{actions(pack)}</footer>
      </article>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配套餐" />}</Spin>}
      <div className={styles.toolbar}><span className={styles.muted}>共 {filtered.length} 个套餐</span><div className={styles.spacer} /><Pagination size="small" current={page} pageSize={pageSize} total={filtered.length} showSizeChanger onChange={(current, size) => { setPage(size === pageSize ? current : 1); setPageSize(size); }} /></div>
    </div>
    <p className={styles.note}><InfoCircleOutlined />套餐内容通过新版本更新，历史版本保留可查。</p>
    <PackageVersionsDrawer pack={selected} revision={revision} canCreateVersion={canCreateVersion} onClose={() => setSelected(undefined)} onCreateVersion={onCreateVersion} onChanged={reload} />
  </>;
}
