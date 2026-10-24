import { App, Button, Input } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { updateVisualVariant, type VisualVariant } from './service';
import styles from './AssetVisualPromptEditor.module.css';

export default function AssetVisualPromptEditor({
  projectId,
  assetName,
  primaryVariant,
  fallbackPrompt,
  blockedReason,
  onSaved,
}: {
  projectId: number;
  assetName: string;
  primaryVariant?: VisualVariant;
  fallbackPrompt: string;
  blockedReason?: string;
  onSaved: (variant: VisualVariant) => void;
}) {
  const { message } = App.useApp();
  const [draft, setDraft] = useState<string>();
  const [isSaving, setIsSaving] = useState(false);
  const mounted = useRef(false);
  const saving = useRef(false);
  const onSavedRef = useRef(onSaved);
  onSavedRef.current = onSaved;
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const editing = draft !== undefined;
  const prompt = draft ?? (primaryVariant?.prompt || fallbackPrompt);
  const lockReason = blockedReason || (primaryVariant?.generationStatus === 'GENERATING'
    ? '当前形象正在生成，完成后可编辑提示词。' : undefined);
  const save = async () => {
    if (saving.current || lockReason || !primaryVariant || !prompt.trim()) return;
    saving.current = true;
    setIsSaving(true);
    try {
      const response = await updateVisualVariant(projectId, primaryVariant.id, {
        name: primaryVariant.name,
        appearance: primaryVariant.appearance,
        prompt: prompt.trim(),
      });
      if (!mounted.current) return;
      onSavedRef.current({ ...primaryVariant, prompt: prompt.trim(), ...response.data });
      setDraft(undefined);
      message.success('提示词已保存');
    } catch {
      if (mounted.current) message.error('提示词保存失败，请重试');
    } finally {
      saving.current = false;
      if (mounted.current) setIsSaving(false);
    }
  };

  return (
    <section className={styles.editor} aria-label="角色提示词">
      <div className={styles.header}>
        <span className={styles.label}>主要形态提示词</span>
        <div className={styles.actions}>
          {editing ? <>
            <Button disabled={isSaving} onClick={() => setDraft(undefined)}>取消</Button>
            <Button type="primary" loading={isSaving}
              disabled={Boolean(lockReason) || isSaving || !prompt.trim()}
              title={lockReason} onClick={() => void save()}>保存</Button>
          </> : <Button disabled={Boolean(lockReason) || !primaryVariant} title={lockReason}
            aria-label="编辑主要形态提示词"
            onClick={() => setDraft(prompt)}>
            编辑
          </Button>}
        </div>
      </div>
      <Input.TextArea aria-label={`${assetName}主要形态提示词`} readOnly={!editing}
        disabled={editing && (Boolean(lockReason) || isSaving)}
        value={prompt} placeholder="暂无提示词"
        onChange={(event) => setDraft(event.target.value)}
        className={`${styles.prompt} ${editing ? styles.editing : ''}`} />
    </section>
  );
}
