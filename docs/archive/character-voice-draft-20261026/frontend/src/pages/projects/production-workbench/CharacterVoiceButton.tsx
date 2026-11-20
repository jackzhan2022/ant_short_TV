import { AudioOutlined } from '@ant-design/icons';
import { App, Button, Flex, Input, Modal, Radio, Typography } from 'antd';
import { useId, useState } from 'react';
import { effectiveCharacterVoice, type CharacterVoiceSetting } from './character-voice';

export type CharacterVoiceScope = Pick<CharacterVoiceSetting, 'characterId' | 'storyboardId' | 'variantId'>;

type Props = {
  characterId: number;
  characterName: string;
  storyboardId: number;
  variantId?: number | null;
  settings: readonly CharacterVoiceSetting[];
  onSave?: (setting: CharacterVoiceSetting) => Promise<void>;
  onReset?: (scope: CharacterVoiceScope) => Promise<void>;
};

export default function CharacterVoiceButton({ characterId, characterName, storyboardId, variantId,
  settings, onSave, onReset }: Props) {
  const { message } = App.useApp();
  const fieldId = useId();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [scope, setScope] = useState<'STORYBOARD' | 'CHARACTER'>('STORYBOARD');
  const [voiceId, setVoiceId] = useState('');
  const [voiceName, setVoiceName] = useState('');
  const effective = effectiveCharacterVoice(settings, characterId, storyboardId, variantId);
  const label = effective?.voiceName || effective?.voiceId || '配置音色';
  const openEditor = () => {
    setVoiceId(effective?.voiceId || '');
    setVoiceName(effective?.voiceName || '');
    setScope('STORYBOARD');
    setOpen(true);
  };
  const save = async () => {
    if (!voiceId.trim()) { message.warning('请填写音色ID'); return; }
    if (!onSave) return;
    setSaving(true);
    try {
      await onSave({ characterId, storyboardId: scope === 'CHARACTER' ? 0 : storyboardId,
        variantId: scope === 'CHARACTER' ? 0 : variantId || 0, voiceId: voiceId.trim(), voiceName: voiceName.trim() || null });
      message.success('音色已保存');
      setOpen(false);
    } catch {
      message.error('音色保存失败，请重试');
    } finally { setSaving(false); }
  };
  const reset = async () => {
    if (!onReset) return;
    setSaving(true);
    try {
      await onReset({ characterId, storyboardId, variantId: variantId || 0 });
      message.success('已恢复角色音色');
      setOpen(false);
    } catch { message.error('恢复角色音色失败，请重试'); }
    finally { setSaving(false); }
  };
  return <>
    <Flex vertical gap={2} style={{ marginTop: 4, minWidth: 0 }}>
      <Button size="small" icon={<AudioOutlined />} aria-label={`配置${characterName}音色`}
        title={effective ? `${label} · ${effective.inherited ? '继承角色音色' : '本分镜形态音色'}` : '配置音色'}
        onClick={openEditor} style={{ width: '100%', minWidth: 0 }}>
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{label}</span>
      </Button>
      {effective ? <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        {effective.inherited ? '继承角色音色' : '本分镜形态音色'}
      </Typography.Text> : null}
    </Flex>
    <Modal title={`${characterName}音色配置`} open={open} okText="保存音色" confirmLoading={saving}
      okButtonProps={{ disabled: !onSave }} onOk={() => void save()} onCancel={() => { if (!saving) setOpen(false); }}>
      <Flex vertical gap={14}>
        <label htmlFor={`${fieldId}-name`}>音色名称<Input id={`${fieldId}-name`} aria-label="音色名称" value={voiceName} maxLength={200}
          placeholder="例如：沉稳男声（可选）" onChange={(event) => setVoiceName(event.target.value)} /></label>
        <label htmlFor={`${fieldId}-voice`}>音色ID<Input id={`${fieldId}-voice`} aria-label="音色ID" value={voiceId} maxLength={128}
          placeholder="请输入音色ID" onChange={(event) => setVoiceId(event.target.value)} /></label>
        <Radio.Group aria-label="音色应用范围" value={scope} onChange={(event) => setScope(event.target.value)}>
          <Flex vertical gap={8}>
            <Radio value="STORYBOARD">仅当前分镜的当前形态</Radio>
            <Radio value="CHARACTER">角色默认音色（所有形态继承）</Radio>
          </Flex>
        </Radio.Group>
        <Typography.Text type="secondary">分镜内的单独配置优先使用；未单独配置的形态继承角色音色。</Typography.Text>
        {effective && !effective.inherited && onReset ? <Button loading={saving} onClick={() => void reset()}>恢复角色音色</Button> : null}
      </Flex>
    </Modal>
  </>;
}
