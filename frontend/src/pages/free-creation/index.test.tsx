import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { StrictMode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FreeCreationPage from './index';

const mocks = vi.hoisted(() => ({
  load: vi.fn(),
  list: vi.fn(),
  create: vi.fn(),
  detail: vi.fn(),
  download: vi.fn(),
  upload: vi.fn(),
  control: vi.fn(),
  navigate: vi.fn(),
  estimate: vi.fn(),
  tenant: 10 as number | undefined,
  user: '1',
  allowed: true,
}));
vi.mock('@umijs/max', () => ({
  request: mocks.estimate,
  history: { push: mocks.navigate },
  useModel: () => ({
    initialState: {
      currentTenantId: mocks.tenant,
      currentUser: { userid: mocks.user },
    },
  }),
  useAccess: () => ({ canUseFreeCreation: mocks.allowed }),
}));
vi.mock('./service', () => ({ loadCreationModels: mocks.load }));
vi.mock('./api', () => ({
  listCreations: mocks.list,
  createCreation: mocks.create,
  getCreation: mocks.detail,
  getCreationDownload: mocks.download,
}));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.upload }));
vi.mock('@/pages/tasks/service', () => ({ controlTask: mocks.control }));

const models = [
  {
    id: 1,
    name: '系统图片模型',
    serviceType: 'IMAGE',
    isDefault: true,
    configStatus: 'configured',
    quality: 'high',
    outputFormat: 'png',
    resolutions: [],
    references: {},
  },
  {
    id: 2,
    name: '短视频模型',
    serviceType: 'VIDEO',
    isDefault: true,
    configStatus: 'configured',
    duration: { min: 4, max: 15, intelligent: true },
    resolutions: ['480p', '720p'],
    references: {
      image: { minCount: 1, maxCount: 9, formats: ['png', 'jpg'] },
    },
  },
  {
    id: 3,
    name: '长视频模型',
    serviceType: 'VIDEO',
    isDefault: false,
    configStatus: 'configured',
    duration: { min: 4, max: 30 },
    resolutions: ['1080p', '4k'],
    references: {
      image: { maxCount: 30 },
      video: { maxCount: 3, formats: ['mp4'] },
    },
  },
];

async function readyButton(name: string | RegExp, scope: Pick<typeof screen, 'getByRole'> = screen) {
  const button = scope.getByRole('button', { name });
  await waitFor(() => {
    expect(button).toBeEnabled();
    expect(button).not.toHaveClass('ant-btn-loading');
  }, { timeout: 3000 });
  return button;
}

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/free-creation');
  mocks.tenant = 10;
  mocks.user = '1';
  mocks.allowed = true;
  localStorage.setItem('currentTenantId', '10');
  mocks.estimate.mockResolvedValue({ success: true, data: {
    estimatedPoints: 10, availablePoints: 100, paidAvailablePoints: 100, sufficient: true,
    lines: [{ label: '生成', points: 10, estimated: true, trialEligible: true }], notice: '',
  } });
  mocks.load.mockResolvedValue(models);
  mocks.list.mockResolvedValue({
    records: [],
    total: 0,
    current: 1,
    pageSize: 10,
  });
  mocks.create.mockImplementation(async (body) => ({
    ...body, id: 1, status: 'QUEUED', creatorId: 1, creatorName: '作者',
    createdAt: '2026-10-23T10:00:00', modelName: '系统图片模型', references: [], outputs: [],
  }));
  mocks.upload.mockReturnValue(new Promise(() => undefined));
});

describe('自由创作页面', () => {
  it('shows the current automatic recovery round on the original generating card', async () => {
    const item = { id: 9, mediaType: 'VIDEO', status: 'RUNNING', phase: 'AVATAR_PREPARING', prompt: '第二轮补充图片', creatorId: 1, creatorName: '作者',
      createdAt: '2026-10-27T19:08:13', modelId: 2, modelName: '视频模型', parameters: {}, references: [], outputs: [],
      avatarRecovery: { status: 'RUNNING', phase: '等待新参考图处理', attempt: 2, maxAttempts: 3 } };
    mocks.list.mockResolvedValue({ records: [item], total: 1, current: 1, pageSize: 10 });
    render(<FreeCreationPage />);
    expect(await screen.findByRole('status', { name: '智能虚拟化中：第二轮补充图片' })).toHaveTextContent('自动恢复第 2 / 3 轮');
  });

  it('shows avatar preparation in the original card and details without a failure or cancel action', async () => {
    const record = { id: 9, mediaType: 'VIDEO', status: 'RUNNING', phase: 'AVATAR_PREPARING', prompt: '连续视频流程', creatorId: 1, creatorName: '作者',
      createdAt: '2026-10-27T10:00:00', modelId: 2, modelName: '视频模型', parameters: {}, references: [], outputs: [], allowedActions: [],
      avatarRecovery: { status: 'RUNNING', phase: '等待素材处理完成' } };
    mocks.list.mockResolvedValue({ records: [record], total: 1, current: 1, pageSize: 10 });
    render(<FreeCreationPage />);
    const preparing = await screen.findByRole('status', { name: '智能虚拟化中：连续视频流程' });
    expect(preparing).toHaveTextContent('智能虚拟化中');
    expect(preparing.closest('article')).not.toHaveTextContent('失败');
    mocks.detail.mockResolvedValueOnce(record);
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findAllByText('智能虚拟化中')).not.toHaveLength(0);
    expect(within(dialog).queryByRole('button', { name: '取消任务' })).not.toBeInTheDocument();
  });

  it('keeps the linked retry accessible from the logical creation details', async () => {
    const record = { id: 9, mediaType: 'VIDEO', status: 'RUNNING', phase: 'QUERY', prompt: '虚拟人像视频', creatorId: 1, creatorName: '作者',
      createdAt: '2026-10-27T10:00:00', modelId: 2, modelName: '视频模型', parameters: {}, references: [], outputs: [],
      avatarRecovery: { status: 'SUCCEEDED', phase: '已创建关联重试视频', retryCreationId: 10, retryStatus: 'RUNNING' } };
    mocks.list.mockResolvedValue({ records: [record], total: 1, current: 1, pageSize: 10 });
    mocks.detail.mockResolvedValueOnce(record).mockResolvedValue({ ...record, id: 10, prompt: '关联视频结果', avatarRecovery: undefined });
    render(<FreeCreationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));
    fireEvent.click(await screen.findByRole('button', { name: '查看关联视频' }));
    await waitFor(() => expect(mocks.detail).toHaveBeenCalledWith(10, expect.any(AbortSignal)));
  });

  it('缩略图未就绪时显示占位，不把高清预览当作列表图片加载', async () => {
    mocks.list.mockResolvedValue({ records: [{
      id: 9, mediaType: 'IMAGE', status: 'SUCCEEDED', prompt: '等待缩略图', creatorId: 1, creatorName: '作者',
      createdAt: '2026-10-24T10:00:00', modelId: 1, modelName: '模型', parameters: {}, references: [],
      outputs: [{ id: 90, previewUrl: '/large-original.png', thumbnailStatus: 'PENDING' }],
    }], total: 1, current: 1, pageSize: 10 });
    render(<FreeCreationPage />);
    expect(await screen.findByRole('status', { name: '缩略图准备中：等待缩略图' })).toBeInTheDocument();
    expect(screen.queryByAltText('生成结果缩略图')).not.toBeInTheDocument();
    expect(document.querySelector('img[src="/large-original.png"]')).toBeNull();
  });

  it('提交后直接插入记录并显示生成占位，保留旧卡片且不重新请求列表', async () => {
    const record = (id: number, status: string) => ({
      id, status, mediaType: 'IMAGE', prompt: `创作 ${id}`, creatorId: 1, creatorName: '作者',
      createdAt: '2026-10-23T10:00:00', modelId: 1, modelName: '系统图片模型',
      parameters: {}, references: [], outputs: status === 'SUCCEEDED' ? [{ id: 10, thumbnailUrl: '/existing.png' }] : [],
    });
    mocks.list.mockResolvedValue({ records: [record(1, 'SUCCEEDED')], total: 1, current: 1, pageSize: 10 });
    mocks.create.mockResolvedValueOnce(record(2, 'RUNNING')).mockResolvedValueOnce(record(3, 'QUEUED'));
    render(<FreeCreationPage />);
    const oldImage = await screen.findByAltText('生成结果缩略图');
    await screen.findByRole('option', { name: '系统图片模型' });
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), { target: { value: '新的灵感' } });
    fireEvent.click(await readyButton('生成图片'));
    const placeholder = await screen.findByRole('status', { name: '生成中：创作 2' });
    expect(within(placeholder).getByText('图片生成中')).toBeInTheDocument();
    expect(placeholder.querySelector('.ant-spin')).not.toBeNull();
    expect(screen.getByAltText('生成结果缩略图')).toBe(oldImage);
    expect(screen.queryByText('正在加载创作记录…')).not.toBeInTheDocument();
    expect(mocks.list).toHaveBeenCalledTimes(1);
    fireEvent.click(await readyButton('生成图片'));
    expect(await screen.findByRole('status', { name: '排队中：创作 3' })).toHaveTextContent('排队等待中');
    expect(screen.getByText('创作记录 · 3')).toBeInTheDocument();
    expect(mocks.list).toHaveBeenCalledTimes(1);
    expect(screen.getByAltText('生成结果缩略图')).toBe(oldImage);
  });

  it('在记录列表底部进入视野时追加下一页，保留已加载记录', async () => {
    let intersect!: IntersectionObserverCallback;
    const observe = vi.fn();
    const disconnect = vi.fn();
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback, options: IntersectionObserverInit) {
        intersect = callback;
        expect(options.root).toBe(screen.getByRole('region', { name: '创作记录列表' }));
      }
      observe = observe;
      disconnect = disconnect;
    });
    const record = (id: number) => ({
      id, mediaType: 'IMAGE', status: 'SUCCEEDED', prompt: `滚动创作 ${id}`, creatorName: '作者',
      createdAt: '2026-10-23T10:00:00', modelId: 1, modelName: '模型', parameters: {}, references: [], outputs: [],
    });
    mocks.list.mockResolvedValueOnce({ records: Array.from({ length: 10 }, (_, i) => record(i + 1)), total: 11, current: 1, pageSize: 10 })
      .mockResolvedValueOnce({ records: [record(11)], total: 11, current: 2, pageSize: 10 });
    try {
      render(<FreeCreationPage />);
      await screen.findByText('滚动创作 10');
      await waitFor(() => expect(observe).toHaveBeenCalled());
      act(() => intersect([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver));
      await screen.findByText('滚动创作 11');
      expect(screen.getByText('滚动创作 1')).toBeInTheDocument();
      expect(screen.getByText('已显示全部创作')).toBeInTheDocument();
      expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2, pageSize: 10 }), expect.any(AbortSignal));
      expect(disconnect).toHaveBeenCalled();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('读取真实模型，保留图片草稿，视频参数随模型切换且不提供虚构首尾帧', async () => {
    render(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    expect(screen.getByText('高质量')).toBeInTheDocument();
    expect(screen.queryByText('2K · 高清')).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
      target: { value: '我的图片灵感' },
    });
    fireEvent.click(screen.getByRole('tab', { name: '视频创作' }));
    const resolution = screen.getByRole('combobox', { name: '分辨率' });
    expect(
      within(resolution)
        .getAllByRole('option')
        .map((item) => item.textContent),
    ).toEqual(['480P', '720P']);
    expect(screen.queryByText('首帧图片')).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox', { name: '分辨率' }), {
      target: { value: '720p' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '生成模型' }), {
      target: { value: '3' },
    });
    expect(screen.getByRole('combobox', { name: '分辨率' })).toHaveValue(
      '1080p',
    );
    expect(
      screen.getByRole('spinbutton', { name: '视频时长' }),
    ).toHaveAttribute('max', '30');
    fireEvent.click(screen.getByRole('tab', { name: '图片创作' }));
    expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue(
      '我的图片灵感',
    );
    await readyButton('生成图片');
  });

  it('个人与团队范围以及媒体筛选按真实契约查询', async () => {
    render(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    fireEvent.click(screen.getByRole('button', { name: '团队素材' }));
    fireEvent.click(screen.getByRole('button', { name: '只看视频' }));
    await waitFor(() =>
      expect(mocks.list).toHaveBeenLastCalledWith(
        expect.objectContaining({
          scope: 'team',
          mediaType: 'VIDEO',
          current: 1,
        }),
        expect.any(AbortSignal),
      ),
    );
    expect(
      screen.getByText('当前团队所有成员的创作会自动出现在这里'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/共享/)).not.toBeInTheDocument();
  });
  it('失败时显示重试，不把读取失败当成没有模型', async () => {
    mocks.load.mockRejectedValueOnce(new Error('模型读取失败'));
    render(<FreeCreationPage />);
    await screen.findByText('模型读取失败');
    fireEvent.click(screen.getByRole('button', { name: '重新加载模型' }));
    await screen.findByRole('option', { name: '系统图片模型' });
    expect(mocks.load).toHaveBeenCalledTimes(2);
  });

  it('没有已启用模型时保持不可提交', async () => {
    mocks.load.mockResolvedValue([]);
    render(<FreeCreationPage />);
    await screen.findByText('暂无已启用的图片模型');
    expect(screen.getByRole('combobox', { name: '生成模型' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '生成图片' })).toBeDisabled();
  });

  it('切换团队销毁旧草稿并取消旧请求，迟到的响应不能覆盖新团队', async () => {
    const view = render(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
      target: { value: '团队甲的灵感' },
    });
    const oldSignal = mocks.load.mock.calls[0][0] as AbortSignal;
    let resolveOld!: (value: typeof models) => void;
    mocks.load.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
    );
    mocks.tenant = 20;
    view.rerender(<FreeCreationPage />);
    expect(oldSignal.aborted).toBe(true);
    mocks.tenant = 30;
    view.rerender(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    await act(async () => resolveOld([{ ...models[0], name: '旧团队响应' }]));
    expect(
      screen.queryByRole('option', { name: '旧团队响应' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue('');
  });

  it('切换模型和用户释放参考图，切换模式保留草稿', async () => {
    const createUrl = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:draft');
    const revokeUrl = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => undefined);
    try {
      const view = render(<FreeCreationPage />);
      await screen.findByRole('option', { name: '系统图片模型' });
      fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
        target: { value: '图片草稿' },
      });
      fireEvent.click(screen.getByRole('tab', { name: '视频创作' }));
      fireEvent.change(
        within(screen.getByRole('tabpanel')).getByLabelText('选择参考图片'),
        {
          target: {
            files: [new File(['x'], 'frame.png', { type: 'image/png' })],
          },
        },
      );
      expect(createUrl).toHaveBeenCalledTimes(1);
      fireEvent.change(screen.getByRole('combobox', { name: '生成模型' }), {
        target: { value: '3' },
      });
      expect(revokeUrl).toHaveBeenCalledTimes(1);
      expect(screen.queryByAltText('frame.png')).not.toBeInTheDocument();
      fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
        target: { value: '视频草稿' },
      });
      fireEvent.click(screen.getByRole('tab', { name: '图片创作' }));
      expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue(
        '图片草稿',
      );
      fireEvent.change(
        within(screen.getByRole('tabpanel')).getByLabelText('选择参考图片'),
        {
          target: {
            files: [new File(['x'], 'picture.png', { type: 'image/png' })],
          },
        },
      );
      mocks.user = '2';
      view.rerender(<FreeCreationPage />);
      await screen.findByRole('option', { name: '系统图片模型' });
      expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue('');
      expect(revokeUrl).toHaveBeenCalledTimes(2);
      expect(screen.queryByAltText('picture.png')).not.toBeInTheDocument();
    } finally {
      createUrl.mockRestore();
      revokeUrl.mockRestore();
    }
  });

  it('没有有效团队访问权限或缺少团队时不请求模型', async () => {
    mocks.allowed = false;
    const view = render(<FreeCreationPage />);
    expect(screen.getByText('请先登录并选择有效的创作团队')).toBeInTheDocument();
    mocks.allowed = true;
    mocks.tenant = undefined;
    view.rerender(<FreeCreationPage />);
    expect(screen.getByText('请先选择创作团队')).toBeInTheDocument();
    await waitFor(() => expect(mocks.load).not.toHaveBeenCalled());
  });
  it('StrictMode下可提交真实参数，网络重试沿用requestId且切换团队中断旧提交', async () => {
    const view = render(
      <StrictMode>
        <FreeCreationPage />
      </StrictMode>,
    );
    await screen.findByRole('option', { name: '系统图片模型' });
    fireEvent.click(screen.getByRole('button', { name: '生成图片' }));
    expect(screen.getByRole('button', { name: '生成图片' })).toBeDisabled();
    expect(mocks.create).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
      target: { value: '生成我的场景' },
    });
    mocks.create.mockRejectedValueOnce(new Error('网络连接中断'));
    await readyButton('生成图片');
    fireEvent.click(screen.getByRole('button', { name: '生成图片' }));
    await screen.findByText('网络连接中断');
    const first = mocks.create.mock.calls[0][0];
    expect(first).toMatchObject({
      mediaType: 'IMAGE',
      modelId: 1,
      prompt: '生成我的场景',
      parameters: { count: 1, quality: 'high', outputFormat: 'png' },
      references: [],
    });
    expect(mocks.create.mock.calls[0][1].aborted).toBe(false);
    mocks.create.mockReturnValueOnce(new Promise(() => undefined));
    await readyButton('生成图片');
    fireEvent.click(screen.getByRole('button', { name: '生成图片' }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
    expect(mocks.create.mock.calls[1][0].requestId).toBe(first.requestId);
    const signal = mocks.create.mock.calls[1][1];
    mocks.tenant = 99;
    view.rerender(
      <StrictMode>
        <FreeCreationPage />
      </StrictMode>,
    );
    expect(signal.aborted).toBe(true);
    await screen.findByRole('option', { name: '系统图片模型' });
    expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue('');
  });
  it('复用会重新授权详情和检查模型，只回填草稿而不生成', async () => {
    const record = {
      id: 12,
      mediaType: 'IMAGE',
      status: 'SUCCEEDED',
      prompt: '同事的灵感',
      creatorId: 9,
      creatorName: '团队成员',
      createdAt: '2026-10-23T10:00:00',
      modelId: 1,
      modelName: '系统图片模型',
      parameters: { quality: 'high', outputFormat: 'png', count: 1 },
      references: [
        {
          id: 1,
          referenceId: 1,
          kind: 'IMAGE',
          fileName: '团队参考图',
          previewUrl: '/preview/1',
          thumbnailUrl: '/thumbnail/1',
        },
      ],
      outputs: [],
    };
    mocks.list.mockResolvedValue({
      records: [record],
      total: 1,
      current: 1,
      pageSize: 10,
    });
    mocks.detail.mockResolvedValue(record);
    render(<FreeCreationPage />);
    await screen.findByText('同事的灵感');
    fireEvent.click(screen.getByRole('button', { name: '复用参数' }));
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue(
        '同事的灵感',
      ),
    );
    expect(mocks.detail).toHaveBeenCalledWith(12, expect.any(AbortSignal));
    expect(mocks.create).not.toHaveBeenCalled();
    await readyButton('生成图片');
    fireEvent.click(screen.getByRole('button', { name: '生成图片' }));
    await waitFor(() =>
      expect(mocks.create).toHaveBeenCalledWith(
        expect.objectContaining({
          references: [{ kind: 'IMAGE', referenceId: 1 }],
        }),
        expect.any(AbortSignal),
      ),
    );
  });
  it('OpenAI only shows one-image generation while other providers retain all counts', async () => {
    mocks.load.mockResolvedValue([
      { ...models[0], providerCode: 'OpenAI' },
      {
        ...models[0],
        id: 99,
        name: '其他图片模型',
        providerCode: 'VOLCENGINE_ARK',
      },
    ]);
    render(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    expect(screen.getByRole('button', { name: '生成1张' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(
      screen.queryByRole('button', { name: '生成2张' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '生成4张' }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox', { name: '生成模型' }), {
      target: { value: '99' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成4张' }));
    expect(screen.getByRole('button', { name: '生成4张' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    fireEvent.change(screen.getByRole('combobox', { name: '生成模型' }), {
      target: { value: '1' },
    });
    expect(screen.getByRole('button', { name: '生成1张' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(
      screen.queryByRole('button', { name: '生成4张' }),
    ).not.toBeInTheDocument();
  });
  it('opens task-center deep links and reuses one control key after a lost retry response', async () => {
    window.history.replaceState({}, '', '/free-creation?creation=12');
    const record = {
      id: 12, taskKey: 'FREE_CREATION:12', mediaType: 'IMAGE', status: 'FAILED',
      prompt: '失败的自由创作', creatorId: 1, creatorName: '作者', createdAt: '2026-10-23T10:00:00',
      modelId: 1, modelName: '系统图片模型', parameters: {}, references: [], outputs: [], allowedActions: ['RETRY'],
    };
    mocks.detail.mockImplementation(async (id: number) => id === 12 ? record : { ...record, id, taskKey: `FREE_CREATION:${id}`, status: 'QUEUED', allowedActions: ['CANCEL'] });
    mocks.control.mockRejectedValueOnce(new Error('响应中断')).mockResolvedValue({ taskKey: 'FREE_CREATION:13' });
    render(<FreeCreationPage />);
    const modal = await screen.findByRole('dialog');
    await readyButton(/重\s*试/, within(modal));
    fireEvent.click(await within(modal).findByRole('button', { name: /重\s*试/ }));
    await within(modal).findByText('响应中断');
    await readyButton(/重\s*试/, within(modal));
    fireEvent.click(within(modal).getByRole('button', { name: /重\s*试/ }));
    await within(modal).findByRole('button', { name: '取消任务' });
    expect(mocks.control).toHaveBeenCalledTimes(2);
    expect(mocks.control.mock.calls[0]).toEqual([10, 'FREE_CREATION:12', 'RETRY', expect.any(String), expect.any(AbortSignal)]);
    expect(mocks.control.mock.calls[0][3]).toBe(mocks.control.mock.calls[1][3]);
    expect(mocks.detail).toHaveBeenCalledWith(13, expect.any(AbortSignal));
    fireEvent.click(within(modal).getByRole('button', { name: '在任务中心查看' }));
    expect(mocks.navigate).toHaveBeenCalledWith('/tasks?task=FREE_CREATION%3A13');
  });

  it('does not offer control actions for another creator', async () => {
    window.history.replaceState({}, '', '/free-creation?creation=19');
    mocks.detail.mockResolvedValue({
      id: 19, mediaType: 'IMAGE', status: 'FAILED', prompt: '其他人的创作', creatorId: 9,
      creatorName: '其他作者', createdAt: '2026-10-23T10:00:00', modelId: 1, modelName: '系统图片模型',
      parameters: {}, references: [], outputs: [], allowedActions: [],
    });
    render(<FreeCreationPage />);
    const modal = await screen.findByRole('dialog');
    await within(modal).findByRole('button', { name: '复用参数' });
    expect(within(modal).queryByRole('button', { name: /重试|取消任务|重新生成/ })).not.toBeInTheDocument();
    expect(mocks.control).not.toHaveBeenCalled();
  });

  it('keeps generation disabled until a quote for the current prompt and count returns', async () => {
    const pending: { data: any; resolve: (value: unknown) => void }[] = [];
    mocks.estimate.mockImplementation((_url, options) => new Promise((resolve) => pending.push({ data: options.data, resolve })));
    render(<FreeCreationPage />);
    await screen.findByRole('option', { name: '系统图片模型' });
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), { target: { value: '草稿一' } });
    const submit = screen.getByRole('button', { name: '生成图片' });
    expect(submit).toBeDisabled();
    await waitFor(() => expect(pending).toHaveLength(1));
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), { target: { value: '草稿二' } });
    fireEvent.click(screen.getByRole('button', { name: '生成4张' }));
    await waitFor(() => expect(pending).toHaveLength(2));
    expect(pending[1].data.operations[0]).toMatchObject({ url: '/api/free-creations', data: { prompt: '草稿二', parameters: { count: 4 } } });
    await act(async () => pending[0].resolve({ success: true, data: { estimatedPoints: 10, sufficient: true, lines: [] } }));
    expect(submit).toBeDisabled();
    expect(mocks.create).not.toHaveBeenCalled();
    await act(async () => pending[1].resolve({ success: true, data: { estimatedPoints: 40, sufficient: true, lines: [] } }));
    expect(submit).toBeEnabled();
    expect(submit).toHaveAccessibleDescription(/预计 40 积分/);
    fireEvent.click(submit);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ prompt: '草稿二', parameters: expect.objectContaining({ count: 4 }) }), expect.any(AbortSignal)));
  });
});

it('saves the original as a file and releases the temporary URL', async () => {
  mocks.list.mockResolvedValue({ records: [{
    id: 14, mediaType: 'IMAGE', status: 'SUCCEEDED', prompt: '下载作品',
    creatorId: 1, creatorName: '作者', createdAt: '2026-10-28T10:00:00',
    modelId: 1, modelName: '图片模型', parameters: {}, references: [], outputs: [{ id: 20 }],
  }], total: 1, current: 1, pageSize: 10 });
  mocks.download.mockResolvedValue({ url: 'blob:creation-download', fileName: '作品.png' });
  const revoke = vi.fn();
  vi.stubGlobal('URL', class extends URL { static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  try {
    render(<FreeCreationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '下载' }));
    await waitFor(() => expect(click).toHaveBeenCalledOnce());
    expect(mocks.download).toHaveBeenCalledWith(14, 20, expect.any(AbortSignal));
    expect(click.mock.instances[0]).toHaveProperty('href', 'blob:creation-download');
    expect(click.mock.instances[0]).toHaveProperty('download', '作品.png');
    expect(click.mock.instances[0]).toHaveProperty('target', '');
    expect(revoke).toHaveBeenCalledWith('blob:creation-download');
    expect(document.querySelector('a[download]')).toBeNull();
  } finally { click.mockRestore(); vi.unstubAllGlobals(); }
});
