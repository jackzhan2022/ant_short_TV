import * as matchers from '@testing-library/jest-dom/matchers';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from '@testing-library/react';

expect.extend(matchers);

const mocks = {
  availability: vi.fn(),
  create: vi.fn(),
  poll: vi.fn(),
};
vi.doMock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.doMock('./service', () => ({
  getAuthorizationAvailability: mocks.availability,
  createAuthorizationRequest: mocks.create,
  getAuthorizationRequest: mocks.poll,
}));

const { default: OfficialAuthorization, isOfficialAuthorizationUrl } =
  await import('./OfficialAuthorization');

const url =
  'https://mp.weixin.qq.com/cgi-bin/componentloginpage?pre_auth_code=test';
const flush = async () => {
  await act(async () => {
    await Promise.resolve();
  });
};
const advance = async (ms = 2000) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};
const start = async () => {
  await flush();
  fireEvent.click(screen.getByRole('button', { name: '微信官方授权小程序' }));
  await flush();
};

describe('官方授权', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    mocks.availability.mockResolvedValue({ status: 'READY', available: true });
    mocks.create.mockResolvedValue({
      requestId: 'request-1',
      authorizationUrl: url,
      expiresAt: new Date(Date.now() + 20 * 60 * 1000).toISOString(),
    });
    mocks.poll.mockResolvedValue({
      requestId: 'request-1',
      status: 'PENDING',
      appid: null,
      expiresAt: '',
    });
    vi.spyOn(window, 'open').mockReturnValue(null);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });
  it.each([
    ['DISABLED', '官方授权尚未启用'],
    ['NOT_CONFIGURED', '服务端第三方平台配置未完成'],
    ['WAITING_TICKET', '正在等待微信推送 component_verify_ticket'],
  ])('%s 显示明确提示并禁止发起', async (status, text) => {
    mocks.availability.mockResolvedValue({ status, available: false });
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await flush();
    expect(screen.getByText(new RegExp(text))).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '微信官方授权小程序' }),
    ).toBeDisabled();
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it('READY 仍要求 available，服务失败可重试', async () => {
    mocks.availability
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ status: 'READY', available: false });
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await flush();
    expect(screen.getByText('授权服务请求失败，请重试。')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /刷新授权状态/ }));
    await flush();
    expect(
      screen.getByRole('button', { name: '微信官方授权小程序' }),
    ).toBeDisabled();
  });
  it('安全打开官方链接并始终保留弹窗拦截后的备用链接', async () => {
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await start();
    expect(window.open).toHaveBeenCalledWith(
      url,
      '_blank',
      'noopener,noreferrer',
    );
    const link = screen.getByRole('link', { name: '打开微信官方授权页面' });
    expect(link).toHaveAttribute('href', url);
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('target', '_blank');
  });
  it.each([
    'SUCCEEDED',
    'FAILED',
    'EXPIRED',
  ])('%s 终态停止轮询，仅成功刷新列表', async (status) => {
    const refresh = vi.fn().mockResolvedValue(undefined);
    mocks.poll.mockResolvedValue({
      requestId: 'request-1',
      status,
      appid: null,
      expiresAt: '',
    });
    render(<OfficialAuthorization onAuthorized={refresh} />);
    await start();
    await advance();
    await advance(20000);
    expect(mocks.poll).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledTimes(status === 'SUCCEEDED' ? 1 : 0);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
  it('PENDING 与 PROCESSING 继续等待，到有限时间停止', async () => {
    mocks.poll
      .mockResolvedValueOnce({ requestId: 'request-1', status: 'PROCESSING' })
      .mockResolvedValue({ requestId: 'request-1', status: 'PENDING' });
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await start();
    await advance();
    expect(
      screen.getByText('微信已回调，服务端正在处理授权，请稍候。'),
    ).toBeInTheDocument();
    await advance();
    expect(
      screen.getByText('等待小程序管理员在微信官方页面确认授权。'),
    ).toBeInTheDocument();
    await advance(10 * 60 * 1000);
    const count = mocks.poll.mock.calls.length;
    await advance(10000);
    expect(mocks.poll).toHaveBeenCalledTimes(count);
    expect(
      screen.getByText(/授权链接或本页等待时间已到期/),
    ).toBeInTheDocument();
  });
  it('关闭弹窗清除轮询', async () => {
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await start();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    await advance(20000);
    expect(mocks.poll).not.toHaveBeenCalled();
  });
  it('切团队忽略旧创建结果，不打开链接或轮询', async () => {
    let resolve!: (value: unknown) => void;
    mocks.create.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const refresh = vi.fn();
    const { rerender } = render(
      <OfficialAuthorization key="team-1" onAuthorized={refresh} />,
    );
    await start();
    rerender(<OfficialAuthorization key="team-2" onAuthorized={refresh} />);
    await flush();
    await act(async () => {
      resolve({
        requestId: 'old',
        authorizationUrl: url,
        expiresAt: new Date(Date.now() + 60000).toISOString(),
      });
    });
    await advance(10000);
    expect(window.open).not.toHaveBeenCalled();
    expect(mocks.poll).not.toHaveBeenCalled();
    expect(refresh).not.toHaveBeenCalled();
  });
  it.each(['切团队', '卸载'])('%s 忽略旧轮询成功并清理定时器', async (mode) => {
    let resolve!: (value: unknown) => void;
    mocks.poll.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const refresh = vi.fn();
    const view = render(
      <OfficialAuthorization key="team-1" onAuthorized={refresh} />,
    );
    await start();
    await advance();
    if (mode === '切团队')
      view.rerender(
        <OfficialAuthorization key="team-2" onAuthorized={refresh} />,
      );
    else view.unmount();
    await act(async () => {
      resolve({ requestId: 'request-1', status: 'SUCCEEDED' });
    });
    await advance(20000);
    expect(refresh).not.toHaveBeenCalled();
    expect(mocks.poll).toHaveBeenCalledTimes(1);
    view.unmount();
    await advance(20000);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('拒绝恶意链接，不打开或展示备用链接', async () => {
    mocks.create.mockResolvedValue({
      requestId: 'bad',
      authorizationUrl: 'https://evil.test/',
      expiresAt: new Date(Date.now() + 60000).toISOString(),
    });
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await start();
    expect(window.open).not.toHaveBeenCalled();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByText('授权服务请求失败，请重试。')).toBeInTheDocument();
  });
  it('轮询失败停止并显示错误', async () => {
    mocks.poll.mockRejectedValue(new Error('offline'));
    render(<OfficialAuthorization onAuthorized={vi.fn()} />);
    await start();
    await advance();
    await advance(20000);
    expect(mocks.poll).toHaveBeenCalledTimes(1);
    expect(screen.getByText('授权服务请求失败，请重试。')).toBeInTheDocument();
  });
});

describe('官方链接白名单', () => {
  it.each([
    'http://mp.weixin.qq.com/cgi-bin/componentloginpage',
    'https://mp.weixin.qq.com.evil.test/cgi-bin/componentloginpage',
    'https://user@mp.weixin.qq.com/cgi-bin/componentloginpage',
    'https://mp.weixin.qq.com:444/cgi-bin/componentloginpage',
    'https://mp.weixin.qq.com/other',
    'javascript:alert(1)',
    'https://mp.weixin.qq.com/cgi-bin/componentloginpage#x',
  ])('拒绝 %s', (value) => {
    expect(isOfficialAuthorizationUrl(value)).toBe(false);
  });
  it('允许真实官方授权路径', () => {
    expect(isOfficialAuthorizationUrl(url)).toBe(true);
  });
});
