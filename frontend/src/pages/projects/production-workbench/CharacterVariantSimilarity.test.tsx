import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CharacterVariantSimilarity from './CharacterVariantSimilarity';
import {
  type CharacterSimilarityStatus,
  queryCharacterVariantResolution,
  resolveCharacterVariants,
} from './character-similarity-service';

vi.mock('./character-similarity-service', () => ({
  queryCharacterVariantResolution: vi.fn(),
  resolveCharacterVariants: vi.fn(),
}));
const left = {
  id: 21,
  name: '墨蓝色工装外套',
  appearance: '深蓝工装',
  prompt: '完整左侧提示词',
  isPrimary: false,
  generationStatus: 'NOT_GENERATED',
  episodeNumbers: [1, 2],
};
const right = {
  ...left,
  id: 22,
  name: '墨蓝工装外套',
  prompt: '完整右侧提示词',
  episodeNumbers: [3],
};
const preflight = {
  fingerprint: 'fresh-version',
  characterId: 11,
  characterName: '马强',
  left,
  right,
  mergedEpisodeNumbers: [1, 2, 3],
  affectedStoryboardCount: 6,
  voiceConflictCount: 2,
  recommendedKeptVariantId: 22,
  canMerge: true,
};
const report = (patch = {}): CharacterSimilarityStatus => ({
  id: 7,
  status: 'SUCCEEDED',
  characters: [
    { id: 11, name: '马强', prompt: '主体提示词', variants: [left, right] },
  ],
  analysis: {
    summary: '疑似重复',
    pairs: [],
    insufficient: [],
    hasMore: false,
    variantPairs: [
      {
        characterId: 11,
        leftVariantId: 21,
        rightVariantId: 22,
        score: 96,
        sharedFeatures: ['同款外套'],
        differences: [],
        suggestions: ['核实后合并'],
        ...patch,
      },
    ],
  },
});
const show = (status = report(), disabled = false) => {
  const onResolved = vi.fn().mockResolvedValue(undefined);
  const onInspect = vi.fn();
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <CharacterVariantSimilarity
          projectId={56}
          checkId={7}
          status={status}
          disabled={disabled}
          onResolved={onResolved}
          onInspect={onInspect}
        />
      </App>
    </ConfigProvider>,
  );
  return { onResolved, onInspect };
};
const openMerge = async () => {
  fireEvent.click(screen.getByRole('button', { name: '合并形态' }));
  return screen.findByRole('dialog', { name: '合并角色形态' });
};
describe('character variant decisions', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(queryCharacterVariantResolution).mockResolvedValue({
      data: preflight,
    });
    vi.mocked(resolveCharacterVariants).mockImplementation(
      async (_project, _check, _character, data) => ({
        data: { ...data, characterId: 11 },
      }),
    );
  });
  it('only reads preflight; cancellation never writes', async () => {
    show();
    const dialog = await openMerge();
    expect(
      await within(dialog).findByText('涉及分镜：6 个'),
    ).toBeInTheDocument();
    expect(queryCharacterVariantResolution).toHaveBeenCalledWith(56, 7, 11, {
      leftVariantId: 21,
      rightVariantId: 22,
    });
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    expect(resolveCharacterVariants).not.toHaveBeenCalled();
  });
  it('selects recommendation, submits once, then only inspects kept variant', async () => {
    const { onResolved, onInspect } = show();
    const dialog = await openMerge();
    expect(
      await within(dialog).findByRole('radio', { name: '保留 墨蓝工装外套' }),
    ).toBeChecked();
    const confirm = within(dialog).getByRole('button', { name: '确认合并' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    await waitFor(() => expect(onResolved).toHaveBeenCalledTimes(1));
    expect(resolveCharacterVariants).toHaveBeenCalledTimes(1);
    expect(resolveCharacterVariants).toHaveBeenCalledWith(56, 7, 11, {
      leftVariantId: 21,
      rightVariantId: 22,
      decision: 'MERGED',
      keptVariantId: 22,
      fingerprint: 'fresh-version',
    });
    expect(
      screen.queryByRole('button', { name: '查看此形态' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看保留形态' }));
    expect(onInspect).toHaveBeenCalledWith(11, 22);
    expect(screen.getByText('待处理 0 · 已处理 1')).toBeInTheDocument();
  });
  it('forces default retention even if recommendation differs', async () => {
    vi.mocked(queryCharacterVariantResolution).mockResolvedValue({
      data: { ...preflight, left: { ...left, isPrimary: true } },
    });
    show();
    const dialog = await openMerge();
    expect(
      await within(dialog).findByRole('radio', { name: '保留 墨蓝色工装外套' }),
    ).toBeChecked();
    expect(
      within(dialog).getByRole('radio', { name: '保留 墨蓝工装外套' }),
    ).toBeDisabled();
    expect(
      within(dialog).getByText('此组包含默认形态，必须保留默认形态。'),
    ).toBeInTheDocument();
    expect(within(dialog).getByText('默认形态 · 将保留')).toBeInTheDocument();
    expect(within(dialog).getByText('合并后移除')).toBeInTheDocument();
  });
  it('allows changing the retained non-default variant', async () => {
    show();
    const dialog = await openMerge();
    const leftRadio = await within(dialog).findByRole('radio', {
      name: '保留 墨蓝色工装外套',
    });
    expect(
      within(dialog).getByText('将保留此形态').parentElement,
    ).toHaveTextContent('墨蓝工装外套');
    expect(
      within(dialog).getByText('合并后移除').parentElement,
    ).toHaveTextContent('墨蓝色工装外套');
    fireEvent.click(leftRadio);
    expect(
      within(dialog).getByText('将保留此形态').parentElement,
    ).toHaveTextContent('墨蓝色工装外套');
    expect(
      within(dialog).getByText('合并后移除').parentElement,
    ).toHaveTextContent('墨蓝工装外套');
    fireEvent.click(within(dialog).getByRole('button', { name: '确认合并' }));
    await waitFor(() =>
      expect(resolveCharacterVariants).toHaveBeenCalledWith(
        56,
        7,
        11,
        expect.objectContaining({ keptVariantId: 21 }),
      ),
    );
  });
  it('permits unchanged stale pairs, fetches fresh fingerprint and persists DISTINCT once', async () => {
    const { onResolved } = show({
      ...report({ canResolve: true }),
      stale: true,
    });
    fireEvent.click(screen.getByRole('button', { name: '保留为不同形态' }));
    await waitFor(() => expect(onResolved).toHaveBeenCalledTimes(1));
    expect(resolveCharacterVariants).toHaveBeenCalledWith(56, 7, 11, {
      leftVariantId: 21,
      rightVariantId: 22,
      decision: 'DISTINCT',
      fingerprint: 'fresh-version',
    });
    expect(
      screen.queryByRole('button', { name: '保留为不同形态' }),
    ).not.toBeInTheDocument();
    expect(screen.getAllByText('已保留为不同形态').length).toBeGreaterThan(0);
  });
  it.each([
    { canResolve: false },
    {},
  ])('disables invalid or historical stale pairs %j', (patch) => {
    show({ ...report(patch), stale: true });
    expect(screen.getByRole('button', { name: '合并形态' })).toBeDisabled();
    expect(
      screen.getByRole('button', { name: '保留为不同形态' }),
    ).toBeDisabled();
  });
  it('retries preflight failures and respects canMerge=false', async () => {
    vi.mocked(queryCharacterVariantResolution)
      .mockRejectedValueOnce(new Error('预检失败'))
      .mockResolvedValueOnce({
        data: {
          ...preflight,
          canMerge: false,
          disabledReason: '形态已变化，请重新检查',
        },
      });
    show();
    const dialog = await openMerge();
    expect(await within(dialog).findByText('预检失败')).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', { name: '确认合并' }),
    ).toBeDisabled();
    fireEvent.click(within(dialog).getByRole('button', { name: '重新读取' }));
    expect(
      await within(dialog).findByText('形态已变化，请重新检查'),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', { name: '确认合并' }),
    ).toBeDisabled();
  });
  it('requires fresh preflight after a failed submit and never claims success', async () => {
    vi.mocked(resolveCharacterVariants).mockRejectedValueOnce(
      new Error('版本冲突，请刷新'),
    );
    const { onResolved } = show();
    const dialog = await openMerge();
    await within(dialog).findByRole('radio', { name: '保留 墨蓝工装外套' });
    fireEvent.click(within(dialog).getByRole('button', { name: '确认合并' }));
    expect(
      await within(dialog).findByText('版本冲突，请刷新'),
    ).toBeInTheDocument();
    expect(onResolved).not.toHaveBeenCalled();
    expect(
      within(dialog).getByRole('button', { name: '确认合并' }),
    ).toBeDisabled();
    fireEvent.click(within(dialog).getByRole('button', { name: '重新读取' }));
    await waitFor(() =>
      expect(queryCharacterVariantResolution).toHaveBeenCalledTimes(2),
    );
    await waitFor(() =>
      expect(
        within(dialog).getByRole('button', { name: '确认合并' }),
      ).toBeEnabled(),
    );
  });
  it('keeps confirmation disabled while loading and ignores a canceled preflight response', async () => {
    let complete!: (value: { data: typeof preflight }) => void;
    vi.mocked(queryCharacterVariantResolution).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    show();
    const dialog = await openMerge();
    expect(
      within(dialog).getByRole('button', { name: '确认合并' }),
    ).toBeDisabled();
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    complete({ data: preflight });
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: '合并角色形态' }),
      ).not.toBeInTheDocument(),
    );
    expect(resolveCharacterVariants).not.toHaveBeenCalled();
  });

  it('fetches preflight again for a DISTINCT retry after failure and prevents duplicate requests', async () => {
    vi.mocked(queryCharacterVariantResolution).mockRejectedValueOnce(
      new Error('读取失败'),
    );
    const { onResolved } = show();
    const button = screen.getByRole('button', { name: '保留为不同形态' });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(await screen.findByText('读取失败')).toBeInTheDocument();
    expect(resolveCharacterVariants).not.toHaveBeenCalled();
    expect(onResolved).not.toHaveBeenCalled();
    expect(queryCharacterVariantResolution).toHaveBeenCalledTimes(1);
    // Antd also guards clicks with its internal loading state.
    await waitFor(() => {
      const retry = screen.getByRole('button', { name: '保留为不同形态' });
      expect(retry).toBeEnabled();
      expect(retry).not.toHaveClass('ant-btn-loading');
    });
    fireEvent.click(screen.getByRole('button', { name: '保留为不同形态' }));
    await waitFor(() =>
      expect(queryCharacterVariantResolution).toHaveBeenCalledTimes(2),
    );
    await waitFor(() => expect(onResolved).toHaveBeenCalledTimes(1));
    expect(queryCharacterVariantResolution).toHaveBeenCalledTimes(2);
    expect(resolveCharacterVariants).toHaveBeenCalledTimes(1);
  });

  it('locks a default variant on the right as well', async () => {
    vi.mocked(queryCharacterVariantResolution).mockResolvedValue({
      data: {
        ...preflight,
        right: { ...right, isPrimary: true },
        recommendedKeptVariantId: 21,
      },
    });
    show();
    const dialog = await openMerge();
    expect(
      await within(dialog).findByRole('radio', { name: '保留 墨蓝工装外套' }),
    ).toBeChecked();
    expect(
      within(dialog).getByRole('radio', { name: '保留 墨蓝色工装外套' }),
    ).toBeDisabled();
  });

  it('allows inspecting persisted DISTINCT pairs independently of resolution permission', () => {
    const { onInspect } = show({
      ...report({
        resolution: 'DISTINCT',
        canResolve: false,
        canInspect: true,
      }),
      stale: true,
    });
    const inspectButtons = screen.getAllByRole('button', {
      name: '查看此形态',
    });
    expect(inspectButtons).toHaveLength(2);
    for (const button of inspectButtons) {
      expect(button).toBeEnabled();
      fireEvent.click(button);
    }
    expect(onInspect).toHaveBeenNthCalledWith(1, 11, 21);
    expect(onInspect).toHaveBeenNthCalledWith(2, 11, 22);
    expect(
      screen.queryByRole('button', { name: '合并形态' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '保留为不同形态' }),
    ).not.toBeInTheDocument();
    expect(queryCharacterVariantResolution).not.toHaveBeenCalled();
    expect(resolveCharacterVariants).not.toHaveBeenCalled();
  });

  it.each([
    {
      response: { data: { errorMessage: '形态正在生成，暂时无法合并' } },
      info: { errorMessage: '次级信息' },
      expected: '形态正在生成，暂时无法合并',
    },
    {
      info: { errorMessage: '形态已变化，请重新检查' },
      expected: '形态已变化，请重新检查',
    },
  ])('shows the actionable backend error from a failed preflight: $expected', async ({
    expected,
    ...payload
  }) => {
    vi.mocked(queryCharacterVariantResolution).mockRejectedValueOnce(
      Object.assign(new Error('Request failed with status code 409'), payload),
    );
    const { onResolved } = show();
    const dialog = await openMerge();
    expect(await within(dialog).findByText(expected)).toBeInTheDocument();
    expect(
      within(dialog).queryByText('Request failed with status code 409'),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', { name: '确认合并' }),
    ).toBeDisabled();
    expect(resolveCharacterVariants).not.toHaveBeenCalled();
    expect(onResolved).not.toHaveBeenCalled();
  });

  it.each([
    {
      response: { data: { errorMessage: '合并目标已删除，请重新检查' } },
      info: { errorMessage: '次级信息' },
      expected: '合并目标已删除，请重新检查',
    },
    {
      info: { errorMessage: '剧本版本已变化，请重新检查' },
      expected: '剧本版本已变化，请重新检查',
    },
  ])('shows the actionable backend error from a failed decision: $expected', async ({
    expected,
    ...payload
  }) => {
    vi.mocked(resolveCharacterVariants).mockRejectedValueOnce(
      Object.assign(new Error('Request failed with status code 409'), payload),
    );
    const { onResolved } = show();
    fireEvent.click(screen.getByRole('button', { name: '保留为不同形态' }));
    expect(await screen.findByText(expected)).toBeInTheDocument();
    expect(
      screen.queryByText('Request failed with status code 409'),
    ).not.toBeInTheDocument();
    expect(onResolved).not.toHaveBeenCalled();
    await waitFor(() => {
      const retry = screen.getByRole('button', { name: '保留为不同形态' });
      expect(retry).toBeEnabled();
      expect(retry).not.toHaveClass('ant-btn-loading');
    });
  });

  it('disables mutation without permission', () => {
    show(report(), true);
    expect(screen.getByRole('button', { name: '合并形态' })).toBeDisabled();
    expect(
      screen.getByRole('button', { name: '保留为不同形态' }),
    ).toBeDisabled();
  });
  it('renders persisted decisions without offering another mutation', () => {
    show(report({ resolution: 'MERGED', keptVariantId: 21 }));
    expect(
      screen.queryByRole('button', { name: '合并形态' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看保留形态' })).toBeEnabled();
  });
});
