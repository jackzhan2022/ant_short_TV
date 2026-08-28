import { PageContainer } from '@ant-design/pro-components';
import { Statistic, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import type { PlatformAiOperationsOverview, ProviderFailureRate } from './data';
import { queryAiOperationsOverview } from './service';

const emptyOverview: PlatformAiOperationsOverview = {
  expiredClaims: 0,
  retryExhausted: 0,
  unpricedUsage: 0,
  incompleteUsage: 0,
  settlementReview: 0,
  totalProviderCost: 0,
  totalSettledPoints: 0,
  providerFailureRates: [],
};

const columns = [
  { title: '服务商', dataIndex: 'provider', key: 'provider' },
  { title: '调用数', dataIndex: 'total', key: 'total' },
  { title: '失败数', dataIndex: 'failed', key: 'failed' },
  {
    title: '失败率',
    dataIndex: 'failureRate',
    key: 'failureRate',
    render: (value: number) => (
      <Tag
        color={value >= 0.2 ? 'red' : 'green'}
      >{`${(value * 100).toFixed(2)}%`}</Tag>
    ),
  },
];

const AiOperationsPage = () => {
  const [overview, setOverview] = useState(emptyOverview);

  useEffect(() => {
    queryAiOperationsOverview().then((response) => {
      if (response.success && response.data) {
        setOverview(response.data);
      }
    });
  }, []);

  const metrics = [
    ['过期 claim', overview.expiredClaims],
    ['重试耗尽', overview.retryExhausted],
    ['未定价 usage', overview.unpricedUsage],
    ['不完整 usage', overview.incompleteUsage],
    ['结算复核', overview.settlementReview],
    ['Provider 成本', overview.totalProviderCost],
    ['已结算积分', overview.totalSettledPoints],
  ] as const;

  return (
    <PageContainer title="AI运维">
      <div style={{ display: 'grid', gap: 16 }}>
        <div
          style={{
            display: 'grid',
            gap: 12,
            gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
          }}
        >
          {metrics.map(([title, value]) => (
            <div
              key={title}
              style={{ border: '1px solid var(--app-color-border-secondary)', padding: 16 }}
            >
              <Statistic title={title} value={value} />
            </div>
          ))}
        </div>
        <Table<ProviderFailureRate>
          rowKey="provider"
          size="small"
          pagination={false}
          columns={columns}
          dataSource={overview.providerFailureRates}
        />
      </div>
    </PageContainer>
  );
};

export default AiOperationsPage;
