import { EditOutlined } from '@ant-design/icons';
import { Button, Input, type InputRef, Modal } from 'antd';
import { useRef, useState } from 'react';
import styles from './settings.module.css';

type Props = {
  name: string;
  label: string;
  maxLength: number;
  disabled?: boolean;
  onSave: (name: string) => Promise<void>;
};

export default function AssetNameEditor({ name, label, maxLength, disabled, onSave }: Props) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(name);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const inputRef = useRef<InputRef>(null);
  const title = `修改${label}名称`;

  const save = async () => {
    if (savingRef.current || disabled) return;
    const nextName = value.trim();
    if (!nextName) { setError('名称不能为空'); return; }
    if (nextName.length > maxLength) { setError(`名称不能超过${maxLength}个字符`); return; }
    if (nextName === name) { setOpen(false); return; }
    savingRef.current = true;
    setSaving(true);
    setError('');
    try {
      await onSave(nextName);
      setOpen(false);
    } catch (failure) {
      const detail = failure as { info?: { errorMessage?: string }; response?: { data?: { errorMessage?: string } } };
      setError(detail?.info?.errorMessage || detail?.response?.data?.errorMessage || '名称保存失败，请重试。');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <span className={styles.editableName}>
      <span className={styles.nameText} title={name}>{name}</span>
      {!disabled && <Button
        type="text"
        size="small"
        className={styles.renameButton}
        aria-label={`${title}：${name}`}
        title={title}
        icon={<EditOutlined />}
        onClick={(event) => {
          event.stopPropagation();
          setValue(name);
          setError('');
          setOpen(true);
        }}
      />}
      <Modal
        title={title}
        open={open}
        width={420}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        okButtonProps={{ disabled }}
        cancelButtonProps={{ disabled: saving }}
        closable={!saving}
        keyboard={!saving}
        mask={{ closable: !saving }}
        onCancel={() => { if (!savingRef.current) setOpen(false); }}
        onOk={() => void save()}
        afterOpenChange={(visible) => { if (visible) inputRef.current?.focus({ cursor: 'all' }); }}
      >
        <Input
          ref={inputRef}
          aria-label={`${label}名称`}
          aria-invalid={Boolean(error)}
          value={value}
          maxLength={maxLength}
          disabled={saving || disabled}
          status={error ? 'error' : undefined}
          onChange={(event) => { setValue(event.target.value); setError(''); }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
              event.preventDefault();
              void save();
            }
          }}
        />
        {error && <p role="alert" className={styles.renameError}>{error}</p>}
      </Modal>
    </span>
  );
}
