import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from 'antd';
import AssetVariantGenerationModal from './AssetVariantGenerationModal';
const mocks = vi.hoisted(() => ({ query: vi.fn(), select: vi.fn(), error: vi.fn() }));
vi.mock('./service', () => ({ queryAiImageResults: mocks.query, selectAiImageResult: mocks.select }));


beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', undefined);
    vi.resetAllMocks();
    vi.spyOn(App, 'useApp').mockReturnValue({ message: { error: mocks.error } } as any);
    mocks.query.mockResolvedValue({ success: true, data: { data: [], total: 0, current: 1, pageSize: 20 } });
    mocks.select.mockResolvedValue({ success: true });
  });
afterEach(() => vi.unstubAllGlobals());

describe('AssetVariantGenerationModal', () => {
  it('shows the complete asset generator and submits edited values', () => {
    const onChange = vi.fn();
    const onSubmit = vi.fn();
    render(
      <AssetVariantGenerationModal
        open
        projectId={1}
        onImageSelected={vi.fn()}
        variant={{
          id: 12,
          assetType: 'CHARACTER',
          assetId: 1,
          name: '婚礼礼服',
          prompt: '原提示词',
          sourceType: 'USER',
          generationStatus: 'NOT_STARTED',
          currentImageUrl: '/variant.png',
          currentImageThumbnailUrl: '/variant-display.png',
          primary: false,
          usable: false,
        }}
        primaryImageUrl="/primary-display.png"
        imageModels={[{ id: 7, name: '图片模型A' }]}
        values={{
          prompt: '原提示词',
          modelId: undefined,
          aspectRatio: '16:9',
          imageCount: 1,
        }}
        submitting={false}
        onChange={onChange}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    expect(screen.getByRole('img', { name: '婚礼礼服当前图' })).toHaveAttribute(
      'src',
      '/variant-display.png',
    );
    expect(screen.getByRole('img', { name: '婚礼礼服引用图' })).toHaveAttribute(
      'src',
      '/primary-display.png',
    );
    fireEvent.change(screen.getByLabelText('婚礼礼服生成提示词'), {
      target: { value: '新提示词' },
    });
    fireEvent.change(screen.getByLabelText('图片模型'), {
      target: { value: '7' },
    });
    fireEvent.change(screen.getByLabelText('画面比例'), {
      target: { value: '3:4' },
    });
    fireEvent.change(screen.getByLabelText('生成数量'), {
      target: { value: '2' },
    });
    fireEvent.click(screen.getByRole('button', { name: '提交婚礼礼服生成' }));

    expect(onChange).toHaveBeenNthCalledWith(1, { prompt: '新提示词' });
    expect(onChange).toHaveBeenNthCalledWith(2, { modelId: 7 });
    expect(onChange).toHaveBeenNthCalledWith(3, { aspectRatio: '3:4' });
    expect(onChange).toHaveBeenNthCalledWith(4, { imageCount: 2 });
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('uses the current asset type in the shared dialog title', () => {
    render(
      <AssetVariantGenerationModal
        open
        projectId={1}
        onImageSelected={vi.fn()}
        variant={{
          id: 21,
          assetType: 'SCENE',
          assetId: 2,
          name: '夜景',
          sourceType: 'USER',
          generationStatus: 'NOT_STARTED',
          primary: true,
          usable: false,
        }}
        imageModels={[]}
        values={{ prompt: '', aspectRatio: '16:9', imageCount: 1 }}
        submitting={false}
        onChange={vi.fn()}
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByText('场景生成')).toBeInTheDocument();
  });

  it('does not render the original when its display rendition is unavailable', () => {
    render(
      <AssetVariantGenerationModal
        open
        projectId={1}
        onImageSelected={vi.fn()}
        variant={{
          id: 22,
          assetType: 'SCENE',
          assetId: 2,
          name: 'Original-only',
          sourceType: 'USER',
          generationStatus: 'GENERATING',
          currentImageUrl: '/original-only.png',
          primary: true,
          usable: false,
        }}
        imageModels={[]}
        values={{ prompt: '', aspectRatio: '16:9', imageCount: 1 }}
        submitting={false}
        onChange={vi.fn()}
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.queryByRole('img', { name: 'Original-only当前图' })).not.toBeInTheDocument();
    expect(screen.getByText('暂无当前图')).toBeInTheDocument();
  });
});

const variant = {
  id: 12, assetType: 'CHARACTER' as const, assetId: 1, name: '日常形象', sourceType: 'MANUAL',
  generationStatus: 'COMPLETED', generationTaskId: 30, currentImageResultId: 90,
  currentImageThumbnailUrl: '/current.png', primary: true, usable: true,
};
const result = (id: number, taskId: number | null = 20) => ({
  id, taskId, thumbnailUrl: `/history-${id}.png`, imageUrl: `/image-${id}`, targetType: 'VISUAL_VARIANT',
  targetId: 12, selected: false, status: 'ACTIVE',
});
const renderDialog = (overrides: Record<string, any> = {}) => {
  const callbacks = { onImageSelected: vi.fn(), onCancel: vi.fn(), onChange: vi.fn(), onSubmit: vi.fn() };
  const props = { open: true, projectId: 1, variant, imageModels: [],
    values: { prompt: '角色描述', aspectRatio: '16:9', imageCount: 1 }, submitting: false, ...callbacks, ...overrides };
  return { ...render(<AssetVariantGenerationModal {...props} />), props, ...callbacks };
};

it('loads only this variant history, previews locally, and binds only after confirmation', async () => {
  mocks.query.mockResolvedValue({ success: true, data: { data: [result(80), result(79, null)], total: 2 } });
  const view = renderDialog();
  fireEvent.click(await screen.findByRole('button', { name: '预览历史配图79' }));
  expect(screen.getByAltText('日常形象历史配图预览')).toHaveAttribute('src', '/history-79.png');
  expect(mocks.select).not.toHaveBeenCalled();
  expect(mocks.query).toHaveBeenCalledWith(1,
    { targetType: 'VISUAL_VARIANT', targetId: 12, current: 1, pageSize: 20 }, expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('button', { name: '确认角色' }));
  await waitFor(() => expect(view.onImageSelected).toHaveBeenCalledOnce());
  expect(mocks.select).toHaveBeenCalledWith(1, 79);
  expect(view.onCancel).toHaveBeenCalledOnce();
});

it('shows new results under latest and keeps the generator open after submitting', async () => {
  const view = renderDialog();
  fireEvent.click(screen.getByRole('button', { name: '提交日常形象生成' }));
  expect(view.onSubmit).toHaveBeenCalledOnce();
  expect(view.onCancel).not.toHaveBeenCalled();
  view.rerender(<AssetVariantGenerationModal {...view.props} variant={{ ...variant, generationTaskId: 31, generationStatus: 'GENERATING' }} />);
  expect(screen.getByRole('region', { name: '最新配图' })).toHaveTextContent('正在生成');
  expect(screen.getByRole('button', { name: '确认角色' })).toBeDisabled();
  mocks.query.mockResolvedValue({ success: true, data: { data: [result(92, 31), result(91, 31), result(80)], total: 3 } });
  view.rerender(<AssetVariantGenerationModal {...view.props} variant={{ ...variant, generationTaskId: 31, currentImageResultId: 92 }} />);
  expect(await screen.findByRole('button', { name: '预览最新配图91' })).toBeTruthy();
  expect(screen.getByRole('button', { name: '预览历史配图80' })).toBeTruthy();
});

it('retains the preview after confirmation failure and allows retry', async () => {
  mocks.query.mockResolvedValue({ success: true, data: { data: [result(80)], total: 1 } });
  mocks.select.mockRejectedValueOnce(new Error('选用失败'));
  const view = renderDialog();
  fireEvent.click(await screen.findByRole('button', { name: '预览历史配图80' }));
  fireEvent.click(screen.getByRole('button', { name: '确认角色' }));
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('选用失败'));
  await waitFor(() => expect(screen.getByRole('button', { name: '确认角色' })).toBeEnabled());
  expect(view.onCancel).not.toHaveBeenCalled();
  expect(screen.getByAltText('日常形象历史配图预览')).toHaveAttribute('src', '/history-80.png');
  fireEvent.click(screen.getByRole('button', { name: '确认角色' }));
  await waitFor(() => expect(view.onCancel).toHaveBeenCalledOnce());
});

it('ignores a history response after switching to another variant', async () => {
  let finish!: (value: unknown) => void;
  mocks.query.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
  const view = renderDialog();
  view.rerender(<AssetVariantGenerationModal {...view.props} variant={{ ...variant, id: 13, name: '礼服' }} />);
  await act(async () => finish({ success: true, data: { data: [result(80)], total: 1 } }));
  expect(screen.queryByRole('button', { name: '预览历史配图80' })).not.toBeInTheDocument();
  expect(screen.getByAltText('礼服当前图')).toBeTruthy();
});

it('retries history loading without clearing the current main image', async () => {
  mocks.query.mockRejectedValueOnce(new Error('offline'));
  renderDialog();
  fireEvent.click(await screen.findByRole('button', { name: '加载失败，重试' }));
  await waitFor(() => expect(mocks.query).toHaveBeenCalledTimes(2));
  expect(screen.getByAltText('日常形象当前图')).toHaveAttribute('src', '/current.png');
});
