import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';
import CharacterVoiceButton from './CharacterVoiceButton';
import type { CharacterVoiceSetting } from './character-voice';

const defaults: CharacterVoiceSetting = { characterId: 1, storyboardId: 0, variantId: 0, voiceId: 'role', voiceName: '沉稳男声' };
const override: CharacterVoiceSetting = { characterId: 1, storyboardId: 10, variantId: 11, voiceId: 'shot', voiceName: '少年男声' };
const renderButton = (settings: CharacterVoiceSetting[] = [defaults]) => {
  const onSave = vi.fn().mockResolvedValue(undefined);
  const onReset = vi.fn().mockResolvedValue(undefined);
  const view = render(<ConfigProvider theme={{ token: { motion: false } }}><App><CharacterVoiceButton
    characterId={1} characterName="陈渡" storyboardId={10} variantId={11}
    settings={settings} onSave={onSave} onReset={onReset} /></App></ConfigProvider>);
  return { ...view, onSave, onReset };
};
afterEach(cleanup);

describe('character voice display and editing', () => {
  it('shows inherited voice, prefills it and defaults edits to the current shot/look', async () => {
    const { onSave } = renderButton();
    expect(screen.getByText('沉稳男声')).toBeInTheDocument();
    expect(screen.getByText('继承角色音色')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '配置陈渡音色' }));
    expect(screen.getByRole('textbox', { name: '音色ID' })).toHaveValue('role');
    fireEvent.change(screen.getByRole('textbox', { name: '音色ID' }), { target: { value: 'custom' } });
    fireEvent.change(screen.getByRole('textbox', { name: '音色名称' }), { target: { value: '当前分镜男声' } });
    fireEvent.click(screen.getByRole('button', { name: '保存音色' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ characterId: 1, storyboardId: 10, variantId: 11,
      voiceId: 'custom', voiceName: '当前分镜男声' }));
  });
  it('shows the exact shot override and can restore inheritance', async () => {
    const { onReset } = renderButton([defaults, override]);
    expect(screen.getByText('少年男声')).toBeInTheDocument();
    expect(screen.getByText('本分镜形态音色')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '配置陈渡音色' }));
    expect(screen.getByRole('textbox', { name: '音色ID' })).toHaveValue('shot');
    fireEvent.click(screen.getByRole('button', { name: '恢复角色音色' }));
    await waitFor(() => expect(onReset).toHaveBeenCalledWith({ characterId: 1, storyboardId: 10, variantId: 11 }));
  });
  it('keeps the saved voice and the editor open if persistence fails', async () => {
    const { onSave } = renderButton();
    onSave.mockRejectedValue(new Error('offline'));
    fireEvent.click(screen.getByRole('button', { name: '配置陈渡音色' }));
    fireEvent.change(screen.getByRole('textbox', { name: '音色ID' }), { target: { value: 'unsaved' } });
    fireEvent.click(screen.getByRole('button', { name: '保存音色' }));
    expect(await screen.findByText('音色保存失败，请重试')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: '陈渡音色配置' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '音色ID' })).toHaveValue('unsaved');
    expect(screen.getByText('沉稳男声')).toBeInTheDocument();
  });

  it('only changes role defaults when explicitly selected and rejects an empty voice', async () => {
    const { onSave } = renderButton([]);
    expect(screen.getByText('配置音色')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '配置陈渡音色' }));
    fireEvent.click(screen.getByRole('button', { name: '保存音色' }));
    expect(onSave).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '音色ID' }), { target: { value: 'new-default' } });
    fireEvent.click(screen.getByRole('radio', { name: '角色默认音色（所有形态继承）' }));
    fireEvent.click(screen.getByRole('button', { name: '保存音色' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ characterId: 1, storyboardId: 0, variantId: 0,
      voiceId: 'new-default', voiceName: null }));
  });
});
