import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import CharacterVoiceModal from './CharacterVoiceModal';

const mocks = vi.hoisted(() => ({
  projects: vi.fn(),
  system: vi.fn(),
  library: vi.fn(),
  current: vi.fn(),
  save: vi.fn(),
  register: vi.fn(),
  upload: vi.fn(),
  saved: vi.fn(),
  close: vi.fn(),
  cancel: vi.fn(),
  storyboardCurrent: vi.fn(),
  storyboardSave: vi.fn(),
  storyboardReset: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useModel: () => ({ initialState: { currentTenantId: 10 } }),
}));
vi.mock('@/services/account-team/project', () => ({
  queryProjects: mocks.projects,
}));
vi.mock('@/pages/asset-library/service', () => ({
  queryProjectAssetLibrary: mocks.library,
}));
vi.mock('@/pages/asset-library/audio-service', () => ({
  queryCharacterVoice: mocks.current,
  saveCharacterVoice: mocks.save,
  registerProjectAudio: mocks.register,
  queryStoryboardCharacterVoice: mocks.storyboardCurrent,
  saveStoryboardCharacterVoice: mocks.storyboardSave,
  resetStoryboardCharacterVoice: mocks.storyboardReset,
}));
vi.mock('@/pages/system-audio/service', () => ({
  querySystemAudio: mocks.system,
}));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.upload }));

const project = {
  id: 1,
  name: '匠心',
  effectivePermissions: ['MENU:ASSET_LIBRARY'],
};
const otherProject = { ...project, id: 2, name: '另一项目' };
const audio = {
  id: 51,
  resourceType: 'PROJECT_AUDIO_ASSET',
  name: '角色参考.wav',
  sourceType: '本地上传',
  category: 'REFERENCE',
  previewUrl: '/audio/51',
  durationSeconds: 12,
};
const reference = {
  sourceProjectId: 1,
  sourceProjectName: '匠心',
  resourceType: 'PROJECT_AUDIO_ASSET',
  resourceId: 51,
  name: audio.name,
  previewUrl: audio.previewUrl,
};
const response = (items = [audio]) => ({
  success: true,
  data: {
    items: { data: items, total: items.length },
    categoryCounts: { REFERENCE: 1, GENERATED: 0 },
  },
});
const show = (system = false) => {
  const view = render(
    <App>
      <CharacterVoiceModal
        projectId={1}
        characterId={11}
        characterName="陈志国"
        onClose={mocks.close}
        onSaved={mocks.saved}
      />
    </App>,
  );
  if (!system) fireEvent.click(screen.getByRole('tab', { name: '项目音频库' }));
  return view;
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.system.mockResolvedValue({
    success: true,
    data: {
      data: [
        {
          id: 71,
          name: '清朗少年',
          gender: '男声',
          ageGroup: '少年',
          category: '清朗',
          durationSeconds: 12,
          previewUrl: '/api/system-audio/71/playback',
        },
      ],
      total: 1,
      current: 1,
      pageSize: 12,
    },
  });
  mocks.projects.mockResolvedValue({
    success: true,
    data: { data: [project, otherProject], total: 2 },
  });
  mocks.library.mockReset();
  mocks.library.mockResolvedValue(response());
  mocks.current.mockResolvedValue({ success: true, data: null });
  mocks.storyboardCurrent.mockResolvedValue({
    success: true,
    data: { audio: reference, overridden: false },
  });
  mocks.storyboardSave.mockResolvedValue({
    success: true,
    data: { audio: reference, overridden: true },
  });
  mocks.storyboardReset.mockResolvedValue({
    success: true,
    data: { audio: null, overridden: false },
  });
  mocks.save.mockResolvedValue({ success: true, data: reference });
  mocks.register.mockResolvedValue({ success: true, data: reference });
  mocks.upload.mockResolvedValue({
    attempt: Promise.resolve({ sessionToken: 'uploaded' }),
    cancel: mocks.cancel,
  });
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local-audio');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it('loads project folders first and saves an audio only from the selected project', async () => {
  show();
  fireEvent.click(
    await screen.findByRole('button', { name: '打开匠心音频库' }),
  );
  const radio = await screen.findByRole('radio', { name: /角色参考.wav/ });
  expect(mocks.library).toHaveBeenCalledWith(
    1,
    expect.objectContaining({ mediaType: 'AUDIO', current: 1, pageSize: 12 }),
    expect.any(AbortSignal),
  );
  fireEvent.click(radio);
  fireEvent.click(screen.getByRole('button', { name: '试听角色参考.wav' }));
  expect(
    screen.getByLabelText('试听角色参考.wav', { selector: 'audio' }),
  ).toHaveAttribute('src', '/audio/51');
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await waitFor(() => expect(mocks.saved).toHaveBeenCalledWith(reference));
  expect(mocks.save).toHaveBeenCalledWith(
    1,
    11,
    expect.objectContaining({ sourceProjectId: 1, resourceId: 51 }),
  );
});

it('selects system audio and saves its reference in the current project without uploading', async () => {
  show(true);
  expect(screen.getByRole('tab', { name: '系统音频' })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  expect(screen.getByRole('button', { name: '确认' })).toBeDisabled();
  const systemReference = {
    sourceProjectId: 1,
    sourceProjectName: '系统音频',
    resourceType: 'SYSTEM_AUDIO_ASSET',
    resourceId: 71,
    name: '清朗少年',
    previewUrl: '/api/system-audio/71/playback',
    durationSeconds: 12,
  };
  mocks.save.mockResolvedValueOnce({ success: true, data: systemReference });
  fireEvent.click(await screen.findByRole('radio', { name: /清朗少年/ }));
  fireEvent.click(screen.getByRole('button', { name: '试听清朗少年' }));
  expect(
    screen.getByLabelText('试听清朗少年', { selector: 'audio' }),
  ).toHaveAttribute('src', systemReference.previewUrl);
  const firstPlayer = screen.getByLabelText('试听清朗少年', { selector: 'audio' });
  fireEvent.error(firstPlayer);
  expect(screen.getByRole('alert')).toHaveTextContent('音频播放失败');
  fireEvent.click(screen.getByRole('button', { name: '试听清朗少年' }));
  expect(screen.getByLabelText('试听清朗少年', { selector: 'audio' })).not.toBe(firstPlayer);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await waitFor(() =>
    expect(mocks.saved).toHaveBeenCalledWith(systemReference),
  );
  expect(mocks.save).toHaveBeenCalledWith(1, 11, systemReference);
  expect(mocks.upload).not.toHaveBeenCalled();
  expect(mocks.projects).not.toHaveBeenCalled();
});

it('clears a system selection and its player when switching sources', async () => {
  show(true);
  fireEvent.click(await screen.findByRole('radio', { name: /清朗少年/ }));
  fireEvent.click(screen.getByRole('button', { name: '试听清朗少年' }));
  expect(screen.getByRole('button', { name: '确认' })).toBeEnabled();
  fireEvent.click(screen.getByRole('tab', { name: '项目音频库' }));
  await screen.findByRole('button', { name: '打开匠心音频库' });
  expect(
    screen.queryByLabelText('试听清朗少年', { selector: 'audio' }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '确认' })).toBeDisabled();
  fireEvent.click(screen.getByRole('tab', { name: '系统音频' }));
  expect(
    await screen.findByRole('radio', { name: /清朗少年/ }),
  ).not.toBeChecked();
  expect(screen.getByRole('button', { name: '确认' })).toBeDisabled();
});

it('ignores stale system audio results after changing filters and supports retry', async () => {
  let finish!: (value: unknown) => void;
  mocks.system.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  show(true);
  await waitFor(() => expect(mocks.system).toHaveBeenCalledOnce());
  mocks.system.mockResolvedValueOnce({
    success: true,
    data: { data: [], total: 0 },
  });
  fireEvent.change(screen.getByLabelText('筛选性别'), {
    target: { value: '女声' },
  });
  await screen.findByText('未找到相关音频');
  await act(async () =>
    finish({
      success: true,
      data: { data: [{ id: 71, name: '旧男声' }], total: 1 },
    }),
  );
  expect(screen.queryByText('旧男声')).not.toBeInTheDocument();
  mocks.system.mockRejectedValueOnce(new Error('network'));
  fireEvent.change(screen.getByLabelText('筛选性别'), {
    target: { value: '' },
  });
  await screen.findByText('音频加载失败');
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(
    await screen.findByRole('radio', { name: /清朗少年/ }),
  ).toBeInTheDocument();
});

it('clears selection and playback when returning to projects and filters folders on the server', async () => {
  show();
  await screen.findByRole('button', { name: '打开匠心音频库' });
  expect(mocks.library).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '打开匠心音频库' }));
  fireEvent.click(await screen.findByRole('radio', { name: /角色参考.wav/ }));
  fireEvent.click(screen.getByRole('tab', { name: '文件夹' }));
  fireEvent.click(screen.getByRole('button', { name: /参考音频/ }));
  await waitFor(() =>
    expect(mocks.library).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ category: 'REFERENCE' }),
      expect.any(AbortSignal),
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: '所有项目' }));
  fireEvent.click(
    await screen.findByRole('button', { name: '打开另一项目音频库' }),
  );
  await screen.findByRole('radio', { name: /角色参考.wav/ });
  expect(screen.getByRole('button', { name: '确认' })).toBeDisabled();
  expect(mocks.library).toHaveBeenLastCalledWith(
    2,
    expect.objectContaining({ category: undefined }),
    expect.any(AbortSignal),
  );
});

it('ignores stale audio responses after switching projects', async () => {
  let finish!: (value: unknown) => void;
  mocks.library.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  show();
  fireEvent.click(
    await screen.findByRole('button', { name: '打开匠心音频库' }),
  );
  await waitFor(() => expect(mocks.library).toHaveBeenCalledOnce());
  fireEvent.click(screen.getByRole('button', { name: '所有项目' }));
  mocks.library.mockResolvedValue(
    response([{ ...audio, id: 52, name: '另一音频.wav' }]),
  );
  fireEvent.click(
    await screen.findByRole('button', { name: '打开另一项目音频库' }),
  );
  await screen.findByRole('radio', { name: /另一音频.wav/ });
  await act(async () => finish(response()));
  expect(
    screen.queryByRole('radio', { name: /角色参考.wav/ }),
  ).not.toBeInTheDocument();
});

it('uploads local audio to the current project before saving the character reference', async () => {
  show();
  fireEvent.click(screen.getByRole('tab', { name: '本地上传' }));
  fireEvent.change(screen.getByLabelText('上传参考音频'), {
    target: { files: [new File(['audio'], '参考.wav', { type: 'audio/wav' })] },
  });
  expect(await screen.findByLabelText('本地参考音频试听')).toHaveAttribute(
    'src',
    'blob:local-audio',
  );
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await waitFor(() => expect(mocks.saved).toHaveBeenCalledWith(reference));
  expect(mocks.upload).toHaveBeenCalledWith(
    expect.any(File),
    expect.objectContaining({ projectId: 1 }),
  );
  expect(mocks.register).toHaveBeenCalledWith(1, 'uploaded', '参考.wav');
  expect(mocks.save).toHaveBeenCalledWith(1, 11, reference);
});

it('rejects unsupported local files and cancels a pending upload when unmounted', async () => {
  let finish!: (value: unknown) => void;
  mocks.upload.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const view = show();
  fireEvent.click(screen.getByRole('tab', { name: '本地上传' }));
  fireEvent.change(screen.getByLabelText('上传参考音频'), {
    target: { files: [new File(['bad'], 'image.png')] },
  });
  expect(screen.getByRole('button', { name: '确认' })).toBeDisabled();
  fireEvent.change(screen.getByLabelText('上传参考音频'), {
    target: { files: [new File(['audio'], '参考.mp3')] },
  });
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await waitFor(() => expect(mocks.upload).toHaveBeenCalledOnce());
  view.unmount();
  await act(async () =>
    finish({
      attempt: Promise.resolve({ sessionToken: 'uploaded' }),
      cancel: mocks.cancel,
    }),
  );
  expect(mocks.cancel).toHaveBeenCalledOnce();
  expect(mocks.register).not.toHaveBeenCalled();
  expect(mocks.save).not.toHaveBeenCalled();
});

it('shows persisted voice information and does not silently accept a failed save', async () => {
  mocks.current.mockResolvedValue({ success: true, data: reference });
  mocks.save.mockResolvedValueOnce({
    success: false,
    errorMessage: '无权访问来源项目',
  });
  show();
  await screen.findByText(`当前音色：${audio.name}`);
  fireEvent.click(
    await screen.findByRole('button', { name: '打开匠心音频库' }),
  );
  fireEvent.click(await screen.findByRole('radio', { name: /角色参考.wav/ }));
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await screen.findByText('无权访问来源项目');
  expect(mocks.saved).not.toHaveBeenCalled();
  expect(mocks.close).not.toHaveBeenCalled();
});

it('saves a storyboard shape override without changing the characters default voice', async () => {
  const scope = { id: 301, variantId: 101, variantName: '老年形态' };
  const changed = vi.fn();
  render(<App><CharacterVoiceModal projectId={1} characterId={11} characterName="陈志国" storyboard={scope} onClose={mocks.close} onSaved={mocks.saved} onChanged={changed} /></App>);
  await screen.findByText(/使用角色默认音色/);
  expect(mocks.current).not.toHaveBeenCalled();
  expect(mocks.storyboardCurrent).toHaveBeenCalledWith(1, 11, scope, expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('tab', { name: '项目音频库' }));
  fireEvent.click(await screen.findByRole('button', { name: '打开匠心音频库' }));
  fireEvent.click(await screen.findByRole('radio', { name: /角色参考.wav/ }));
  fireEvent.click(screen.getByRole('button', { name: '确认' }));
  await waitFor(() => expect(mocks.saved).toHaveBeenCalledWith(reference));
  expect(mocks.storyboardSave).toHaveBeenCalledWith(1, 11, scope, expect.objectContaining({ sourceProjectId: 1, resourceId: 51 }));
  expect(changed).toHaveBeenCalledTimes(1);
  expect(mocks.save).not.toHaveBeenCalled();
});

it('removes only the storyboard override when restoring the default voice', async () => {
  mocks.storyboardCurrent.mockResolvedValue({ success: true, data: { audio: reference, overridden: true } });
  mocks.storyboardReset.mockResolvedValue({ success: true, data: { audio: { ...reference, name: '角色默认.wav' }, overridden: false } });
  const scope = { id: 301, variantId: 101 };
  render(<App><CharacterVoiceModal projectId={1} characterId={11} characterName="陈志国" storyboard={scope} onClose={mocks.close} onSaved={mocks.saved} /></App>);
  fireEvent.click(await screen.findByRole('button', { name: '恢复角色默认音色' }));
  await screen.findByText('当前音色：角色默认.wav');
  expect(mocks.storyboardReset).toHaveBeenCalledWith(1, 11, scope);
  expect(screen.queryByRole('button', { name: '恢复角色默认音色' })).not.toBeInTheDocument();
  expect(mocks.save).not.toHaveBeenCalled();
  expect(mocks.storyboardSave).not.toHaveBeenCalled();
});


it.each([reference, null])('notifies the card after restoring a default, including an empty default (%s)', async (audio) => {
  mocks.storyboardCurrent.mockResolvedValue({ success: true, data: { audio: reference, overridden: true } });
  mocks.storyboardReset.mockResolvedValue({ success: true, data: { audio, overridden: false } });
  const changed = vi.fn();
  render(<App><CharacterVoiceModal projectId={1} characterId={11} characterName="陈志国"
    storyboard={{ id: 301, variantId: 101 }} onClose={mocks.close} onSaved={mocks.saved} onChanged={changed} /></App>);
  fireEvent.click(await screen.findByRole('button', { name: '恢复角色默认音色' }));
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
  expect(mocks.saved).not.toHaveBeenCalled();
  expect(mocks.close).not.toHaveBeenCalled();
});
