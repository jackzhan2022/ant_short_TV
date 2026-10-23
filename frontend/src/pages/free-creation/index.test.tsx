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
  tenant: 10 as number | undefined,
  user: '1',
  allowed: true,
}));
vi.mock('@umijs/max', () => ({
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

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/free-creation');
  mocks.tenant = 10;
  mocks.user = '1';
  mocks.allowed = true;
  mocks.load.mockResolvedValue(models);
  mocks.list.mockResolvedValue({
    records: [],
    total: 0,
    current: 1,
    pageSize: 10,
  });
  mocks.create.mockResolvedValue({ id: 1 });
  mocks.upload.mockReturnValue(new Promise(() => undefined));
});

describe('自由创作页面', () => {
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
    expect(screen.getByRole('button', { name: '生成图片' })).toBeEnabled();
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

  it('切换模型和用户释放参考图，新建只清空当前模式草稿', async () => {
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
      fireEvent.click(screen.getByRole('button', { name: '新建创作' }));
      expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue('');
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
    expect(await screen.findByText('请先输入创意描述')).toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '创意描述' }), {
      target: { value: '生成我的场景' },
    });
    mocks.create.mockRejectedValueOnce(new Error('网络连接中断'));
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
    fireEvent.click(screen.getByRole('button', { name: '生成图片' }));
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
    fireEvent.click(await within(modal).findByRole('button', { name: /重\s*试/ }));
    await within(modal).findByText('响应中断');
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
});
