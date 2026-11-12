import { ClockCircleOutlined, CrownOutlined, GiftOutlined, TeamOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Alert, Button, Drawer, Table, Tabs, Tag } from 'antd';
import { type ReactNode, useEffect, useState } from 'react';
import { formatPoints } from '@/utils/points';
import { useStyles } from './index.style';
import { DateCell, dateTime, PackageGlance, packageTypeText, TenantCode, TenantMark, TenantStatus, tenantTypeText } from './presentation';
import { getPlatformTenant, type PlatformTenantDetail, type PlatformTenantMember, type PlatformTenantPackage } from './service';

const PackageCard = ({ value, queued = false }: { value: PlatformTenantPackage; queued?: boolean }) => {
  const { styles } = useStyles();
  return <article className={styles.packageCard}>
    <div>{queued ? '待生效' : '当前生效'}</div><header><h4>{value.name}</h4><Tag>{packageTypeText(value.packageType)}</Tag></header>
    <dl className={styles.packageDates}><div><dt>开始时间</dt><dd>{dateTime(value.startsAt)}</dd></div><div><dt>结束时间</dt><dd>{value.packageType === 'TRIAL' ? '无到期时间' : dateTime(value.endsAt)}</dd></div></dl>
  </article>;
};

type Props = {
  tenantId?: number;
  revision: number;
  onClose: () => void;
  onImpersonate: (tenant: PlatformTenantDetail, member: PlatformTenantMember) => void;
  actions: (tenant: PlatformTenantDetail) => ReactNode;
};

export default function TenantDetailsDrawer({ tenantId, revision, onClose, onImpersonate, actions }: Props) {
  const access = useAccess();
  const { styles } = useStyles();
  const [detail, setDetail] = useState<PlatformTenantDetail>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [tab, setTab] = useState('overview');
  useEffect(() => { setTab('overview'); }, [tenantId]);
  useEffect(() => {
    if (tenantId === undefined) return;
    let active = true;
    setFailed(false); setLoading(true);
    void getPlatformTenant(tenantId).then((response) => {
      if (!response.success) throw new Error('租户详情加载失败');
      if (active) setDetail(response.data);
    }).catch(() => { if (active) setFailed(true); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tenantId, revision, attempt]);
  const current = detail?.id === tenantId ? detail : undefined;
  const members = current?.members ?? [];
  return <Drawer title="租户详情" open={tenantId !== undefined} size={760} loading={loading} closable={{ placement: 'end' }} destroyOnHidden className={styles.drawer} onClose={onClose}
    footer={<div className={styles.footer}><div>{!failed && !loading && current && actions(current)}</div><Button onClick={onClose}>关闭</Button></div>}>
    {failed ? <Alert type="error" showIcon title="租户详情加载失败" action={<Button onClick={() => setAttempt((value) => value + 1)}>重试</Button>} /> : current && <>
      <header className={styles.drawerHero}><TenantMark tenant={current} large /><div><h3>{current.name}</h3><TenantCode code={current.code} /></div><TenantStatus active={current.status === 'ACTIVE'}>{current.status === 'ACTIVE' ? '已启用' : '已停用'}</TenantStatus></header>
      <dl className={styles.metrics}><div><dt>积分余额</dt><dd>{formatPoints(Number(current.pointBalance))}</dd></div><div><dt>有效成员</dt><dd>{current.activeMemberCount}</dd></div><div><dt>试用专属积分</dt><dd>{formatPoints(Number(current.trialPointBalance ?? 0))}</dd></div></dl>
      <Tabs activeKey={tab} onChange={setTab} className={styles.tabs} items={[
        { key: 'overview', label: '概览', children: <>
          <section className={styles.detailSection}><h3><TeamOutlined />基本信息</h3><dl className={styles.detailGrid}>
            {[['负责人', current.owner?.nickname], ['租户类型', tenantTypeText(current.type)], ['负责人手机号', current.owner?.mobile], ['创建时间', dateTime(current.createdAt)], ['更新时间', dateTime(current.updatedAt)], ['简介', current.description]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}
          </dl></section>
          <section className={styles.detailSection}><h3><CrownOutlined />当前套餐</h3><PackageGlance value={current.currentPackage} />{current.currentPackage?.packageType === 'TRIAL' && <p className={styles.detailTip}>试用专属积分不可用于视频生成。</p>}</section>
        </> },
        { key: 'members', label: <>成员<span className={styles.tabCount}>{members.length}</span></>, children: <>
          <p className={styles.sectionCaption}>全部成员 · {members.length} 人</p>
          <Table<PlatformTenantMember> className={styles.membersTable} rowKey="memberId" size="small" dataSource={members} scroll={{ x: 610 }} pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }} columns={[
            { title: '成员', key: 'identity', width: 180, render: (_, member) => <div className={styles.member}><strong>{member.nickname || '—'}</strong><small>{member.memberType === 'OWNER' ? 'Owner' : '成员'}{member.mobile ? ` · ${member.mobile}` : ''}</small></div> },
            { title: '成员状态', dataIndex: 'status', width: 90, render: (value) => <TenantStatus active={value === 'ACTIVE'}>{value === 'ACTIVE' ? '有效' : '已移除'}</TenantStatus> },
            { title: '账号状态', dataIndex: 'userStatus', width: 90, render: (value: PlatformTenantMember['userStatus']) => <TenantStatus active={value === 'ACTIVE'}>{{ ACTIVE: '正常', DISABLED: '已停用', DELETED: '已删除' }[value]}</TenantStatus> },
            { title: '加入时间', dataIndex: 'joinedAt', width: 125, render: (value) => <DateCell value={value} /> },
            ...(access.canImpersonateTenantMember ? [{ title: '操作', key: 'actions', width: 90, align: 'right' as const, render: (_: unknown, member: PlatformTenantMember) => <Button type="link" disabled={current.status !== 'ACTIVE' || !member.canImpersonate} onClick={() => onImpersonate(current, member)}>切换登录</Button> }] : []),
          ]} />
        </> },
        { key: 'packages', label: '套餐', children: <>
          <section className={styles.detailSection}><h3><CrownOutlined />当前套餐</h3>{current.currentPackage ? <PackageCard value={current.currentPackage} /> : <div className={styles.quietEmpty}><GiftOutlined />暂无套餐</div>}{current.currentPackage?.packageType === 'TRIAL' && <p className={styles.detailTip}>试用专属积分不可用于视频生成。</p>}</section>
          <section className={styles.detailSection}><h3><ClockCircleOutlined />待生效套餐</h3>{current.queuedPackages.length ? <div className={styles.packageQueue}>{current.queuedPackages.map((value) => <PackageCard key={value.subscriptionId} value={value} queued />)}</div> : <div className={styles.quietEmpty}><ClockCircleOutlined />暂无待生效套餐</div>}</section>
        </> },
      ]} />
    </>}
  </Drawer>;
}
