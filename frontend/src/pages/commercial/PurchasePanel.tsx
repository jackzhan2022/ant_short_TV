import {
  CheckOutlined, ClockCircleOutlined, CrownOutlined, FileTextOutlined,
  SafetyCertificateOutlined, WalletOutlined, WechatOutlined,
} from '@ant-design/icons';
import { Button, Radio, Tabs } from 'antd';
import { formatPoints } from '@/utils/points';
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
  category: 'membership', period: 'monthly', selected: {},
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
  { label: '月度', value: 'monthly' }, { label: '季度', value: 'quarterly' },
  { label: '半年', value: 'halfYear' }, { label: '年度', value: 'yearly' },
];

const PurchasePanel = ({
  catalog, selection, onSelectionChange, canManageBilling, teamName,
  loading, error, submitting, onReload, onPurchase,
}: PurchasePanelProps) => {
  const grouped = groupCatalog(catalog);
  const isMember = selection.category === 'membership';
  const key: SelectionKey = isMember ? selection.period : 'points';
  const offers = isMember ? grouped.membership[selection.period] : grouped.points;
  const purchasable = offers.filter((item) => presentOffer(item).canPurchase);
  const selected = purchasable.find((item) => item.packageVersionId === selection.selected[key])
    ?? purchasable[0];
  const selectedView = selected ? presentOffer(selected) : null;
  const canPay = !loading && !error && !submitting && canManageBilling && Boolean(selected);

  return (
    <section className={styles.purchaseSection} aria-label="套餐购买">
      <div className={styles.purchasePanel}>
        <div className={styles.purchasePanelHeading}>
          <Tabs aria-label="商品类型" activeKey={selection.category}
            onChange={(category) => onSelectionChange({ ...selection, category: category as PurchaseCategory })}
            items={[{ key: 'membership', label: '会员套餐' }, { key: 'points', label: '积分包' }]} />
          <span>选择适合你的创作额度</span>
        </div>
        <div className={styles.purchasePeriod}>
          <span className={styles.purchaseSectionLabel}>{isMember ? '选择会员周期' : '选择积分额度'}</span>
          {isMember && <Radio.Group aria-label="会员周期" optionType="button" value={selection.period}
            options={periods} onChange={(event) => onSelectionChange({
              ...selection, period: event.target.value as MembershipPeriod,
            })} />}
        </div>
        {loading ? <div className={styles.purchaseState} role="status">套餐加载中</div> : error ? (
          <div className={styles.purchaseState} role="alert">
            <span>{error}</span><Button onClick={onReload}>重新加载套餐</Button>
          </div>
        ) : (
          <>
            {offers.length ? (
              <div className={`${styles.purchaseGrid} ${isMember && offers.length === 1 ? styles.singleMembership : ''}`}>
                {offers.map((item) => {
                  const view = presentOffer(item);
                  const active = selected?.packageVersionId === item.packageVersionId;
                  return (
                    <button key={item.packageVersionId} type="button" aria-label={`选择${item.name}`}
                      aria-pressed={active} disabled={!view.canPurchase} data-selected={active}
                      className={`${styles.purchaseOffer} ${active ? styles.purchaseOfferSelected : ''}`}
                      onClick={() => onSelectionChange({
                        ...selection, selected: { ...selection.selected, [key]: item.packageVersionId },
                      })}>
                      <span className={styles.purchaseOfferHeading}>
                        <span className={styles.purchaseOfferName}>{item.name}</span>
                        <span className={styles.purchaseSelectedMark} aria-hidden="true">{active && <CheckOutlined />}</span>
                      </span>
                      <span className={styles.purchaseOfferBody}>
                        <span className={styles.purchaseBenefitGroup}>
                          {view.pointAmount !== null ? <>
                            <span className={styles.srOnly}>{view.primaryBenefit}</span>
                            <span className={styles.purchaseBenefit} aria-hidden="true">
                              {formatPoints(view.pointAmount)}<small>{view.recurringPoints ? '积分 / 月' : '积分'}</small>
                            </span>
                          </> : <span className={styles.purchaseBenefit}>创作权益</span>}
                          <span className={styles.purchaseDescription}>
                            {view.recurringPoints ? '每个订阅月发放，持续支持日常创作。' : '支付成功后一次到账，灵活补充创作额度。'}
                          </span>
                        </span>
                        <span className={styles.purchasePriceGroup}>
                          <span className={styles.purchasePrice}>
                            <strong>{view.price}</strong>{view.listPrice && <del>{view.listPrice}</del>}
                          </span>
                          <span className={styles.purchaseUnit}>
                            {isMember && view.periodMonths ? `订阅周期 ${view.periodMonths} 个月` : view.unitPrice}
                          </span>
                          {isMember && view.periodMonths !== 1 && view.monthlyEquivalent && <span className={styles.purchaseSupporting}>{view.monthlyEquivalent}</span>}
                        </span>
                      </span>
                      <span className={styles.purchaseOfferBenefits}>
                        <span><CheckOutlined />已发放积分永久有效</span>
                        {isMember && <span><CheckOutlined />无自动扣款</span>}
                        {view.periodMonths !== 1 && view.periodTotal && <span>{view.periodTotal}，按月发放</span>}
                        {view.discount && <span>{view.discount}</span>}
                        {view.extraBenefits.map((benefit) => <span key={benefit}>{benefit}</span>)}
                        {!view.canPurchase && <span>权益配置待确认，暂不可购买</span>}
                      </span>
                    </button>
                  );
                })}
              </div>
            ) : <div className={styles.purchaseState} role="status">
              <CrownOutlined />
              <span>{isMember ? '当前周期暂无可售会员套餐' : '暂无可售积分包'}</span>
              <small>可切换其他周期或商品类型。</small>
            </div>}
            {isMember && grouped.unsupported.length > 0 && <div className={styles.purchaseUnsupported} role="status">
              {grouped.unsupported.map((item) => <span key={item.packageVersionId}>周期信息待确认：{item.name}</span>)}
            </div>}
          </>
        )}
        <div className={styles.purchaseDelivery}><ClockCircleOutlined />
          {isMember ? '生效时发放当期积分，续购顺延，无自动扣款。' : '支付成功后一次到账，可用于文本、图片和视频创作。'}
        </div>
        <div className={styles.purchaseTrust}>
          <span><SafetyCertificateOutlined />微信安全支付</span>
          <span><CheckOutlined />已发放积分永久有效</span>
        </div>
      </div>

      <aside className={styles.purchaseCheckout} aria-label="订单摘要" aria-live="polite">
        <h2><FileTextOutlined />订单摘要</h2>
        <div className={styles.purchaseCheckoutOffer}>
          <span className={styles.purchaseProductIcon}>{isMember ? <CrownOutlined /> : <WalletOutlined />}</span>
          <div><strong>{selected?.name ?? '请选择可售套餐'}</strong>
            <span>{isMember ? `会员套餐 · ${periods.find((period) => period.value === selection.period)?.label}` : '积分包 · 一次购买'}</span>
          </div>
        </div>
        <dl className={styles.purchaseSummaryRows}>
          <div><dt>发放积分</dt><dd>{selectedView?.pointAmount != null ? `${formatPoints(selectedView.pointAmount)}${selectedView.recurringPoints ? ' / 月' : ' 积分'}` : '—'}</dd></div>
          <div><dt>{isMember ? '订阅周期' : '发放方式'}</dt><dd>{selected ? (isMember ? `${selectedView?.periodMonths} 个月` : '一次性发放') : '—'}</dd></div>
          <div><dt>到账团队</dt><dd>{teamName}</dd></div>
          <div><dt>支付方式</dt><dd>微信支付</dd></div>
        </dl>
        <div className={styles.purchaseCheckoutPrice}><small>应付金额</small><strong>{selectedView?.price ?? '—'}</strong></div>
        <Button type="primary" block aria-label="确认付款" loading={submitting} disabled={!canPay} icon={<WechatOutlined />}
          onClick={() => { if (canPay && selected) onPurchase(selected); }}>确认付款</Button>
        <p className={styles.purchasePermission}>{canManageBilling ? '权益将发放至当前团队' : '仅团队管理员可购买'}</p>
      </aside>
    </section>
  );
};

export default PurchasePanel;
