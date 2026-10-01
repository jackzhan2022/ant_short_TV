import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';
import InspirationManagementDrawer from './InspirationManagementDrawer';

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  query: vi.fn(),
  compress: vi.fn(),
}));
vi.mock('./service', () => ({
  createManagedInspiration: mocks.create,
  queryManagedInspirations: mocks.query,
  updateManagedInspiration: vi.fn(),
  updateManagedInspirationStatus: vi.fn(),
  deleteManagedInspiration: vi.fn(),
  reorderManagedInspirations: vi.fn(),
}));
vi.mock('./imageCompression', () => ({
  compressImage: mocks.compress,
  formatFileSize: (size: number) => String(size),
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.query.mockResolvedValue({ data: { records: [] } });
});

it('uploads the original file and retains recovery state when saving is retried', async () => {
  const original = new File(['original png bytes'], 'reference.png', {
    type: 'image/png',
  });
  mocks.compress.mockResolvedValue({
    file: new File(['resized jpeg bytes'], 'reference.jpg', {
      type: 'image/jpeg',
    }),
    originalBytes: original.size,
    compressedBytes: 10,
    width: 1920,
    height: 1080,
  });
  const handle = { cancel: vi.fn() };
  mocks.create
    .mockImplementationOnce((_values, recovery) => {
      recovery?.onHandle?.(handle);
      return Promise.reject(new Error('网络已断开'));
    })
    .mockResolvedValue({ success: true, data: { id: 1 } });
  const changed = vi.fn();
  const view = render(
    <App>
      <InspirationManagementDrawer open onClose={vi.fn()} onChanged={changed} />
    </App>,
  );
  const input = view.baseElement.querySelector('input[type="file"]');
  expect(input).not.toBeNull();
  fireEvent.change(input as HTMLInputElement, {
    target: { files: [original] },
  });
  fireEvent.change(screen.getByLabelText('标题'), {
    target: { value: '灵感' },
  });
  fireEvent.change(screen.getByLabelText('完整提示词'), {
    target: { value: '原图提示词' },
  });
  fireEvent.click(screen.getByRole('button', { name: /添加内容/ }));
  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
  expect(mocks.create.mock.calls[0][0].file).toBe(original);
  expect(mocks.compress).not.toHaveBeenCalled();
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: /添加内容/ }),
    ).not.toHaveAttribute('disabled'),
  );
  fireEvent.click(screen.getByRole('button', { name: /添加内容/ }));
  await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
  expect(mocks.create.mock.calls[1][1].handle).toBe(handle);
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
  view.unmount();
});
