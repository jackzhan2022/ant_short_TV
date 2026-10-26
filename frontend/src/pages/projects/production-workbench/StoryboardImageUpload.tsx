import { PlusOutlined } from '@ant-design/icons';
import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { type MediaUploadHandle, startMediaUpload } from '@/services/mediaUpload';
import { bindStoryboardImageUpload, type UploadedStoryboardImage } from './storyboard-image-upload';

export default function StoryboardImageUpload({ projectId, storyboardId, storyboardNo, disabled, onUploaded }: {
  projectId: number;
  storyboardId: number;
  storyboardNo: number;
  disabled: boolean;
  onUploaded: (image: UploadedStoryboardImage) => void;
}) {
  const { message } = App.useApp();
  const input = useRef<HTMLInputElement>(null);
  const active = useRef(true);
  const busy = useRef(false);
  const handle = useRef<MediaUploadHandle | undefined>(undefined);
  const latestOnUploaded = useRef(onUploaded);
  latestOnUploaded.current = onUploaded;
  const [progress, setProgress] = useState<number>();

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      handle.current?.cancel();
    };
  }, []);

  const upload = async (file: File) => {
    if (disabled || busy.current) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      || file.size === 0 || file.size > 20 * 1024 * 1024) {
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
      const response = await bindStoryboardImageUpload(projectId, storyboardId,
        verified.sessionToken, file.name.slice(0, 200));
      if (!response.success) throw new Error(response.errorMessage || '图片保存失败');
      if (active.current) latestOnUploaded.current(response.data);
    } catch (error) {
      if (active.current) message.error(error instanceof Error ? error.message : '图片上传失败，请重试');
    } finally {
      busy.current = false;
      handle.current = undefined;
      if (active.current) setProgress(undefined);
    }
  };

  return <>
    <input ref={input} type="file" hidden accept="image/jpeg,image/png,image/webp"
      aria-label={`选择分镜${storyboardNo}本地图片`} disabled={disabled || progress !== undefined}
      onChange={(event) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (file) void upload(file);
      }} />
    <Button aria-label={`上传分镜${storyboardNo}参考图片`} icon={<PlusOutlined />}
      title="上传本地图片，同时保存到图片资产库（JPG、PNG、WebP，最大20MB）"
      disabled={disabled} loading={progress !== undefined} onClick={() => input.current?.click()} />
    {progress !== undefined ? <span role="status">{progress === 100 ? '保存图片中' : `上传中 ${progress}%`}</span> : null}
  </>;
}
