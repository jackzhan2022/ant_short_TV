import * as matchers from '@testing-library/jest-dom/matchers';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App, ConfigProvider } from 'antd';

expect.extend(matchers);
// 仅隔离本页面测试的框架 locale 收集。
vi.mock('antd/es/locale/zh_CN', () => ({ default: {} }));

const mocks = vi.hoisted(() => ({
  team: 1,
  canView: true,
  canManage: true,
  canManageDramas: false,
  templates: vi.fn(),
  list: vi.fn(),
  apps: vi.fn(),
  enable: vi.fn(),
  detail: vi.fn(),
  episodes: vi.fn(),
  save: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useAccess: () => ({
    canViewDramas: mocks.canView,
    canManageMiniapps: mocks.canManage,
    canManageDramas: mocks.canManageDramas,
    canPublishDramas: true,
  }),
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
  useModel: () => ({
    initialState: { selectedTenant: { tenant: { id: mocks.team } } },
  }),
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => children,
}));
vi.mock('./OfficialAuthorization', () => ({
  default: () => <button type="button">微信官方授权小程序</button>,
}));
vi.mock('./ImportEpisode', () => ({ default: () => null }));
vi.mock('./service', () => ({
  listDramas: mocks.list,
  listMiniapps: mocks.apps,
  enableMiniapp: mocks.enable,
  getDrama: mocks.detail,
  listEpisodes: mocks.episodes,
  saveDrama: mocks.save,
  getCodeTemplates: mocks.templates,
  canManageCode: (app: {
    enabled: boolean;
    bindingType: string;
    authStatus?: string;
  }) =>
    app.enabled &&
    app.bindingType === 'OFFICIAL' &&
    app.authStatus === 'AUTHORIZED',
  canUseMiniapp: (app: { bindingType: string; authStatus?: string }) =>
    app.bindingType !== 'OFFICIAL' || app.authStatus === 'AUTHORIZED',
}));

import DramaLibraryPage from './index';

describe('团队小程序官方入口', () => {
  it('目录展示运营元数据和批量导入入口', async () => {
    mocks.canManageDramas = true;
    const drama = {
      id: 7,
      title: '测试短剧',
      category: '都市',
      completionStatus: 'COMPLETED',
      totalEpisodes: 12,
      episodeCount: 8,
      publishedAppCount: 2,
      coverUrl: 'https://example.com/cover.jpg',
    };
    mocks.list.mockResolvedValue([drama]);
    mocks.detail.mockResolvedValue(drama);
    mocks.episodes.mockResolvedValue([]);
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await screen.findByText('测试短剧');
    expect(screen.getByText('都市 · 已完结')).toBeInTheDocument();
    expect(screen.getByText('8 / 12 集')).toBeInTheDocument();
    expect(screen.getByAltText('测试短剧封面')).toHaveAttribute(
      'src',
      drama.coverUrl,
    );
    fireEvent.click(screen.getByRole('button', { name: '单集目录' }));
    expect(
      await screen.findByRole('button', { name: '批量本地导入' }),
    ).toBeInTheDocument();
  });
  it('新建剧集校验 HTTPS 封面并保存运营元数据', async () => {
    mocks.canManageDramas = true;
    mocks.save.mockResolvedValue({ id: 9 });
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    fireEvent.click(
      (await screen.findAllByRole('button', { name: /新建剧集/ }))[0],
    );
    fireEvent.change(screen.getByLabelText('剧名'), {
      target: { value: '元数据短剧' },
    });
    fireEvent.change(screen.getByLabelText('封面 HTTPS 地址'), {
      target: { value: 'http://example.com/cover.jpg' },
    });
    fireEvent.change(screen.getByLabelText('分类'), {
      target: { value: '悬疑' },
    });
    fireEvent.change(screen.getByLabelText('总集数'), {
      target: { value: '24' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await screen.findByText('请输入有效的 HTTPS 地址');
    expect(mocks.save).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('封面 HTTPS 地址'), {
      target: { value: 'https://example.com/cover.jpg' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(mocks.save).toHaveBeenCalledWith(
        {
          title: '元数据短剧',
          description: '',
          coverUrl: 'https://example.com/cover.jpg',
          category: '悬疑',
          totalEpisodes: 24,
          completionStatus: 'ONGOING',
        },
        undefined,
      ),
    );
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.team = 1;
    mocks.canView = true;
    mocks.canManage = true;
    mocks.canManageDramas = false;
    mocks.templates.mockResolvedValue([]);
    mocks.list.mockResolvedValue([]);
    mocks.detail.mockImplementation(async (id: number) => ({
      id,
      title: id === 1 ? '第一部剧' : '第二部剧',
      createdAt: '2026-11-11T12:00:00',
    }));
    mocks.episodes.mockImplementation(async (id: number) => [
      {
        id: id * 10,
        dramaId: id,
        episodeNo: 1,
        title: `剧集 ${id} 的第一集`,
        sourceType: 'LOCAL_UPLOAD',
        createdAt: '2026-11-11T12:00:00',
      },
    ]);
    mocks.apps.mockResolvedValue([
      {
        id: 1,
        name: '已取消的小程序',
        appid: 'wx1',
        bindingType: 'OFFICIAL',
        authStatus: 'REVOKED',
        enabled: false,
      },
      {
        id: 2,
        name: '历史手工小程序',
        appid: 'wx2',
        bindingType: 'MANUAL',
        enabled: false,
      },
    ]);
  });
  it('绕过路由直接挂载未授权页面不展示入口且不请求管理数据', () => {
    mocks.canView = false;
    const { container } = render(<DramaLibraryPage />);
    expect(container).toBeEmptyDOMElement();
    expect(mocks.list).not.toHaveBeenCalled();
    expect(mocks.apps).not.toHaveBeenCalled();
  });

  it('轻卡片可搜索简介和排序，浏览不会逐部请求单集', async () => {
    mocks.list.mockResolvedValue([
      {
        id: 1,
        title: '第一部剧',
        description: '都市悬疑',
        createdAt: '2026-11-10T12:00:00',
      },
      {
        id: 2,
        title: '第二部剧',
        description: '古装故事',
        createdAt: '2026-11-11T12:00:00',
      },
    ]);
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await screen.findByRole('article', { name: '剧集：第一部剧' });
    expect(screen.getAllByRole('article')[0]).toHaveAccessibleName(
      '剧集：第二部剧',
    );
    fireEvent.change(screen.getByRole('textbox', { name: '搜索剧名或简介' }), {
      target: { value: '悬疑' },
    });
    expect(
      screen.queryByRole('article', { name: '剧集：第二部剧' }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('article', { name: '剧集：第一部剧' }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '搜索剧名或简介' }), {
      target: { value: '' },
    });
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '剧集排序' }));
    fireEvent.click(await screen.findByText('最早创建'));
    expect(screen.getAllByRole('article')[0]).toHaveAccessibleName(
      '剧集：第一部剧',
    );
    expect(mocks.episodes).not.toHaveBeenCalled();
    expect(mocks.detail).not.toHaveBeenCalled();
  });

  it('页内目录返回后保留搜索，切换剧集清除单集选择', async () => {
    mocks.list.mockResolvedValue([
      { id: 1, title: '第一部剧', createdAt: '2026-11-11T12:00:00' },
      { id: 2, title: '第二部剧', createdAt: '2026-11-10T12:00:00' },
    ]);
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await screen.findByRole('article', { name: '剧集：第一部剧' });
    fireEvent.change(screen.getByRole('textbox', { name: '搜索剧名或简介' }), {
      target: { value: '第一' },
    });
    fireEvent.click(screen.getByRole('button', { name: '单集目录' }));
    await screen.findByText('剧集 1 的第一集');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    expect(screen.getByRole('status', { name: '已选单集' })).toHaveTextContent(
      '已选 1 集',
    );
    fireEvent.click(screen.getByRole('button', { name: '全部剧集' }));
    expect(screen.getByRole('textbox', { name: '搜索剧名或简介' })).toHaveValue(
      '第一',
    );
    fireEvent.change(screen.getByRole('textbox', { name: '搜索剧名或简介' }), {
      target: { value: '第二' },
    });
    fireEvent.click(screen.getByRole('button', { name: '单集目录' }));
    await screen.findByText('剧集 2 的第一集');
    expect(screen.getByRole('status', { name: '已选单集' })).toHaveTextContent(
      '已选 0 集',
    );
    expect(mocks.detail).toHaveBeenLastCalledWith(2);
    expect(mocks.episodes).toHaveBeenLastCalledWith(2);
  });

  it('目录编辑后同步标题，二次编辑使用最新内容且保留单集选择', async () => {
    mocks.canManageDramas = true;
    mocks.list.mockResolvedValue([
      { id: 1, title: '第一部剧', createdAt: '2026-11-11T12:00:00' },
    ]);
    mocks.save.mockImplementation(
      async (values: { title: string; description?: string }, id: number) => ({
        id,
        ...values,
        createdAt: '2026-11-11T12:00:00',
      }),
    );
    render(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <App>
          <DramaLibraryPage />
        </App>
      </ConfigProvider>,
    );
    await screen.findByRole('article', { name: '剧集：第一部剧' });
    fireEvent.click(screen.getByRole('button', { name: '单集目录' }));
    await screen.findByText('剧集 1 的第一集');
    fireEvent.click(screen.getAllByRole('checkbox')[1]);
    fireEvent.click(screen.getByRole('button', { name: /编辑剧集/ }));
    fireEvent.change(screen.getByLabelText('剧名'), {
      target: { value: '新的剧名' },
    });
    fireEvent.click(screen.getByRole('button', { name: /OK|确\s*定/ }));
    await screen.findByRole('heading', { name: '新的剧名', level: 1 });
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('status', { name: '已选单集' })).toHaveTextContent(
      '已选 1 集',
    );
    fireEvent.click(screen.getByRole('button', { name: /编辑剧集/ }));
    expect(screen.getByLabelText('剧名')).toHaveValue('新的剧名');
  });

  it('权限撤销后卸载管理页面与授权入口', async () => {
    const view = render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(1));
    mocks.canView = false;
    view.rerender(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    expect(
      screen.queryByRole('tab', { name: '团队小程序分发' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '微信官方授权小程序' }),
    ).not.toBeInTheDocument();
  });

  it('官方为主入口，保留历史记录，取消授权不可再次启用', async () => {
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    fireEvent.click(screen.getByRole('tab', { name: '团队小程序分发' }));
    await screen.findByText('已取消的小程序');
    expect(
      screen.getByRole('button', { name: '微信官方授权小程序' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '手工绑定小程序' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('历史手工小程序')).toBeInTheDocument();
    for (const button of screen.getAllByRole('button', {
      name: '体验版管理',
    })) {
      expect(button).toBeDisabled();
    }
    const codeButtons = screen.getAllByRole('button', { name: '体验版管理' });
    expect(codeButtons).toHaveLength(2);
    for (const button of codeButtons) expect(button).toBeDisabled();
    const switches = screen.getAllByRole('switch');
    expect(switches[0]).toBeDisabled();
    expect(switches[1]).toBeEnabled();
    fireEvent.click(switches[0]);
    expect(mocks.enable).not.toHaveBeenCalled();
  });
  it('有效目标可从列表打开体验版弹窗，停用目标禁用入口', async () => {
    const target = {
      id: 3,
      name: '有效目标',
      appid: 'wx0123456789abcdef',
      bindingType: 'OFFICIAL',
      authStatus: 'AUTHORIZED',
      enabled: true,
    };
    mocks.apps.mockResolvedValue([
      target,
      { ...target, id: 4, name: '停用目标', enabled: false },
    ]);
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    fireEvent.click(screen.getByRole('tab', { name: '团队小程序分发' }));
    await screen.findByText('有效目标');
    const buttons = screen.getAllByRole('button', { name: '体验版管理' });
    expect(buttons[0]).toBeEnabled();
    expect(buttons[1]).toBeDisabled();
    fireEvent.click(buttons[0]);
    expect(await screen.findByRole('dialog')).toHaveTextContent(target.appid);
    expect(mocks.templates).toHaveBeenCalledWith(1, expect.any(AbortSignal));
  });
  it('缺少管理权限时列表不展示体验版操作', async () => {
    mocks.canManage = false;
    render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    fireEvent.click(screen.getByRole('tab', { name: '团队小程序分发' }));
    await screen.findByText('已取消的小程序');
    expect(
      screen.queryByRole('button', { name: '体验版管理' }),
    ).not.toBeInTheDocument();
    expect(mocks.templates).not.toHaveBeenCalled();
  });
  it('切团队后旧列表结果不污染新团队', async () => {
    let resolve!: (rows: unknown[]) => void;
    mocks.apps
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done;
        }),
      )
      .mockResolvedValue([]);
    const view = render(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await waitFor(() => expect(mocks.apps).toHaveBeenCalledTimes(1));
    mocks.team = 2;
    view.rerender(
      <App>
        <DramaLibraryPage />
      </App>,
    );
    await waitFor(() => expect(mocks.apps).toHaveBeenCalledTimes(2));
    await act(async () => {
      resolve([
        {
          id: 99,
          name: '旧团队记录',
          appid: 'wxold',
          bindingType: 'MANUAL',
          enabled: true,
        },
      ]);
    });
    fireEvent.click(screen.getByRole('tab', { name: '团队小程序分发' }));
    expect(screen.queryByText('旧团队记录')).not.toBeInTheDocument();
  });
});
