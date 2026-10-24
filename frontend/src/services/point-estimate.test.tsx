import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { submitAssetNormalization } from '@/pages/projects/production-workbench/normalization-service';
import {
  confirmPointEstimates,
  formatPoints,
  PointEstimateCancelledError,
  pointEstimatedRequest,
  registerPointEstimateModal,
  requiresPointEstimate,
} from './point-estimate';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  confirm: vi.fn(),
  update: vi.fn(),
  destroy: vi.fn(),
}));
vi.mock('@umijs/max', () => ({ request: mocks.request }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ modal: { confirm: mocks.confirm } }) },
}));

const quote = {
  estimatedPoints: 12.125,
  availablePoints: 100,
  paidAvailablePoints: 80,
  sufficient: true,
  lines: [
    {
      label: '生成图片',
      points: 12.125,
      estimated: false,
      trialEligible: true,
    },
  ],
  notice: '实际按模型用量结算',
};
const flush = () =>
  act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });

describe('point estimates before submission', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('currentTenantId', '7');
    mocks.confirm.mockReturnValue({
      update: mocks.update,
      destroy: mocks.destroy,
    });
    registerPointEstimateModal({ confirm: mocks.confirm });
    mocks.request
      .mockResolvedValueOnce({ success: true, data: quote })
      .mockResolvedValue({ data: { id: 9 } });
  });

  it('does not submit or reserve anything before explicit confirmation', async () => {
    const pending = pointEstimatedRequest('/api/projects/3/ai-image-tasks', {
      method: 'POST',
      data: { modelId: 12, imageCount: 4 },
    });
    expect(mocks.confirm.mock.calls[0][0].okButtonProps.disabled).toBe(true);
    await flush();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request).toHaveBeenCalledWith(
      '/api/tenants/7/points/estimate',
      expect.objectContaining({
        data: {
          operations: [
            {
              url: '/api/projects/3/ai-image-tasks',
              data: { modelId: 12, imageCount: 4 },
            },
          ],
        },
      }),
    );
    render(mocks.update.mock.calls[0][0].content);
    expect(screen.getByText('12')).toBeInTheDocument();
    mocks.confirm.mock.calls[0][0].onOk();
    await expect(pending).resolves.toEqual({ data: { id: 9 } });
    expect(mocks.request).toHaveBeenCalledTimes(2);
  });

  it('requires an approved quote before submitting a direct script review', async () => {
    const data = { content: '第一集\n林晚走进门。', selectedDimensions: ['台词合理性'], requestId: 'review-submission-0001' };
    const pending = pointEstimatedRequest('/api/script-review/submissions', { method: 'POST', data });
    await flush();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request.mock.calls[0][0]).toBe('/api/tenants/7/points/estimate');
    mocks.confirm.mock.calls[0][0].onOk();
    await pending;
    expect(mocks.request).toHaveBeenLastCalledWith('/api/script-review/submissions', expect.objectContaining({ method: 'POST', data }));
  });

  it('confirms a quote before submitting a character prompt similarity check', async () => {
    const url = '/api/projects/56/character-prompt-similarity';
    const pending = pointEstimatedRequest(url, { method: 'POST' });
    await flush();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request.mock.calls[0][0]).toBe('/api/tenants/7/points/estimate');
    mocks.confirm.mock.calls[0][0].onOk();
    await pending;
    expect(mocks.request).toHaveBeenLastCalledWith(url, expect.objectContaining({ method: 'POST' }));
  });

  it('keeps normalization behind point confirmation after moving its button', async () => {
    const pending = submitAssetNormalization(56);
    await flush();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request.mock.calls[0][0]).toBe('/api/tenants/7/points/estimate');
    mocks.confirm.mock.calls[0][0].onCancel();
    await expect(pending).rejects.toBeInstanceOf(PointEstimateCancelledError);
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });

  it('cancels without a paid request, even while the estimate is loading', async () => {
    let resolve: (value: unknown) => void = () => undefined;
    mocks.request.mockReset().mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const pending = pointEstimatedRequest('/api/projects/3/ai-video-tasks', {
      method: 'POST',
    });
    const rejected = expect(pending).rejects.toBeInstanceOf(
      PointEstimateCancelledError,
    );
    mocks.confirm.mock.calls[0][0].onCancel();
    await rejected;
    resolve({ success: true, data: quote });
    await flush();
    expect(mocks.update).not.toHaveBeenCalled();
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });

  it('blocks paid submission when a preview fails or funds are insufficient', async () => {
    mocks.request
      .mockReset()
      .mockRejectedValueOnce(new Error('模型价格未配置'));
    await expect(
      pointEstimatedRequest('/api/projects/3/ai-image-tasks', {
        method: 'POST',
      }),
    ).rejects.toThrow('模型价格未配置');
    expect(mocks.request).toHaveBeenCalledTimes(1);
    mocks.request.mockResolvedValueOnce({
      success: true,
      data: { ...quote, sufficient: false },
    });
    const pending = pointEstimatedRequest('/api/projects/3/ai-image-tasks', {
      method: 'POST',
    });
    await flush();
    expect(mocks.update.mock.calls.at(-1)![0].okButtonProps.disabled).toBe(
      true,
    );
    await expect(mocks.confirm.mock.calls.at(-1)![0].onOk()).rejects.toThrow();
    const rejected = expect(pending).rejects.toBeInstanceOf(
      PointEstimateCancelledError,
    );
    mocks.confirm.mock.calls.at(-1)![0].onCancel();
    await rejected;
    expect(mocks.request).toHaveBeenCalledTimes(2);
  });

  it.each([
    '/api/free-creations','http', 'business'])(
    'preserves the backend reason for a %s estimate failure',
    async (kind) => {
      const reason =
        '当前分镜没有可用的参考图片，请先在「设定」中生成资产图片，或绑定一张已生成的参考图片。';
      const error = Object.assign(
        new Error('Request failed with status code 400'),
        kind === 'http'
          ? {
              response: {
                status: 400,
                data: { success: false, errorMessage: reason },
              },
            }
          : { info: { errorMessage: reason } },
      );
      mocks.request.mockReset().mockRejectedValueOnce(error);

      await expect(
        pointEstimatedRequest('/api/projects/56/ai-video-tasks', {
          method: 'POST',
          data: { storyboardId: 3454 },
        }),
      ).rejects.toThrow(`积分预估失败，未提交任务：${reason}`);
      expect(mocks.request).toHaveBeenCalledTimes(1);
      expect(mocks.destroy).toHaveBeenCalledTimes(1);
      expect(mocks.update).not.toHaveBeenCalled();
    },
  );

  it('sends every batch item in one estimate and only rounds points for display', async () => {
    const operations = [
      { url: '/api/projects/3/ai-video-tasks', data: { storyboardId: 1 } },
      { url: '/api/projects/3/ai-video-tasks', data: { storyboardId: 2 } },
    ];
    const pending = confirmPointEstimates(operations);
    await flush();
    expect(mocks.request.mock.calls[0][1].data.operations).toEqual(operations);
    mocks.confirm.mock.calls[0][0].onOk();
    expect(await pending).toEqual(quote);
    expect(mocks.confirm).toHaveBeenCalledTimes(1);
    expect(formatPoints(0.00000001)).toBe('0');
    expect(formatPoints(12.49)).toBe('12');
    expect(formatPoints(12.5)).toBe('13');
    expect(formatPoints(-12.5)).toBe('-13');
    expect(formatPoints(-0.1)).toBe('0');
    expect(formatPoints(1234.5)).toBe('1,235');
  });

  it.each([
    '/api/free-creations',
    '/api/projects/1/script-analysis/current/reanalyze',
    '/api/projects/1/script-analysis/current/retry/EPISODE_SUMMARY',
    '/api/projects/1/script-analysis/versions/2/reanalyze',
    '/api/projects/1/script-analysis/current/regenerate-episodes',
    '/api/projects/1/episodes/2/summary/regenerate',
    '/api/projects/1/episodes/2/assets/regenerate',
    '/api/projects/1/asset-reextraction',
    '/api/projects/1/asset-normalization',
    '/api/projects/1/prop-simplification',
    '/api/projects/1/asset-image-batches',
    '/api/projects/1/asset-image-batches/2/retry-failed',
    '/api/projects/1/storyboard-batches',
    '/api/projects/1/storyboards/ai-breakdown',
    '/api/projects/1/ai-image-tasks/2/regenerate',
    '/api/projects/1/ai-video-tasks/2/regenerate',
    '/api/script-review/projects/1/tasks',
    '/api/script-review/tasks/1/retry',
    '/api/video-script-decomposition/batches',
    '/api/video-script-decomposition/episodes/1/retry',
    '/api/tenants/1/production-tasks/IMAGE%3A2/regenerate',
    '/api/tenants/1/ai-executions/2/retry',
  ])('covers the paid action %s', (url) =>
    expect(requiresPointEstimate(url, { method: 'POST' })).toBe(true));

  it('does not estimate viewing, polling, uploads, local composition or cancellation', () => {
    for (const url of [
      '/api/free-creations/1/reuse',
      '/api/projects/1/ai-video-tasks/2/poll',
      '/api/projects/1/ai-image-tasks/2/cancel',
      '/api/projects/1/episode-compose-tasks',
      '/api/projects/1/ai-voice-tasks',
    ]) {
      expect(requiresPointEstimate(url, { method: 'POST' })).toBe(false);
    }
    expect(requiresPointEstimate('/api/projects/1/ai-image-tasks')).toBe(false);
    expect(
      requiresPointEstimate('/api/projects', { method: 'POST', data: {} }),
    ).toBe(false);
    expect(
      requiresPointEstimate('/api/projects', {
        method: 'POST',
        data: { initialScriptContent: '剧本正文' },
      }),
    ).toBe(true);
  });
  it('free creation waits for explicit confirmation and aborts after a team switch', async () => {
    const abort = new AbortController();
    const pending = pointEstimatedRequest('/api/free-creations', { method: 'POST', data: { prompt: '自由创作' }, signal: abort.signal });
    await flush();
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.request.mock.calls[0][1].data.operations[0].url).toBe('/api/free-creations');
    abort.abort();
    mocks.confirm.mock.calls[0][0].onOk();
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });});
