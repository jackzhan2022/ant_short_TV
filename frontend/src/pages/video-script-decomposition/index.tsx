import { HistoryOutlined, PlusOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Upload, type UploadFile, type UploadProps } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import PointEstimateButton from '@/components/PointEstimateButton';
import type { MediaUploadHandle } from '@/services/mediaUpload';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import BatchHistory from './BatchHistory';
import styles from './index.module.css';
import {
  pollingDelay,
  shouldPollVideoDecompositionBatch,
  VideoDecompositionQueries,
} from './queries';
import ScreenplayWorkspace from './ScreenplayWorkspace';
import type { VideoDecompositionUpload } from './service';
import {
  createVideoDecompositionBatch,
  queryVideoDecompositionBatches,
  queryVideoUnderstandingModels,
  uploadEpisodeVideo,
} from './service';
import UploadComposer from './UploadComposer';

export { shouldPollVideoDecompositionBatch } from './queries';
export { buildCopyAllScreenplays } from './ScreenplayWorkspace';

type EpisodeUpload = {
  uid: string;
  episodeNo: number;
  fileName: string;
  size: number;
  mimeType?: string;
  status: 'READY';
  uploadStatus?: UploadFile['status'];
  storagePath?: string;
  uploadSessionToken?: string;
};

const MAX_VIDEO_SIZE = 1024 * 1024 * 1024;
const SUPPORTED_VIDEO_TYPES = [
  'video/mp4',
  'video/quicktime',
  'video/x-msvideo',
];
const padTimePart = (value: number) => String(value).padStart(2, '0');

export const buildDefaultVideoDecompositionBatchName = (date = new Date()) =>
  '拆剧批次-' +
  date.getFullYear() +
  padTimePart(date.getMonth() + 1) +
  padTimePart(date.getDate()) +
  '-' +
  padTimePart(date.getHours()) +
  padTimePart(date.getMinutes()) +
  padTimePart(date.getSeconds());

export const canCreateVideoDecompositionBatch = (episodes: EpisodeUpload[]) =>
  episodes.length > 0 && episodes.every((episode) => episode.storagePath);

const normalizeEpisodes = (files: UploadFile[]): EpisodeUpload[] =>
  files.map((file, index) => ({
    uid: file.uid,
    episodeNo: index + 1,
    fileName: file.name,
    size: file.size ?? 0,
    mimeType: file.type,
    status: 'READY',
    uploadStatus: file.status,
    storagePath: (file.response as VideoDecompositionUpload | undefined)
      ?.storagePath,
    uploadSessionToken: (file.response as VideoDecompositionUpload | undefined)
      ?.uploadSessionToken,
  }));

const VideoScriptDecompositionPage = () => {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [view, setView] = useState<'compose' | 'history'>('compose');
  const removedUploads = useRef(new Set<string>());
  const [files, setFiles] = useState<UploadFile[]>([]);
  const uploadHandles = useRef(new Map<string, MediaUploadHandle>());
  const activeUploads = useRef(new Set<string>());
  const [batchName, setBatchName] = useState(
    buildDefaultVideoDecompositionBatchName,
  );
  const [modelId, setModelId] = useState<number>();
  const modelsQuery = useQuery({
    queryKey: ['video-understanding-models'],
    queryFn: async () => (await queryVideoUnderstandingModels()).data,
    staleTime: 5 * 60 * 1000,
  });
  const models = useMemo(
    () =>
      (modelsQuery.data ?? []).filter(
        (model) =>
          model.serviceType === 'VIDEO_UNDERSTANDING' &&
          model.status === 'ENABLED',
      ),
    [modelsQuery.data],
  );
  const [batchPage, setBatchPage] = useState(1);
  const [batchPageSize, setBatchPageSize] = useState(20);
  const [creating, setCreating] = useState(false);
  const [openBatchId, setOpenBatchId] = useState<number>();
  const batchesQuery = useQuery({
    queryKey: ['video-batches', batchPage, batchPageSize],
    queryFn: async ({ signal }) =>
      (
        await queryVideoDecompositionBatches(
          undefined,
          { current: batchPage, pageSize: batchPageSize },
          signal,
        )
      ).data,
    enabled: !openBatchId,
    refetchInterval: (query) =>
      query.state.fetchStatus !== 'idle'
        ? false
        : query.state.status === 'error' ||
            query.state.data?.data.some((batch) =>
              shouldPollVideoDecompositionBatch(batch.status),
            )
          ? pollingDelay(
              query.state.status === 'error' ? query.state.errorUpdateCount : 0,
            )
          : false,
  });
  const batches = batchesQuery.data?.data ?? [];
  const batchTotal = batchesQuery.data?.total ?? 0;
  const episodes = useMemo(() => normalizeEpisodes(files), [files]);

  useEffect(() => {
    const handles = uploadHandles.current;
    const active = activeUploads.current;
    return () => {
      active.clear();
      for (const handle of handles.values()) handle.cancel();
      handles.clear();
    };
  }, []);

  const removeUpload = (uid: string) => {
    removedUploads.current.add(uid);
    activeUploads.current.delete(uid);
    uploadHandles.current.get(uid)?.cancel();
    uploadHandles.current.delete(uid);
    setFiles((current) => current.filter((file) => file.uid !== uid));
  };

  const uploadRecovery = (uid: string) => ({
    handle: uploadHandles.current.get(uid),
    onHandle: (handle: MediaUploadHandle) => {
      if (activeUploads.current.has(uid))
        uploadHandles.current.set(uid, handle);
      else handle.cancel();
    },
  });

  const updateUploadProgress = (uid: string, percent: number) => {
    setFiles((current) =>
      current.map((file) => (file.uid === uid ? { ...file, percent } : file)),
    );
  };

  const retryUpload = async (uid: string) => {
    const file = files.find((item) => item.uid === uid);
    if (!file?.originFileObj || file.status !== 'error') return;
    activeUploads.current.add(uid);
    setFiles((current) =>
      current.map((item) =>
        item.uid === uid
          ? { ...item, status: 'uploading', error: undefined }
          : item,
      ),
    );
    try {
      const response = await uploadEpisodeVideo(
        file.originFileObj,
        (percent) => updateUploadProgress(uid, percent),
        uploadRecovery(uid),
      );
      if (activeUploads.current.has(uid)) {
        setFiles((current) =>
          current.map((item) =>
            item.uid === uid
              ? {
                  ...item,
                  status: 'done',
                  percent: 100,
                  response: response.data,
                }
              : item,
          ),
        );
      }
    } catch (error) {
      if (activeUploads.current.has(uid)) {
        setFiles((current) =>
          current.map((item) =>
            item.uid === uid ? { ...item, status: 'error', error } : item,
          ),
        );
      }
    }
  };

  useEffect(() => {
    setModelId((current) =>
      models.some((model) => model.id === current)
        ? current
        : (models.find((model) => model.isDefault)?.id ?? models[0]?.id),
    );
  }, [models]);

  const reorderFile = (uid: string, targetUid: string) => {
    setFiles((current) => {
      const from = current.findIndex((file) => file.uid === uid);
      const to = current.findIndex((file) => file.uid === targetUid);
      if (from < 0 || to < 0 || from === to) return current;
      const next = [...current];
      const [file] = next.splice(from, 1);
      next.splice(to, 0, file);
      return next;
    });
  };

  const moveFile = (uid: string, direction: -1 | 1) => {
    setFiles((current) => {
      const index = current.findIndex((file) => file.uid === uid);
      const target = index + direction;
      if (index < 0 || target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const uploadProps: UploadProps = {
    disabled: creating,
    accept: 'video/mp4,video/quicktime,video/x-msvideo,.mp4,.mov,.avi',
    multiple: true,
    fileList: files,
    showUploadList: false,
    beforeUpload: (file) => {
      if (file.size > MAX_VIDEO_SIZE) {
        message.error(`${file.name} 超过单文件 1GB 上限`);
        return Upload.LIST_IGNORE;
      }
      if (
        !/\.(mp4|mov|avi)$/i.test(file.name) ||
        (file.type && !SUPPORTED_VIDEO_TYPES.includes(file.type))
      ) {
        message.error(`${file.name} 暂不支持该视频格式`);
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: (options) => {
      const file = options.file as File & { uid: string };
      const uid = file.uid;
      activeUploads.current.add(uid);
      void uploadEpisodeVideo(
        file,
        (percent) => {
          updateUploadProgress(uid, percent);
          options.onProgress?.({ percent });
        },
        uploadRecovery(uid),
      ).then(
        (response) => {
          if (activeUploads.current.has(uid))
            options.onSuccess?.(response.data);
        },
        (error) => {
          if (activeUploads.current.has(uid)) options.onError?.(error as Error);
        },
      );
      return { abort: () => removeUpload(uid) };
    },
    onChange: ({ file, fileList }) =>
      setFiles((current) => {
        // Upload progress must never undo a user-defined order or restore a removed file.
        const incoming = fileList.filter(
          (item) => !removedUploads.current.has(item.uid),
        );
        const updates = new Map(incoming.map((item) => [item.uid, item]));
        const known = new Set(current.map((item) => item.uid));
        return [
          ...current.map((item) =>
            !file || item.uid === file.uid
              ? (updates.get(item.uid) ?? item)
              : item,
          ),
          ...incoming.filter((item) => !known.has(item.uid)),
        ];
      }),
    onRemove: (file) => {
      removeUpload(file.uid);
      return true;
    },
  };

  const creationValues = useMemo(
    () => ({
      name: batchName.trim() || buildDefaultVideoDecompositionBatchName(),
      modelId,
      videos: episodes.map((episode) => ({
        fileName: episode.fileName,
        storagePath: episode.storagePath || '',
        uploadSessionToken: episode.uploadSessionToken || '',
        mimeType: episode.mimeType,
        fileSize: episode.size,
      })),
    }),
    [batchName, modelId, episodes],
  );
  const ready = canCreateVideoDecompositionBatch(episodes) && Boolean(modelId);
  const showHistory = () => {
    setOpenBatchId(undefined);
    setView('history');
  };
  const showCompose = () => {
    setOpenBatchId(undefined);
    setView('compose');
  };

  const createBatch = async () => {
    if (!ready || creating) return;
    setCreating(true);
    try {
      const response = await createVideoDecompositionBatch(creationValues);
      message.success('剧本生成批次已创建');
      setFiles([]);
      uploadHandles.current.clear();
      activeUploads.current.clear();
      removedUploads.current.clear();
      setBatchName(buildDefaultVideoDecompositionBatchName());
      client.setQueryData(['video-batch', response.data.id], response.data);
      setOpenBatchId(response.data.id);
      setBatchPage(1);
      void client.invalidateQueries({
        queryKey: ['video-batches'],
        refetchType: 'none',
      });
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(error instanceof Error ? error.message : '创建批次失败');
    } finally {
      setCreating(false);
    }
  };

  return (
    <PageContainer
      title={false}
      breadcrumb={{ items: [] }}
      className={styles.pageContainer}
    >
      <div className={styles.page}>
        <header className={styles.pageHeading}>
          <div className={styles.headingTitle}>
            <h1>视频拆剧</h1>
            <span>从视频到剧本，按集整理</span>
          </div>
          <div className={styles.headingActions}>
            {openBatchId ? (
              <Button className={styles.quietButton} onClick={showHistory}>
                ← 返回生成记录
              </Button>
            ) : (
              view !== 'history' && (
                <Button
                  className={styles.quietButton}
                  icon={<HistoryOutlined />}
                  disabled={creating}
                  onClick={showHistory}
                >
                  生成记录
                  <span className={styles.countBadge}>{batchTotal}</span>
                </Button>
              )
            )}
            {(openBatchId || view === 'history') && (
              <Button
                type="primary"
                icon={files.length ? undefined : <PlusOutlined />}
                disabled={creating}
                onClick={showCompose}
              >
                {files.length ? `继续整理 ${files.length} 集` : '新建拆剧'}
              </Button>
            )}
          </div>
        </header>
        <UploadComposer
          files={files}
          uploadProps={uploadProps}
          active={!openBatchId && view === 'compose'}
          batchName={batchName}
          onBatchNameChange={setBatchName}
          modelId={modelId}
          models={models}
          modelLoading={modelsQuery.isPending}
          modelError={modelsQuery.isError}
          onReloadModels={() => void modelsQuery.refetch()}
          onModelChange={setModelId}
          onMove={moveFile}
          onReorder={reorderFile}
          onRemove={removeUpload}
          onRetry={(uid) => void retryUpload(uid)}
          disabled={creating}
          action={
            <PointEstimateButton
              compact
              operations={
                ready
                  ? [
                      {
                        url: '/api/video-script-decomposition/batches',
                        data: creationValues,
                      },
                    ]
                  : undefined
              }
              type="primary"
              disabled={!ready || creating}
              loading={creating}
              onClick={() => void createBatch()}
            >
              {files.length ? `生成 ${files.length} 集剧本` : '生成剧本'}
            </PointEstimateButton>
          }
        />
        {!openBatchId && view === 'history' && (
          <BatchHistory
            batches={batches}
            total={batchTotal}
            current={batchPage}
            pageSize={batchPageSize}
            pending={batchesQuery.isPending}
            error={batchesQuery.isError}
            onRetry={() => void batchesQuery.refetch()}
            onPageChange={(current, size) => {
              setBatchPage(current);
              setBatchPageSize(size);
            }}
            onOpen={setOpenBatchId}
          />
        )}
        {openBatchId && (
          <ScreenplayWorkspace key={openBatchId} batchId={openBatchId} />
        )}
      </div>
    </PageContainer>
  );
};

export default function VideoScriptDecomposition() {
  return (
    <VideoDecompositionQueries>
      <VideoScriptDecompositionPage />
    </VideoDecompositionQueries>
  );
}
