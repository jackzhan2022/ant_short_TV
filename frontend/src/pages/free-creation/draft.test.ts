import { describe, expect, it } from 'vitest';
import {
  draftError,
  imageCountOptions,
  newDraft,
  requestFromDraft,
  reuseDraft,
} from './draft';
import { referenceMetadataError } from './reference-metadata';
import type { CreationModel } from './service';

const image: CreationModel = {
  id: 1,
  name: '图片',
  serviceType: 'IMAGE',
  isDefault: true,
  configStatus: 'configured',
  quality: 'high',
  outputFormat: 'png',
  references: {},
  resolutions: [],
};
describe('free creation drafts', () => {
  it('submits project image identities without converting them to upload tokens or URLs', () => {
    const draft = { ...newDraft(image), prompt: '项目资产参考', references: [{
      id: 'project-3-image-9', kind: 'image' as const, name: '角色图片',
      projectImage: { projectId: 3, resultId: 9 },
      status: 'complete' as const, percent: 100, url: '/thumbnail',
    }] };
    expect(draftError(draft, image)).toBeUndefined();
    expect(requestFromDraft(draft, 'IMAGE', 'request').references).toEqual([
      { kind: 'IMAGE', projectImage: { projectId: 3, resultId: 9 } },
    ]);
  });

  it('blocks incomplete uploads and maps verified references without arbitrary URLs', () => {
    const draft = {
      ...newDraft(image),
      prompt: '我的灵感',
      references: [
        {
          id: 'x',
          kind: 'image' as const,
          name: 'a.png',
          percent: 100,
          status: 'verifying' as const,
        },
      ],
    };
    expect(draftError(draft, image)).toContain('完成校验');
    const verified = {
      ...draft,
      references: [
        {
          ...draft.references[0],
          status: 'complete' as const,
          uploadSessionToken: 'session',
          url: 'blob:test',
        },
      ],
    };
    expect(draftError(verified, image)).toBeUndefined();
    expect(requestFromDraft(verified, 'IMAGE', 'uuid').references).toEqual([
      { kind: 'IMAGE', uploadSessionToken: 'session' },
    ]);
  });
  it('checks dimension, aspect ratio and duration bounds', () => {
    expect(
      referenceMetadataError({ width: 40, height: 60 }, { minDimension: 100 }),
    ).toContain('尺寸');
    expect(
      referenceMetadataError(
        { width: 500, height: 100 },
        { maxAspectRatio: 2 },
      ),
    ).toContain('宽高比');
    expect(
      referenceMetadataError(
        { durationSeconds: 12 },
        { maxDurationSeconds: 10 },
      ),
    ).toContain('时长');
  });
  it('rejects reuse after readonly model settings change and accepts authorized cross-creator references', () => {
    const record = {
      id: 9,
      mediaType: 'IMAGE' as const,
      status: 'SUCCEEDED' as const,
      prompt: 'reuse',
      creatorId: 8,
      creatorName: '同事',
      createdAt: '',
      modelId: 1,
      modelName: '图片',
      parameters: { quality: 'medium', outputFormat: 'png', count: 1 },
      references: [
        {
          id: 1,
          referenceId: 1,
          kind: 'IMAGE' as const,
          fileName: 'a.png',
          previewUrl: '/authorized-preview',
        },
      ],
      outputs: [],
    };
    expect(() => reuseDraft(record, [image])).toThrow('配置');
    const draft = reuseDraft(
      { ...record, parameters: { ...record.parameters, quality: 'high' } },
      [image],
    );
    expect(requestFromDraft(draft, 'IMAGE', 'uuid').references).toEqual([
      { kind: 'IMAGE', referenceId: 1 },
    ]);
    expect(() => reuseDraft(record, [])).toThrow('不可用');
  });
  it('canonicalizes historical video resolution casing and rejects unsupported resolutions', () => {
    const video: CreationModel = {
      ...image,
      serviceType: 'VIDEO',
      quality: undefined,
      outputFormat: undefined,
      resolutions: ['480p', '720p'],
      duration: { min: 4, max: 15 },
    };
    const record = {
      id: 9,
      mediaType: 'VIDEO' as const,
      status: 'SUCCEEDED' as const,
      prompt: '视频灵感',
      creatorId: 8,
      creatorName: '同事',
      createdAt: '',
      modelId: 1,
      modelName: '视频',
      parameters: { resolution: '720P', durationSeconds: 5 },
      references: [],
      outputs: [],
    };
    const draft = reuseDraft(record, [video]);
    expect(draft.parameters.resolution).toBe('720p');
    expect(requestFromDraft(draft, 'VIDEO', 'uuid').parameters.resolution).toBe(
      '720p',
    );
    expect(record.parameters.resolution).toBe('720P');
    expect(() =>
      reuseDraft(
        {
          ...record,
          parameters: { ...record.parameters, resolution: '1080P' },
        },
        [video],
      ),
    ).toThrow('不支持所选分辨率');
  });
  it.each([
    { quality: undefined, outputFormat: undefined },
    { quality: undefined, outputFormat: 'png' },
    { quality: 'high', outputFormat: undefined },
  ])('accepts server defaults for model fields that are not explicitly configured: %j', (configuration) => {
    const model = { ...image, ...configuration };
    const record = {
      id: 9,
      mediaType: 'IMAGE' as const,
      status: 'SUCCEEDED' as const,
      prompt: '灵感',
      creatorId: 8,
      creatorName: '同事',
      createdAt: '',
      modelId: 1,
      modelName: '图片',
      parameters: {
        quality: configuration.quality || 'STANDARD',
        outputFormat: configuration.outputFormat || 'png',
        count: 1,
      },
      references: [],
      outputs: [],
    };
    expect(reuseDraft(record, [model]).parameters).toEqual(record.parameters);
    if (configuration.quality)
      expect(() =>
        reuseDraft(
          { ...record, parameters: { ...record.parameters, quality: 'low' } },
          [model],
        ),
      ).toThrow('配置已变化');
    if (configuration.outputFormat)
      expect(() =>
        reuseDraft(
          {
            ...record,
            parameters: { ...record.parameters, outputFormat: 'webp' },
          },
          [model],
        ),
      ).toThrow('配置已变化');
  });
  it.each([
    'OpenAI',
    'OPENAI',
    'openai',
  ])('allows only one image for %s and rejects unsupported historical counts', (providerCode) => {
    const model = { ...image, providerCode };
    expect(imageCountOptions(model)).toEqual([1]);
    expect(newDraft(model).parameters.count).toBe(1);
    const record = {
      id: 9,
      mediaType: 'IMAGE' as const,
      status: 'SUCCEEDED' as const,
      prompt: '灵感',
      creatorId: 8,
      creatorName: '同事',
      createdAt: '',
      modelId: 1,
      modelName: '图片',
      parameters: { quality: 'high', outputFormat: 'png', count: 1 },
      references: [],
      outputs: [],
    };
    expect(reuseDraft(record, [model]).parameters.count).toBe(1);
    expect(() =>
      reuseDraft(
        { ...record, parameters: { ...record.parameters, count: 4 } },
        [model],
      ),
    ).toThrow('图片数量');
    expect(
      imageCountOptions({ ...image, providerCode: 'VOLCENGINE_ARK' }),
    ).toEqual([1, 2, 4]);
    expect(
      reuseDraft(
        { ...record, parameters: { ...record.parameters, count: 4 } },
        [{ ...image, providerCode: 'VOLCENGINE_ARK' }],
      ).parameters.count,
    ).toBe(4);
  });
});
