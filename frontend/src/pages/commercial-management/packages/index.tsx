import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormDateTimePicker,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Drawer, Empty, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import {
  createCommercialPackageDraft,
  listCommercialPackages,
  listCommercialPackageVersions,
  publishCommercialPackageVersion,
  unpublishCommercialPackageVersion,
  type CommercialPackageDraft,
  type CommercialPackageSummary,
  type CommercialPackageVersion,
} from './service';
import { entitlementTypeText, statusText } from '@/utils/fieldDictionary';

const entitlementOptions = [
  { label: '一次性积分', value: 'ONE_TIME_POINTS' },
  { label: '周期积分', value: 'PERIODIC_POINTS' },
  { label: '全局折扣', value: 'GLOBAL_DISCOUNT' },
];
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

const CommercialPackageManagementPage = () => {
  const access = useAccess();
  const { message } = App.useApp();
  const [packages, setPackages] = useState<CommercialPackageSummary[]>([]);
  const [selected, setSelected] = useState<CommercialPackageSummary>();
  const [versions, setVersions] = useState<CommercialPackageVersion[]>([]);

  const loadPackages = async () => setPackages((await listCommercialPackages()).data ?? []);
  const loadVersions = async (pack: CommercialPackageSummary) => {
    setSelected(pack);
    setVersions((await listCommercialPackageVersions(pack.id)).data ?? []);
  };
  useEffect(() => { void loadPackages(); }, []);

  const transition = async (version: CommercialPackageVersion, action: 'publish' | 'unpublish') => {
    if (action === 'publish') await publishCommercialPackageVersion(version.packageId, version.versionId);
    else await unpublishCommercialPackageVersion(version.packageId, version.versionId);
    message.success(action === 'publish' ? '套餐版本已发布' : '套餐版本已下架');
    if (selected) await loadVersions(selected);
  };

  const createDraft = async (values: CommercialPackageDraft) => {
    await createCommercialPackageDraft({ ...values, currency: values.currency ?? 'CNY' });
    message.success('套餐草稿已创建');
    await loadPackages();
    return true;
  };

  return <PageContainer title="商业化套餐">
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      {access.canEditCommercialPackages && <ModalForm<CommercialPackageDraft>
        title="创建套餐草稿"
        trigger={<Button type="primary" icon={<PlusOutlined />}>创建草稿</Button>}
        initialValues={{
          currency: 'CNY',
          effectiveFrom: dayjs(),
          billingPeriod: 'MONTH',
          periodMonths: 1,
          entitlements: [{ type: 'ONE_TIME_POINTS' }],
        }}
        modalProps={{ destroyOnHidden: true }}
        onFinish={createDraft}
      >
        <ProFormSelect name="packageType" label="套餐类型" options={[{ label: '积分包', value: 'POINT_PACKAGE' }, { label: '会员订阅', value: 'SUBSCRIPTION' }]} rules={[{ required: true }]} />
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
          <Space align="start"><ProFormSelect name="type" label="权益类型" options={entitlementOptions} rules={[{ required: true }]} /><ProFormDigit name="value" label="权益值" min={0} rules={[{ required: true }]} /></Space>
        </ProFormList>
      </ModalForm>}
      <Table<CommercialPackageSummary>
        rowKey="id"
        dataSource={packages}
        columns={[
          { title: '编码', dataIndex: 'code' },
          { title: '类型', dataIndex: 'packageType', render: (value) => value === 'SUBSCRIPTION' ? '会员订阅' : '积分包' },
          { title: '状态', dataIndex: 'status', render: (value) => <Tag>{statusText(value)}</Tag> },
          { title: '操作', render: (_, record) => <Button type="link" onClick={() => void loadVersions(record)}>版本历史</Button> },
        ]}
      />
    </Space>
    <Drawer size="large" open={Boolean(selected)} title={`${selected?.code ?? ''} 版本历史`} onClose={() => setSelected(undefined)}>
      {versions.length === 0 ? <Empty description="暂无版本" /> : <Table<CommercialPackageVersion>
        rowKey="versionId"
        pagination={false}
        dataSource={versions}
        columns={[
          { title: '版本', dataIndex: 'versionNo', width: 70, render: (value) => `v${value}` },
          { title: '名称', dataIndex: 'name' },
          { title: '售价', render: (_, record) => `${record.price} ${record.currency}` },
          { title: '状态', dataIndex: 'status', render: (value) => <Tag>{statusText(value)}</Tag> },
          { title: '权益', render: (_, record) => <Space orientation="vertical" size={0}>{record.entitlements.map((item) => <Typography.Text key={item.type}>{entitlementTypeText(item.type)}：{item.value}</Typography.Text>)}</Space> },
          { title: '操作', width: 90, render: (_, record) => access.canEditCommercialPackages && (record.status === 'DRAFT' ? <Popconfirm title="确认发布？" onConfirm={() => void transition(record, 'publish')}><Button type="link">发布</Button></Popconfirm> : record.status === 'PUBLISHED' ? <Popconfirm title="确认下架？" onConfirm={() => void transition(record, 'unpublish')}><Button type="link" danger>下架</Button></Popconfirm> : '-') },
        ]}
      />}
    </Drawer>
  </PageContainer>;
};

export default CommercialPackageManagementPage;
