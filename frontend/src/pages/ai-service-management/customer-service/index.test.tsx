import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canEditCustomerService: true },
  get: vi.fn(),
  save: vi.fn(),
  remove: vi.fn(),
}));
vi.mock('@umijs/max', () => ({ useAccess: () => mocks.access }));
vi.mock('@/services/customerService', () => ({
  getCustomerServiceConfig: mocks.get,
  saveCustomerService: mocks.save,
  removeCustomerService: mocks.remove,
}));

import CustomerServicePage from '.';

const uploadInput = (container: HTMLElement) => {
  const input = container.querySelector('input[type="file"]');
  if (!input) throw new Error('未找到文件选择入口');
  return input;
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.access.canEditCustomerService = true;
  mocks.get.mockResolvedValue({ qrCodeUrl: '' });
  mocks.save.mockResolvedValue({ qrCodeUrl: 'https://cdn.example/saved.png' });
  vi.stubGlobal(
    'URL',
    Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:qr-preview'),
      revokeObjectURL: vi.fn(),
    }),
  );
});

it('previews a selected file and saves only when the administrator confirms', async () => {
  const view = render(
    <App>
      <CustomerServicePage />
    </App>,
  );
  await screen.findByRole('button', { name: '选择二维码' });
  const file = new File(['png'], 'qr.png', { type: 'image/png' });
  fireEvent.change(uploadInput(view.container), {
    target: { files: [file] },
  });
  expect(await screen.findByAltText('客服二维码预览')).toHaveAttribute(
    'src',
    'blob:qr-preview',
  );
  expect(mocks.save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '保存二维码' }));
  await waitFor(() => expect(mocks.save).toHaveBeenCalledWith(file));
  await waitFor(() =>
    expect(screen.getByAltText('客服二维码预览')).toHaveAttribute(
      'src',
      'https://cdn.example/saved.png',
    ),
  );
});

it('keeps the selected file for retry when saving fails', async () => {
  mocks.save.mockRejectedValueOnce(new Error('保存失败'));
  const view = render(
    <App>
      <CustomerServicePage />
    </App>,
  );
  await screen.findByRole('button', { name: '选择二维码' });
  fireEvent.change(uploadInput(view.container), {
    target: { files: [new File(['png'], 'qr.png', { type: 'image/png' })] },
  });
  fireEvent.click(await screen.findByRole('button', { name: '保存二维码' }));
  expect(await screen.findByText('保存失败')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存二维码' })).toBeEnabled();
});

it('shows the configured QR to viewers without offering editing actions', async () => {
  mocks.access.canEditCustomerService = false;
  mocks.get.mockResolvedValue({ qrCodeUrl: 'https://cdn.example/current.png' });
  render(
    <App>
      <CustomerServicePage />
    </App>,
  );
  expect(await screen.findByAltText('客服二维码预览')).toHaveAttribute(
    'src',
    'https://cdn.example/current.png',
  );
  expect(
    screen.queryByRole('button', { name: '选择二维码' }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '保存二维码' }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '移除二维码' }),
  ).not.toBeInTheDocument();
});

it('requires confirmation before removing a configured QR', async () => {
  mocks.get.mockResolvedValue({ qrCodeUrl: 'https://cdn.example/current.png' });
  mocks.remove.mockResolvedValue({ qrCodeUrl: '' });
  render(
    <App>
      <CustomerServicePage />
    </App>,
  );
  fireEvent.click(await screen.findByRole('button', { name: '移除二维码' }));
  expect(mocks.remove).not.toHaveBeenCalled();
  fireEvent.click(await screen.findByRole('button', { name: /^移\s*除$/ }));
  await waitFor(() => expect(mocks.remove).toHaveBeenCalledOnce());
  expect(await screen.findByText('暂未配置客服二维码')).toBeInTheDocument();
});

it('rejects an unsupported file without enabling save', async () => {
  const view = render(
    <App>
      <CustomerServicePage />
    </App>,
  );
  await screen.findByRole('button', { name: '选择二维码' });
  fireEvent.change(uploadInput(view.container), {
    target: {
      files: [new File(['<svg/>'], 'qr.svg', { type: 'image/svg+xml' })],
    },
  });
  expect(
    await screen.findByText('请选择不超过 2 MB 的 PNG 或 JPG 图片'),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存二维码' })).toBeDisabled();
  expect(mocks.save).not.toHaveBeenCalled();
});
