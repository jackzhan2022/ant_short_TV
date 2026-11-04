import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import ProjectArtStylePicker, { type ArtStyleSelection } from './index';

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), legacy: vi.fn() }));
vi.mock('./service', () => ({ queryArtStyles: mocks.catalog }));
vi.mock('@/pages/style-library/service', () => ({
  queryStyleLibrary: mocks.legacy,
}));

function Harness() {
  const [value, setValue] = useState<ArtStyleSelection>({
    visualStyleVersion: 'V1',
    visualStyle: '旧画风',
  });
  return (
    <>
      <ProjectArtStylePicker
        value={value}
        onChange={setValue}
        legacyPicker={<p>现有画风库</p>}
      />
      <output>{JSON.stringify(value)}</output>
    </>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.catalog.mockResolvedValue({
    data: [
      {
        code: 'realpeople_modern_city',
        name: '真人都市电影',
        category: '真人写实',
        description: '自然光与生活痕迹',
        imageUrl: '/api/style-library/v2/images/realpeople_modern_city',
      },
    ],
  });
});

it('keeps V1 default and restores each version selection when switching', async () => {
  render(<Harness />);
  expect(screen.getByRole('radio', { name: '画风 1.0' })).toBeChecked();
  expect(mocks.catalog).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('radio', { name: '画风 2.0' }));
  await waitFor(() => expect(mocks.catalog).toHaveBeenCalled());
  expect(screen.queryByText('现有画风库')).not.toBeInTheDocument();
  const choice = await screen.findByRole('button', {
    name: '选择真人都市电影',
  });
  expect(screen.getByAltText('真人都市电影示例图')).toHaveAttribute(
    'src',
    '/api/style-library/v2/images/realpeople_modern_city',
  );
  fireEvent.click(choice);
  expect(choice).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('status')).toHaveTextContent(
    'realpeople_modern_city',
  );
  expect(screen.getByText('自然光与生活痕迹')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '画风 1.0' }));
  expect(screen.getByRole('status')).toHaveTextContent('旧画风');
  fireEvent.click(screen.getByRole('radio', { name: '画风 2.0' }));
  expect(screen.getByRole('status')).toHaveTextContent(
    'realpeople_modern_city',
  );
});

it('shows a retry when the V2 catalog cannot be loaded', async () => {
  mocks.catalog.mockRejectedValueOnce(new Error('offline'));
  render(<Harness />);
  fireEvent.click(screen.getByRole('radio', { name: '画风 2.0' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('画风加载失败');
  fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
  await waitFor(() => expect(mocks.catalog).toHaveBeenCalledTimes(2));
  await waitFor(() =>
    expect(screen.queryByRole('alert')).not.toBeInTheDocument(),
  );
});
