import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Input, Select, Table, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import styles from './index.module.css';
import {
  type DownloadPage,
  type DownloadTask,
  effectiveStatus,
  listDownloadTasks,
  receiveDownload,
  retryDownloadTask,
  sourceLabels,
  statusLabels,
} from './service';

const dateText = (value?: string) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const sizeText = (value?: number) =>
  value === undefined || value === null
    ? '—'
    : value < 1048576
      ? `${(value / 1024).toFixed(1)} KB`
      : value < 1073741824
        ? `${(value / 1048576).toFixed(1)} MB`
        : `${(value / 1073741824).toFixed(2)} GB`;
const colors = {
  QUEUED: 'default',
  RUNNING: 'processing',
  READY: 'success',
  FAILED: 'error',
  EXPIRED: 'default',
};

export function DownloadList({ tenant }: { tenant: number }) {
  const { message } = App.useApp();
  const [query, setQuery] = useState({
    page: 1,
    pageSize: 10,
    keyword: '',
    status: '',
  });
  const [data, setData] = useState<DownloadPage>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState<string>();
  const busyRef = useRef(false);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    let disposed = false;
    let pending = false;
    const controller = new AbortController();
    const load = async () => {
      if (pending) return;
      pending = true;
      try {
        const result = await listDownloadTasks(
          tenant,
          query,
          controller.signal,
        );
        if (!disposed) {
          setData(result);
          setError('');
        }
      } catch (failure) {
        if (!disposed)
          setError(
            failure instanceof Error ? failure.message : '任务加载失败，请重试',
          );
      } finally {
        pending = false;
        if (!disposed) setLoading(false);
      }
    };
    setLoading(true);
    void load();
    const reload = () => {
      if (!document.hidden) void load();
    };
    const timer = window.setInterval(reload, 5000);
    window.addEventListener('focus', reload);
    window.addEventListener('download-center-created', reload);
    return () => {
      disposed = true;
      controller.abort();
      window.clearInterval(timer);
      window.removeEventListener('focus', reload);
      window.removeEventListener('download-center-created', reload);
    };
  }, [tenant, query, refresh]);
  const action = async (task: DownloadTask) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(task.id);
    try {
      if (effectiveStatus(task) === 'READY')
        await receiveDownload(tenant, task);
      else {
        await retryDownloadTask(tenant, task.id);
        if (alive.current) {
          message.success('已创建新的下载任务');
          setQuery({ page: 1, pageSize: 10, keyword: '', status: '' });
        }
      }
    } catch (failure) {
      if (alive.current)
        message.error(
          failure instanceof Error ? failure.message : '操作失败，请重试',
        );
    } finally {
      busyRef.current = false;
      if (alive.current) {
        setBusy(undefined);
        setRefresh((value) => value + 1);
      }
    }
  };
  const columns: TableColumnsType<DownloadTask> = [
    {
      title: '下载任务',
      dataIndex: 'name',
      width: 260,
      render: (name: string, task) => (
        <div>
          <div className={styles.taskName}>{name}.zip</div>
          <span className={styles.secondary}>
            {sourceLabels[task.sourceType] || '文件下载'} · {task.fileCount}{' '}
            个文件
          </span>
        </div>
      ),
    },
    {
      title: '状态',
      width: 180,
      render: (_, task) => (
        <div>
          <Tag color={colors[effectiveStatus(task)]}>
            {statusLabels[effectiveStatus(task)]}
          </Tag>
          {task.errorMessage && (
            <div className={styles.failure}>{task.errorMessage}</div>
          )}
        </div>
      ),
    },
    { title: '文件大小', dataIndex: 'fileSize', width: 110, render: sizeText },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: dateText },
    {
      title: '生成成功时间',
      dataIndex: 'completedAt',
      width: 180,
      render: dateText,
    },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      width: 180,
      render: (value: string, task) => (
        <div>
          {dateText(value)}
          {effectiveStatus(task) === 'EXPIRED' && (
            <div className={styles.secondary}>已失效，压缩包自动清理</div>
          )}
        </div>
      ),
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      render: (_, task) => {
        const status = effectiveStatus(task);
        return status === 'READY' ||
          status === 'FAILED' ||
          status === 'EXPIRED' ? (
          <Button
            type="link"
            loading={busy === task.id}
            disabled={Boolean(busy) && busy !== task.id}
            onClick={() => void action(task)}
          >
            {status === 'READY' ? '下载 ZIP' : '重新生成'}
          </Button>
        ) : (
          <span className={styles.secondary}>
            {status === 'QUEUED' ? '等待生成' : '正在打包'}
          </span>
        );
      },
    },
  ];
  return (
    <main className={styles.page}>
      <div className={styles.header}>
        <div>
          <h1>
            <DownloadOutlined /> 下载中心
          </h1>
          <p>统一管理下载任务，文件异步打包完成后在此领取。</p>
        </div>
        <Button
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={() => setRefresh((value) => value + 1)}
        >
          刷新列表
        </Button>
      </div>
      <Alert
        type="info"
        showIcon
        title="ZIP 压缩包自生成成功起保留 24 小时"
        description="到期后立即停止下载，并自动从服务器清除压缩包。原始素材不受影响；如需再次下载，可重新生成。"
      />
      <section className={styles.content} aria-label="下载任务列表">
        <div className={styles.filters}>
          <Input.Search
            aria-label="搜索下载任务"
            placeholder="搜索任务名称"
            allowClear
            onSearch={(keyword) =>
              setQuery((value) => ({ ...value, keyword, page: 1 }))
            }
          />
          <Select
            aria-label="任务状态"
            value={query.status}
            onChange={(status) =>
              setQuery((value) => ({ ...value, status, page: 1 }))
            }
            options={[
              { value: '', label: '全部状态' },
              ...Object.entries(statusLabels).map(([value, label]) => ({
                value,
                label,
              })),
            ]}
          />
          <span className={styles.secondary}>
            共 {data?.total ?? 0} 条任务 · 仅本人在当前团队发起的下载
          </span>
        </div>
        {error && <Alert type="error" showIcon title={error} />}
        <Table<DownloadTask>
          rowKey="id"
          dataSource={data?.items || []}
          columns={columns}
          loading={loading}
          scroll={{ x: 1220 }}
          locale={{
            emptyText: error
              ? '暂时无法加载任务，请刷新重试'
              : '暂无下载任务，在创作或素材页面点击下载后，任务将出现在这里',
          }}
          pagination={{
            current: query.page,
            pageSize: query.pageSize,
            total: data?.total ?? 0,
            showSizeChanger: true,
            onChange: (page, pageSize) =>
              setQuery((value) => ({ ...value, page, pageSize })),
          }}
        />
      </section>
    </main>
  );
}
export default function DownloadCenterPage() {
  const { initialState } = useModel('@@initialState');
  const tenant = initialState?.currentTenantId;
  return tenant ? (
    <DownloadList key={tenant} tenant={tenant} />
  ) : (
    <div className={styles.page}>
      <Alert type="info" title="请先选择当前团队" />
    </div>
  );
}
