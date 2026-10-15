import * as matchers from '@testing-library/jest-dom/matchers';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';

expect.extend(matchers);
// 仅隔离本页面测试的框架 locale 收集。
vi.mock('antd/es/locale/zh_CN', () => ({ default: {} }));

const mocks = vi.hoisted(() => ({
  team: 1,
  canView: true,
  canManage: true,
  templates: vi.fn(),
  list: vi.fn(),
  apps: vi.fn(),
  enable: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useAccess: () => ({
    canViewDramas: mocks.canView,
    canManageMiniapps: mocks.canManage,
    canManageDramas: false,
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
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.team = 1;
    mocks.canView = true;
    mocks.canManage = true;
    mocks.templates.mockResolvedValue([]);
    mocks.list.mockResolvedValue([]);
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
