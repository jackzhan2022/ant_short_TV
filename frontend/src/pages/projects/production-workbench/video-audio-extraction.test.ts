import { afterEach, expect, it, vi } from 'vitest';
import {
  audioWaveform,
  canExtractVideoAudio,
  decodeVideoAudio,
  encodeAudioSelection,
  formatAudioTime,
  MAX_SOURCE_BYTES,
  validateAudioRange,
} from './video-audio-extraction';

const audioBuffer = (channels: number[][], sampleRate = 4) =>
  ({
    sampleRate,
    length: channels[0].length,
    duration: channels[0].length / sampleRate,
    numberOfChannels: channels.length,
    getChannelData: (channel: number) => Float32Array.from(channels[channel]),
  }) as AudioBuffer;

afterEach(() => vi.unstubAllGlobals());

it('builds a normalized waveform from all channels with stable sample positions', () => {
  const waveform = audioWaveform(
    audioBuffer([
      [0, 0, 0.2, 0],
      [0, -0.5, 0, 1],
    ]),
  );
  expect(waveform.map((bar) => bar.sample)).toEqual([0, 1, 2, 3]);
  expect(waveform.map((bar) => bar.level)).toEqual([
    0,
    0.5,
    expect.closeTo(0.2),
    1,
  ]);
  expect(audioWaveform(audioBuffer([[0, 0]]))).toEqual([
    { sample: 0, level: 0 },
    { sample: 1, level: 0 },
  ]);
  const long = audioBuffer([
    Array.from({ length: 321 }, (_, index) => (index === 320 ? 1 : 0)),
  ]);
  const bars = audioWaveform(long);
  expect(bars).toHaveLength(160);
  expect(bars.at(-1)?.level).toBe(1);
});

it('formats rounded time without displaying a 60-second remainder', () => {
  expect(formatAudioTime(59.96)).toBe('01:00.0');
  expect(formatAudioTime(5.2)).toBe('00:05.2');
  expect(formatAudioTime(NaN)).toBe('00:00.0');
});

it('writes a valid mono PCM WAV containing only the selected stereo samples', async () => {
  const blob = encodeAudioSelection(
    audioBuffer([
      [0, 1, -1, 0.5],
      [0, 0, -1, -0.5],
    ]),
    0.25,
    0.75,
  );
  const bytes = await blob.arrayBuffer();
  const view = new DataView(bytes);
  expect(blob.type).toBe('audio/wav');
  expect(bytes.byteLength).toBe(48);
  expect(new TextDecoder().decode(bytes.slice(0, 4))).toBe('RIFF');
  expect(new TextDecoder().decode(bytes.slice(8, 12))).toBe('WAVE');
  expect(view.getUint32(4, true)).toBe(40);
  expect(view.getUint16(20, true)).toBe(1);
  expect(view.getUint16(22, true)).toBe(1);
  expect(view.getUint32(24, true)).toBe(4);
  expect(view.getUint32(28, true)).toBe(8);
  expect(view.getUint16(34, true)).toBe(16);
  expect(view.getUint32(40, true)).toBe(4);
  expect(view.getInt16(44, true)).toBe(16384);
  expect(view.getInt16(46, true)).toBe(-32768);
});

it('rejects invalid ranges and clips that exceed the project upload limit', () => {
  for (const [duration, start, end] of [
    [10, -1, 5],
    [10, 4, 4],
    [10, 0, 11],
    [10, NaN, 5],
    [40, 0, 31],
  ]) {
    expect(() => validateAudioRange(duration, start, end)).toThrow();
  }
  const audio = {
    duration: 30,
    sampleRate: 192000,
    length: 5760000,
  } as AudioBuffer;
  expect(() => encodeAudioSelection(audio, 0, 30)).toThrow('超过10MB');
  expect(() => encodeAudioSelection(audioBuffer([[1]]), 0, 0)).toThrow();
});

it('uses the systems assignable menu permissions and excludes users with only library access', () => {
  expect(
    canExtractVideoAudio({ effectivePermissions: ['MENU:PROJECTS'] }),
  ).toBe(true);
  expect(
    canExtractVideoAudio({
      effectivePermissions: ['MENU:SHORT_DRAMA_CREATION'],
    }),
  ).toBe(true);
  expect(canExtractVideoAudio()).toBe(false);
  expect(
    canExtractVideoAudio({ effectivePermissions: ['MENU:ASSET_LIBRARY'] }),
  ).toBe(false);
  expect(
    canExtractVideoAudio({ effectivePermissions: ['MENU:PRODUCTION_TASKS'] }),
  ).toBe(false);
});

function mockDecoder(
  decode = vi.fn().mockResolvedValue(audioBuffer([[0, 0, 1, 0]])),
) {
  const close = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal(
    'AudioContext',
    class {
      decodeAudioData = decode;
      close = close;
    },
  );
  const fetcher = vi
    .fn()
    .mockImplementation(async () => new Response(new Uint8Array([1, 2, 3])));
  vi.stubGlobal('fetch', fetcher);
  return { decode, close, fetcher };
}

it('loads the protected source with credentials and closes the decoder', async () => {
  const { fetcher, close, decode } = mockDecoder();
  const signal = new AbortController().signal;
  expect((await decodeVideoAudio('/protected-video', signal)).duration).toBe(1);
  expect(fetcher).toHaveBeenCalledWith('/protected-video', {
    credentials: 'same-origin',
    signal,
  });
  expect(new Uint8Array(decode.mock.calls[0][0])).toEqual(
    new Uint8Array([1, 2, 3]),
  );
  expect(close).toHaveBeenCalledOnce();
});

it('rejects missing audio and oversized sources before saving any data', async () => {
  const { fetcher, close, decode } = mockDecoder(
    vi.fn().mockRejectedValue(new Error('no audio stream')),
  );
  await expect(
    decodeVideoAudio('/silent', new AbortController().signal),
  ).rejects.toThrow('未找到可解码的音轨');
  expect(close).toHaveBeenCalledOnce();
  fetcher.mockResolvedValueOnce(
    new Response(new Uint8Array([1]), {
      headers: { 'content-length': String(MAX_SOURCE_BYTES + 1) },
    }),
  );
  await expect(
    decodeVideoAudio('/large', new AbortController().signal),
  ).rejects.toThrow('超过100MB');
  expect(decode).toHaveBeenCalledOnce();
  fetcher.mockResolvedValueOnce(new Response(null, { status: 403 }));
  await expect(
    decodeVideoAudio('/denied', new AbortController().signal),
  ).rejects.toThrow('视频读取失败');
});

it('ignores late decoding results after cancellation and releases the context', async () => {
  let finish!: (value: AudioBuffer) => void;
  const { decode, close } = mockDecoder(
    vi.fn().mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    ),
  );
  const controller = new AbortController();
  const result = decodeVideoAudio('/video', controller.signal);
  const rejection = expect(result).rejects.toMatchObject({
    name: 'AbortError',
  });
  await vi.waitFor(() => expect(decode).toHaveBeenCalledOnce());
  controller.abort();
  finish(audioBuffer([[1]]));
  await rejection;
  expect(close).toHaveBeenCalledOnce();
});
