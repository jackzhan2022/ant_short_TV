import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import VideoAudioExtractionModal from './VideoAudioExtractionModal';

const mocks = vi.hoisted(() => ({
  decode: vi.fn(),
  encode: vi.fn(),
  upload: vi.fn(),
  register: vi.fn(),
  cancel: vi.fn(),
  retry: vi.fn(),
  push: vi.fn(),
  tenant: 1,
}));
vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('./video-audio-extraction', async () => ({
  ...(await vi.importActual('./video-audio-extraction')),
  decodeVideoAudio: mocks.decode,
  encodeAudioSelection: mocks.encode,
}));
vi.mock('@/pages/asset-library/audio-service', () => ({
  registerProjectAudio: mocks.register,
}));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.upload }));
vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () => mocks.tenant,
}));
vi.mock('./video-frame-service', () => ({
  videoFrameSource: (project: number, result: number) =>
    `/api/projects/${project}/ai-video-results/${result}/frame-source`,
}));

const buffer = (duration = 6) => ({
  duration,
  sampleRate: 4,
  length: duration * 4,
  numberOfChannels: 1,
  getChannelData: () =>
    Float32Array.from({ length: duration * 4 }, (_, index) =>
      index % 2 ? 0.5 : 0,
    ),
});

beforeEach(() => {
  vi.resetAllMocks();
  mocks.tenant = 1;
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(
    () => undefined,
  );
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(
    () => undefined,
  );
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:voice');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  mocks.decode.mockResolvedValue(buffer());
  mocks.encode.mockReturnValue(new Blob(['wave'], { type: 'audio/wav' }));
  mocks.upload.mockResolvedValue({
    attempt: Promise.resolve({ sessionToken: 'session-audio' }),
    cancel: mocks.cancel,
    retry: mocks.retry,
  });
  mocks.register.mockResolvedValue({
    success: true,
    data: { resourceId: 33, name: '林夏音色' },
  });
});
afterEach(() => vi.restoreAllMocks());

function open(projectId = 1) {
  const onClose = vi.fn();
  const view = render(
    <VideoAudioExtractionModal
      projectId={projectId}
      resultId={22}
      sourceName="分镜3"
      projectName="长夜未央"
      onClose={onClose}
    />,
  );
  const video = screen.getByLabelText('音色提取视频预览') as HTMLVideoElement;
  Object.defineProperties(video, {
    duration: { value: 6 },
    readyState: { value: 2 },
  });
  fireEvent.loadedData(video);
  return { ...view, onClose, video };
}
async function ready() {
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: '提取并试听' }),
    ).not.toBeDisabled(),
  );
}
async function extract() {
  await ready();
  fireEvent.click(screen.getByRole('button', { name: '提取并试听' }));
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: '试听提取结果' }),
    ).not.toBeDisabled(),
  );
}

it('keeps a stable preview and footer and saves to the source project before opening its library', async () => {
  const { onClose } = open(7);
  const result = screen.getByRole('region', { name: '提取结果试听' });
  expect(screen.getByRole('button', { name: '试听提取结果' })).toBeDisabled();
  expect(
    screen.getByRole('button', { name: '保存到项目音频库' }),
  ).toBeDisabled();
  expect(screen.getByText('保存到 长夜未央 / 项目音频库')).toBeInTheDocument();
  await extract();
  expect(screen.getByRole('region', { name: '提取结果试听' })).toBe(result);
  expect(mocks.encode).toHaveBeenCalledWith(
    expect.objectContaining({ duration: 6 }),
    0,
    5,
  );
  fireEvent.change(screen.getByLabelText('音频名称'), {
    target: { value: ' 林夏音色 ' },
  });
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await screen.findByText('「林夏音色」已保存到项目音频库');
  expect(mocks.upload).toHaveBeenCalledWith(
    expect.objectContaining({ type: 'audio/wav' }),
    expect.objectContaining({ projectId: 7 }),
  );
  expect(mocks.register).toHaveBeenCalledWith(
    7,
    'session-audio',
    '林夏音色',
    expect.any(AbortSignal),
  );
  fireEvent.click(screen.getByRole('button', { name: '查看项目音频' }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(mocks.push).toHaveBeenCalledWith(
    '/asset-library/projects/7?type=AUDIO',
  );
});

it('allows keyboard adjustment of waveform handles and invalidates a completed preview', async () => {
  open();
  await extract();
  const handle = screen.getByRole('slider', { name: '选段开始时间' });
  fireEvent.keyDown(handle, {
    key: 'ArrowRight',
    code: 'ArrowRight',
    keyCode: 39,
    which: 39,
  });
  await waitFor(() => expect(handle).toHaveAttribute('aria-valuenow', '0.1'));
  expect(screen.getByRole('button', { name: '试听提取结果' })).toBeDisabled();
  expect(
    screen.getByRole('button', { name: '保存到项目音频库' }),
  ).toBeDisabled();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:voice');
  fireEvent.click(screen.getByRole('button', { name: '提取并试听' }));
  expect(mocks.encode).toHaveBeenLastCalledWith(expect.anything(), 0.1, 5);
});

it('rejects reversed and overlong ranges entered through the numeric alternative', async () => {
  mocks.decode.mockResolvedValue(buffer(60));
  open();
  await ready();
  fireEvent.change(screen.getByLabelText('开始时间（秒）'), {
    target: { value: '5.5' },
  });
  expect(screen.getByRole('button', { name: '提取并试听' })).toBeDisabled();
  expect(screen.getByRole('alert')).toHaveTextContent('结束时间须大于开始时间');
  fireEvent.change(screen.getByLabelText('开始时间（秒）'), {
    target: { value: '0' },
  });
  fireEvent.change(screen.getByLabelText('结束时间（秒）'), {
    target: { value: '31' },
  });
  expect(screen.getByRole('button', { name: '提取并试听' })).toBeDisabled();
  expect(screen.getByRole('alert')).toHaveTextContent('单次最多提取30秒');
  expect(mocks.encode).not.toHaveBeenCalled();
});

it('offers recovery after source reading fails without enabling save', async () => {
  mocks.decode.mockRejectedValueOnce(new Error('未找到可解码的音轨'));
  open();
  await screen.findByText('未找到可解码的音轨');
  expect(
    screen.getByRole('button', { name: '保存到项目音频库' }),
  ).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '重新读取音轨' }));
  await ready();
  expect(mocks.decode).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('retries failed registration with the same completed upload session', async () => {
  mocks.register.mockResolvedValueOnce({
    success: false,
    errorMessage: '注册暂时失败',
  });
  open();
  await extract();
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await screen.findByText('注册暂时失败');
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await screen.findByText('「林夏音色」已保存到项目音频库');
  expect(mocks.upload).toHaveBeenCalledOnce();
  expect(mocks.register).toHaveBeenCalledTimes(2);
});

it('recovers a failed upload through its existing handle', async () => {
  mocks.upload.mockImplementation(() => ({
    attempt: Promise.reject(new Error('上传连接中断')),
    cancel: mocks.cancel,
    retry: mocks.retry,
  }));
  mocks.retry.mockResolvedValue({ sessionToken: 'recovered-session' });
  open();
  await extract();
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await screen.findByText('上传连接中断');
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await screen.findByText('「林夏音色」已保存到项目音频库');
  expect(mocks.upload).toHaveBeenCalledOnce();
  expect(mocks.retry).toHaveBeenCalledOnce();
  expect(mocks.register).toHaveBeenCalledWith(
    1,
    'recovered-session',
    expect.any(String),
    expect.any(AbortSignal),
  );
});

it('blocks duplicate saves, closing and selection edits while registration is pending', async () => {
  let finish!: (value: unknown) => void;
  mocks.register.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  open();
  await extract();
  const save = screen.getByRole('button', { name: '保存到项目音频库' });
  fireEvent.click(save);
  fireEvent.click(save);
  await waitFor(() => expect(mocks.register).toHaveBeenCalledOnce());
  expect(
    screen.queryByRole('button', { name: 'Close' }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '取消' })).toBeDisabled();
  expect(screen.getByLabelText('音频名称')).toBeDisabled();
  expect(screen.getByLabelText('开始时间（秒）')).toBeDisabled();
  await act(async () => finish({ success: true, data: { name: '林夏音色' } }));
});

it('previews only the selected part of the original video and pauses at its end', async () => {
  const { video } = open();
  await ready();
  fireEvent.change(screen.getByLabelText('开始时间（秒）'), {
    target: { value: '1.5' },
  });
  video.currentTime = 0;
  fireEvent.click(screen.getByRole('button', { name: '播放选中视频片段' }));
  expect(video.currentTime).toBe(1.5);
  expect(HTMLMediaElement.prototype.play).toHaveBeenCalledOnce();
  vi.mocked(HTMLMediaElement.prototype.pause).mockClear();
  video.currentTime = 5.1;
  fireEvent.timeUpdate(video);
  expect(HTMLMediaElement.prototype.pause).toHaveBeenCalledOnce();
});

it('keeps audio extraction usable when the browser cannot render the video track', async () => {
  const { video } = open();
  await ready();
  fireEvent.error(video);
  expect(screen.getByText(/仍可根据音轨选段/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '提取并试听' })).not.toBeDisabled();
});

it('ignores a decode that finishes after the team changes', async () => {
  let finish!: (value: unknown) => void;
  mocks.decode.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const { onClose } = open();
  mocks.tenant = 2;
  act(() => window.dispatchEvent(new Event('current-tenant-changed')));
  expect(onClose).toHaveBeenCalledOnce();
  expect(mocks.decode.mock.calls[0][1].aborted).toBe(true);
  await act(async () => finish(buffer()));
  expect(screen.getByRole('button', { name: '提取并试听' })).toBeDisabled();
  expect(mocks.encode).not.toHaveBeenCalled();
  expect(mocks.upload).not.toHaveBeenCalled();
});

it('aborts an obsolete source when switching videos and ignores its late response', async () => {
  let finish!: (value: unknown) => void;
  mocks.decode.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const { rerender } = open();
  const oldSignal = mocks.decode.mock.calls[0][1];
  rerender(
    <VideoAudioExtractionModal
      projectId={2}
      resultId={44}
      sourceName="分镜9"
      onClose={vi.fn()}
    />,
  );
  await ready();
  await act(async () => finish(buffer(40)));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.getByRole('slider', { name: '选段结束时间' })).toHaveAttribute(
    'aria-valuemax',
    '6',
  );
  expect(screen.getByText('分镜9')).toBeInTheDocument();
});

it('cancels a pending upload and releases both media elements on unmount', async () => {
  mocks.upload.mockResolvedValue({
    attempt: new Promise(() => undefined),
    cancel: mocks.cancel,
  });
  const { unmount, video } = open();
  await extract();
  const audio = screen.getByLabelText('提取音色试听');
  fireEvent.click(screen.getByRole('button', { name: '保存到项目音频库' }));
  await waitFor(() => expect(mocks.upload).toHaveBeenCalledOnce());
  unmount();
  expect(mocks.cancel).toHaveBeenCalled();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:voice');
  expect(video).not.toHaveAttribute('src');
  expect(audio).not.toHaveAttribute('src');
  expect(mocks.register).not.toHaveBeenCalled();
});
