import { useState } from 'react';
import { Button, Card, Empty, List, Statistic, Tag, Tabs, Typography } from 'antd';
import { CheckOutlined, LeftOutlined, LockOutlined, WalletOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { mockCommercialPackages, mockCurrentSubscription, mockQueuedSubscriptions } from './data';
import styles from './index.less';

const { Text, Title } = Typography;

const CommercialPage = () => {
  const [activeTab, setActiveTab] = useState('subscriptions');
  const packages = mockCommercialPackages.filter((item) => activeTab === 'subscriptions' ? item.packageType === 'SUBSCRIPTION' : item.packageType === 'POINT_PACKAGE');
  return <main className={styles.page}>
    <header className={styles.header}>
      <button className={styles.brand} type="button" onClick={() => history.push('/')}><span className={styles.brandMark}>A</span><span>Ant Short TV</span></button>
      <div className={styles.headerRight}><Statistic title="团队积分余额" value={mockCurrentSubscription.points} suffix="积分" prefix={<WalletOutlined />} /><Button type="text" icon={<LeftOutlined />} onClick={() => history.back()}>返回工作台</Button></div>
    </header>
    <section className={styles.content}>
      <div className={styles.hero}><Title level={2}>充值中心</Title><Text type="secondary">选择适合团队的积分包或会员订阅，已发放积分永久有效</Text></div>
      <Card className={styles.currentCard} bordered={false}><div><Text className={styles.label}>当前会员</Text><Title level={4}>{mockCurrentSubscription.name} <Tag color="success">{mockCurrentSubscription.status}</Tag></Title></div><div><Text className={styles.label}>有效期至</Text><strong>{mockCurrentSubscription.endsAt}</strong></div><div><Text className={styles.label}>全局 AI 折扣</Text><strong>{mockCurrentSubscription.discount}</strong></div><Button disabled icon={<LockOutlined />}>续费支付待配置</Button></Card>
      <Tabs className={styles.tabs} activeKey={activeTab} onChange={setActiveTab} items={[{ key: 'subscriptions', label: '会员订阅' }, { key: 'points', label: '积分包' }]} />
      <div className={styles.packageGrid}>{packages.map((item) => <Card key={item.id} className={`${styles.packageCard} ${item.recommended ? styles.recommended : ''}`} bordered={false}>{item.badge && <Tag className={styles.badge} color="purple">{item.badge}</Tag>}<Text className={styles.packageType}>{item.packageType === 'SUBSCRIPTION' ? '会员订阅' : '积分充值'}</Text><Title level={3}>{item.name}</Title><Text type="secondary">{item.subtitle}</Text><div className={styles.price}><span>¥</span>{item.price}<small>{item.billingPeriod ? `/${item.billingPeriod}` : ''}</small>{item.originalPrice && <del>¥{item.originalPrice}</del>}</div><div className={styles.pointSummary}>{item.points ? `${item.points.toLocaleString()} 积分一次性到账` : `${item.periodicPoints?.toLocaleString()} 积分 / 月${item.totalPoints ? `，周期共 ${item.totalPoints.toLocaleString()} 积分` : ''}`}</div><List className={styles.entitlements} size="small" split={false} dataSource={item.entitlements} renderItem={(entitlement) => <List.Item><CheckOutlined />{entitlement}</List.Item>} /><Button className={styles.buyButton} type="primary" block disabled icon={<LockOutlined />}>支付配置中</Button></Card>)}</div>
      <Card className={styles.queueCard} bordered={false} title="排队中的会员">{mockQueuedSubscriptions.length ? <List dataSource={mockQueuedSubscriptions} renderItem={(item) => <List.Item><List.Item.Meta title={<>{item.name} <Tag>{item.status}</Tag></>} description={`${item.startsAt} 至 ${item.endsAt}`} /></List.Item>} /> : <Empty description="暂无排队套餐" />}</Card>
      <section className={styles.faq}><Title level={3}>常见问题</Title>{[['积分购买后可以退款吗？', '积分属于虚拟数字商品，一经购买不支持退款。积分永久有效，可随时用于 AI 生成。'], ['积分和会员的有效期是怎么算的？', '积分永久有效；会员按购买套餐周期生效，到期后需要手动续费。'], ['会员到期后还可以使用剩余积分吗？', '可以。会员到期只影响会员折扣和周期权益，账户内剩余积分仍可继续使用。']].map(([question, answer]) => <details key={question} className={styles.faqItem}><summary>{question}</summary><p>{answer}</p></details>)}</section>
    </section>
  </main>;
};
export default CommercialPage;
