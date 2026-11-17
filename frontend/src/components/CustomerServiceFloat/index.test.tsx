import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getConfig: vi.fn() }));
vi.mock('@/services/customerService', () => ({
  getCustomerService: mocks.getConfig,
}));

import CustomerServiceFloat from '.';

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getConfig.mockResolvedValue({
    qrCodeUrl: 'https://cdn.example/qr.png',
  });
});

it('shows a labelled help entry and fetches fresh configuration on every opening', async () => {
  render(<CustomerServiceFloat />);
  const trigger = screen.getByRole('button', { name: '获取帮助' });
  expect(trigger).toHaveTextContent('获取帮助');
  expect(mocks.getConfig).not.toHaveBeenCalled();
  fireEvent.click(trigger);
  expect(
    await screen.findByAltText('企业微信客服「联系我」二维码'),
  ).toHaveAttribute('src', 'https://cdn.example/qr.png');
  fireEvent.click(screen.getByRole('button', { name: '关闭客服卡片' }));
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  mocks.getConfig.mockResolvedValue({
    qrCodeUrl: 'https://cdn.example/replaced.png',
  });
  fireEvent.click(trigger);
  await waitFor(() =>
    expect(screen.getByAltText('企业微信客服「联系我」二维码')).toHaveAttribute(
      'src',
      'https://cdn.example/replaced.png',
    ),
  );
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
});

it('handles missing configuration and lets users retry a failed request', async () => {
  mocks.getConfig.mockRejectedValueOnce(new Error('network'));
  render(<CustomerServiceFloat />);
  fireEvent.click(screen.getByRole('button', { name: '获取帮助' }));
  expect(
    await screen.findByText('客服信息加载失败，请重试'),
  ).toBeInTheDocument();
  mocks.getConfig.mockResolvedValueOnce({ qrCodeUrl: '' });
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(
    await screen.findByText('客服暂未开放，请稍后再试'),
  ).toBeInTheDocument();
});

it('shows a retry action when the QR image cannot load', async () => {
  render(<CustomerServiceFloat />);
  fireEvent.click(screen.getByRole('button', { name: '获取帮助' }));
  fireEvent.error(await screen.findByAltText('企业微信客服「联系我」二维码'));
  expect(await screen.findByText('二维码加载失败，请重试')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(
    await screen.findByAltText('企业微信客服「联系我」二维码'),
  ).toBeInTheDocument();
});
