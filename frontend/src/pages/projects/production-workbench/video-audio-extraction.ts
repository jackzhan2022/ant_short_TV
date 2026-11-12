import type { Project } from '@/services/account-team/types';

export const MAX_SOURCE_BYTES = 100 * 1024 * 1024;
export const MAX_CLIP_SECONDS = 30;
const MAX_AUDIO_BYTES = 10 * 1024 * 1024;

export type AudioWaveformBar = { sample: number; level: number };

export function audioWaveform(audio: AudioBuffer): AudioWaveformBar[] {
  const channels = Array.from({ length: audio.numberOfChannels }, (_, index) =>
    audio.getChannelData(index),
  );
  const count = Math.min(160, audio.length);
  const bars = Array.from({ length: count }, (_, index) => {
    const first = Math.floor((index * audio.length) / count);
    const last = Math.floor(((index + 1) * audio.length) / count);
    let level = 0;
    for (const channel of channels) {
      for (let sample = first; sample < last; sample++)
        level = Math.max(level, Math.abs(channel[sample]));
    }
    return { sample: first, level };
  });
  const maximum = Math.max(0.000001, ...bars.map((bar) => bar.level));
  return bars.map((bar) => ({ ...bar, level: bar.level / maximum }));
}

export function formatAudioTime(seconds: number) {
  const tenths = Math.max(
    0,
    Math.round((Number.isFinite(seconds) ? seconds : 0) * 10),
  );
  return `${String(Math.floor(tenths / 600)).padStart(2, '0')}:${String(Math.floor(tenths / 10) % 60).padStart(2, '0')}.${tenths % 10}`;
}

export function canExtractVideoAudio(
  project?: Pick<Project, 'effectivePermissions'>,
) {
  // These menus grant ELEMENT:EDIT, MEDIA:UPLOAD and source/audio read operations.
  // Business operation IDs are not stored in effectivePermissions.
  return (
    project?.effectivePermissions?.some((permission) =>
      ['MENU:PROJECTS', 'MENU:SHORT_DRAMA_CREATION'].includes(permission),
    ) ?? false
  );
}

export function validateAudioRange(
  duration: number,
  start: number,
  end: number,
) {
  if (
    ![duration, start, end].every(Number.isFinite) ||
    duration <= 0 ||
    start < 0 ||
    end > duration ||
    end <= start
  ) {
    throw new Error('请选择视频范围内有效的起止时间，结束时间须大于开始时间。');
  }
  if (end - start > MAX_CLIP_SECONDS)
    throw new Error('单次最多提取30秒，请缩短选段。');
}

async function readSource(
  response: Response,
  signal: AbortSignal,
): Promise<ArrayBuffer> {
  if (!response.ok) throw new Error('视频读取失败，请确认视频仍可访问后重试。');
  if (Number(response.headers.get('content-length')) > MAX_SOURCE_BYTES) {
    await response.body?.cancel();
    throw new Error('视频超过100MB，请选择较短的分镜视频。');
  }
  if (!response.body) throw new Error('视频内容读取失败，请重试。');
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      signal.throwIfAborted();
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_SOURCE_BYTES)
        throw new Error('视频超过100MB，请选择较短的分镜视频。');
      chunks.push(value);
    }
    signal.throwIfAborted();
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return bytes.buffer;
  } finally {
    await reader.cancel().catch(() => undefined);
    reader.releaseLock();
  }
}

export async function decodeVideoAudio(
  source: string,
  signal: AbortSignal,
): Promise<AudioBuffer> {
  signal.throwIfAborted();
  if (typeof AudioContext === 'undefined')
    throw new Error('当前浏览器不支持音频提取，请使用新版 Chrome 或 Edge。');
  const bytes = await readSource(
    await fetch(source, { credentials: 'same-origin', signal }),
    signal,
  );
  signal.throwIfAborted();
  const context = new AudioContext();
  try {
    let audio: AudioBuffer;
    try {
      audio = await context.decodeAudioData(bytes);
    } catch {
      signal.throwIfAborted();
      throw new Error(
        '未找到可解码的音轨，请选择有声音的视频；当前音频编码可能不受浏览器支持。',
      );
    }
    signal.throwIfAborted();
    if (
      !Number.isFinite(audio.duration) ||
      audio.duration <= 0 ||
      !audio.numberOfChannels ||
      !audio.length
    ) {
      throw new Error('视频中没有可用的音频。');
    }
    if (audio.duration > 120)
      throw new Error('视频音轨超过2分钟，请选择较短的分镜视频。');
    return audio;
  } finally {
    await context.close().catch(() => undefined);
  }
}

export function encodeAudioSelection(
  audio: AudioBuffer,
  start: number,
  end: number,
): Blob {
  validateAudioRange(audio.duration, start, end);
  const first = Math.floor(start * audio.sampleRate);
  const last = Math.min(audio.length, Math.ceil(end * audio.sampleRate));
  const count = last - first;
  const size = 44 + count * 2;
  if (count <= 0 || size > MAX_AUDIO_BYTES)
    throw new Error('提取音频为空或超过10MB，请缩短选段。');
  const buffer = new ArrayBuffer(size);
  const view = new DataView(buffer);
  const text = (offset: number, value: string) => {
    for (let i = 0; i < value.length; i++)
      view.setUint8(offset + i, value.charCodeAt(i));
  };
  text(0, 'RIFF');
  view.setUint32(4, size - 8, true);
  text(8, 'WAVE');
  text(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, audio.sampleRate, true);
  view.setUint32(28, audio.sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  text(36, 'data');
  view.setUint32(40, count * 2, true);
  const channels = Array.from({ length: audio.numberOfChannels }, (_, i) =>
    audio.getChannelData(i),
  );
  for (let i = 0; i < count; i++) {
    let sample = 0;
    for (const channel of channels) sample += channel[first + i];
    sample = Math.max(-1, Math.min(1, sample / channels.length));
    view.setInt16(
      44 + i * 2,
      Math.round(sample * (sample < 0 ? 32768 : 32767)),
      true,
    );
  }
  return new Blob([buffer], { type: 'audio/wav' });
}
