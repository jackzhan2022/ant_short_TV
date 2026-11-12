import { EyeOutlined } from '@ant-design/icons';
import { ModalForm, ProFormDateTimePicker, ProFormDependency, ProFormDigit, ProFormList, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { App } from 'antd';
import dayjs from 'dayjs';
import { activeEntitlementOptions, entitlementRequiresValue, normalizePackageEntitlements } from './entitlementForm';
import { useStyles } from './index.style';
import { PackageBenefits, PackageMark, PackagePrice } from './presentation';
import { createCommercialPackageDraft, type CommercialEntitlementDefinition, type CommercialPackageDraft, type CommercialPackageSummary } from './service';

export default function PackageDraftDialog({ pack, definitions, onClose, onCreated }: {
  pack: CommercialPackageSummary | null;
  definitions: CommercialEntitlementDefinition[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const { styles } = useStyles();
  const { message } = App.useApp();
  return <ModalForm<CommercialPackageDraft>
    title={pack ? '新增版本草稿' : '创建套餐草稿'} width={1020} open
    initialValues={{ code: pack?.code, packageType: pack?.packageType, currency: 'CNY', effectiveFrom: dayjs(), billingPeriod: 'MONTH', periodMonths: 1, entitlements: [{ type: activeEntitlementOptions(definitions)[0]?.value }] }}
    modalProps={{ destroyOnHidden: true, className: styles.modal, centered: true }}
    submitter={{ searchConfig: { submitText: '保存草稿', resetText: '取消' } }}
    onOpenChange={(open) => { if (!open) onClose(); }}
    onFinish={async (values) => {
      await createCommercialPackageDraft({ ...values, ...(pack ? { code: pack.code, packageType: pack.packageType } : {}), currency: values.currency ?? 'CNY', entitlements: normalizePackageEntitlements(values.entitlements, definitions) });
      message.success('套餐草稿已创建');
      onCreated();
      onClose();
      return true;
    }}
  >
    <div className={styles.draftLayout}>
      <div className={styles.formBody}>
        <section><h3>基本信息</h3><div className={styles.formGrid}>
          <ProFormText name="code" label="套餐编码" placeholder="留空自动生成" disabled={Boolean(pack)} tooltip="填写已有编码会为该套餐创建下一版本。" />
          <ProFormSelect name="packageType" label="套餐类型" disabled={Boolean(pack)} options={[{ label: '积分包', value: 'POINT_PACKAGE' }, { label: '会员订阅', value: 'SUBSCRIPTION' }]} rules={[{ required: true }]} />
        </div><ProFormText name="name" label="名称" placeholder="例如：创作月卡" rules={[{ required: true, whitespace: true }]} /><ProFormText name="description" label="说明" placeholder="套餐简短介绍（选填）" /></section>
        <section><h3>价格与周期</h3><div className={styles.formGrid}>
          <ProFormDigit name="price" label="售价" min={0} fieldProps={{ precision: 2 }} rules={[{ required: true }]} />
          <ProFormDigit name="listPrice" label="划线价" min={0} fieldProps={{ precision: 2 }} />
          <ProFormText name="currency" label="币种" rules={[{ required: true }]} />
          <ProFormSelect name="billingPeriod" label="计费周期" options={[{ label: '月', value: 'MONTH' }, { label: '季', value: 'QUARTER' }, { label: '半年', value: 'HALF_YEAR' }, { label: '年', value: 'YEAR' }]} />
          <ProFormSelect name="periodMonths" label="周期月数" options={[{ label: '1 个月', value: 1 }, { label: '3 个月', value: 3 }, { label: '6 个月', value: 6 }, { label: '12 个月', value: 12 }]} />
        </div></section>
        <section><h3>套餐权益</h3><ProFormList name="entitlements" min={1} creatorButtonProps={{ creatorButtonText: '添加权益' }}>
          <div className={styles.entitlementEntry}><ProFormSelect name="type" label="权益类型" options={activeEntitlementOptions(definitions)} rules={[{ required: true }]} />
            <ProFormDependency name={['type']}>{({ type }) => {
              const definition = definitions.find((item) => item.code === type);
              if (!definition) return null;
              return entitlementRequiresValue(definitions, type) ? <ProFormDigit name="value" label="权益值" min={0} rules={[{ required: true }]} /> : <div className={styles.muted}>{definition.description ?? '展示类权益无需填写数值'}</div>;
            }}</ProFormDependency></div>
        </ProFormList></section>
        <section><h3>生效时间</h3><div className={styles.formGrid}><ProFormDateTimePicker name="effectiveFrom" label="生效时间" rules={[{ required: true }]} /><ProFormDateTimePicker name="effectiveTo" label="失效时间" placeholder="留空长期有效" /></div></section>
      </div>
      <aside className={styles.preview}><p><EyeOutlined /> 套餐展示预览</p><ProFormDependency name={['name', 'description', 'price', 'currency', 'packageType', 'entitlements']}>
        {(values) => <div className={styles.previewCard}><PackageMark subscription={values.packageType === 'SUBSCRIPTION'} /><h3>{values.name || '套餐名称'}</h3><p>{values.description || (values.packageType === 'SUBSCRIPTION' ? '会员订阅' : '积分包')}</p><div className={styles.cardPrice}><PackagePrice value={values.price ?? 0} currency={values.currency || 'CNY'} /></div><footer><PackageBenefits items={(values.entitlements ?? []).filter((item: { type?: string }) => item.type).map((item: { type: string; value?: number }) => ({ ...item, name: definitions.find((definition) => definition.code === item.type)?.name, category: definitions.find((definition) => definition.code === item.type)?.category }))} /></footer></div>}
      </ProFormDependency></aside>
    </div>
  </ModalForm>;
}
