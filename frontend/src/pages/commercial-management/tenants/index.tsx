import { ApartmentOutlined, GiftOutlined, InfoCircleOutlined, MoreOutlined, ReloadOutlined, SearchOutlined, SafetyOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, App, Button, Dropdown, Empty, Input, Modal, Select, Table } from 'antd';
import type { MenuProps, TableColumnsType } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { formatPoints } from '@/utils/points';
import GrantPackageModal from './GrantPackageModal';
import ImpersonationModal from './ImpersonationModal';
import TenantDetailsDrawer from './TenantDetailsDrawer';
import { useStyles } from './index.style';
import { DateCell, PackageCell, TenantCode, TenantMark, TenantStatus } from './presentation';
import { assignPlatformTenantTrial, queryPlatformTenants, updatePlatformTenantStatus, type CommercialPackageType, type PlatformTenantDetail, type PlatformTenantMember, type PlatformTenantQuery, type PlatformTenantSummary, type PlatformTenantStatus } from './service';

type Confirmation = { tenant: PlatformTenantSummary; kind: 'trial' | 'status' };

export default function PlatformTenantManagementPage() {
  const access = useAccess();
  const { message } = App.useApp();
  const { styles } = useStyles();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<PlatformTenantQuery>({ current: 1, pageSize: 20 });
  const [data, setData] = useState<PlatformTenantSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  const [tenantId, setTenantId] = useState<number>();
  const [grantTenant, setGrantTenant] = useState<PlatformTenantSummary>();
  const [impersonation, setImpersonation] = useState<{ tenant: PlatformTenantDetail; member: PlatformTenantMember }>();
  const [confirmation, setConfirmation] = useState<Confirmation>();
  const [busy, setBusy] = useState(false);
  const [operationFailed, setOperationFailed] = useState(false);
  const submitting = useRef(false);
  const filtered = Boolean(keyword.trim() || query.status || query.packageType);

  useEffect(() => {
    const value = keyword.trim();
    if (value === (query.keyword ?? '')) return;
    const timer = setTimeout(() => setQuery((previous) => ({ ...previous, current: 1, keyword: value || undefined })), 300);
    return () => clearTimeout(timer);
  }, [keyword, query.keyword]);
  useEffect(() => {
    if (!access.canViewPlatformTenants) { setData([]); setTotal(0); setLoading(false); return; }
    let active = true;
    setLoading(true); setFailed(false);
    const params: PlatformTenantQuery = { current: query.current, pageSize: query.pageSize, ...(query.keyword ? { keyword: query.keyword } : {}), ...(query.status ? { status: query.status } : {}), ...(query.packageType ? { packageType: query.packageType } : {}) };
    void queryPlatformTenants(params).then((response) => {
      if (!response.success) throw new Error('租户加载失败');
      if (active) { setData(response.data.records); setTotal(response.data.total); }
    }).catch(() => { if (active) { setFailed(true); setData([]); setTotal(0); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [access.canViewPlatformTenants, query, revision]);

  const refresh = () => setRevision((value) => value + 1);
  const reset = () => { setKeyword(''); setQuery({ current: 1, pageSize: query.pageSize }); };
  const ask = (tenant: PlatformTenantSummary, kind: Confirmation['kind']) => { setOperationFailed(false); setConfirmation({ tenant, kind }); };
  const confirm = async () => {
    if (!confirmation || submitting.current || !access.canViewPlatformTenants) return;
    const { tenant, kind } = confirmation;
    if (kind === 'trial' ? !access.canAssignPlatformTenantTrial || Boolean(tenant.currentPackage) || tenant.trialAssigned : !access.canEditPlatformTenantStatus) return;
    submitting.current = true; setBusy(true); setOperationFailed(false);
    try {
      const status: PlatformTenantStatus = tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
      const response = kind === 'trial' ? await assignPlatformTenantTrial(tenant.id) : await updatePlatformTenantStatus(tenant.id, status);
      if (!response.success) throw new Error('操作失败');
      message.success(kind === 'trial' ? '试用套餐已分配，3000 专属积分已到账' : status === 'ACTIVE' ? '租户已启用' : '租户已停用');
      setConfirmation(undefined); refresh();
    } catch { setOperationFailed(true); } finally { submitting.current = false; setBusy(false); }
  };
  const more = (tenant: PlatformTenantSummary, includeGrant = true) => {
    const items: MenuProps['items'] = [];
    if (includeGrant && access.canGrantPlatformTenantPackage) items.push({ key: 'grant', label: '添加套餐/积分包', icon: <GiftOutlined aria-hidden />, disabled: tenant.status !== 'ACTIVE' });
    if (access.canAssignPlatformTenantTrial && !tenant.currentPackage && !tenant.trialAssigned) items.push({ key: 'trial', label: '分配试用套餐', icon: <GiftOutlined aria-hidden /> });
    if (access.canEditPlatformTenantStatus) {
      if (items.length) items.push({ type: 'divider' });
      items.push({ key: 'status', label: tenant.status === 'ACTIVE' ? '停用租户' : '启用租户', icon: <SafetyOutlined aria-hidden />, danger: tenant.status === 'ACTIVE' });
    }
    if (!items.length) return null;
    return <Dropdown trigger={['click']} menu={{ items, onClick: ({ key }) => { if (key === 'grant') setGrantTenant(tenant); else ask(tenant, key === 'trial' ? 'trial' : 'status'); } }}>
      <Button type="text" className={styles.more} icon={<MoreOutlined />} aria-label={includeGrant ? `${tenant.name}的更多操作` : '更多租户操作'} />
    </Dropdown>;
  };
  const columns: TableColumnsType<PlatformTenantSummary> = [
    { title: '租户', key: 'identity', width: '22%', render: (_, tenant) => <div className={styles.identity}><TenantMark tenant={tenant} /><div><Button type="text" className={styles.tenantName} title={tenant.name} onClick={() => setTenantId(tenant.id)}>{tenant.name}</Button><TenantCode code={tenant.code} /></div></div> },
    { title: '状态', dataIndex: 'status', width: '10%', render: (status) => <TenantStatus active={status === 'ACTIVE'}>{status === 'ACTIVE' ? '已启用' : '已停用'}</TenantStatus> },
    { title: '负责人', key: 'owner', width: '13%', render: (_, tenant) => <div className={styles.owner} title={tenant.owner?.nickname}>{tenant.owner?.nickname || '—'}</div> },
    { title: '成员', dataIndex: 'activeMemberCount', width: '6%', align: 'center' },
    { title: '积分余额', dataIndex: 'pointBalance', width: '11%', align: 'right', render: (value) => <span className={styles.balance} data-zero={Number(value) === 0}>{formatPoints(Number(value))}</span> },
    { title: '当前套餐', key: 'package', width: '18%', render: (_, tenant) => <PackageCell value={tenant.currentPackage} /> },
    { title: '创建时间', dataIndex: 'createdAt', width: '11%', render: (value) => <DateCell value={value} /> },
    { title: '操作', key: 'actions', align: 'right', width: '9%', render: (_, tenant) => <div className={styles.rowActions}><Button type="link" aria-label="查看详情" onClick={() => setTenantId(tenant.id)}>详情</Button>{more(tenant)}</div> },
  ];
  const trial = confirmation?.kind === 'trial';
  const disabling = confirmation?.tenant.status === 'ACTIVE';
  return <PageContainer pageHeaderRender={false}>
    <div className={styles.page}><header className={styles.heading}><div><h1>租户管理</h1><p>管理团队、成员与套餐权益</p></div><span className={styles.headingMark}><ApartmentOutlined />商业管理</span></header>
      <section className={styles.surface} aria-label="租户列表"><div className={styles.toolbar}>
        <Input variant="outlined" prefix={<SearchOutlined />} allowClear aria-label="搜索租户名称或编码" placeholder="搜索租户名称或编码" disabled={!access.canViewPlatformTenants} value={keyword} onChange={(event) => { const value = event.target.value; setKeyword(value); if (!value.trim()) setQuery((previous) => ({ ...previous, current: 1, keyword: undefined })); }} onPressEnter={() => setQuery((previous) => ({ ...previous, current: 1, keyword: keyword.trim() || undefined }))} />
        <Select variant="outlined" aria-label="当前套餐类型" disabled={!access.canViewPlatformTenants} value={query.packageType ?? 'all'} options={[{ value: 'all', label: '全部套餐类型' }, { value: 'TRIAL', label: '试用套餐' }, { value: 'SUBSCRIPTION', label: '会员订阅' }, { value: 'POINT_PACKAGE', label: '积分包' }]} onChange={(value) => setQuery((previous) => ({ ...previous, current: 1, packageType: value === 'all' ? undefined : value as CommercialPackageType }))} />
        <div className={styles.spacer} />{filtered && <Button type="link" onClick={reset}>清除筛选</Button>}{!failed && <span className={styles.count}>{total} 个租户</span>}<Button type="text" icon={<ReloadOutlined spin={loading} />} aria-label="刷新租户" disabled={loading || !access.canViewPlatformTenants} onClick={refresh} />
      </div><div className={styles.filterLine}><fieldset className={styles.filters} aria-label="租户状态">{[{ value: undefined, label: '全部租户' }, { value: 'ACTIVE' as const, label: '已启用' }, { value: 'DISABLED' as const, label: '已停用' }].map((item) => <Button key={item.value ?? 'all'} type="text" aria-pressed={query.status === item.value} disabled={!access.canViewPlatformTenants} onClick={() => setQuery((previous) => ({ ...previous, current: 1, status: item.value }))}>{item.label}</Button>)}</fieldset><span className={styles.hint}>试用积分不支持视频生成<InfoCircleOutlined /></span></div>
        {failed ? <div className={styles.error}><Alert type="error" showIcon title="租户加载失败" action={<Button onClick={refresh}>重试</Button>} /></div> : <Table<PlatformTenantSummary> className={styles.table} rowKey="id" columns={columns} dataSource={data} loading={loading} rowClassName={(tenant) => tenant.id === tenantId ? styles.selectedRow : ''} scroll={{ x: 1100, y: 'calc(100dvh - 390px)', scrollToFirstRowOnChange: true }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={access.canViewPlatformTenants ? '没有匹配的租户' : '暂无租户查看权限'}>{filtered && <Button onClick={reset}>清除筛选</Button>}</Empty> }} pagination={{ current: query.current, pageSize: query.pageSize, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (count, range) => `第 ${range[0]}–${range[1]} 条，共 ${count} 条`, onChange: (current, pageSize) => setQuery((previous) => ({ ...previous, current: pageSize === previous.pageSize ? current : 1, pageSize })) }} />}
      </section>
    </div>
    <TenantDetailsDrawer tenantId={access.canViewPlatformTenants ? tenantId : undefined} revision={revision} onClose={() => { setTenantId(undefined); setImpersonation(undefined); }} onImpersonate={(tenant, member) => setImpersonation({ tenant, member })} actions={(tenant) => <>{access.canGrantPlatformTenantPackage && <Button type="link" icon={<GiftOutlined aria-hidden />} disabled={tenant.status !== 'ACTIVE'} onClick={() => setGrantTenant(tenant)}>添加套餐/积分包</Button>}{more(tenant, false)}</>} />
    <Modal open={Boolean(confirmation) && access.canViewPlatformTenants} title={trial ? '分配试用套餐' : disabling ? '确认停用租户' : '确认启用租户'} centered className={styles.confirm} width={460} confirmLoading={busy} okText={trial ? '确认分配' : disabling ? '确认停用' : '确认启用'} cancelText="取消" okButtonProps={{ danger: !trial && disabling }} cancelButtonProps={{ disabled: busy }} closable={!busy} keyboard={!busy} mask={{ closable: false }} onOk={() => void confirm()} onCancel={() => { if (!submitting.current) setConfirmation(undefined); }} destroyOnHidden>
      <strong>{confirmation?.tenant.name}</strong><p>{trial ? '每个租户限领一次，包含 3000 专属积分，不可用于视频生成。' : disabling ? '停用后，该租户成员将无法进入团队业务。' : '启用后，该租户的有效成员可重新进入团队业务。'}</p>{operationFailed && <Alert type="error" showIcon title="操作失败，请重试" />}
    </Modal>
    {access.canViewPlatformTenants && access.canGrantPlatformTenantPackage && grantTenant && <GrantPackageModal tenant={grantTenant} onClose={() => setGrantTenant(undefined)} onGranted={() => { setGrantTenant(undefined); message.success('发放成功，套餐或积分包已生效'); refresh(); }} />}
    {access.canViewPlatformTenants && access.canImpersonateTenantMember && impersonation && tenantId !== undefined && <ImpersonationModal tenant={impersonation.tenant} member={impersonation.member} onClose={() => setImpersonation(undefined)} />}
  </PageContainer>;
}
