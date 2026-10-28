import { App, Button } from 'antd';
import { useRef, useState } from 'react';
import { createDownloadTask, type DownloadSource } from './service';

export default function DownloadButton({
  name,
  sources,
  children = '下载',
  type = 'link',
}: {
  name: string;
  sources: DownloadSource[];
  children?: React.ReactNode;
  type?: 'link' | 'default';
}) {
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const submit = async () => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    try {
      await createDownloadTask(name, sources);
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '创建下载任务失败，请重试',
      );
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Button type={type} loading={busy} onClick={() => void submit()}>
      {children}
    </Button>
  );
}
