import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadCreationModels, normalizeCreationModels } from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

const providers = [
  { id: 1, status: 'ENABLED' },
  { id: 2, status: 'DISABLED' },
];
const model = (overrides: Record<string, unknown> = {}) => ({
  id: 1,
  providerId: 1,
  providerName: '火山方舟',
  name: 'Seedance',
  serviceType: 'VIDEO',
  status: 'ENABLED',
  capabilities: ['VIDEO_GENERATION'],
  isDefault: false,
  sort: 0,
  ...overrides,
});
const normalize = (config: unknown) =>
  normalizeCreationModels(
    [model({ configJson: JSON.stringify(config) })],
    providers,
  )[0];

describe('creation model metadata', () => {
  it('retains the authoritative provider code through providerId, not the display name', () => {
    const result = normalizeCreationModels(
      [
        model({
          id: 1,
          providerId: 1,
          providerName: '自定义名称',
          serviceType: 'IMAGE',
          capabilities: ['IMAGE_GENERATION'],
        }),
        model({
          id: 2,
          providerId: 2,
          providerName: 'OpenAI',
          serviceType: 'IMAGE',
          capabilities: ['IMAGE_GENERATION'],
        }),
      ],
      [
        { id: 1, code: 'OpenAI', status: 'ENABLED' },
        { id: 2, code: 'VOLCENGINE_ARK', status: 'ENABLED' },
      ],
    );
    expect(result.find((item) => item.id === 1)?.providerCode).toBe('OpenAI');
    expect(result.find((item) => item.id === 2)?.providerCode).toBe(
      'VOLCENGINE_ARK',
    );
  });
  it('requires enabled models, enabled providers and the matching generation capability', () => {
    const result = normalizeCreationModels(
      [
        model(),
        model({ id: 2, status: 'DISABLED' }),
        model({ id: 3, providerId: 2 }),
        model({ id: 4, providerId: 99 }),
        model({ id: 5, capabilities: ['IMAGE_GENERATION'] }),
        model({ id: 6, capabilities: [] }),
        model({ id: 7, serviceType: 'TEXT' }),
        model({
          id: 8,
          serviceType: 'IMAGE',
          capabilities: ['IMAGE_GENERATION'],
        }),
      ],
      providers,
    );
    expect(result.map((item) => item.id)).toEqual([8, 1]);
  });

  it('sorts defaults first, then descending sort and id without mutating source', () => {
    const input = [
      model({ id: 1, sort: 99 }),
      model({ id: 2, isDefault: true }),
      model({ id: 3, sort: 99 }),
      model({ id: 4, sort: 100 }),
    ];
    expect(
      normalizeCreationModels(input, providers).map((item) => item.id),
    ).toEqual([2, 4, 3, 1]);
    expect(input.map((item) => item.id)).toEqual([1, 2, 3, 4]);
  });

  it('reads the real video generation configuration and reference limits', () => {
    const videoGeneration = {
      duration: { min: 4, max: 15, intelligent: true },
      resolutions: ['480p', '720p'],
      image: {
        formats: ['png', 'jpg'],
        minCount: 1,
        maxCount: 9,
        maxBytes: 29999999,
        minDimension: 300,
        maxDimension: 6000,
        minAspectRatio: 0.4,
        maxAspectRatio: 2.5,
      },
      video: {
        formats: ['mp4'],
        maxCount: 3,
        minDurationSeconds: 2,
        maxDurationSeconds: 15,
        maxTotalDurationSeconds: 15,
      },
      audio: { formats: ['mp3'], maxCount: 3 },
    };
    expect(normalize({ videoGeneration })).toMatchObject({
      configStatus: 'configured',
      duration: videoGeneration.duration,
      resolutions: videoGeneration.resolutions,
      references: {
        image: videoGeneration.image,
        video: videoGeneration.video,
        audio: videoGeneration.audio,
      },
    });
  });

  it('reads image quality and output format only from actual root fields', () => {
    const [result] = normalizeCreationModels(
      [
        model({
          serviceType: 'IMAGE',
          capabilities: ['IMAGE_GENERATION'],
          configJson: JSON.stringify({
            quality: 'high',
            outputFormat: 'webp',
            resolutions: ['4K'],
            videoGeneration: { duration: { min: 4, max: 15 } },
          }),
        }),
      ],
      providers,
    );
    expect(result).toMatchObject({
      quality: 'high',
      outputFormat: 'webp',
      configStatus: 'configured',
      resolutions: [],
      references: {},
    });
    expect(result.duration).toBeUndefined();
  });

  it.each([
    undefined,
    null,
    '',
    '  ',
    '{}',
    '{"unrelated":true}',
  ])('keeps missing configuration absent: %s', (configJson) => {
    const [result] = normalizeCreationModels(
      [model({ configJson })],
      providers,
    );
    expect(result).toMatchObject({
      configStatus: 'absent',
      resolutions: [],
      references: {},
    });
    expect(result.duration).toBeUndefined();
    expect(result.quality).toBeUndefined();
  });

  it.each([
    '{bad',
    'null',
    '[]',
    '42',
  ])('marks broken configuration invalid: %s', (configJson) => {
    expect(
      normalizeCreationModels([model({ configJson })], providers)[0]
        .configStatus,
    ).toBe('invalid');
  });

  it('rejects invalid parameter types, inverted bounds and unbounded duration choices', () => {
    const result = normalize({
      videoGeneration: {
        duration: { min: 4, max: 1e100 },
        resolutions: ['720p', 3, '', '720p'],
        image: { minCount: 9, maxCount: 1, maxBytes: -1 },
        video: {
          formats: 'mp4',
          minDurationSeconds: 15,
          maxDurationSeconds: 2,
        },
        audio: { maxCount: 1.5 },
      },
    });
    expect(result.configStatus).toBe('invalid');
    expect(result.duration).toBeUndefined();
    expect(result.resolutions).toEqual(['720p']);
    expect(result.references.image?.maxBytes).toBeUndefined();
    expect(result.references.image?.maxCount).toBeUndefined();
    expect(result.references.video?.minDurationSeconds).toBeUndefined();
    expect(result.references.audio?.maxCount).toBeUndefined();
  });
});

describe('loadCreationModels', () => {
  beforeEach(() => vi.resetAllMocks());

  it('reads only the two listing endpoints and forwards abort and local error handling', async () => {
    vi.mocked(request).mockImplementation(async (url) => ({
      success: true,
      data: url === '/api/platform/ai/models' ? [model()] : providers,
    }));
    const signal = new AbortController().signal;
    expect(await loadCreationModels(signal)).toHaveLength(1);
    expect(request).toHaveBeenCalledTimes(2);
    for (const path of [
      '/api/platform/ai/models',
      '/api/platform/ai/providers',
    ]) {
      expect(request).toHaveBeenCalledWith(path, {
        method: 'GET',
        skipErrorHandler: true,
        signal,
      });
    }
  });

  it.each([
    '/api/platform/ai/models',
    '/api/platform/ai/providers',
  ])('fails when %s reports business failure', async (path) => {
    vi.mocked(request).mockImplementation(async (url) => ({
      success: url !== path,
      data: url === '/api/platform/ai/models' ? [model()] : providers,
    }));
    await expect(loadCreationModels()).rejects.toThrow(
      '模型列表加载失败，请稍后重试',
    );
  });

  it('returns a friendly error for transport failure', async () => {
    vi.mocked(request).mockRejectedValue(new Error('network error'));
    await expect(loadCreationModels()).rejects.toThrow(
      '模型列表加载失败，请稍后重试',
    );
  });

  it('keeps cancellation recognizable by callers', async () => {
    const error = new DOMException('Aborted', 'AbortError');
    vi.mocked(request).mockRejectedValue(error);
    await expect(loadCreationModels()).rejects.toBe(error);
  });

  it('rejects malformed listing payloads rather than displaying an empty success state', async () => {
    vi.mocked(request).mockResolvedValue({ success: true, data: null });
    await expect(loadCreationModels()).rejects.toThrow(
      '模型列表加载失败，请稍后重试',
    );
  });
});
