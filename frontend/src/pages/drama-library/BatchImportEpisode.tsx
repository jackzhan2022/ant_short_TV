import { useIntl } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Input,
  InputNumber,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  Upload,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  type MediaUploadHandle,
  startMediaUpload,
  type VerifiedMediaUpload,
} from '@/services/mediaUpload';
import { importEpisode } from './service';

type ImportRow = {
  key: string;
  file: File;
  episodeNo: number;
  title: string;
  percent: number;
  status: 'pending' | 'uploading' | 'importing' | 'done' | 'failed';
  error?: string;
  handle?: MediaUploadHandle;
  verified?: VerifiedMediaUpload;
};
export default function BatchImportEpisode({
  dramaId,
  nextNo,
  onClose,
  onImported,
}: {
  dramaId: number;
  nextNo: number;
  onClose: () => void;
  onImported: () => Promise<void>;
}) {
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message } = App.useApp();
  const [rows, setRows] = useState<ImportRow[]>([]);
  const rowsRef = useRef<ImportRow[]>([]);
  const working = useRef(false);
  const [busy, setBusy] = useState(false);
  const alive = useRef(true);
  const sync = () => {
    if (alive.current) setRows([...rowsRef.current]);
  };
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      for (const row of rowsRef.current) row.handle?.cancel();
    };
  }, []);
  const run = async (keys?: string[]) => {
    if (working.current) return;
    const targets = rowsRef.current.filter(
      (row) => row.status !== 'done' && (!keys || keys.includes(row.key)),
    );
    if (!targets.length) return;
    if (
      rowsRef.current.some(
        (row) =>
          !row.title.trim() ||
          row.title.trim().length > 200 ||
          !Number.isInteger(row.episodeNo) ||
          row.episodeNo < 1 ||
          row.episodeNo > 2147483647,
      ) ||
      new Set(rowsRef.current.map((row) => row.episodeNo)).size !==
        rowsRef.current.length
    ) {
      message.warning(t('batchInvalid', '请填写有效且不重复的集号及单集标题'));
      return;
    }
    working.current = true;
    setBusy(true);
    let imported = false;
    try {
      for (const row of targets) {
        if (!alive.current) break;
        try {
          row.error = undefined;
          if (!row.verified) {
            row.status = 'uploading';
            sync();
            if (row.handle) row.verified = await row.handle.retry();
            else {
              row.handle = await startMediaUpload(row.file, {
                onProgress: (progress) => {
                  row.percent = Math.round(progress.percent * 100);
                  sync();
                },
              });
              if (!alive.current) {
                row.handle.cancel();
                break;
              }
              row.verified = await row.handle.attempt;
            }
          }
          if (!alive.current) break;
          row.percent = 100;
          row.status = 'importing';
          sync();
          await importEpisode(dramaId, {
            episodeNo: row.episodeNo,
            title: row.title.trim(),
            uploadSessionToken: row.verified.sessionToken,
          });
          row.status = 'done';
          imported = true;
        } catch (error) {
          row.status = 'failed';
          row.error =
            error instanceof Error
              ? error.message
              : t('importFailed', '导入失败，请重试');
        }
        sync();
      }
      if (imported && alive.current) await onImported();
    } catch {
      message.warning(t('refreshFailed', '导入结果已保留，请刷新目录确认'));
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  };
  const labels = {
    pending: t('pending', '待导入'),
    uploading: t('uploading', '上传与校验中'),
    importing: t('importing', '导入中'),
    done: t('imported', '已导入'),
    failed: t('failed', '失败'),
  };
  return (
    <Modal
      open
      width={1000}
      title={t('batchImport', '批量本地导入')}
      onCancel={onClose}
      closable={!busy}
      keyboard={!busy}
      mask={{ closable: !busy }}
      footer={
        <Button disabled={busy} onClick={onClose}>
          {t('close', '关闭')}
        </Button>
      }
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Alert
          type="info"
          title={t(
            'batchHint',
            '按选择顺序分配集号，可在导入前调整。逐文件上传并校验，成功文件不会重复导入。失败项保留会话，可单独重试。',
          )}
        />
        <Space wrap>
          <Upload
            multiple
            accept="video/*"
            disabled={busy}
            showUploadList={false}
            beforeUpload={(file) => {
              if (!file.type.startsWith('video/') || !file.size) {
                message.error(t('videoOnly', '请选择非空视频文件'));
                return Upload.LIST_IGNORE;
              }
              const key = `${file.name}:${file.size}:${file.lastModified}`;
              if (rowsRef.current.some((row) => row.key === key)) return false;
              if (rowsRef.current.length >= 500) {
                message.warning(t('batchLimit', '单次最多选择500个文件'));
                return Upload.LIST_IGNORE;
              }
              rowsRef.current.push({
                key,
                file,
                episodeNo:
                  Math.max(
                    nextNo - 1,
                    ...rowsRef.current.map((row) => row.episodeNo),
                  ) + 1,
                title: file.name.replace(/\.[^.]+$/, '').slice(0, 200),
                percent: 0,
                status: 'pending',
              });
              sync();
              return false;
            }}
          >
            <Button disabled={busy}>{t('chooseFiles', '选择多个视频')}</Button>
          </Upload>
          <Button
            type="primary"
            loading={busy}
            disabled={!rows.some((row) => row.status === 'pending')}
            onClick={() =>
              void run(
                rows
                  .filter((row) => row.status === 'pending')
                  .map((row) => row.key),
              )
            }
          >
            {t('startBatch', '开始批量导入')}
          </Button>
          <Button
            disabled={busy || !rows.some((row) => row.status === 'failed')}
            onClick={() =>
              void run(
                rows
                  .filter((row) => row.status === 'failed')
                  .map((row) => row.key),
              )
            }
          >
            {t('retryFailed', '重试失败项')}
          </Button>
        </Space>
        <Table<ImportRow>
          rowKey="key"
          dataSource={rows}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 850 }}
          columns={[
            { title: t('file', '文件'), render: (_, row) => row.file.name },
            {
              title: t('episodeNo', '剧库集号'),
              width: 120,
              render: (_, row) => (
                <InputNumber
                  aria-label={`${row.file.name} 集号`}
                  min={1}
                  max={2147483647}
                  precision={0}
                  disabled={busy || row.status === 'done'}
                  value={row.episodeNo}
                  onChange={(value) => {
                    row.episodeNo = value ?? 0;
                    sync();
                  }}
                />
              ),
            },
            {
              title: t('episodeTitle', '单集标题'),
              render: (_, row) => (
                <Input
                  aria-label={`${row.file.name} 标题`}
                  maxLength={200}
                  disabled={busy || row.status === 'done'}
                  value={row.title}
                  onChange={(event) => {
                    row.title = event.target.value;
                    sync();
                  }}
                />
              ),
            },
            {
              title: t('status', '状态'),
              width: 220,
              render: (_, row) => (
                <Space orientation="vertical">
                  <Tag
                    color={
                      row.status === 'done'
                        ? 'success'
                        : row.status === 'failed'
                          ? 'error'
                          : 'default'
                    }
                  >
                    {labels[row.status]}
                  </Tag>
                  {row.status === 'uploading' && (
                    <Progress percent={row.percent} />
                  )}
                  {row.error && <span>{row.error}</span>}
                </Space>
              ),
            },
            {
              title: t('actions', '操作'),
              render: (_, row) => (
                <Space>
                  {row.status === 'failed' && (
                    <Button disabled={busy} onClick={() => void run([row.key])}>
                      {t('retry', '重试')}
                    </Button>
                  )}
                  {row.status !== 'done' && (
                    <Button
                      disabled={busy}
                      onClick={() => {
                        row.handle?.cancel();
                        rowsRef.current = rowsRef.current.filter(
                          (item) => item.key !== row.key,
                        );
                        sync();
                      }}
                    >
                      {t('remove', '移除')}
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Space>
    </Modal>
  );
}
