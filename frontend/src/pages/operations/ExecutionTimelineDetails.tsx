import { Alert, Card, Space, Table, Typography } from 'antd';
import { columns, time } from './presentation';
import type { AiCall, Attempt, Execution, ExecutionTimeline } from './service';

export function ExecutionTimelineDetails({
  data,
}: {
  data: ExecutionTimeline;
}) {
  const truncated =
    data.truncated ||
    [data.versions, data.attempts, data.calls].some(
      (rows) => rows.length > 100,
    );
  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        type="warning"
        title="时间线覆盖不完整（PARTIAL）"
        description="版本展示最新保留上下文；尝试/调用按所选时间。只展示成功采集或当前可查的证据，缺失记录不能推断为未发生。服务商筛选仅作用于尝试与调用。"
      />
      {truncated && (
        <Alert
          type="warning"
          title="结果已截断：每个数据源最多100条"
          description="请缩小时间或筛选范围后重新打开详情。"
        />
      )}
      <Typography.Text type="secondary">
        执行 {data.executionId} · {time(data.from)} — {time(data.to)} ·
        Asia/Shanghai · 只读
      </Typography.Text>
      <Card title="执行版本时间线" size="small">
        <Table<Execution>
          size="small"
          pagination={false}
          scroll={{ x: 'max-content' }}
          rowKey={(row) => `${row.executionId}:${row.executionVersion}`}
          dataSource={data.versions.slice(0, 100)}
          columns={columns<Execution>([
            ['executionVersion', '版本'],
            ['status', '状态'],
            ['phase', '阶段'],
            ['createdAt', '创建时间'],
            ['startedAt', '开始时间'],
            ['completedAt', '完成时间'],
            ['observedAt', '观察时间'],
            ['pointSettlementStatus', '结算状态'],
            ['errorCode', '错误码'],
            ['errorMessage', '脱敏错误'],
            ['traceId', 'Trace ID'],
          ])}
          locale={{ emptyText: '此范围没有保留的执行版本' }}
        />
      </Card>
      <Card title="执行尝试时间线" size="small">
        <Table<Attempt>
          size="small"
          pagination={false}
          scroll={{ x: 'max-content' }}
          rowKey={(row) => row.id}
          dataSource={data.attempts.slice(0, 100)}
          columns={columns<Attempt>([
            ['id', '尝试ID'],
            ['executionVersion', '版本'],
            ['phase', '阶段'],
            ['attemptNo', '尝试序号'],
            ['status', '状态'],
            ['providerId', '服务商'],
            ['modelId', '模型'],
            ['transportOutcome', '传输结果'],
            ['businessOutcome', '业务结果'],
            ['errorCode', '错误码'],
            ['errorMessage', '脱敏错误'],
            ['startedAt', '开始时间'],
            ['finishedAt', '结束时间'],
          ])}
          locale={{ emptyText: '此范围没有可查的执行尝试' }}
        />
      </Card>
      <Card title="AI调用时间线" size="small">
        <Table<AiCall>
          size="small"
          pagination={false}
          scroll={{ x: 'max-content' }}
          rowKey={(row) => row.callLogId}
          dataSource={data.calls.slice(0, 100)}
          columns={columns<AiCall>([
            ['callLogId', '调用ID'],
            ['attemptId', '尝试ID'],
            ['occurredAt', '发生时间'],
            ['providerId', '服务商'],
            ['modelId', '模型'],
            ['scene', '场景'],
            ['transportOutcome', '传输结果'],
            ['businessOutcome', '业务结果'],
            ['errorCode', '错误码'],
            ['durationMs', '耗时 ms'],
            ['traceId', 'Trace ID'],
          ])}
          locale={{ emptyText: '此范围没有保留的AI调用' }}
        />
      </Card>
    </Space>
  );
}
