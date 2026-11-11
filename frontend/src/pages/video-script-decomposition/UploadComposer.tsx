import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import {
  Button,
  Input,
  Select,
  Upload,
  type UploadFile,
  type UploadProps,
} from 'antd';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { FlowSteps } from './DecompositionProgress';
import styles from './index.module.css';
import type {
  VideoDecompositionUpload,
  VideoUnderstandingModel,
} from './service';

export const formatFileSize = (size: number) =>
  size >= 1024 ** 3
    ? `${(size / 1024 ** 3).toFixed(2)} GB`
    : `${(size / 1024 ** 2).toFixed(1)} MB`;

function LocalVideoPreview({
  file,
  index,
  active,
}: {
  file: UploadFile;
  index: number;
  active: boolean;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const [source, setSource] = useState<{ uid: string; url: string }>();
  const [failedUid, setFailedUid] = useState<string>();
  useEffect(() => {
    if (!file.originFileObj) {
      setSource(undefined);
      return;
    }
    const url = URL.createObjectURL(file.originFileObj);
    setSource({ uid: file.uid, url });
    return () => URL.revokeObjectURL(url);
  }, [file.uid, file.originFileObj]);
  useEffect(() => {
    if (!active) video.current?.pause();
  }, [active]);
  return (
    <section className={styles.previewArea} aria-label="视频预览">
      <div className={styles.videoTop}>
        <strong>第 {String(index + 1).padStart(2, '0')} 集</strong>
        <span>{formatFileSize(file.size ?? 0)}</span>
      </div>
      <div className={styles.videoScreen}>
        {source?.uid === file.uid && failedUid !== file.uid ? (
          // biome-ignore lint/a11y/useMediaCaption: Local source videos have no caption asset at upload time.
          <video
            ref={video}
            key={source.url}
            src={source.url}
            controls
            playsInline
            preload="metadata"
            aria-label={file.name}
            onError={() => setFailedUid(file.uid)}
          />
        ) : (
          <div className={styles.videoPlaceholder}>
            <PlayCircleOutlined />
            <p>{file.name}</p>
            <small>
              {failedUid === file.uid
                ? '浏览器暂不支持播放此视频，仍可上传并生成剧本'
                : '选中视频后可在这里预览'}
            </small>
          </div>
        )}
      </div>
      <div className={styles.previewCaption}>
        <span title={file.name}>
          <VideoCameraOutlined />
          {file.name}
        </span>
        <span>横屏 / 竖屏自动适配，完整显示画面</span>
      </div>
    </section>
  );
}

export default function UploadComposer({
  files,
  uploadProps,
  active,
  batchName,
  onBatchNameChange,
  modelId,
  models,
  modelLoading,
  modelError,
  onReloadModels,
  onModelChange,
  onMove,
  onReorder,
  onRemove,
  onRetry,
  action,
  disabled,
}: {
  files: UploadFile[];
  uploadProps: UploadProps;
  active: boolean;
  batchName: string;
  onBatchNameChange: (value: string) => void;
  modelId?: number;
  models: VideoUnderstandingModel[];
  modelLoading: boolean;
  modelError: boolean;
  onReloadModels: () => void;
  onModelChange: (value: number) => void;
  onMove: (uid: string, direction: -1 | 1) => void;
  onReorder: (uid: string, targetUid: string) => void;
  onRemove: (uid: string) => void;
  onRetry: (uid: string) => void;
  action: ReactNode;
  disabled: boolean;
}) {
  const [selectedUid, setSelectedUid] = useState<string>();
  const dragging = useRef<string | undefined>(undefined);
  const selected = files.find((file) => file.uid === selectedUid) ?? files[0];
  const index = files.findIndex((file) => file.uid === selected?.uid);
  const ready = files.filter(
    (file) =>
      (file.response as VideoDecompositionUpload | undefined)?.storagePath,
  ).length;
  const hasFiles = files.length > 0;
  return (
    <div hidden={!active} className={styles.composerView}>
      <FlowSteps current={0} />
      <section
        className={`${styles.panel} ${hasFiles ? '' : styles.intake}`}
        aria-label="新建生成批次"
      >
        {hasFiles ? (
          <header className={styles.panelHeading}>
            <div>
              <h2>整理集数，确认后开始生成</h2>
              <p>按左侧顺序生成，选中视频可预览内容</p>
            </div>
            <span className={styles.statusSummary}>
              已上传 {ready} / {files.length} 个视频
            </span>
          </header>
        ) : (
          <div className={styles.intakeCopy}>
            <span>VIDEO TO SCREENPLAY</span>
            <h2>先选视频，再开始拆剧</h2>
            <p>一个视频对应一集，生成后按集阅读与复制。</p>
          </div>
        )}
        <div className={hasFiles ? styles.studioGrid : undefined}>
          <aside
            className={hasFiles ? styles.uploadRail : styles.intakeUpload}
            aria-label={hasFiles ? '视频顺序' : '选择视频'}
          >
            <div className={styles.railLabel} hidden={!hasFiles}>
              <strong>视频顺序</strong>
              <span>{files.length} 集</span>
            </div>
            <ol className={styles.uploadList} hidden={!hasFiles}>
              {files.map((file, i) => {
                const uploaded = Boolean(
                  (file.response as VideoDecompositionUpload | undefined)
                    ?.storagePath,
                );
                return (
                  <li
                    key={file.uid}
                    className={styles.uploadItem}
                    data-selected={file.uid === selected?.uid}
                    draggable={!disabled}
                    onDragStart={(event) => {
                      dragging.current = file.uid;
                      event.dataTransfer.effectAllowed = 'move';
                      event.dataTransfer.setData('text/plain', file.uid);
                    }}
                    onDragEnd={() => {
                      dragging.current = undefined;
                    }}
                    onDragOver={(event) => {
                      if (!disabled) event.preventDefault();
                    }}
                    onDrop={(event) => {
                      event.preventDefault();
                      if (dragging.current && !disabled)
                        onReorder(dragging.current, file.uid);
                      dragging.current = undefined;
                    }}
                  >
                    <button
                      type="button"
                      className={styles.uploadSelect}
                      aria-pressed={file.uid === selected?.uid}
                      aria-label={`第 ${i + 1} 集 ${file.name}`}
                      onClick={() => setSelectedUid(file.uid)}
                    >
                      <span className={styles.thumbnail}>
                        <VideoCameraOutlined />
                      </span>
                      <span className={styles.uploadInfo}>
                        <strong>第 {String(i + 1).padStart(2, '0')} 集</strong>
                        <span title={file.name}>{file.name}</span>
                        <small
                          className={
                            file.status === 'error'
                              ? styles.failed
                              : uploaded
                                ? styles.succeeded
                                : styles.muted
                          }
                        >
                          {formatFileSize(file.size ?? 0)} ·{' '}
                          {file.status === 'error'
                            ? '上传失败'
                            : uploaded
                              ? '已就绪'
                              : `上传中 ${Math.round(file.percent ?? 0)}%`}
                        </small>
                      </span>
                    </button>
                    <div className={styles.fileActions}>
                      {file.status === 'error' && (
                        <Button
                          type="text"
                          size="small"
                          aria-label="重试上传"
                          title="重试上传"
                          disabled={disabled}
                          icon={<ReloadOutlined />}
                          onClick={() => onRetry(file.uid)}
                        />
                      )}
                      <Button
                        type="text"
                        size="small"
                        aria-label={`上移第 ${i + 1} 集`}
                        title="上移"
                        icon={<ArrowUpOutlined />}
                        disabled={disabled || i === 0}
                        onClick={() => onMove(file.uid, -1)}
                      />
                      <Button
                        type="text"
                        size="small"
                        aria-label={`下移第 ${i + 1} 集`}
                        title="下移"
                        icon={<ArrowDownOutlined />}
                        disabled={disabled || i === files.length - 1}
                        onClick={() => onMove(file.uid, 1)}
                      />
                      <Button
                        type="text"
                        size="small"
                        aria-label={`移除第 ${i + 1} 集`}
                        title="移除"
                        danger
                        icon={<DeleteOutlined />}
                        disabled={disabled}
                        onClick={() => onRemove(file.uid)}
                      />
                    </div>
                  </li>
                );
              })}
            </ol>
            <Upload.Dragger
              {...uploadProps}
              className={hasFiles ? styles.smallUpload : undefined}
            >
              {hasFiles ? (
                <span>＋ 继续添加视频</span>
              ) : (
                <div className={styles.dropContent}>
                  <span className={styles.dropIllustration}>
                    <CloudUploadOutlined />
                  </span>
                  <strong>
                    将视频拖到这里，或<em>选择视频</em>
                  </strong>
                  <small>支持多选 · MP4 / MOV / AVI · 单个最大 1 GB</small>
                </div>
              )}
            </Upload.Dragger>
            <p className={styles.railTip} hidden={!hasFiles}>
              拖动调整顺序，或使用移入后的上下箭头。列表顺序即生成集数。
            </p>
          </aside>
          {selected && (
            <LocalVideoPreview file={selected} index={index} active={active} />
          )}
        </div>
        <footer className={styles.configBar}>
          <div className={styles.batchNameControl}>
            <label htmlFor="decomposition-batch-name">批次</label>
            <Input
              id="decomposition-batch-name"
              aria-label="批次名称"
              variant="filled"
              value={batchName}
              disabled={disabled}
              onChange={(event) => onBatchNameChange(event.target.value)}
              placeholder="自动命名，也可修改"
              maxLength={200}
            />
          </div>
          <div className={styles.modelControl}>
            <label htmlFor="decomposition-model">模型</label>
            <Select
              id="decomposition-model"
              aria-label="视频理解模型"
              variant="filled"
              value={modelId}
              loading={modelLoading}
              disabled={disabled}
              onChange={onModelChange}
              placeholder={
                modelError
                  ? '模型加载失败'
                  : models.length
                    ? '请选择模型'
                    : '暂无可用模型'
              }
              options={models.map((model) => ({
                value: model.id,
                label: model.name,
              }))}
            />
          </div>
          {action}
        </footer>
        {modelError && (
          <div role="alert" className={styles.modelError}>
            模型加载失败
            <Button type="link" size="small" onClick={onReloadModels}>
              重新加载模型
            </Button>
          </div>
        )}
      </section>
      {!hasFiles && (
        <div className={styles.intakeNote}>
          <span>
            <VideoCameraOutlined />
            按视频顺序整理集数
          </span>
          <span>上传后可随时调整顺序</span>
        </div>
      )}
      {hasFiles && files.some((file) => file.status === 'error') && (
        <p className={styles.uploadWarning}>
          请重试失败的视频，或移除后继续生成。
        </p>
      )}
    </div>
  );
}
