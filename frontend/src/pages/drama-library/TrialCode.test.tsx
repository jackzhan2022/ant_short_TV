import * as matchers from '@testing-library/jest-dom/matchers';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

expect.extend(matchers);
vi.mock('antd/es/locale/zh_CN', () => ({ default: {} }));
const mocks = vi.hoisted(() => ({
  allowed: true,
  templates: vi.fn(),
  commit: vi.fn(),
  qr: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useAccess: () => ({ canManageMiniapps: mocks.allowed }),
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.mock('./service', () => ({
  canManageCode: (app: {
    enabled: boolean;
    bindingType: string;
    authStatus: string;
  }) =>
    app.enabled &&
    app.bindingType === 'OFFICIAL' &&
    app.authStatus === 'AUTHORIZED',
  getCodeTemplates: mocks.templates,
  commitCode: mocks.commit,
  getTrialQrCode: mocks.qr,
}));

import TrialCode from './TrialCode';

const app = {
  id: 3,
  appid: 'wx0123456789abcdef',
  name: '测试目标',
  bindingType: 'OFFICIAL',
  authStatus: 'AUTHORIZED',
  enabled: true,
  createdAt: '',
};
const template = {
  templateId: 0,
  version: 'source-v1',
  description: '来源描述',
  sourceName: '来源',
  sourceAppid: 'wxsource',
};
const close = vi.fn();
async function fill() {
  await waitFor(() => expect(mocks.templates).toHaveBeenCalled());
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByText('#0 · source-v1 · 来源'));
  fireEvent.change(screen.getByRole('textbox', { name: '上传版本号' }), {
    target: { value: '1.0' },
  });
  fireEvent.change(screen.getByRole('textbox', { name: '上传说明' }), {
    target: { value: '体验版' },
  });
  fireEvent.click(screen.getByRole('checkbox'));
}
describe('体验版管理', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.allowed = true;
    mocks.templates.mockResolvedValue([template]);
    mocks.commit.mockResolvedValue('SUCCEEDED');
    mocks.qr.mockResolvedValue(new Blob(['png'], { type: 'image/png' }));
    URL.createObjectURL = vi.fn(() => 'blob:trial');
    URL.revokeObjectURL = vi.fn();
  });
  it('模板0可上传，展示来源，确认目标后只上传一次', async () => {
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    expect(screen.getByRole('button', { name: /确认上传一次/ })).toBeDisabled();
    await fill();
    expect(screen.getByText(/模板来源 \/ 来源版本/)).toHaveTextContent(
      'source-v1',
    );
    const upload = screen.getByRole('button', { name: /确认上传一次/ });
    fireEvent.click(upload);
    fireEvent.click(upload);
    await screen.findByText('代码上传成功，可获取体验版二维码；正式版未改变。');
    expect(mocks.commit).toHaveBeenCalledTimes(1);
    expect(mocks.commit).toHaveBeenCalledWith(
      1,
      3,
      { templateId: 0, version: '1.0', description: '体验版' },
      expect.any(AbortSignal),
    );
    expect(upload).toBeDisabled();
  });
  it('UNKNOWN明显警告且禁止重复上传', async () => {
    mocks.commit.mockResolvedValue('UNKNOWN');
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    await fill();
    fireEvent.click(screen.getByRole('button', { name: /确认上传一次/ }));
    await screen.findByText(/UNKNOWN：上传结果未知/);
    expect(screen.getByRole('button', { name: /确认上传一次/ })).toBeDisabled();
    expect(mocks.commit).toHaveBeenCalledTimes(1);
  });
  it.each([
    { ...app, bindingType: 'MANUAL' },
    { ...app, authStatus: 'REVOKED' },
    { ...app, enabled: false },
  ])('无效目标不访问接口', (target) => {
    render(<TrialCode app={target} tenantId={1} onClose={close} />);
    expect(mocks.templates).not.toHaveBeenCalled();
  });
  it('切团队后忽略旧上传结果，权限撤销释放二维码', async () => {
    let finish!: (status: string) => void;
    mocks.commit.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const view = render(<TrialCode app={app} tenantId={1} onClose={close} />);
    await fill();
    fireEvent.click(screen.getByRole('button', { name: /确认上传一次/ }));
    const signal = mocks.commit.mock.calls[0][3];
    view.rerender(<TrialCode app={app} tenantId={2} onClose={close} />);
    expect(signal.aborted).toBe(true);
    await act(async () => finish('SUCCEEDED'));
    expect(screen.queryByText(/代码上传成功/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    await screen.findByRole('img', { name: '体验版二维码' });
    mocks.allowed = false;
    view.rerender(<TrialCode app={app} tenantId={2} onClose={close} />);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:trial');
  });
  it('非管理员不访问接口', () => {
    mocks.allowed = false;
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    expect(mocks.templates).not.toHaveBeenCalled();
  });
  it.each(['team', 'target'])('上传中切换%s忽略旧成功结果', async (kind) => {
    let resolve!: (value: string) => void;
    mocks.commit.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const view = render(<TrialCode app={app} tenantId={1} onClose={close} />);
    await fill();
    fireEvent.click(screen.getByRole('button', { name: /确认上传一次/ }));
    const signal = mocks.commit.mock.calls[0][3];
    view.rerender(
      <TrialCode
        app={kind === 'target' ? { ...app, id: 4 } : app}
        tenantId={kind === 'team' ? 2 : 1}
        onClose={close}
      />,
    );
    expect(signal.aborted).toBe(true);
    await act(async () => {
      resolve('SUCCEEDED');
    });
    expect(screen.queryByText(/代码上传成功/)).not.toBeInTheDocument();
    expect(mocks.commit).toHaveBeenCalledTimes(1);
  });
  it('权限撤销销毁弹窗并释放二维码', async () => {
    const view = render(<TrialCode app={app} tenantId={1} onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    await screen.findByRole('img', { name: '体验版二维码' });
    mocks.allowed = false;
    view.rerender(<TrialCode app={app} tenantId={1} onClose={close} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:trial');
  });
  it('明确拒绝后不重复上传', async () => {
    mocks.commit.mockRejectedValue(new Error('FORBIDDEN'));
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    await fill();
    fireEvent.click(screen.getByRole('button', { name: '确认上传一次' }));
    await screen.findByText(/请求失败，请核对权限/);
    const upload = screen.getByRole('button', { name: /确认上传一次/ });
    expect(upload).toBeDisabled();
    fireEvent.click(upload);
    expect(mocks.commit).toHaveBeenCalledTimes(1);
  });
  it.each([
    '\u007f',
    '\u0085',
    '\u009f',
  ])('禁止后端不接受的控制字符路径', async (path) => {
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    await waitFor(() => expect(mocks.templates).toHaveBeenCalled());
    fireEvent.change(
      screen.getByRole('textbox', { name: '体验版页面路径（可选）' }),
      {
        target: { value: `pages/${path}` },
      },
    );
    expect(
      screen.getByRole('button', { name: '获取体验版二维码' }),
    ).toBeDisabled();
    expect(mocks.qr).not.toHaveBeenCalled();
  });
  it('图片解码失败释放objectURL', async () => {
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    fireEvent.error(await screen.findByRole('img', { name: '体验版二维码' }));
    await screen.findByText(/二维码获取失败/);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:trial');
    expect(
      screen.queryByRole('img', { name: '体验版二维码' }),
    ).not.toBeInTheDocument();
  });
  it('二维码错误不显示图片', async () => {
    mocks.qr.mockRejectedValue(new Error('JSON错误'));
    render(<TrialCode app={app} tenantId={1} onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    await screen.findByText(/二维码获取失败/);
    expect(
      screen.queryByRole('img', { name: '体验版二维码' }),
    ).not.toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
  it('二维码替换与销毁释放objectURL', async () => {
    const view = render(<TrialCode app={app} tenantId={1} onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    await screen.findByRole('img', { name: '体验版二维码' });
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /获取体验版二维码/ }),
      ).not.toHaveClass('ant-btn-loading'),
    );
    fireEvent.click(screen.getByRole('button', { name: /获取体验版二维码/ }));
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(2));
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    view.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });
  it.each([
    'team',
    'target',
  ])('切换%s取消请求并忽略旧模板和二维码', async (kind) => {
    let resolveTemplates!: (value: unknown) => void;
    let resolveQr!: (value: Blob) => void;
    mocks.templates.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveTemplates = resolve;
      }),
    );
    mocks.qr.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveQr = resolve;
      }),
    );
    const view = render(<TrialCode app={app} tenantId={1} onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: '获取体验版二维码' }));
    const signal = mocks.templates.mock.calls[0][1];
    view.rerender(
      <TrialCode
        app={kind === 'target' ? { ...app, id: 4 } : app}
        tenantId={kind === 'team' ? 2 : 1}
        onClose={close}
      />,
    );
    expect(signal.aborted).toBe(true);
    await act(async () => {
      resolveTemplates([{ ...template, version: '旧版本' }]);
      resolveQr(new Blob(['old']));
    });
    expect(screen.queryByText(/旧版本/)).not.toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
