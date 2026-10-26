import { Button, Checkbox, Modal, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  type AvatarCandidate,
  type AvatarTask,
  createAvatarTasks,
  queryAvatarCandidates,
  retryAvatarTask,
} from './virtual-avatar-service';

type Props = {
  projectId: number;
  variant?: { id: number; generationStatus: string; virtualAvatarAssetId?: string | null };
  onChanged: () => unknown;
};
const active = (item: AvatarCandidate) => ['QUEUED', 'RUNNING'].includes(item.status || '');
const available = (item: AvatarCandidate) => !item.assetId && !active(item) && item.status !== 'FAILED';
const errorText = (error: unknown) => error instanceof Error ? error.message : '虚拟人像任务操作失败';

export default function VirtualAvatarPanel({ projectId, variant, onChanged }: Props) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AvatarCandidate[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<AvatarTask[]>();
  const [version, setVersion] = useState(0);
  const callback = useRef(onChanged);
  callback.current = onChanged;
  const previous = useRef(new Map<number, string | undefined>());
  const submitting = useRef(false);
  const variantId = variant?.id;

  useEffect(() => {
    if (!open) return;
    let current = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setLoading(true);
    const load = async () => {
      try {
        const next = await queryAvatarCandidates(projectId, page, variantId);
        if (!current) return;
        const completed = next.some((item) => item.status === 'SUCCEEDED' && previous.current.get(item.id) !== 'SUCCEEDED');
        for (const item of next) previous.current.set(item.id, item.status);
        setItems(next);
        setError('');
        setSelected((ids) => ids.filter((id) => !next.some((item) => item.id === id && !available(item))));
        if (completed) await callback.current();
        if (current && next.some(active)) timer = setTimeout(load, 3000);
      } catch (failure) {
        if (current) setError(errorText(failure));
      } finally {
        if (current) setLoading(false);
      }
    };
    void load();
    return () => { current = false; if (timer) clearTimeout(timer); };
  }, [open, projectId, page, variantId, version]);

  const submit = async () => {
    if (submitting.current || !selected.length) return;
    submitting.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await createAvatarTasks(projectId, selected);
      setReceipt(result);
      setSelected((ids) => ids.filter((id) => result.some((item) => item.variantId === id && !item.id)));
      setVersion((value) => value + 1);
    } catch (failure) { setError(errorText(failure)); }
    finally { setBusy(false); submitting.current = false; }
  };
  const retry = async (taskId: number) => {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true);
    try {
      await retryAvatarTask(projectId, taskId);
      setVersion((value) => value + 1);
    } catch (failure) { setError(errorText(failure)); }
    finally { setBusy(false); submitting.current = false; }
  };
  return (
    <>
      <Button
        disabled={Boolean(variant && (variant.generationStatus !== 'COMPLETED' || variant.virtualAvatarAssetId))}
        onClick={() => {
          previous.current.clear();
          setReceipt(undefined);
          setError('');
          setPage(1);
          setSelected(variant ? [variant.id] : []);
          setOpen(true);
        }}
      >{variant ? '虚拟人像入库' : '批量虚拟人像入库'}</Button>
      <Modal
        title="私域虚拟人像入库"
        open={open}
        width={720}
        onCancel={() => setOpen(false)}
        footer={null}
      >
        <p>选择已有的虚拟角色图片加入私域素材库。处理成功并保存后，对应图片会显示绿色【虚拟】标签，生成视频时自动引用资产 ID。</p>
        <p>可跨角色选择多个形态，每次最多 100 张。提交后可关闭窗口，在任务中心查看进度。</p>
        {error ? <p role="alert">{error}</p> : null}
        {receipt ? (
          <div role="status">
            <p>已提交 {receipt.filter((item) => item.id).length} 张，未提交 {receipt.filter((item) => !item.id).length} 张</p>
            {receipt.filter((item) => !item.id).map((item) => <p key={item.variantId}>形态 #{item.variantId}：{item.errorMessage}</p>)}
          </div>
        ) : null}
        <div style={{ maxHeight: 400, overflowY: 'auto' }} aria-busy={loading}>
          {items.map((item) => (
            <div key={item.id} style={{ borderBottom: '1px solid #eee', padding: '12px 0' }}>
              <Checkbox
                aria-label={`选择${item.characterName} · ${item.variantName}`}
                disabled={busy || !available(item) || (!selected.includes(item.id) && selected.length >= 100)}
                checked={selected.includes(item.id)}
                onChange={(event) => setSelected((ids) => event.target.checked ? [...ids, item.id] : ids.filter((id) => id !== item.id))}
              >{item.characterName} · {item.variantName}</Checkbox>
              {item.assetId ? <Tag color="green">虚拟</Tag> : null}
              {item.phase && !item.assetId ? <span style={{ marginLeft: 8 }}>{item.status === 'FAILED' ? '入库失败' : item.phase}</span> : null}
              {item.taskId ? <a style={{ marginLeft: 12 }} href={`/tasks?task=${encodeURIComponent(`VIRTUAL_AVATAR:${item.taskId}`)}`}>查看任务</a> : null}
              {item.status === 'FAILED' && item.retryable && item.taskId ? <Button size="small" disabled={busy} onClick={() => void retry(item.taskId as number)}>重试入库</Button> : null}
              {item.errorMessage ? <p role="alert">{item.errorMessage}</p> : null}
            </div>
          ))}
          {!items.length && !loading ? <p>暂无可入库的角色形态图，请先生成或上传图片。</p> : null}
        </div>
        <div style={{ display: 'flex', gap: 12, marginTop: 16, alignItems: 'center' }}>
          {!variant ? <>
            <Button disabled={page <= 1 || loading} onClick={() => setPage((value) => value - 1)}>上一页</Button>
            <span>第 {page} 页</span>
            <Button disabled={items.length < 100 || loading} onClick={() => setPage((value) => value + 1)}>下一页</Button>
          </> : null}
          <Button loading={loading} onClick={() => setVersion((value) => value + 1)}>刷新状态</Button>
          <Button type="primary" loading={busy} disabled={!selected.length || loading} onClick={() => void submit()}>提交 {selected.length} 张</Button>
        </div>
      </Modal>
    </>
  );
}
