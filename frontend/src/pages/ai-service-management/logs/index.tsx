import { type ProColumns, ProTable } from '@ant-design/pro-components';
import { Button, Descriptions, Drawer, Empty, Tag } from 'antd';
import { useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { statusText } from '@/utils/fieldDictionary';
import { AdminHeading, useAdminStyles } from '../admin-ui';
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
const tokenCount = (value?: number) =>
  value == null ? '-' : value.toLocaleString();
const ordinaryInput = (record: AiCallLog) =>
  record.promptTokens == null
    ? '-'
    : Math.max(
        0,
        record.promptTokens -
          (record.cachedInputTokens ?? 0) -
          (record.cacheWriteTokens ?? 0),
      ).toLocaleString();
const cacheRate = (record: AiCallLog) =>
  record.cachedInputTokens == null ||
  record.promptTokens == null ||
  record.promptTokens <= 0
    ? '-'
    : `${((record.cachedInputTokens / record.promptTokens) * 100).toFixed(1)}%`;

const AiCallLogsPage = () => {
  const { styles } = useAdminStyles();
  const [selected, setSelected] = useState<AiCallLog>();
  const tenantId = getCurrentTenantId();
  if (!tenantId)
    return (
      <div className={styles.page}>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </div>
    );

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
      width: 85,
      render: (_, record) => (
        <Tag>{SERVICE_TYPE_TEXT[record.serviceType] ?? record.serviceType}</Tag>
      ),
    },
    {
      title: '模型 / 服务商',
      dataIndex: 'model',
      search: false,
      width: 150,
      render: (_, record) => (
        <div className={styles.summary}>
          <strong>{record.model || '-'}</strong>
          <small>{record.provider || '-'}</small>
        </div>
      ),
    },
    {
      title: '调用状态',
      dataIndex: 'status',
      valueEnum: statusValueEnum,
      width: 85,
      render: (_, record) => (
        <Tag color={statusColor[record.status] ?? 'default'}>
          {statusText(record.status)}
        </Tag>
      ),
    },
    {
      title: 'Token 用量',
      dataIndex: 'totalTokens',
      search: false,
      width: 150,
      render: (_, record) => (
        <div className={styles.summary}>
          <span>
            {tokenCount(
              record.totalTokens ??
                (record.promptTokens == null && record.completionTokens == null
                  ? undefined
                  : (record.promptTokens ?? 0) +
                    (record.completionTokens ?? 0)),
            )}
          </span>
          <small>
            输入 {tokenCount(record.promptTokens)} / 输出{' '}
            {tokenCount(record.completionTokens)}
          </small>
        </div>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      search: false,
      width: 85,
      renderText: (value) => `${value ?? 0} ms`,
    },
    {
      title: '调用时间',
      dataIndex: 'createdAt',
      valueType: 'dateTime',
      render: (_, record) => (
        <div className={styles.summary}>
          <span>{record.createdAt.slice(0, 10)}</span>
          <small>{record.createdAt.slice(11)}</small>
        </div>
      ),
      search: false,
      width: 120,
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 75,
      render: (_, record) => (
        <Button type="link" onClick={() => setSelected(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <AdminHeading
        title="调用日志"
        description="查看模型请求、运行状态与用量明细。"
      />
      <ProTable<AiCallLog>
        className={styles.compactTable}
        rowKey="id"
        headerTitle={false}
        columns={columns}
        tableLayout="fixed"
        scroll={{ x: 890 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
          span: { xs: 24, sm: 12, md: 8, lg: 6, xl: 6, xxl: 6 },
        }}
        options={{
          density: false,
          fullScreen: false,
          setting: false,
          reload: true,
        }}
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
      <Drawer
        title="调用详情"
        rootClassName={styles.overlay}
        open={Boolean(selected)}
        onClose={() => setSelected(undefined)}
        size={680}
        destroyOnHidden
      >
        {selected && (
          <>
            <Descriptions
              column={2}
              items={[
                { key: 'id', label: '日志 ID', children: selected.id },
                {
                  key: 'status',
                  label: '调用状态',
                  children: (
                    <Tag color={statusColor[selected.status]}>
                      {statusText(selected.status)}
                    </Tag>
                  ),
                },
                {
                  key: 'scene',
                  label: '业务场景',
                  children: selected.businessScene,
                },
                {
                  key: 'type',
                  label: '服务类型',
                  children:
                    SERVICE_TYPE_TEXT[selected.serviceType] ??
                    selected.serviceType,
                },
                {
                  key: 'model',
                  label: '模型',
                  children: selected.model || '-',
                },
                {
                  key: 'provider',
                  label: '服务商',
                  children: selected.provider || '-',
                },
                {
                  key: 'modelId',
                  label: '模型 ID',
                  children: selected.modelId ?? '-',
                },
                {
                  key: 'providerId',
                  label: '服务商 ID',
                  children: selected.providerId ?? '-',
                },
                {
                  key: 'tenantId',
                  label: '团队 ID',
                  children: selected.tenantId,
                },
                { key: 'userId', label: '用户 ID', children: selected.userId },
                {
                  key: 'taskId',
                  label: '任务 ID',
                  children: selected.taskId ?? '-',
                },
                {
                  key: 'time',
                  label: '调用时间',
                  children: selected.createdAt,
                },
                {
                  key: 'duration',
                  label: '耗时',
                  children: `${selected.durationMs ?? 0} ms`,
                },
                {
                  key: 'input',
                  label: '输入 Token',
                  children: ordinaryInput(selected),
                },
                {
                  key: 'allInput',
                  label: '输入 Token 总量',
                  children: tokenCount(selected.promptTokens),
                },
                {
                  key: 'cache',
                  label: '缓存 Token',
                  children: tokenCount(selected.cachedInputTokens),
                },
                {
                  key: 'cacheWrite',
                  label: '缓存写入',
                  children: tokenCount(selected.cacheWriteTokens),
                },
                {
                  key: 'output',
                  label: '输出 Token',
                  children: tokenCount(selected.completionTokens),
                },
                {
                  key: 'total',
                  label: '总 Token',
                  children: tokenCount(selected.totalTokens),
                },
                {
                  key: 'cacheRate',
                  label: '缓存命中率',
                  children: cacheRate(selected),
                },
                {
                  key: 'cacheKey',
                  label: '缓存键',
                  children: selected.promptCacheKey || '-',
                  span: 2,
                },
              ]}
            />
            <h3>请求摘要</h3>
            <pre className={styles.code}>{selected.requestSummary || '-'}</pre>
            <h3>响应摘要</h3>
            <pre className={styles.code}>{selected.responseSummary || '-'}</pre>
            <h3>失败原因</h3>
            <pre className={styles.code}>{selected.errorMessage || '-'}</pre>
          </>
        )}
      </Drawer>
    </div>
  );
};

export default AiCallLogsPage;
