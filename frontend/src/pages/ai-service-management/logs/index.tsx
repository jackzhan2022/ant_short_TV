import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Empty, Tag } from 'antd';
import { statusText } from '@/utils/fieldDictionary';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { AiCallLog } from './data';
import { SERVICE_TYPE_TEXT } from './data';
import { queryAiCallLogs } from './service';

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  CANCELED: 'default',
};

const serviceTypeValueEnum = {
  TEXT: { text: '文本' },
  IMAGE: { text: '图片' },
  VIDEO: { text: '视频' },
  VOICE: { text: '语音' },
};

const statusValueEnum = {
  SUCCESS: { text: '成功', status: 'Success' },
  FAILED: { text: '失败', status: 'Error' },
  CANCELED: { text: '已取消', status: 'Default' },
};

const AiCallLogsPage = () => {
  const tenantId = getCurrentTenantId();

  if (!tenantId) {
    return (
      <Empty description="请先在我的团队中选择当前创作团队" />
    );
  }

  const columns: ProColumns<AiCallLog>[] = [
    {
      title: '业务场景',
      dataIndex: 'businessScene',
      ellipsis: true,
      width: 140,
    },
    {
      title: '服务类型',
      dataIndex: 'serviceType',
      valueEnum: serviceTypeValueEnum,
      width: 110,
      render: (_, record) => (
        <Tag>{SERVICE_TYPE_TEXT[record.serviceType] ?? record.serviceType}</Tag>
      ),
    },
    {
      title: '调用状态',
      dataIndex: 'status',
      valueEnum: statusValueEnum,
      width: 110,
      render: (_, record) => (
        <Tag color={statusColor[record.status] ?? 'default'}>
          {statusText(record.status)}
        </Tag>
      ),
    },
    {
      title: '服务商',
      dataIndex: 'provider',
      search: false,
      width: 120,
    },
    {
      title: '模型',
      dataIndex: 'model',
      search: false,
      ellipsis: true,
      width: 180,
    },
    {
      title: '请求摘要',
      dataIndex: 'requestSummary',
      search: false,
      ellipsis: true,
      width: 220,
    },
    {
      title: '响应摘要',
      dataIndex: 'responseSummary',
      search: false,
      ellipsis: true,
      width: 220,
    },
    {
      title: '失败原因',
      dataIndex: 'errorMessage',
      search: false,
      ellipsis: true,
      width: 200,
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      search: false,
      width: 100,
      renderText: (value) => `${value ?? 0} ms`,
    },
    {
      title: '调用时间',
      dataIndex: 'createdAt',
      valueType: 'dateTime',
      search: false,
      width: 180,
    },
  ];

  return (
    <ProTable<AiCallLog>
        rowKey="id"
        headerTitle="AI调用日志"
        columns={columns}
        tableLayout="fixed"
        scroll={{ x: 1500 }}
        request={async (params) => {
          const response = await queryAiCallLogs(tenantId, {
            current: params.current,
            pageSize: params.pageSize,
            serviceType: params.serviceType,
            status: params.status,
            businessScene: params.businessScene,
          });
          return {
            data: response.data.records,
            success: response.success,
            total: response.data.total,
          };
        }}
    />
  );
};

export default AiCallLogsPage;
