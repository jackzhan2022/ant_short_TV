import {
  DeleteOutlined,
  FileImageOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useEffect, useId, useRef, useState } from 'react';
import {
  type MediaUploadHandle,
  startMediaUpload,
  type VerifiedMediaUpload,
} from '@/services/mediaUpload';
import styles from './index.module.css';
import { readReferenceMetadata } from './reference-metadata';
import type { ReferenceConstraint } from './service';
export type ReferenceKind = 'image' | 'video' | 'audio';
export type ReferenceDraft = {
  id: string;
  kind: ReferenceKind;
  name: string;
  url?: string;
  status: 'uploading' | 'paused' | 'verifying' | 'failed' | 'complete';
  percent: number;
  uploadSessionToken?: string;
  referenceId?: number;
  error?: string;
  durationSeconds?: number;
};
type Props = {
  kind: ReferenceKind;
  constraints?: ReferenceConstraint;
  initialReferences?: ReferenceDraft[];
  onChange?: (references: ReferenceDraft[]) => void;
  disabled?: boolean;
};
const labels = { image: '参考图片', video: '参考视频', audio: '参考音频' };
export function referenceFileError(
  file: File,
  kind: ReferenceKind,
  constraints?: ReferenceConstraint,
): string | undefined {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  const formats = constraints?.formats?.map((format) =>
    format.toLowerCase().replace(/^\./, ''),
  );
  if (
    formats?.length
      ? !formats.includes(extension)
      : !file.type.startsWith(`${kind}/`)
  )
    return `${file.name} 的格式不符合要求`;
  if (!file.size) return `${file.name} 是空文件`;
  if (constraints?.maxBytes !== undefined && file.size > constraints.maxBytes)
    return `${file.name} 超过单文件大小限制`;
  return undefined;
}
export default function ReferenceFiles({
  kind,
  constraints,
  initialReferences = [],
  onChange,
  disabled,
}: Props) {
  const [files, setFiles] = useState<ReferenceDraft[]>(initialReferences);
  const items = useRef(files);
  const initialFiles = useRef(files);
  const [error, setError] = useState('');
  const alive = useRef(true);
  const uploads = useRef(
    new Map<
      string,
      {
        file: File;
        handle?: MediaUploadHandle;
        attempt: number;
        abort: AbortController;
      }
    >(),
  );
  const urls = useRef(new Set<string>());
  const callback = useRef(onChange);
  callback.current = onChange;
  const id = useId();
  useEffect(() => {
    if (files !== initialFiles.current) callback.current?.(files);
  }, [files]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      for (const task of uploads.current.values()) {
        task.abort.abort();
        task.handle?.cancel();
      }
      uploads.current.clear();
      for (const url of urls.current) URL.revokeObjectURL(url);
      urls.current.clear();
    };
  }, []);
  const update = (next: ReferenceDraft[]) => {
    items.current = next;
    if (alive.current) setFiles(next);
  };
  const patch = (itemId: string, values: Partial<ReferenceDraft>) => {
    if (alive.current && uploads.current.has(itemId))
      update(
        items.current.map((item) =>
          item.id === itemId ? { ...item, ...values } : item,
        ),
      );
  };
  const observe = async (
    itemId: string,
    promise: Promise<VerifiedMediaUpload>,
  ) => {
    const task = uploads.current.get(itemId);
    if (!task) {
      void promise.catch(() => undefined);
      return;
    }
    const attempt = ++task.attempt;
    try {
      const result = await promise;
      if (task.attempt === attempt)
        patch(itemId, {
          status: 'complete',
          percent: 100,
          uploadSessionToken: result.sessionToken,
          error: undefined,
        });
    } catch (failure) {
      if (task.attempt === attempt)
        patch(itemId, {
          status: 'failed',
          error:
            failure instanceof Error ? failure.message : '上传失败，请重试',
        });
    }
  };
  const start = async (itemId: string) => {
    const task = uploads.current.get(itemId);
    if (!task) return;
    patch(itemId, { status: 'uploading', percent: 0, error: undefined });
    try {
      const metadata = await readReferenceMetadata(
        task.file,
        kind,
        constraints,
        task.abort.signal,
      );
      if (task.abort.signal.aborted) return;
      patch(itemId, metadata);
      const handle = await startMediaUpload(task.file, {
        onProgress: ({ percent }) => {
          patch(itemId, {
            percent: Math.round(percent * 100),
            status: percent >= 1 ? 'verifying' : 'uploading',
          });
        },
      });
      if (!alive.current || uploads.current.get(itemId) !== task) {
        void handle.attempt.catch(() => undefined);
        handle.cancel();
        return;
      }
      task.handle = handle;
      // Re-render when controls become available even before the first progress event.
      patch(itemId, {});
      void observe(itemId, handle.attempt);
    } catch (failure) {
      patch(itemId, {
        status: 'failed',
        error:
          failure instanceof Error
            ? failure.message
            : '创建上传会话失败，请重试',
      });
    }
  };
  const remove = (item: ReferenceDraft) => {
    const task = uploads.current.get(item.id);
    uploads.current.delete(item.id);
    task?.abort.abort();
    task?.handle?.cancel();
    if (item.url && urls.current.delete(item.url))
      URL.revokeObjectURL(item.url);
    update(items.current.filter((value) => value.id !== item.id));
    setError('');
  };
  const add = (selected: File[]) => {
    if (
      constraints?.maxCount !== undefined &&
      items.current.length + selected.length > constraints.maxCount
    ) {
      setError(`当前模型最多允许 ${constraints.maxCount} 个${labels[kind]}`);
      return;
    }
    for (const file of selected) {
      const invalid = referenceFileError(file, kind, constraints);
      if (invalid) {
        setError(invalid);
        return;
      }
    }
    const next = selected.map((file): ReferenceDraft => {
      const itemId = crypto.randomUUID();
      const url = kind === 'image' ? URL.createObjectURL(file) : undefined;
      if (url) urls.current.add(url);
      uploads.current.set(itemId, {
        file,
        attempt: 0,
        abort: new AbortController(),
      });
      return {
        id: itemId,
        kind,
        name: file.name,
        url,
        status: 'uploading',
        percent: 0,
      };
    });
    update([...items.current, ...next]);
    setError('');
    for (const item of next) void start(item.id);
  };
  const formats = constraints?.formats
    ?.map((format) => `.${format.replace(/^\./, '')}`)
    .join(',');
  const limitText = [
    constraints?.formats?.join(' / ').toUpperCase(),
    constraints?.maxCount !== undefined
      ? `最多 ${constraints.maxCount} 个`
      : undefined,
    constraints?.maxBytes !== undefined
      ? `单个 ≤ ${(constraints.maxBytes / 1024 / 1024).toFixed(1)} MB`
      : undefined,
    constraints?.maxTotalDurationSeconds !== undefined
      ? `总时长 ≤ ${constraints.maxTotalDurationSeconds} 秒`
      : undefined,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <div className={styles.field}>
      <div className={styles.fieldLabel}>
        <span>{labels[kind]}</span>
        <span className={styles.muted}>
          {files.length ? `${files.length} 个` : '可选'}
        </span>
      </div>
      {constraints?.minCount ? (
        <p className={styles.help}>
          模型要求至少 {constraints.minCount} 个{labels[kind]}
        </p>
      ) : null}
      <label className={styles.upload} htmlFor={id}>
        <PlusOutlined aria-hidden="true" />
        <span>
          选择{labels[kind]}
          <small>上传并完成校验后可用于生成</small>
        </span>
        <input
          id={id}
          type="file"
          aria-label={`选择${labels[kind]}`}
          accept={formats || `${kind}/*`}
          multiple
          disabled={disabled || constraints?.maxCount === 0}
          onChange={(event) => {
            add(Array.from(event.target.files ?? []));
            event.target.value = '';
          }}
        />
      </label>
      {limitText ? <p className={styles.help}>{limitText}</p> : null}
      {files.length ? (
        <ul className={styles.referenceList}>
          {files.map((item) => {
            const handle = uploads.current.get(item.id)?.handle;
            const status =
              item.status === 'uploading'
                ? `上传中 ${item.percent}%`
                : item.status === 'verifying'
                  ? '完成校验中'
                  : item.status === 'paused'
                    ? '已暂停'
                    : item.status === 'failed'
                      ? '上传失败'
                      : '上传完成';
            return (
              <li key={item.id}>
                {item.url ? (
                  <img src={item.url} alt={item.name} />
                ) : (
                  <FileImageOutlined aria-hidden="true" />
                )}
                <div className={styles.referenceInfo}>
                  <span title={item.name}>{item.name}</span>
                  <small role="status">{status}</small>
                  {item.error ? (
                    <small className={styles.error}>{item.error}</small>
                  ) : null}
                </div>
                {!disabled && item.status === 'uploading' && handle ? (
                  <button
                    type="button"
                    aria-label={`暂停${item.name}`}
                    onClick={() => {
                      handle.pause();
                      patch(item.id, { status: 'paused' });
                    }}
                  >
                    暂停
                  </button>
                ) : null}
                {!disabled && item.status === 'paused' && handle ? (
                  <button
                    type="button"
                    aria-label={`继续${item.name}`}
                    onClick={() => {
                      patch(item.id, { status: 'uploading' });
                      void observe(item.id, handle.resume());
                    }}
                  >
                    继续
                  </button>
                ) : null}
                {!disabled && item.status === 'failed' ? (
                  <button
                    type="button"
                    aria-label={`重试${item.name}`}
                    onClick={() => {
                      if (handle) {
                        patch(item.id, {
                          status:
                            item.percent >= 100 ? 'verifying' : 'uploading',
                          error: undefined,
                        });
                        void observe(item.id, handle.retry());
                      } else void start(item.id);
                    }}
                  >
                    重试
                  </button>
                ) : null}
                <button
                  type="button"
                  disabled={disabled}
                  aria-label={`移除${item.name}`}
                  onClick={() => remove(item)}
                >
                  <DeleteOutlined aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
      {error ? (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
