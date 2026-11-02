import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());

vi.mock('@umijs/max', () => ({ request }));

import {
  queryStoryboardEditorPage,
  renameScriptElement,
  renameVisualVariant,
  queryStoryboardEpisodeDetails,
  queryStoryboardWorkspace,
  createAssetImageBatch,
  downloadEpisodeVideoVersion,
  queryAssetImageBatch,
  queryAssetImageBatchPreflight,
  queryAiImageTask,
  queryStoryboardBatch,
  queryAssetVisualWorkspace,
} from './service';

describe('cancellable storyboard reads', () => {
  beforeEach(() => request.mockReset());

  it('forwards cancellation to batch progress requests', async () => {
    const signal = new AbortController().signal;
    await queryStoryboardBatch(5, 71, signal);
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-batches/71',
      expect.objectContaining({ signal, skipErrorHandler: true }));
  });

  it('forwards cancellation to image task requests', async () => {
    const signal = new AbortController().signal;
    await queryAiImageTask(5, 72, signal);
    expect(request).toHaveBeenCalledWith('/api/projects/5/ai-image-tasks/72',
      expect.objectContaining({ signal, skipErrorHandler: true }));
  });

  it('forwards cancellation without losing visual workspace query parameters', async () => {
    const signal = new AbortController().signal;
    await queryAssetVisualWorkspace(5, 'CHARACTER', 11, { current: 2 }, signal);
    expect(request).toHaveBeenCalledWith('/api/projects/5/script-elements/CHARACTER/11/visual-workspace',
      expect.objectContaining({ params: { current: 2 }, signal, skipErrorHandler: true }));
  });
});

// Transport contract tests; confirmation behaviour is covered in point-estimate.test.tsx.
vi.mock('@/services/point-estimate', async () => {
  const { request } = await import('@umijs/max');
  return { pointEstimatedRequest: request };
});

describe('asset image batch service', () => {
  const values = {
    assetType: 'CHARACTER' as const,
    assetIds: [11, 12],
    mode: 'PRIMARY' as const,
    modelId: 8,
    aspectRatio: '16:9',
    imageCount: 1,
  };

  beforeEach(() => request.mockReset());

  it('preflights selected assets', async () => {
    await queryAssetImageBatchPreflight(5, values);

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches/preflight',
      expect.objectContaining({ method: 'POST', data: values }),
    );
  });

  it('submits an idempotent batch', async () => {
    await createAssetImageBatch(5, values, 'batch-key');

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches',
      expect.objectContaining({
        method: 'POST',
        data: values,
        headers: expect.objectContaining({ 'Idempotency-Key': 'batch-key' }),
      }),
    );
  });

  it('queries batch progress', async () => {
    await queryAssetImageBatch(5, 71);

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches/71',
    );
  });
});

describe('downloadEpisodeVideoVersion', () => {
  beforeEach(() => request.mockReset());

  it('creates a download-center task for the selected episode version', async () => {
    const task = { id: 'episode-download', status: 'QUEUED' };
    request.mockResolvedValue({ success: true, data: task });
    await expect(downloadEpisodeVideoVersion(5, 11)).resolves.toEqual(task);
    expect(request).toHaveBeenCalledExactlyOnceWith('/api/downloads',
      expect.objectContaining({ method: 'POST', data: {
        name: '成片_11', sources: [{ kind: 'EPISODE_VERSION', projectId: 5, id: 11 }],
      } }));
  });
});

describe('focused storyboard editor transport', () => {
  beforeEach(() => request.mockReset());

  it('requests the focused editor page with episode pagination', async () => {
    await queryStoryboardEditorPage(5, { episodeNo: 2, current: 3, pageSize: 20 });
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-editor-page', {
      params: { episodeNo: 2, current: 3, pageSize: 20 },
    });
  });

  it('forwards the optional deep-link shot for current-script page resolution', async () => {
    await queryStoryboardEditorPage(5, { episodeNo: 2, current: 4, pageSize: 20, storyboardId: 201 });
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-editor-page', {
      params: { episodeNo: 2, current: 4, pageSize: 20, storyboardId: 201 },
    });
  });

  it('requests independent episode details with cancellation support', async () => {
    const controller = new AbortController();
    await queryStoryboardEpisodeDetails(5, 12, controller.signal);
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-episodes/12/details', {
      signal: controller.signal,
    });
  });

  it('preserves the original workspace endpoint for other consumers', async () => {
    await queryStoryboardWorkspace(5);
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-workspace', { params: {} });
  });
});


describe('name-only updates', () => {
  beforeEach(() => request.mockReset());
  it('renames an asset without submitting other fields', async () => {
    await renameScriptElement(5, 'SCENE', 12, '夜间车站');
    expect(request).toHaveBeenCalledWith('/api/projects/5/script-elements/SCENE/12/name',
      expect.objectContaining({ method: 'PUT', data: { name: '夜间车站' }, skipErrorHandler: true }));
  });
  it('renames a variant without submitting prompts', async () => {
    await renameVisualVariant(5, 22, '冬装');
    expect(request).toHaveBeenCalledWith('/api/projects/5/visual-variants/22/name',
      expect.objectContaining({ method: 'PUT', data: { name: '冬装' }, skipErrorHandler: true }));
  });
});
