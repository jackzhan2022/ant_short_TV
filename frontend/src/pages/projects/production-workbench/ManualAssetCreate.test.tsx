import { readFileSync } from 'node:fs';
import {
  act,
  configure,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ManualAssetCreate from './ManualAssetCreate';
import {
  createManualAsset,
  type ManualAssetOptions,
  queryManualAssetOptions,
} from './manual-assets';

configure({ asyncUtilTimeout: 5000 });
vi.mock('@/services/point-estimate', () => ({
  pointEstimatedRequest: vi.fn(),
}));
vi.mock('./manual-assets', async (original) => ({
  ...(await original<object>()),
  createManualAsset: vi.fn(),
  queryManualAssetOptions: vi.fn(),
}));
const definitions = JSON.parse(
  readFileSync(
    '../backend/src/main/resources/manual-assets/configuration.json',
    'utf8',
  ),
);
const options: ManualAssetOptions = {
  scriptId: 7,
  canCreate: true,
  disabledReason: '',
  definitions,
};
const create = vi.mocked(createManualAsset);
const query = vi.mocked(queryManualAssetOptions);
const onCreated = vi.fn();
function mount(
  type: 'CHARACTER' | 'SCENE' | 'PROP' = 'CHARACTER',
  projectId = 1,
) {
  return render(
    <App>
      <ManualAssetCreate
        projectId={projectId}
        type={type}
        characters={[]}
        blocked={false}
        onCreated={onCreated}
      />
    </App>,
  );
}
async function open(label = '角色') {
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: `新增${label}` }),
    ).not.toBeDisabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: `新增${label}` }));
  await screen.findByRole('dialog');
}
function fill(type: 'CHARACTER' | 'SCENE' | 'PROP') {
  fireEvent.change(
    screen.getByLabelText(
      `${{ CHARACTER: '角色', SCENE: '场景', PROP: '道具' }[type]}名称`,
    ),
    { target: { value: '新资产' } },
  );
  for (const field of definitions[type].fields)
    fireEvent.change(screen.getByLabelText(field.label), {
      target: { value: field.unknown || '具体外貌' },
    });
}
beforeEach(() => {
  vi.clearAllMocks();
  query.mockResolvedValue({ success: true, data: options });
  create.mockResolvedValue({
    success: true,
    data: { assetType: 'CHARACTER', assetId: 12, variantId: 13 },
  });
});
describe('manual asset creation', () => {
  it('prevents name-only creation and shows all 14 configuration fields', async () => {
    mount();
    await open();
    fireEvent.change(screen.getByLabelText('角色名称'), {
      target: { value: '新资产' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }));
    await screen.findByText('脸型不能为空且不能超过 200 字符。');
    expect(create).not.toHaveBeenCalled();
    for (const field of definitions.CHARACTER.fields)
      expect(screen.getByLabelText(field.label)).toBeInTheDocument();
  });
  it('retains values and request identity after failure and opens existing visual configuration after retry', async () => {
    create.mockRejectedValueOnce(new Error('服务器繁忙，请重试'));
    mount();
    await open();
    fill('CHARACTER');
    fireEvent.click(screen.getByRole('button', { name: '保存并配置形象' }));
    await screen.findByText('服务器繁忙，请重试');
    expect(screen.getByLabelText('角色名称')).toHaveValue('新资产');
    const request = create.mock.calls[0][1];
    fireEvent.click(screen.getByRole('button', { name: '保存并配置形象' }));
    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith(
        { assetType: 'CHARACTER', assetId: 12, variantId: 13 },
        true,
      ),
    );
    expect(create.mock.calls[1][1].requestId).toBe(request.requestId);
    expect(request.configuration).toHaveProperty('face', '具体外貌');
  });
  it('disables entry without current script or editing permission', async () => {
    query.mockResolvedValue({
      success: true,
      data: {
        ...options,
        canCreate: false,
        scriptId: null,
        disabledReason: '请先创建或导入剧本',
      },
    });
    mount();
    await waitFor(() =>
      expect(screen.getByTitle('请先创建或导入剧本')).toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: '新增角色' })).toBeDisabled();
  });
  it('saves scene configuration without automatically opening the visual panel', async () => {
    create.mockResolvedValue({
      success: true,
      data: { assetType: 'SCENE', assetId: 14, variantId: 15 },
    });
    mount('SCENE');
    await open('场景');
    fill('SCENE');
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }));
    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith(
        { assetType: 'SCENE', assetId: 14, variantId: 15 },
        false,
      ),
    );
    expect(create.mock.calls[0][1].configuration).toHaveProperty('space');
  });
  it('requires confirmation when cancelling edited configuration', async () => {
    mount();
    await open();
    fireEvent.change(screen.getByLabelText('脸型'), {
      target: { value: '鹅蛋脸' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^取\s*消$/ }));
    expect(
      (await screen.findAllByText('放弃本次新增？')).length,
    ).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: '继续填写' }));
    expect(screen.getByLabelText('脸型')).toHaveValue('鹅蛋脸');
    expect(create).not.toHaveBeenCalled();
  });
  it('submits once while pending and ignores success after leaving the project', async () => {
    let finish:
      | ((value: Awaited<ReturnType<typeof createManualAsset>>) => void)
      | undefined;
    create.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const view = mount();
    await open();
    fill('CHARACTER');
    const save = screen.getByRole('button', { name: '保存并配置形象' });
    fireEvent.click(save);
    fireEvent.click(save);
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => {
      finish?.({
        success: true,
        data: { assetType: 'CHARACTER', assetId: 12, variantId: 13 },
      });
    });
    expect(onCreated).not.toHaveBeenCalled();
  });
  it('supports props and does not ask users for AI-only metadata', async () => {
    create.mockResolvedValue({
      success: true,
      data: { assetType: 'PROP', assetId: 16, variantId: 17 },
    });
    mount('PROP');
    await open('道具');
    fill('PROP');
    expect(screen.getByLabelText('持有角色（选填）')).toBeInTheDocument();
    expect(screen.queryByLabelText('evidence')).not.toBeInTheDocument();
    expect(screen.getByLabelText('自动生成的提示词')).toHaveTextContent(
      '#FFFFFF',
    );
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }));
    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith(
        { assetType: 'PROP', assetId: 16, variantId: 17 },
        false,
      ),
    );
  });
});
