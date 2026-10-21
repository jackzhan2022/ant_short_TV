import { UploadOutlined } from '@ant-design/icons';
import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { type MediaUploadHandle, startMediaUpload } from '@/services/mediaUpload';
import { bindVisualVariantImageUpload } from './service';

export default function AssetLocalImageUpload({
  projectId,
  variantId,
  disabled,
  onUploaded,
}: {
  projectId: number;
  variantId: number;
  disabled: boolean;
  onUploaded: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const input = useRef<HTMLInputElement>(null);
  const active = useRef(true);
  const busy = useRef(false);
  const handle = useRef<MediaUploadHandle | undefined>(undefined);
  const [progress, setProgress] = useState<number>();

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      handle.current?.cancel();
    };
  }, []);

  const upload = async (file: File) => {
    if (busy.current || disabled) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
      file.size === 0 || file.size > 20 * 1024 * 1024) {
      message.error('请选择不超过20MB的 JPG、PNG 或 WebP 图片');
      return;
    }
    busy.current = true;
    setProgress(0);
    try {
      const current = await startMediaUpload(file, {
        projectId,
        onProgress: ({ percent }) => {
          if (active.current) setProgress(Math.min(100, Math.round(percent * 100)));
        },
      });
      handle.current = current;
      if (!active.current) {
        current.cancel();
        return;
      }
      const verified = await current.attempt;
      if (!active.current) return;
      setProgress(100);
      const response = await bindVisualVariantImageUpload(projectId, variantId, verified.sessionToken);
      if (!response.success) throw new Error(response.errorMessage || '图片保存失败');
      if (active.current) await onUploaded();
    } catch (error) {
      if (active.current) message.error(error instanceof Error ? error.message : '图片上传失败，请重试');
    } finally {
      busy.current = false;
      handle.current = undefined;
      if (active.current) setProgress(undefined);
    }
  };

  return (
    <>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        aria-label="选择本地图片"
        hidden
        disabled={disabled || progress !== undefined}
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) void upload(file);
        }}
      />
      <Button
        icon={<UploadOutlined />}
        disabled={disabled}
        loading={progress !== undefined}
        onClick={() => input.current?.click()}
        title="支持 JPG、PNG、WebP，最大20MB"
      >
        {progress === undefined ? '本地上传' : progress === 100 ? '保存中' : `上传中 ${progress}%`}
      </Button>
    </>
  );
}
