import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import ProjectCoverImage from '@/components/ProjectCoverImage';
import type { Project } from '@/services/account-team/types';

export type ProjectCoverDraft = {
  file?: File;
  removed: boolean;
  ready: boolean;
};
const allowedTypes = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
]);
const extensionTypes: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  gif: 'image/gif',
};

export default function ProjectCoverPicker({
  project,
  value,
  onChange,
  disabled = false,
}: {
  project: Pick<Project, 'coverUrl' | 'coverStatus'>;
  value: ProjectCoverDraft;
  onChange: (value: ProjectCoverDraft) => void;
  disabled?: boolean;
}) {
  const { message } = App.useApp();
  const input = useRef<HTMLInputElement>(null);
  const currentFile = useRef(value.file);
  currentFile.current = value.file;
  const [preview, setPreview] = useState<{ file: File; url: string }>();
  useEffect(() => {
    if (!value.file) {
      setPreview(undefined);
      return;
    }
    const url = URL.createObjectURL(value.file);
    setPreview({ file: value.file, url });
    return () => URL.revokeObjectURL(url);
  }, [value.file]);
  const hasImage = !!value.file || (!value.removed && !!project.coverUrl);

  return (
    <fieldset style={{ border: 0, padding: 0, margin: '0 0 24px' }}>
      <legend style={{ fontSize: 14, marginBottom: 8, padding: 0 }}>
        封面图片
      </legend>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div
          style={{
            width: 160,
            height: 120,
            border: '1px dashed var(--app-color-border, #d9d9d9)',
            borderRadius: 8,
            overflow: 'hidden',
            display: 'grid',
            placeItems: 'center',
            background: 'var(--app-color-bg-layout, #fafafa)',
          }}
        >
          {value.file && preview?.file === value.file ? (
            <img
              key={preview.url}
              src={preview.url}
              alt="新封面预览"
              style={{ width: '100%', height: '100%', objectFit: 'contain' }}
              onLoad={() => {
                if (currentFile.current === value.file && !value.ready)
                  onChange({ ...value, ready: true });
              }}
              onError={() => {
                if (currentFile.current === value.file) {
                  message.error('图片无法预览，请选择有效图片。');
                  onChange({ removed: false, ready: false });
                }
              }}
            />
          ) : !value.removed && project.coverUrl ? (
            <ProjectCoverImage
              src={project.coverUrl}
              status={project.coverStatus}
              alt="当前封面"
              width={160}
              height={120}
              imageStyle={{ objectFit: 'contain' }}
            />
          ) : (
            <span
              style={{
                color: 'var(--app-color-text-secondary, #999)',
                fontSize: 13,
              }}
            >
              暂无封面
            </span>
          )}
        </div>
        <div>
          <input
            ref={input}
            type="file"
            aria-label="选择封面图片"
            accept=".jpg,.jpeg,.png,.webp,.gif"
            disabled={disabled}
            style={{ display: 'none' }}
            onChange={(event) => {
              const selected = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (!selected || disabled) return;
              const type =
                selected.type ||
                extensionTypes[
                  selected.name.split('.').pop()?.toLowerCase() || ''
                ];
              if (!allowedTypes.has(type)) {
                message.error('请选择 JPG、PNG、WebP 或 GIF 图片。');
                return;
              }
              if (selected.size <= 0 || selected.size > 20 * 1024 * 1024) {
                message.error('图片大小需大于 0 且不超过 20 MB。');
                return;
              }
              const file = selected.type
                ? selected
                : new File([selected], selected.name, {
                    type,
                    lastModified: selected.lastModified,
                  });
              onChange({ file, removed: false, ready: false });
            }}
          />
          <Button disabled={disabled} onClick={() => input.current?.click()}>
            {hasImage ? '更换图片' : '选择本地图片'}
          </Button>
          {hasImage && (
            <Button
              type="link"
              disabled={disabled}
              onClick={() => onChange({ removed: true, ready: false })}
            >
              移除封面
            </Button>
          )}
          <p
            style={{
              margin: '8px 0 0',
              fontSize: 12,
              color: 'var(--app-color-text-secondary, #999)',
            }}
          >
            JPG、PNG、WebP、GIF，最大 20 MB
          </p>
        </div>
      </div>
    </fieldset>
  );
}
