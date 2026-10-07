import { CreditCardOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Radio, Tabs } from 'antd';
import type { CommercialCatalogItem } from './service';
import { groupCatalog, presentOffer, type MembershipPeriod } from './purchasePresentation';
import styles from './index.less';

type PurchaseCategory = 'membership' | 'points';
type SelectionKey = MembershipPeriod | 'points';

export type PurchaseSelection = {
  category: PurchaseCategory;
  period: MembershipPeriod;
  selected: Partial<Record<SelectionKey, number>>;
};

export const initialPurchaseSelection: PurchaseSelection = {
  category: 'membership',
  period: 'monthly',
  selected: {},
};

type PurchasePanelProps = {
  catalog: CommercialCatalogItem[];
  selection: PurchaseSelection;
  onSelectionChange: (selection: PurchaseSelection) => void;
  canManageBilling: boolean;
  teamName: string;
  loading: boolean;
  error?: string | null;
  submitting: boolean;
  onReload: () => void;
  onPurchase: (offer: CommercialCatalogItem) => void;
};

const periods: Array<{ label: string; value: MembershipPeriod }> = [
  { label: '月度', value: 'monthly' },
  { label: '季度', value: 'quarterly' },
  { label: '半年', value: 'halfYear' },
  { label: '年度', value: 'yearly' },
];

const periodLabel: Record<MembershipPeriod, string> = {
  monthly: '每月',
  quarterly: '每季',
  halfYear: '每半年',
  yearly: '每年',
};

const PurchasePanel = ({
  catalog,
  selection,
  onSelectionChange,
  canManageBilling,
  teamName,
  loading,
  error,
  submitting,
  onReload,
  onPurchase,
}: PurchasePanelProps) => {
  const grouped = groupCatalog(catalog);
  const key: SelectionKey = selection.category === 'points' ? 'points' : selection.period;
  const offers = selection.category === 'points'
    ? grouped.points
    : grouped.membership[selection.period];
  const purchasable = offers.filter((item) => presentOffer(item).canPurchase);
  const selected = purchasable.find((item) => item.packageVersionId === selection.selected[key])
    ?? purchasable[0];
  const selectedView = selected ? presentOffer(selected) : null;
  const canPay = !loading && !error && !submitting && canManageBilling && Boolean(selected);

  return (
    <section className={styles.purchaseSection} aria-label="套餐购买">
      <div className={styles.purchaseHeading}>
        <div>
          <span className={styles.purchaseEyebrow}>CREATOR PLANS</span>
          <h1>为下一场创作，备好灵感。</h1>
          <p>会员享周期权益，积分包随时补充创作额度。</p>
        </div>
      </div>
      <Tabs
        aria-label="商品类型"
        activeKey={selection.category}
        onChange={(category) => onSelectionChange({
          ...selection,
          category: category as PurchaseCategory,
        })}
        items={[
          { key: 'membership', label: '会员套餐' },
          { key: 'points', label: '积分包' },
        ]}
      />
      {selection.category === 'membership' ? (
        <div className={styles.purchasePeriod}>
          <div className={styles.purchaseSectionLabel}>
            <strong>选择会员周期</strong><span>按周期享权益</span>
          </div>
          <Radio.Group
            aria-label="会员周期"
            optionType="button"
            buttonStyle="solid"
            value={selection.period}
            options={periods}
            onChange={(event) => onSelectionChange({
              ...selection,
              period: event.target.value as MembershipPeriod,
            })}
          />
        </div>
      ) : (
        <div className={styles.purchaseSectionLabel}>
          <strong>积分补给</strong><span>一次购买，无自动续费</span>
        </div>
      )}

      {loading ? <div className={styles.purchaseState} role="status">套餐加载中</div> : error ? (
        <div className={styles.purchaseState} role="alert">
          <span>{error}</span><Button onClick={onReload}>重新加载套餐</Button>
        </div>
      ) : (
        <>
          {offers.length ? (
            <div className={styles.purchaseGrid}>
              {offers.map((item) => {
                const view = presentOffer(item);
                const active = selected?.packageVersionId === item.packageVersionId;
                return (
                  <button
                    key={item.packageVersionId}
                    type="button"
                    aria-label={`选择${item.name}`}
                    aria-pressed={active}
                    disabled={!view.canPurchase}
                    data-selected={active}
                    className={`${styles.purchaseOffer} ${active ? styles.purchaseOfferSelected : ''}`}
                    onClick={() => onSelectionChange({
                      ...selection,
                      selected: { ...selection.selected, [key]: item.packageVersionId },
                    })}
                  >
                    <span className={styles.purchaseOfferHeading}>
                      <span className={styles.purchaseOfferName}>{item.name}</span>
                      {active ? <span className={styles.purchaseSelectedLabel}>已选方案</span> : null}
                    </span>
                    {item.description ? <span className={styles.purchaseDescription}>{item.description}</span> : null}
                    {view.primaryBenefit ? <span className={styles.purchaseBenefit}>{view.primaryBenefit}</span> : null}
                    {view.periodTotal ? <span className={styles.purchaseSupporting}>{view.periodTotal}，按月发放</span> : null}
                    {view.discount ? <span className={styles.purchaseSupporting}>{view.discount}</span> : null}
                    {view.extraBenefits.map((benefit) => (
                      <span className={styles.purchaseSupporting} key={`${item.packageVersionId}-${benefit}`}>{benefit}</span>
                    ))}
                    {!view.canPurchase ? <span className={styles.purchaseSupporting}>权益配置待确认，暂不可购买</span> : null}
                    <span className={styles.purchasePrice}>
                      <strong>{view.price}</strong>
                      {view.listPrice ? <del>{view.listPrice}</del> : null}
                    </span>
                    {view.monthlyEquivalent || view.unitPrice ? (
                      <span className={styles.purchaseUnit}>{view.monthlyEquivalent || view.unitPrice}</span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          ) : (
            <div className={styles.purchaseState} role="status">
              {selection.category === 'points' ? '暂无可售积分包' : '当前周期暂无可售会员套餐'}
            </div>
          )}
          {selection.category === 'membership' && grouped.unsupported.length ? (
            <div className={styles.purchaseUnsupported} role="status">
              {grouped.unsupported.map((item) => (
                <span key={item.packageVersionId}>周期信息待确认：{item.name}</span>
              ))}
            </div>
          ) : null}
        </>
      )}

      <div className={styles.purchaseTrust}>
        <span><SafetyCertificateOutlined /> 微信安全支付</span>
        <span><CreditCardOutlined /> 已发放积分永久有效</span>
      </div>
      <div className={styles.purchaseCheckout} aria-live="polite">
        <div className={styles.purchaseCheckoutOffer}>
          <strong>{selected?.name ?? '请选择可售套餐'}</strong>
          <span>
            {selected && selectedView?.primaryBenefit ? `${selectedView.primaryBenefit} · ` : ''}
            {selected && selection.category === 'membership' ? `${periodLabel[selection.period]}计费 · ` : ''}
            {teamName}
          </span>
        </div>
        <div className={styles.purchaseCheckoutActions}>
          <div className={styles.purchaseCheckoutPrice}>
            <small>应付金额</small><strong>{selectedView?.price ?? '-'}</strong>
          </div>
          <Button
            type="primary"
            loading={submitting}
            disabled={!canPay}
            onClick={() => { if (canPay && selected) onPurchase(selected); }}
          >确认付款</Button>
        </div>
      </div>
      {!canManageBilling ? <p className={styles.purchasePermission}>仅团队管理员可购买</p> : null}
    </section>
  );
};

export default PurchasePanel;
