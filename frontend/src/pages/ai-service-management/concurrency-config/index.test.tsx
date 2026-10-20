import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ConcurrencyConfigPage from './index';
import { getConcurrencyConfig, updateConcurrencyConfig } from './service';

const mocks = vi.hoisted(() => ({
  access: {
    canViewPlatformAiConcurrency: true,
    canEditPlatformAiConcurrency: true,
  },
}));
vi.mock('@umijs/max', () => ({ useAccess: () => mocks.access }));
vi.mock('./service', () => ({
  getConcurrencyConfig: vi.fn(),
  updateConcurrencyConfig: vi.fn(),
}));

const config = {
  settings: {
    teamTextLimit: 17,
    teamImageLimit: 19,
    teamVideoLimit: 23,
    modelLimit: 29,
    providerLimit: 31,
    textWorkers: 3,
    imageWorkers: 5,
    videoSubmitWorkers: 7,
    videoQueryWorkers: 9,
  },
  databaseMaxConnections: 43,
};

const textInput = () =>
  screen.getByRole('spinbutton', { name: '文本并行上限' });
const saveButton = () => screen.getByRole('button', { name: /保存配置/ });

describe('ConcurrencyConfigPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mocks.access.canViewPlatformAiConcurrency = true;
    mocks.access.canEditPlatformAiConcurrency = true;
    vi.mocked(getConcurrencyConfig).mockResolvedValue(config);
    vi.mocked(updateConcurrencyConfig).mockResolvedValue(config);
  });

  it('loads server values without saving and preserves edits across background renders', async () => {
    let resolveLoad!: (value: typeof config) => void;
    vi.mocked(getConcurrencyConfig).mockReturnValue(
      new Promise((resolve) => {
        resolveLoad = resolve;
      }),
    );
    const view = render(<ConcurrencyConfigPage />, { wrapper: App });
    expect(screen.getByRole('status')).toHaveTextContent('正在读取配置');
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    await act(async () => resolveLoad(config));
    expect(textInput()).toHaveValue('17');
    expect(screen.getByLabelText('数据库连接池最大连接数')).toHaveTextContent(
      '43',
    );
    fireEvent.change(textInput(), { target: { value: '37' } });
    view.rerender(<ConcurrencyConfigPage />);
    expect(textInput()).toHaveValue('37');
    expect(getConcurrencyConfig).toHaveBeenCalledTimes(1);
    expect(updateConcurrencyConfig).not.toHaveBeenCalled();
  });

  it('requires view access and keeps viewers read-only even after re-render', async () => {
    mocks.access.canViewPlatformAiConcurrency = false;
    const view = render(<ConcurrencyConfigPage />, { wrapper: App });
    expect(getConcurrencyConfig).not.toHaveBeenCalled();
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    mocks.access.canViewPlatformAiConcurrency = true;
    mocks.access.canEditPlatformAiConcurrency = false;
    view.rerender(<ConcurrencyConfigPage />);
    await waitFor(() => expect(textInput()).toHaveValue('17'));
    expect(textInput()).toBeDisabled();
    expect(
      screen.queryByRole('button', { name: /保存配置/ }),
    ).not.toBeInTheDocument();
    expect(updateConcurrencyConfig).not.toHaveBeenCalled();
  });

  it('shows a read failure and retries without rendering made-up defaults', async () => {
    vi.mocked(getConcurrencyConfig).mockRejectedValueOnce(
      new Error('读取被拒绝'),
    );
    render(<ConcurrencyConfigPage />, { wrapper: App });
    expect(await screen.findByRole('alert')).toHaveTextContent('读取被拒绝');
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /保存配置/ }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    await waitFor(() => expect(textInput()).toHaveValue('17'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('saves all nine settings, shows pending state and uses the confirmed response', async () => {
    let resolveSave!: (value: typeof config) => void;
    vi.mocked(updateConcurrencyConfig).mockReturnValue(
      new Promise((resolve) => {
        resolveSave = resolve;
      }),
    );
    render(<ConcurrencyConfigPage />, { wrapper: App });
    await waitFor(() => expect(textInput()).toHaveValue('17'));
    fireEvent.change(textInput(), { target: { value: '37' } });
    fireEvent.click(saveButton());
    await waitFor(() =>
      expect(updateConcurrencyConfig).toHaveBeenCalledWith({
        ...config.settings,
        teamTextLimit: 37,
      }),
    );
    expect(screen.getByRole('button', { name: /保存中/ })).toBeDisabled();
    expect(textInput()).toBeDisabled();
    expect(screen.queryByText('配置已保存')).not.toBeInTheDocument();
    expect(screen.queryByText('配置保存成功')).not.toBeInTheDocument();
    await act(async () =>
      resolveSave({
        ...config,
        settings: { ...config.settings, teamTextLimit: 37 },
      }),
    );
    expect(screen.getByRole('status', { name: '保存状态' })).toHaveTextContent(
      '配置已保存',
    );
    expect(textInput()).toHaveValue('37');
    expect(await screen.findByText('配置保存成功')).toBeInTheDocument();
    fireEvent.change(textInput(), { target: { value: '38' } });
    expect(screen.queryByRole('status', { name: '保存状态' })).not.toBeInTheDocument();
  });

  it('retains edits on failed saves and only shows success after a successful retry', async () => {
    vi.mocked(updateConcurrencyConfig).mockRejectedValueOnce(
      new Error('保存被拒绝'),
    );
    render(<ConcurrencyConfigPage />, { wrapper: App });
    await waitFor(() => expect(textInput()).toHaveValue('17'));
    fireEvent.change(textInput(), { target: { value: '37' } });
    fireEvent.click(saveButton());
    expect(await screen.findByRole('alert')).toHaveTextContent('保存被拒绝');
    expect(screen.queryByText('配置已保存')).not.toBeInTheDocument();
    expect(screen.queryByText('配置保存成功')).not.toBeInTheDocument();
    expect(textInput()).toHaveValue('37');
    vi.mocked(updateConcurrencyConfig).mockResolvedValue({
      ...config,
      settings: { ...config.settings, teamTextLimit: 37 },
    });
    fireEvent.click(saveButton());
    await waitFor(() =>
      expect(
        screen.getByRole('status', { name: '保存状态' }),
      ).toHaveTextContent('配置已保存'),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await screen.findByText('配置保存成功')).toBeInTheDocument();
  });

  it.each([
    ['文本并行上限', 'abc', 10000],
    ['文本并行上限', '1e309', 10000],
    ['文本并行上限', '0', 10000],
    ['图片并行上限', '-1', 10000],
    ['视频在途上限', '1.5', 10000],
    ['服务商请求总并发', '10001', 10000],
    ['单模型执行配额', '', 10000],
    ['文本处理并行数', '201', 200],
    ['图片处理并行数', '2.5', 200],
    ['视频提交并行数', '0', 200],
    ['视频查询并行数', '-1', 200],
  ])('rejects invalid %s values before any save', async (label, value, max) => {
    render(<ConcurrencyConfigPage />, { wrapper: App });
    await waitFor(() => expect(textInput()).toHaveValue('17'));
    fireEvent.change(screen.getByRole('spinbutton', { name: label }), {
      target: { value },
    });
    fireEvent.click(saveButton());
    expect(
      await screen.findByText(`请输入 1–${max} 的正整数`),
    ).toBeInTheDocument();
    expect(updateConcurrencyConfig).not.toHaveBeenCalled();
  });
});
