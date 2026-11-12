import { LockOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { ModalForm, PageContainer, ProFormDigit, ProFormText } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Empty, Input, Popconfirm, Space, Table, Tabs, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import PackageDraftDialog from './PackageDraftDialog';
import PackageList from './PackageList';
import { useStyles } from './index.style';
import { createDisplayEntitlement, disableDisplayEntitlement, enableDisplayEntitlement, listCommercialEntitlements, updateDisplayEntitlement, type CommercialEntitlementDefinition, type DisplayEntitlementDraft, type CommercialPackageSummary } from './service';

const EntitlementManagementContent = ({ entitlementDefinitions, onCatalogChanged }: {
  entitlementDefinitions: CommercialEntitlementDefinition[];
  onCatalogChanged: () => Promise<void>;
}) => {
  const access = useAccess();
  const { message } = App.useApp();
  const { styles } = useStyles();
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('all');
  const filtered = entitlementDefinitions.filter((item) => (category === 'all' || item.category === category) && [item.name, item.code].some((value) => value.toLowerCase().includes(keyword.trim().toLowerCase())));
  const [editing, setEditing] = useState<CommercialEntitlementDefinition | null>();

  const changeStatus = async (record: CommercialEntitlementDefinition) => {
    if (record.status === 'ACTIVE') await disableDisplayEntitlement(record.id);
    else await enableDisplayEntitlement(record.id);
    await onCatalogChanged();
    message.success(record.status === 'ACTIVE' ? '展示权益已停用' : '展示权益已启用');
  };

  const columns: TableColumnsType<CommercialEntitlementDefinition> = [
    { title: '权益名称 / 编码', key: 'identity', width: '28%', render: (_, record) => <div className={styles.identity}><div><strong>{record.name}</strong><code>{record.code}</code></div></div> },
    { title: '类别', dataIndex: 'category', render: (value) => value === 'SYSTEM' ? '系统权益' : '展示权益' },
    { title: '说明', dataIndex: 'description', render: (value) => value || '-' },
    { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', render: (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—' },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => {
        if (record.category === 'SYSTEM') return '系统预置';
        if (!access.canEditCommercialPackages) return '-';
        const action = record.status === 'ACTIVE' ? '停用' : '启用';
        return <Space>
          <Button type="link" onClick={() => setEditing(record)}>编辑</Button>
          <Popconfirm title={`确认${action}该展示权益？`} onConfirm={() => void changeStatus(record)}>
            <Button type="link" danger={record.status === 'ACTIVE'}>{action}</Button>
          </Popconfirm>
        </Space>;
      },
    },
  ];
  return <>
    <div className={styles.surface}>
      <div className={styles.toolbar}>
        <Input prefix={<SearchOutlined />} allowClear aria-label="搜索权益名称或编码" placeholder="搜索权益名称或编码" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <div className={styles.spacer} />
        <fieldset className={styles.views} aria-label="权益类别">{[{ value: 'all', label: '全部' }, { value: 'SYSTEM', label: '系统权益' }, { value: 'DISPLAY', label: '展示权益' }].map((item) => <Button key={item.value} style={{ width: 'auto', paddingInline: 12 }} type="text" aria-pressed={category === item.value} onClick={() => setCategory(item.value)}>{item.label}</Button>)}</fieldset>
        {access.canEditCommercialPackages && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新增展示权益</Button>}
      </div>
      <Table<CommercialEntitlementDefinition> className={styles.table} rowKey="id" columns={columns} dataSource={filtered} pagination={false} scroll={{ x: 950 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配权益" /> }} />
    </div>
    <p className={styles.note}><LockOutlined />系统权益只读；展示权益可编辑、启用或停用。</p>
    <ModalForm<DisplayEntitlementDraft>
      title={editing ? '编辑展示权益' : '新增展示权益'}
      open={editing !== undefined}
      initialValues={editing ?? { sortOrder: 100 }}
      modalProps={{ destroyOnHidden: true }}
      onOpenChange={(open) => { if (!open) setEditing(undefined); }}
      onFinish={async (values) => {
        if (editing) await updateDisplayEntitlement(editing.id, values);
        else await createDisplayEntitlement(values);
        await onCatalogChanged();
        message.success(editing ? '展示权益已更新' : '展示权益已创建');
        setEditing(undefined);
        return true;
      }}
    >
      <ProFormText name="name" label="权益名称" fieldProps={{ maxLength: 128 }} rules={[{ required: true, whitespace: true, message: '请输入权益名称' }]} />
      {editing && <ProFormText name="code" label="权益编码" disabled tooltip="权益编码创建后不可修改" />}
      <ProFormText name="description" label="说明" fieldProps={{ maxLength: 500 }} />
      <ProFormDigit name="sortOrder" label="排序" min={0} fieldProps={{ precision: 0 }} />
    </ModalForm>
  </>;
};

type PackageManagementTab = 'packages' | 'entitlements';

const CommercialPackageManagementPage = () => {
  const { styles } = useStyles();
  const access = useAccess();
  const [activeKey, setActiveKey] = useState<PackageManagementTab>('packages');
  const [entitlementDefinitions, setEntitlementDefinitions] = useState<CommercialEntitlementDefinition[]>([]);
  const [catalogFailed, setCatalogFailed] = useState(false);
  const [draftPackage, setDraftPackage] = useState<CommercialPackageSummary | null | undefined>(undefined);
  const [revision, setRevision] = useState(0);
  const refreshEntitlements = useCallback(async () => {
    setCatalogFailed(false);
    try {
      setEntitlementDefinitions((await listCommercialEntitlements()).data ?? []);
    } catch (error) {
      setCatalogFailed(true);
      throw error;
    }
  }, []);
  useEffect(() => { void refreshEntitlements().catch(() => undefined); }, [refreshEntitlements]);
  const canCreateVersion = entitlementDefinitions.some((definition) => definition.status === 'ACTIVE');

  return <PageContainer pageHeaderRender={false}>
    <div className={styles.page}>
      <header className={styles.heading}><div><h1>套餐管理</h1><p>管理套餐、销售版本与权益配置</p></div>{activeKey === 'packages' && access.canEditCommercialPackages && <Button type="primary" icon={<PlusOutlined />} disabled={!canCreateVersion} onClick={() => setDraftPackage(null)}>创建草稿</Button>}</header>
      {catalogFailed && <div className={styles.error}><p>权益目录加载失败</p><Button onClick={() => void refreshEntitlements().catch(() => undefined)}>重新加载权益</Button></div>}
      <Tabs className={styles.tabs} activeKey={activeKey} onChange={(key) => setActiveKey(key as PackageManagementTab)} items={[
        { key: 'packages', label: '套餐列表', children: <PackageList revision={revision} canCreateVersion={canCreateVersion} onCreateVersion={setDraftPackage} /> },
        { key: 'entitlements', label: '权益管理', children: <EntitlementManagementContent entitlementDefinitions={entitlementDefinitions} onCatalogChanged={refreshEntitlements} /> },
      ]} />
    </div>
    {draftPackage !== undefined && access.canEditCommercialPackages && <PackageDraftDialog key={draftPackage?.code ?? 'new'} pack={draftPackage} definitions={entitlementDefinitions} onClose={() => setDraftPackage(undefined)} onCreated={() => setRevision((value) => value + 1)} />}
  </PageContainer>;
};

export default CommercialPackageManagementPage;
