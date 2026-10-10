import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ShortDramaCreationPage from './index';

const mocks = vi.hoisted(() => ({
  createProject: vi.fn(),
  startProjectCoverUpload: vi.fn(),
  bindProjectCover: vi.fn(),
  getCurrentTenantId: vi.fn(),
  historyPush: vi.fn(),
  queryInspirationCreationDetail: vi.fn(),
  queryInspirationCreations: vi.fn(),
  queryStyleLibrary: vi.fn(),
  queryStyleCategories: vi.fn(),
  queryTenantMembers: vi.fn(),
  queryManagedInspirations: vi.fn(),
  currentAccess: 'admin',
}));

const intersectionObservers = vi.hoisted(
  () => [] as IntersectionObserverCallback[],
);

const observedTargets = vi.hoisted(
  () => [] as { callback: IntersectionObserverCallback; target: Element }[],
);

class MockIntersectionObserver {
  constructor(private readonly callback: IntersectionObserverCallback) {
    intersectionObservers.push(callback);
  }

  disconnect() {}

  observe(target: Element) {
    observedTargets.push({ callback: this.callback, target });
  }

  unobserve() {}

  takeRecords() {
    return [];
  }

  root = null;

  rootMargin = '';

  thresholds = [];
}

vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
  useModel: () => ({
    initialState: { currentUser: { access: mocks.currentAccess } },
  }),
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));
vi.mock('../style-library/service', () => ({
  queryStyleCategories: mocks.queryStyleCategories,
}));

vi.mock('./service', () => ({
  createProject: mocks.createProject,
  startProjectCoverUpload: mocks.startProjectCoverUpload,
  bindProjectCover: mocks.bindProjectCover,
  queryInspirationCreationDetail: mocks.queryInspirationCreationDetail,
  queryInspirationCreations: mocks.queryInspirationCreations,
  queryStyleLibrary: mocks.queryStyleLibrary,
  queryTenantMembers: mocks.queryTenantMembers,
  queryManagedInspirations: mocks.queryManagedInspirations,
  createManagedInspiration: vi.fn(),
  updateManagedInspiration: vi.fn(),
  updateManagedInspirationStatus: vi.fn(),
  reorderManagedInspirations: vi.fn(),
  deleteManagedInspiration: vi.fn(),
}));

vi.mock('./ScriptContentImport', () => ({
  default: ({
    onImport,
  }: {
    onImport: (content: string, label: string) => void;
  }) => (
    <button
      onClick={() => onImport('引用的版本内容', '已引用：审核剧本 A · 版本 2')}
      type="button"
    >
      模拟导入
    </button>
  ),
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, title }: any) => (
    <main>
      <h1>{title}</h1>
      {children}
    </main>
  ),
}));

const enterStyleFooter = async () => {
  await waitFor(() => expect(observedTargets.some(
    (observer) => observer.target.getAttribute('aria-label') === '风格加载状态',
  )).toBe(true));
  const observer = observedTargets.filter(
    (item) => item.target.getAttribute('aria-label') === '风格加载状态',
  ).at(-1);
  if (!observer) throw new Error('风格加载观察器未注册');
  act(() => observer.callback(
    [{ target: observer.target, isIntersecting: true }] as IntersectionObserverEntry[],
    {} as IntersectionObserver,
  ));
};

const laterStyle = {
  id: 13, externalId: '13', name: '后续页风格', category: '未分组',
  description: '后续页', imageUrl: '/later-style.png',
};

describe('ShortDramaCreationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    intersectionObservers.length = 0;
    observedTargets.length = 0;
    mocks.getCurrentTenantId.mockReturnValue(9);
    mocks.currentAccess = 'admin';
    mocks.queryStyleCategories.mockResolvedValue({
      data: ['3D风格', '未分组'],
    });
    mocks.startProjectCoverUpload.mockResolvedValue({
      attempt: Promise.resolve({ sessionToken: 'cover-session', objectKey: 'uploads/cover.png' }),
      cancel: vi.fn(),
    });
    mocks.bindProjectCover.mockResolvedValue({ data: { status: 'PENDING' } });
    vi.mocked(URL.createObjectURL).mockReturnValue('blob:cover-preview');
    mocks.queryManagedInspirations.mockResolvedValue({
      data: { records: [], total: 0, current: 1, pageSize: 100 },
    });
    mocks.queryTenantMembers.mockResolvedValue({
      data: [{ userId: 1, nickname: '负责人A' }],
    });
    mocks.queryInspirationCreations.mockImplementation(({ page = 1 } = {}) =>
      Promise.resolve({
        data:
          page === 1
            ? {
                records: [
                  {
                    id: 101,
                    externalId: '864900000000000001',
                    creationType: 'IMAGE',
                    taskType: 'STORY',
                    title: '线上灵感 A',
                    authorName: '管理员',
                    url: '/api/inspiration-creations/101/file',
                    thumbnailUrl: '/api/inspiration-creations/101/thumbnail',
                    mimeType: 'image/png',
                    sortOrder: 1,
                    sourceCreatedAt: '2026-08-22T10:00:00',
                  },
                  {
                    id: 102,
                    externalId: '864900000000000002',
                    creationType: 'IMAGE',
                    taskType: 'STORY',
                    title: '线上灵感 B',
                    authorName: '管理员',
                    url: '/api/inspiration-creations/102/file',
                    thumbnailUrl: '/api/inspiration-creations/102/thumbnail',
                    mimeType: 'image/png',
                    sortOrder: 2,
                    sourceCreatedAt: '2026-08-22T10:10:00',
                  },
                ],
                total: 9,
                current: 1,
                pageSize: 8,
              }
            : {
                records: [
                  {
                    id: 103,
                    externalId: '864900000000000003',
                    creationType: 'IMAGE',
                    taskType: 'STORY',
                    title: '线上灵感 C',
                    authorName: '管理员',
                    url: '/api/inspiration-creations/103/file',
                    thumbnailUrl: '/api/inspiration-creations/103/thumbnail',
                    mimeType: 'image/png',
                    sortOrder: 3,
                  },
                ],
                total: 9,
                current: 2,
                pageSize: 8,
              },
      }),
    );
    mocks.queryInspirationCreationDetail.mockResolvedValue({
      data: {
        id: 101,
        externalId: '864900000000000001',
        creationType: 'IMAGE',
        taskType: 'STORY',
        title: '线上灵感 A',
        authorName: '管理员',
        url: '/api/inspiration-creations/101/file',
        thumbnailUrl: '/api/inspiration-creations/101/thumbnail',
        mimeType: 'image/png',
        sortOrder: 1,
        sourceCreatedAt: '2026-08-22T10:00:00',
        detailJson: { prompt: '被误解的女主多年后带着证据回归。' },
      },
    });
    mocks.queryStyleLibrary.mockResolvedValue({
      data: {
        current: 1,
        pageSize: 12,
        total: 25,
        data: [
          {
            id: 1,
            externalId: '864621266010645040',
            name: '3D风格-高清真实渲染',
            category: '3D风格',
            description: '高清 3D 真实渲染风格',
            imageUrl: '/api/style-library/images/864621266010645040',
          },
        ],
      },
    });
  });

  it('shows the full online inspiration gallery on the first page', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    expect(
      screen.getByRole('heading', { name: '今天想创作 什么样的故事?' }),
    ).toBeInTheDocument();
    expect(screen.getByText('灵感广场')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '跳过上传，创建空白剧本' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('线上灵感 A')).toBeInTheDocument();
    expect(screen.getByText('线上灵感 B')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /海外剧/ }));
    expect(screen.getByText('线上灵感 A')).toBeInTheDocument();
    expect(screen.getByText('线上灵感 B')).toBeInTheDocument();
    expect(screen.queryByText('豪门继承人归来')).not.toBeInTheDocument();
    expect(screen.queryByText('3D风格-高清真实渲染')).not.toBeInTheDocument();
    expect(mocks.queryInspirationCreations).toHaveBeenCalledWith({
      page: 1,
      pageSize: 8,
    });
    expect(screen.getByAltText('线上灵感 A')).not.toHaveAttribute('src');
    act(() => {
      intersectionObservers[0](
        [{ isIntersecting: true }] as IntersectionObserverEntry[],
        {} as IntersectionObserver,
      );
    });
    expect(screen.getByAltText('线上灵感 A')).toHaveAttribute(
      'src',
      '/api/inspiration-creations/101/thumbnail',
    );
    expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({
      current: 1,
      pageSize: 12,
    });
  });

  it('shows management only to administrators and opens the drawer', async () => {
    mocks.queryManagedInspirations.mockResolvedValue({
      data: {
        records: [
          {
            id: 201,
            title: '待压缩素材',
            promptText: '测试提示词',
            publishStatus: 'UNPUBLISHED',
            mediaType: 'IMAGE',
            url: '/api/inspiration-management/201/file',
          },
        ],
        total: 1,
        current: 1,
        pageSize: 100,
      },
    });
    const { unmount } = render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    fireEvent.click(screen.getByRole('button', { name: '管理灵感广场' }));
    expect(await screen.findByText('灵感广场管理')).toBeInTheDocument();
    expect(mocks.queryManagedInspirations).toHaveBeenCalled();
    expect(
      screen.getByLabelText('待压缩素材 缩略图未就绪'),
    ).toBeInTheDocument();
    unmount();
    mocks.currentAccess = 'user';
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    expect(
      screen.queryByRole('button', { name: '管理灵感广场' }),
    ).not.toBeInTheDocument();
  });

  it('opens an inspiration detail panel with media and prompt', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    fireEvent.click(await screen.findByRole('button', { name: '线上灵感 A' }));

    expect(mocks.queryInspirationCreationDetail).toHaveBeenCalledWith(101);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText('素材提示词')).toBeInTheDocument();
    expect(
      screen.getByText('被误解的女主多年后带着证据回归。'),
    ).toBeInTheDocument();
    expect(within(dialog).getAllByAltText('线上灵感 A')[0]).not.toHaveAttribute(
      'src',
    );
    act(() =>
      intersectionObservers.forEach((callback) => {
        callback(
          [{ isIntersecting: true }] as IntersectionObserverEntry[],
          {} as IntersectionObserver,
        );
      }),
    );
    expect(within(dialog).getAllByAltText('线上灵感 A')[0]).toHaveAttribute(
      'src',
      '/api/inspiration-creations/101/thumbnail',
    );
  });

  it('does not load the next inspiration page before the user scrolls', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await screen.findByText('线上灵感 A');
    intersectionObservers.at(-1)?.(
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    );

    await waitFor(() => {
      expect(mocks.queryInspirationCreations).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText('线上灵感 C')).not.toBeInTheDocument();
  });

  it('loads the next inspiration page after user scroll reaches the gallery bottom', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await screen.findByText('线上灵感 A');
    fireEvent.scroll(window, { target: { scrollY: 240 } });
    const paginationObserver = intersectionObservers.at(-1);
    paginationObserver?.(
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    );
    paginationObserver?.(
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    );

    expect(await screen.findByText('线上灵感 C')).toBeInTheDocument();
    expect(mocks.queryInspirationCreations).toHaveBeenCalledWith({
      page: 2,
      pageSize: 8,
    });
    expect(mocks.queryInspirationCreations).toHaveBeenCalledTimes(2);
  });

  it('opens the settings page from the first page start button', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await waitFor(() =>
      expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({
        current: 1,
        pageSize: 12,
      }),
    );

    fireEvent.click(screen.getByRole('button', { name: '开始创作' }));

    expect(
      screen.getByRole('button', { name: /初始设定/ }),
    ).toBeInTheDocument();
  });

  it('appends every platform style batch without losing selection or loading offscreen images', async () => {
    const baseline = await mocks.queryStyleLibrary();
    mocks.queryStyleLibrary.mockClear();
    mocks.queryStyleLibrary.mockImplementation((params) => Promise.resolve({ data: {
      current: params.current, pageSize: 12, total: 25,
      data: params.current === 1 ? baseline.data.data
        : params.current === 2 ? [baseline.data.data[0], laterStyle]
        : [{ ...laterStyle, id: 25, externalId: '25', name: '最后一批风格' }],
    } }));
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    expect(document.querySelector('.ant-pagination')).toBeNull();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1);

    await enterStyleFooter();
    const card = await screen.findByRole('button', { name: '后续页风格' });
    expect(mocks.queryStyleLibrary).toHaveBeenLastCalledWith({ current: 2, pageSize: 12 });
    expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(2);
    const image = within(card).getByAltText('后续页风格');
    expect(image).not.toHaveAttribute('src');
    const imageObserver = observedTargets.find((item) => item.target.contains(image));
    if (!imageObserver) throw new Error('风格图片观察器未注册');
    act(() => imageObserver.callback(
      [{ target: imageObserver.target, isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    ));
    expect(image).toHaveAttribute('src', '/later-style.png');
    fireEvent.click(card);
    expect(card).toHaveAttribute('aria-pressed', 'true');

    await enterStyleFooter();
    await screen.findByRole('button', { name: '最后一批风格' });
    expect(screen.getAllByText('后续页风格')).toHaveLength(2);
    expect(screen.getByText('已展示全部风格')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '加载更多风格' })).not.toBeInTheDocument();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(3);
  });

  it('ignores an obsolete batch after switching category Tabs', async () => {
    const baseline = await mocks.queryStyleLibrary();
    let finishOldBatch: (value: unknown) => void = () => {};
    mocks.queryStyleLibrary.mockClear();
    mocks.queryStyleLibrary.mockImplementation((params) => {
      if (params.category) return Promise.resolve({ data: {
        current: 1, pageSize: 12, total: 1, data: [laterStyle],
      } });
      if (params.current === 2) return new Promise((resolve) => { finishOldBatch = resolve; });
      return Promise.resolve(baseline);
    });
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    await enterStyleFooter();
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(2));
    await enterStyleFooter();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByRole('tab', { name: '未分组' }));
    await screen.findByRole('button', { name: '后续页风格' });
    await act(async () => finishOldBatch({ data: {
      current: 2, pageSize: 12, total: 25,
      data: [{ ...laterStyle, id: 99, externalId: '99', name: '过期分类风格' }],
    } }));
    expect(screen.queryByRole('button', { name: '过期分类风格' })).not.toBeInTheDocument();
    expect(screen.getByText('3D风格-高清真实渲染')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '未分组' })).toHaveAttribute('aria-selected', 'true');
  });

  it('retries a failed next batch without dropping existing cards or restarting page one', async () => {
    const baseline = await mocks.queryStyleLibrary();
    mocks.queryStyleLibrary.mockClear();
    mocks.queryStyleLibrary.mockResolvedValueOnce(baseline)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { current: 2, pageSize: 12, total: 13, data: [laterStyle] } });
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    await enterStyleFooter();
    const retry = await screen.findByRole('button', { name: '重试加载风格' });
    expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(2);
    await enterStyleFooter();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(2);
    fireEvent.click(retry);
    await screen.findByRole('button', { name: '后续页风格' });
    expect(mocks.queryStyleLibrary).toHaveBeenLastCalledWith({ current: 2, pageSize: 12 });
    expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(2);
    expect(screen.getByText('已展示全部风格')).toBeInTheDocument();
  });

  it('keeps the chosen style when a category is empty and when returning to all styles', async () => {
    const baseline = await mocks.queryStyleLibrary();
    mocks.queryStyleLibrary.mockClear();
    mocks.queryStyleLibrary.mockImplementation((params) => Promise.resolve(params.category
      ? { data: { current: 1, pageSize: 12, total: 0, data: [] } } : baseline));
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    fireEvent.click(screen.getByRole('tab', { name: '未分组' }));
    expect(await screen.findByText('当前分类暂无平台风格')).toBeInTheDocument();
    expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: '加载更多风格' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '全部' }));
    await waitFor(() => expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(2));
    expect(mocks.queryStyleLibrary).toHaveBeenLastCalledWith({ current: 1, pageSize: 12 });
  });

  it('stops requesting more after an empty batch even when the total is out of date', async () => {
    const baseline = await mocks.queryStyleLibrary();
    mocks.queryStyleLibrary.mockClear();
    mocks.queryStyleLibrary.mockResolvedValueOnce(baseline).mockResolvedValueOnce({ data: {
      current: 2, pageSize: 12, total: 25, data: [],
    } });
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    await enterStyleFooter();
    await screen.findByText('已展示全部风格');
    await enterStyleFooter();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledTimes(2);
    expect(screen.getAllByText('3D风格-高清真实渲染')).toHaveLength(2);
  });

  it('opens video inspiration as a cover and only starts its source on play', async () => {
    mocks.queryInspirationCreationDetail.mockResolvedValue({
      data: {
        id: 101,
        title: '视频灵感',
        mimeType: 'video/mp4',
        url: '/api/inspiration-creations/101/file',
        thumbnailUrl: '/cover.png',
      },
    });
    const view = render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '线上灵感 A' }));
    const play = await screen.findByRole('button', { name: '播放视频灵感' });
    expect(view.baseElement.querySelector('video')).toBeNull();
    fireEvent.click(play);
    const video = view.baseElement.querySelector('video');
    expect(video).toHaveAttribute('src', '/api/inspiration-creations/101/file');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(video).not.toHaveAttribute('src');
  });

  it('does not reopen a closed media detail when its request finishes late', async () => {
    let finish: (value: unknown) => void = () => {};
    mocks.queryInspirationCreationDetail.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '线上灵感 A' }));
    await screen.findByRole('dialog');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    await act(async () =>
      finish({
        data: {
          id: 101,
          title: '迟到视频',
          mimeType: 'video/mp4',
          url: '/late.mp4',
        },
      }),
    );
    expect(
      screen.queryByRole('button', { name: '播放迟到视频' }),
    ).not.toBeInTheDocument();
  });

  it('uses categories from the independent catalog when changing a style filter', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalled());
    fireEvent.click(
      screen.getByRole('button', { name: '跳过上传，创建空白剧本' }),
    );
    expect(screen.getByRole('tab', { name: '全部' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: '3D风格' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: '风格分类' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '未分组' }));
    await waitFor(() =>
      expect(mocks.queryStyleLibrary).toHaveBeenLastCalledWith({
        current: 1,
        pageSize: 12,
        category: '未分组',
      }),
    );
  });

  it('creates a project from the settings page and opens the script workbench', async () => {
    mocks.createProject.mockResolvedValue({ data: { id: 9 } });

    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await waitFor(() =>
      expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({
        current: 1,
        pageSize: 12,
      }),
    );

    fireEvent.click(
      screen.getByRole('button', { name: '跳过上传，创建空白剧本' }),
    );
    const settingLabels = screen.getAllByText(
      /剧本名称|画面比例|文件格式|剧本类型|剧本解析力度/,
    );
    expect(settingLabels[0]).toHaveTextContent('剧本名称');
    expect(document.querySelectorAll('.ant-radio-group')).toHaveLength(4);
    fireEvent.change(screen.getByRole('textbox', { name: '剧本名称' }), {
      target: { value: '雨夜归来' },
    });
    fireEvent.click(screen.getByRole('button', { name: /开始创作/ }));

    await waitFor(() => {
      expect(mocks.createProject).toHaveBeenCalledWith(
        expect.objectContaining({
          name: '未命名短剧',
          scriptName: '雨夜归来',
          code: expect.stringMatching(/^SHORT_DRAMA_/),
          ownerId: 1,
          videoResolution: '720p',
          videoGenerateAudio: true,
          videoWatermark: false,
        }),
      );
      expect(mocks.historyPush).toHaveBeenCalledWith(
        '/projects/9/production-workbench/script',
      );
    });
  });

  it('allows imported script content to be edited before project creation', async () => {
    mocks.createProject.mockResolvedValue({ data: { id: 9 } });
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );
    await waitFor(() =>
      expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({
        current: 1,
        pageSize: 12,
      }),
    );

    fireEvent.click(screen.getByRole('button', { name: '模拟导入' }));
    const editor = screen.getByPlaceholderText(
      '复制粘贴剧本，或导入文件（支持 txt、md、docx）',
    );
    expect(editor).toHaveValue('引用的版本内容');
    fireEvent.change(editor, { target: { value: '引用后继续编辑' } });
    fireEvent.click(screen.getByRole('button', { name: '开始创作' }));
    fireEvent.click(screen.getByRole('button', { name: /开始创作/ }));

    await waitFor(() =>
      expect(mocks.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ initialScriptContent: '引用后继续编辑' }),
      ),
    );
    const payload = mocks.createProject.mock.calls[0][0];
    expect(payload).not.toHaveProperty('reviewProjectId');
    expect(payload).not.toHaveProperty('reviewVersionId');
  });

  it('uploads a cover through a controlled session and never persists its data URL', async () => {
    mocks.createProject.mockResolvedValue({ data: { id: 9 } });
    render(<App><ShortDramaCreationPage /></App>);
    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));

    const file = new File([new Uint8Array(2048)], 'cover.png', { type: 'image/png' });
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, {
      target: { files: [file] },
    });
    await waitFor(() => expect(mocks.startProjectCoverUpload).toHaveBeenCalledWith(file));
    act(() => intersectionObservers.at(-1)?.(
      [{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver,
    ));
    await waitFor(() => expect(screen.getByAltText('封面预览')).toHaveAttribute('src', 'blob:cover-preview'));

    fireEvent.click(screen.getByRole('button', { name: /开始创作/ }));
    await waitFor(() => expect(mocks.bindProjectCover).toHaveBeenCalledWith(9, 'cover-session'));
    const payload = mocks.createProject.mock.calls[0][0];
    expect(payload.coverSource).toBe('UPLOAD');
    expect(payload.coverUrl).toBeUndefined();
    expect(JSON.stringify(payload)).not.toContain('data:image');
    expect(mocks.bindProjectCover.mock.invocationCallOrder[0])
      .toBeLessThan(mocks.historyPush.mock.invocationCallOrder[0]);
  });
});
