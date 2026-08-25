import type { ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history, useModel } from '@umijs/max';
import { App, Button, Tag } from 'antd';
import type { TenantSummary } from '@/services/account-team/types';
import { applyBootstrapSelection } from '@/services/account-team/bootstrap';
import { queryMyTenants } from '@/services/account-team/tenant';

const TeamSelect = () => {
  const { message } = App.useApp();
  const { setInitialState } = useModel('@@initialState');

  const columns: ProColumns<TenantSummary>[] = [
    { title: '团队名称', dataIndex: 'name' },
    { title: '团队编码', dataIndex: 'code', search: false },
    {
      title: '身份',
      dataIndex: 'memberType',
      search: false,
      render: (_, record) => (
        <Tag color={record.memberType === 'OWNER' ? 'blue' : 'default'}>
          {record.memberType}
        </Tag>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Button
          type="primary"
          onClick={async () => {
            await applyBootstrapSelection(record.id, setInitialState);
            message.success(`已进入 ${record.name}`);
            history.replace('/team/my');
          }}
        >
          进入
        </Button>
      ),
    },
  ];

  return (
    <PageContainer>
      <ProTable<TenantSummary>
        rowKey="id"
        headerTitle="选择创作团队"
        search={false}
        request={async () => {
          const response = await queryMyTenants();
          return { data: response.data, success: response.success };
        }}
        columns={columns}
      />
    </PageContainer>
  );
};

export default TeamSelect;
