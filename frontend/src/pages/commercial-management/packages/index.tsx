import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormDateTimePicker,
  ProFormDependency,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormText,
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Drawer, Empty, Popconfirm, Space, Table, Tabs, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createDisplayEntitlement,
  createCommercialPackageDraft,
  disableDisplayEntitlement,
  enableDisplayEntitlement,
  listCommercialEntitlements,
  listCommercialPackages,
  listCommercialPackageVersions,
  publishCommercialPackageVersion,
  unpublishCommercialPackageVersion,
  updateDisplayEntitlement,
  type CommercialEntitlementDefinition,
  type DisplayEntitlementDraft,
  type CommercialPackageDraft,
  type CommercialPackageSummary,
  type CommercialPackageVersion,
} from './service';
import { entitlementTypeText, statusText } from '@/utils/fieldDictionary';
import {
  activeEntitlementOptions,
  entitlementRequiresValue,
  normalizePackageEntitlements,
} from './entitlementForm';

const billingPeriodOptions = [
  { label: '月', value: 'MONTH' },
  { label: '季', value: 'QUARTER' },
  { label: '半年', value: 'HALF_YEAR' },
  { label: '年', value: 'YEAR' },
];
const periodMonthOptions = [
  { label: '1 个月', value: 1 },
  { label: '3 个月', value: 3 },
  { label: '6 个月', value: 6 },
  { label: '12 个月', value: 12 },
];

type CommercialPackageQuery = {
  keyword?: string;
  packageType?: CommercialPackageSummary['packageType'];
  latestStatus?: CommercialPackageVersion['status'];
};

const PackageListContent = ({ entitlementDefinitions }: {
  entitlementDefinitions: CommercialEntitlementDefinition[];
}) => {
  const access = useAccess();
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const [selected, setSelected] = useState<CommercialPackageSummary>();
  const [versions, setVersions] = useState<CommercialPackageVersion[]>([]);
  const [draftPackage, setDraftPackage] = useState<CommercialPackageSummary | null | undefined>(undefined);

  const loadVersions = async (pack: CommercialPackageSummary) => {
    setSelected(pack);
    setVersions((await listCommercialPackageVersions(pack.id)).data ?? []);
  };

  const transition = async (version: CommercialPackageVersion, action: 'publish' | 'unpublish') => {
    if (action === 'publish') await publishCommercialPackageVersion(version.packageId, version.versionId);
    else await unpublishCommercialPackageVersion(version.packageId, version.versionId);
    message.success(action === 'publish' ? '套餐版本已发布' : '套餐版本已下架');
    if (selected) await loadVersions(selected);
  };

  const createDraft = async (values: CommercialPackageDraft) => {
    await createCommercialPackageDraft({
      ...values,
      currency: values.currency ?? 'CNY',
      entitlements: normalizePackageEntitlements(values.entitlements, entitlementDefinitions),
    });
    message.success('套餐草稿已创建');
    actionRef.current?.reload();
    return true;
  };

  const latestStatusTag = (status?: CommercialPackageVersion['status']) => {
    if (!status) return <Tag>未建版本</Tag>;
    const color = status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'default';
    return <Tag color={color}>{statusText(status)}</Tag>;
  };
  const isVersionDraft = Boolean(draftPackage);
  const columns: ProColumns<CommercialPackageSummary>[] = [
    { title: '套餐名称或编码', dataIndex: 'keyword', hideInTable: true },
    { title: '套餐类型', dataIndex: 'packageType', hideInTable: true, valueType: 'select', valueEnum: { POINT_PACKAGE: { text: '积分包' }, SUBSCRIPTION: { text: '会员订阅' } } },
    { title: '销售状态', dataIndex: 'latestStatus', hideInTable: true, valueType: 'select', valueEnum: { DRAFT: { text: '草稿' }, PUBLISHED: { text: '销售中' }, OFF_SALE: { text: '已下架' } } },
    { title: '套餐名称', dataIndex: 'latestName', search: false, render: (_, record) => <Space orientation="vertical" size={0}><Typography.Text strong>{record.latestName ?? '未命名草稿'}</Typography.Text><Typography.Text type="secondary">{record.code}</Typography.Text></Space> },
    { title: '类型', dataIndex: 'packageType', search: false, render: (value) => value === 'SUBSCRIPTION' ? '会员订阅' : '积分包' },
    { title: '售价', search: false, render: (_, record) => record.latestPrice == null ? '-' : `${record.latestPrice} ${record.latestCurrency ?? 'CNY'}` },
    { title: '权益', search: false, render: (_, record) => record.latestEntitlements?.map((item) => item.category === 'DISPLAY' || item.value == null ? item.name ?? entitlementTypeText(item.type) : `${item.name ?? entitlementTypeText(item.type)} ${item.value}`).join(' · ') || '-' },
    { title: '当前版本', dataIndex: 'latestVersionNo', search: false, render: (_, record) => record.latestVersionNo ? `V${record.latestVersionNo}` : '-' },
    { title: '销售状态', dataIndex: 'latestStatus', search: false, render: (_, record) => latestStatusTag(record.latestStatus) },
    { title: '操作', valueType: 'option', search: false, render: (_, record) => [
      access.canEditCommercialPackages ? <Button key="new-version" type="link" disabled={entitlementDefinitions.length === 0} onClick={() => setDraftPackage(record)}>新增版本</Button> : null,
      <Button key="versions" type="link" onClick={() => void loadVersions(record)}>查看版本</Button>,
    ] },
  ];

  return <>
    <ProTable<CommercialPackageSummary, CommercialPackageQuery>
      actionRef={actionRef}
      rowKey="id"
      columns={columns}
      search={{ labelWidth: 'auto' }}
      pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      toolBarRender={() => access.canEditCommercialPackages ? [<Button key="create" type="primary" icon={<PlusOutlined />} disabled={entitlementDefinitions.length === 0} onClick={() => setDraftPackage(null)}>创建草稿</Button>] : []}
      request={async (params) => {
        const packages = (await listCommercialPackages()).data ?? [];
        const keyword = params.keyword?.trim().toLowerCase();
        const filtered = packages.filter((pack) =>
          (!keyword || [pack.code, pack.latestName].filter(Boolean).some((value) => value?.toLowerCase().includes(keyword)))
          && (!params.packageType || pack.packageType === params.packageType)
          && (!params.latestStatus || pack.latestStatus === params.latestStatus),
        );
        const current = params.current ?? 1;
        const pageSize = params.pageSize ?? 20;
        return { data: filtered.slice((current - 1) * pageSize, current * pageSize), total: filtered.length, success: true };
      }}
    />
    <ModalForm<CommercialPackageDraft>
      title={isVersionDraft ? '新增版本草稿' : '创建套餐草稿'}
      open={draftPackage !== undefined}
      onOpenChange={(open) => { if (!open) setDraftPackage(undefined); }}
      initialValues={{ code: draftPackage?.code, packageType: draftPackage?.packageType, currency: 'CNY', effectiveFrom: dayjs(), billingPeriod: 'MONTH', periodMonths: 1, entitlements: [{ type: 'ONE_TIME_POINTS' }] }}
      modalProps={{ destroyOnHidden: true }}
      onFinish={async (values) => {
        const created = await createDraft(values);
        if (created) setDraftPackage(undefined);
        return created;
      }}
    >
      <ProFormText name="code" label="套餐编码" disabled={isVersionDraft} tooltip="留空创建新套餐；填写已有编码会为该套餐创建下一版本。" />
      <ProFormSelect name="packageType" label="套餐类型" disabled={isVersionDraft} options={[{ label: '积分包', value: 'POINT_PACKAGE' }, { label: '会员订阅', value: 'SUBSCRIPTION' }]} rules={[{ required: true }]} />
      <ProFormText name="name" label="名称" rules={[{ required: true }]} />
      <ProFormText name="description" label="说明" />
      <ProFormSelect name="billingPeriod" label="计费周期" options={billingPeriodOptions} />
      <ProFormSelect name="periodMonths" label="周期月数" options={periodMonthOptions} />
      <ProFormDigit name="price" label="售价" min={0} rules={[{ required: true }]} />
      <ProFormDigit name="listPrice" label="划线价" min={0} />
      <ProFormText name="currency" label="币种" rules={[{ required: true }]} />
      <ProFormDateTimePicker name="effectiveFrom" label="生效时间" rules={[{ required: true }]} />
      <ProFormDateTimePicker name="effectiveTo" label="失效时间" />
      <ProFormList name="entitlements" label="权益版本" min={1} creatorButtonProps={{ creatorButtonText: '添加权益' }}>
        <Space align="start">
          <ProFormSelect name="type" label="权益类型" options={activeEntitlementOptions(entitlementDefinitions)} rules={[{ required: true }]} />
          <ProFormDependency name={['type']}>
            {({ type }) => {
              const definition = entitlementDefinitions.find((item) => item.code === type);
              if (!definition) return null;
              if (entitlementRequiresValue(entitlementDefinitions, type)) {
                return <ProFormDigit name="value" label="权益值" min={0} rules={[{ required: true }]} />;
              }
              return <Space orientation="vertical" size={0}>
                <Typography.Text>{definition.name}</Typography.Text>
                <Typography.Text type="secondary">{definition.description ?? '展示类权益无需填写数值'}</Typography.Text>
              </Space>;
            }}
          </ProFormDependency>
        </Space>
      </ProFormList>
    </ModalForm>
    <Drawer size={720} open={Boolean(selected)} title={`${selected?.latestName ?? selected?.code ?? ''} · 版本历史`} extra={access.canEditCommercialPackages && selected ? <Button type="primary" onClick={() => setDraftPackage(selected)}>新增版本</Button> : undefined} onClose={() => setSelected(undefined)}>
      {versions.length === 0 ? <Empty description="暂无版本" /> : <Table<CommercialPackageVersion>
        rowKey="versionId"
        pagination={false}
        dataSource={versions}
        columns={[
          { title: '版本', dataIndex: 'versionNo', width: 70, render: (value) => `v${value}` },
          { title: '名称', dataIndex: 'name' },
          { title: '售价', render: (_, record) => `${record.price} ${record.currency}` },
          { title: '状态', dataIndex: 'status', render: (value) => <Tag>{statusText(value)}</Tag> },
          { title: '权益', render: (_, record) => <Space orientation="vertical" size={0}>{record.entitlements.map((item) => <Typography.Text key={item.type}>{item.category === 'DISPLAY' || item.value == null ? item.name ?? entitlementTypeText(item.type) : `${item.name ?? entitlementTypeText(item.type)}：${item.value}`}</Typography.Text>)}</Space> },
          { title: '操作', width: 90, render: (_, record) => access.canEditCommercialPackages && (record.status === 'DRAFT' ? <Popconfirm title="确认发布？" onConfirm={() => void transition(record, 'publish')}><Button type="link">发布</Button></Popconfirm> : record.status === 'PUBLISHED' ? <Popconfirm title="确认下架？" onConfirm={() => void transition(record, 'unpublish')}><Button type="link" danger>下架</Button></Popconfirm> : '-') },
        ]}
      />}
    </Drawer>
  </>;
};

const EntitlementManagementContent = ({ entitlementDefinitions, onCatalogChanged }: {
  entitlementDefinitions: CommercialEntitlementDefinition[];
  onCatalogChanged: () => Promise<void>;
}) => {
  const access = useAccess();
  const { message } = App.useApp();
  const [editing, setEditing] = useState<CommercialEntitlementDefinition | null>();

  const changeStatus = async (record: CommercialEntitlementDefinition) => {
    if (record.status === 'ACTIVE') await disableDisplayEntitlement(record.id);
    else await enableDisplayEntitlement(record.id);
    await onCatalogChanged();
    message.success(record.status === 'ACTIVE' ? '展示权益已停用' : '展示权益已启用');
  };

  const columns: ProColumns<CommercialEntitlementDefinition>[] = [
    { title: '权益名称', dataIndex: 'name' },
    { title: '权益编码', dataIndex: 'code' },
    { title: '类别', dataIndex: 'category', render: (value) => value === 'SYSTEM' ? '系统权益' : '展示权益' },
    { title: '说明', dataIndex: 'description', renderText: (value) => value || '-' },
    { title: '状态', dataIndex: 'status', render: (value) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
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
    <ProTable<CommercialEntitlementDefinition>
      rowKey="id"
      columns={columns}
      dataSource={entitlementDefinitions}
      search={false}
      pagination={false}
      toolBarRender={() => access.canEditCommercialPackages ? [
        <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新增展示权益</Button>,
      ] : []}
    />
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
  const [activeKey, setActiveKey] = useState<PackageManagementTab>('packages');
  const [entitlementDefinitions, setEntitlementDefinitions] = useState<CommercialEntitlementDefinition[]>([]);
  const refreshEntitlements = useCallback(async () => {
    setEntitlementDefinitions((await listCommercialEntitlements()).data ?? []);
  }, []);

  useEffect(() => {
    void refreshEntitlements();
  }, [refreshEntitlements]);

  return <PageContainer title="套餐管理">
    <Tabs
      activeKey={activeKey}
      onChange={(key) => setActiveKey(key as PackageManagementTab)}
      items={[
        { key: 'packages', label: '套餐列表', children: <PackageListContent entitlementDefinitions={entitlementDefinitions} /> },
        { key: 'entitlements', label: '权益管理', children: <EntitlementManagementContent entitlementDefinitions={entitlementDefinitions} onCatalogChanged={refreshEntitlements} /> },
      ]}
    />
  </PageContainer>;
};

export default CommercialPackageManagementPage;
