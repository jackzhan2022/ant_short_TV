import { useAccess } from '@umijs/max';
import { App, Button, Drawer, Empty, Popconfirm, Spin } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useStyles } from './index.style';
import { PackageBenefits, PackageMark, PackagePrice, PackageStatus } from './presentation';
import { listCommercialPackageVersions, publishCommercialPackageVersion, unpublishCommercialPackageVersion, type CommercialPackageSummary, type CommercialPackageVersion } from './service';

const dateText = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '长期有效';

export default function PackageVersionsDrawer({ pack, revision, canCreateVersion, onClose, onCreateVersion, onChanged }: {
  pack?: CommercialPackageSummary;
  revision: number;
  canCreateVersion: boolean;
  onClose: () => void;
  onCreateVersion: (pack: CommercialPackageSummary) => void;
  onChanged: () => Promise<void>;
}) {
  const { styles } = useStyles();
  const access = useAccess();
  const { message } = App.useApp();
  const [versions, setVersions] = useState<CommercialPackageVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const requestId = useRef(0);
  const packageId = pack?.id;
  const reload = useCallback(async () => {
    if (packageId == null) return;
    const id = ++requestId.current;
    setLoading(true);
    setFailed(false);
    try {
      const result = await listCommercialPackageVersions(packageId);
      if (id === requestId.current) setVersions([...(result.data ?? [])].sort((a, b) => b.versionNo - a.versionNo));
    } catch {
      if (id === requestId.current) setFailed(true);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [packageId]);
  useEffect(() => {
    void revision;
    setVersions([]);
    void reload();
    return () => { requestId.current += 1; };
  }, [revision, reload]);
  const transition = async (version: CommercialPackageVersion) => {
    if (busy || !access.canEditCommercialPackages) return;
    setBusy(true);
    try {
      if (version.status === 'DRAFT') await publishCommercialPackageVersion(version.packageId, version.versionId);
      else await unpublishCommercialPackageVersion(version.packageId, version.versionId);
      message.success(version.status === 'DRAFT' ? '套餐版本已发布' : '套餐版本已下架');
      await Promise.all([reload(), onChanged()]);
    } finally {
      setBusy(false);
    }
  };
  const current = versions.find((version) => version.status === 'PUBLISHED') ?? versions[0];
  return <Drawer className={styles.drawer} size={720} open={Boolean(pack)} title="版本历史" onClose={onClose} footer={<div className={styles.footer}><Button onClick={onClose}>关闭</Button>{access.canEditCommercialPackages && pack && <Button type="primary" disabled={!canCreateVersion} onClick={() => onCreateVersion(pack)}>新增版本</Button>}</div>}>
    {failed ? <div className={styles.error}><p>版本加载失败</p><Button onClick={() => void reload()}>重新加载</Button></div> : <Spin spinning={loading}>
      {current && <section className={styles.summary}><header><PackageMark subscription={pack?.packageType === 'SUBSCRIPTION'} /><PackageStatus status={current.status} /></header><h3>{current.name}</h3><code>{pack?.code}</code><footer><div><span className={styles.muted}>{current.status === 'PUBLISHED' ? '在售版本' : '最新版本'} · V{current.versionNo}</span><div><PackagePrice value={current.price} currency={current.currency} /></div></div><PackageBenefits items={current.entitlements} /></footer></section>}
      {versions.length ? <section className={styles.versionList}><h3>全部版本 · {versions.length}</h3>{versions.map((version) => <article key={version.versionId} className={styles.versionCard}>
        <header><h4>V{version.versionNo}</h4><PackageStatus status={version.status} /><div className={styles.spacer} /><span className={styles.muted}>{version.name}</span></header>
        {version.description && <p>{version.description}</p>}
        <div className={styles.versionDetails}><div><small>售价{version.periodMonths ? ` · ${version.periodMonths} 个月` : ''}</small><PackagePrice value={version.price} currency={version.currency} />{version.listPrice != null && <div className={styles.muted}>划线价 {version.listPrice} {version.currency}</div>}</div><div><small>套餐权益</small><PackageBenefits items={version.entitlements} /></div></div>
        <footer><span>{dateText(version.effectiveFrom)} 起 · {dateText(version.effectiveTo)}</span>{access.canEditCommercialPackages && version.status !== 'OFF_SALE' && <Popconfirm title={version.status === 'DRAFT' ? '确认发布？' : '确认下架？'} onConfirm={() => transition(version)}><Button type="link" danger={version.status === 'PUBLISHED'} disabled={busy}>{version.status === 'DRAFT' ? '发布' : '下架'}</Button></Popconfirm>}</footer>
      </article>)}</section> : !loading && <Empty description="暂无版本" />}
    </Spin>}
  </Drawer>;
}
