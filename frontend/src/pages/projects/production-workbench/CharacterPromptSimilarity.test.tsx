import '@root/tests/mockPointEstimate';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CharacterPromptSimilarity from './CharacterPromptSimilarity';
import {
  applyCharacterSimilarityPrompt,
  type CharacterSimilarityStatus,
  queryCharacterSimilarity,
  submitCharacterSimilarity,
} from './character-similarity-service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('./character-similarity-service', () => ({
  applyCharacterSimilarityPrompt: vi.fn(),
  queryCharacterSimilarity: vi.fn(),
  submitCharacterSimilarity: vi.fn(),
}));

const completed = {
  status: 'SUCCEEDED',
  id: 1,
  executionId: 99,
  characters: [
    { id: 11, name: '马强', prompt: '长窄脸，细直眉' },
    { id: 12, name: '李明', prompt: '窄长脸，平直细眉' },
  ],
  analysis: {
    summary: '两人的面部轮廓与眉形较接近。',
    pairs: [
      {
        leftId: 11,
        rightId: 12,
        score: 88,
        sharedFeatures: ['长窄脸、细直眉'],
        differences: ['眼形不同'],
        suggestions: ['调整下颌与眉眼结构'],
      },
    ],
    insufficient: [],
    hasMore: false,
    revisions: [
      {
        characterId: 11,
        originalFace: '长窄脸，细直眉',
        face: '长方脸，宽下颌，浓短眉',
        prompt:
          '## 任务：角色设定图\n- **脸型**: 长方脸，宽下颌，浓短眉\n- **年龄**: 未明确\n- **上身着装**: 卡其衬衫',
        changes: ['拉开下颌与眉形差异'],
      },
    ],
  },
} satisfies CharacterSimilarityStatus;
const show = (count = 2, onApplied?: () => Promise<unknown>) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <App>
        <CharacterPromptSimilarity
          projectId={56}
          characterCount={count}
          onApplied={onApplied}
        />
      </App>
    </ConfigProvider>,
  );

describe('character prompt similarity', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({
      data: { status: 'IDLE' },
    });
    vi.mocked(submitCharacterSimilarity).mockResolvedValue({
      data: { id: 99, status: 'PENDING' },
    });
    vi.mocked(applyCharacterSimilarityPrompt).mockResolvedValue({
      data: { characterId: 11, prompt: completed.analysis.revisions[0].prompt },
    });
  });

  it('only reads previous results when opened and waits for an explicit analysis request', async () => {
    show();
    expect(queryCharacterSimilarity).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '开始检查' })).toBeEnabled(),
    );
    expect(queryCharacterSimilarity).toHaveBeenCalledWith(56);
    expect(submitCharacterSimilarity).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '开始检查' }));
    await waitFor(() =>
      expect(submitCharacterSimilarity).toHaveBeenCalledTimes(1),
    );
  });

  it('shows similar pairs, source-specific reasons and concrete suggestions', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({ data: completed });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    expect(await screen.findByText('马强 / 李明')).toBeInTheDocument();
    expect(screen.getByText('88 分 · 高度相似')).toBeInTheDocument();
    expect(screen.getByText('长窄脸、细直眉')).toBeInTheDocument();
    expect(screen.getByText('调整下颌与眉眼结构')).toBeInTheDocument();
    expect(submitCharacterSimilarity).not.toHaveBeenCalled();
  });

  it('warns when prompts changed or incomplete settings prevent a confident comparison', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({
      data: {
        ...completed,
        stale: true,
        analysis: {
          ...completed.analysis,
          insufficient: [{ characterId: 11, reason: '年龄未明确' }],
        },
      },
    });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    expect(await screen.findByText(/设定已变化/)).toBeInTheDocument();
    expect(screen.getByText(/年龄未明确/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新检查' })).toBeEnabled();
  });

  it('does not present an unreadable result as a clean report and lets the user retry the read', async () => {
    vi.mocked(queryCharacterSimilarity)
      .mockRejectedValueOnce(new Error('网络连接失败'))
      .mockResolvedValue({ data: completed });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    expect(await screen.findByText('网络连接失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始检查' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
    expect(await screen.findByText('马强 / 李明')).toBeInTheDocument();
    expect(submitCharacterSimilarity).not.toHaveBeenCalled();
  });

  it('continues reading a durable running check without submitting another one', async () => {
    vi.mocked(queryCharacterSimilarity)
      .mockResolvedValueOnce({ data: { status: 'RUNNING', executionId: 99 } })
      .mockResolvedValue({ data: completed });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    expect(await screen.findByText(/关闭后仍会继续/)).toBeInTheDocument();
    await waitFor(
      () => expect(screen.getByText('马强 / 李明')).toBeInTheDocument(),
      { timeout: 5000 },
    );
    expect(submitCharacterSimilarity).not.toHaveBeenCalled();
  });

  it('requires at least two characters before opening a comparison', () => {
    show(1);
    expect(
      screen.getByRole('button', { name: '提示词相似度检查' }),
    ).toBeDisabled();
    expect(queryCharacterSimilarity).not.toHaveBeenCalled();
  });

  it('provides a complete editable revision, original text and face comparison', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({ data: completed });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    const editor = await screen.findByRole('textbox', {
      name: '修改后的角色提示词',
    });
    expect(editor).toHaveValue(completed.analysis.revisions[0].prompt);
    expect(screen.getByText('拉开下颌与眉形差异')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '原文' }));
    expect(screen.getByRole('textbox', { name: '原始角色提示词' })).toHaveValue(
      '长窄脸，细直眉',
    );
    expect(screen.getByRole('button', { name: '复制原文' })).toBeEnabled();
    expect(applyCharacterSimilarityPrompt).not.toHaveBeenCalled();
  });

  it('applies only the explicitly reviewed draft and refreshes the workbench', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({ data: completed });
    const onApplied = vi.fn().mockResolvedValue(undefined);
    show(2, onApplied);
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    const editor = await screen.findByRole('textbox', {
      name: '修改后的角色提示词',
    });
    const draft = `${completed.analysis.revisions[0].prompt}\n- **鞋子**: 黑色工作鞋`;
    fireEvent.change(editor, { target: { value: draft } });
    fireEvent.click(screen.getByRole('button', { name: '应用到马强' }));
    await waitFor(() =>
      expect(applyCharacterSimilarityPrompt).toHaveBeenCalledWith(
        56,
        1,
        11,
        draft,
      ),
    );
    expect(onApplied).toHaveBeenCalledTimes(1);
    expect(
      await screen.findByRole('button', { name: '已应用' }),
    ).toBeDisabled();
    expect(editor).toHaveAttribute('readonly');
    expect(submitCharacterSimilarity).not.toHaveBeenCalled();
  });

  it('keeps the draft on an application conflict and does not claim success', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({ data: completed });
    vi.mocked(applyCharacterSimilarityPrompt).mockRejectedValue(
      new Error('这个角色的提示词已被修改，请重新检查'),
    );
    const onApplied = vi.fn().mockResolvedValue(undefined);
    show(2, onApplied);
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    const editor = await screen.findByRole('textbox', {
      name: '修改后的角色提示词',
    });
    fireEvent.click(screen.getByRole('button', { name: '应用到马强' }));
    expect(
      await screen.findByText('这个角色的提示词已被修改，请重新检查'),
    ).toBeInTheDocument();
    expect(editor).toHaveValue(completed.analysis.revisions[0].prompt);
    expect(onApplied).not.toHaveBeenCalled();
  });

  it('explains historical reports without revisions instead of inventing a modified prompt', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({
      data: {
        ...completed,
        analysis: { ...completed.analysis, revisions: undefined },
      },
    });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    expect(await screen.findByText(/报告未含修改稿/)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '应用到马强' }),
    ).not.toBeInTheDocument();
  });

  it('opens directly in the draft and preserves edits while switching to a single explanation view', async () => {
    vi.mocked(queryCharacterSimilarity).mockResolvedValue({ data: completed });
    show();
    fireEvent.click(screen.getByRole('button', { name: '提示词相似度检查' }));
    const editor = await screen.findByRole('textbox', {
      name: '修改后的角色提示词',
    });
    const edited = `${completed.analysis.revisions[0].prompt}\n补充细节`;
    fireEvent.change(editor, { target: { value: edited } });
    fireEvent.click(screen.getByRole('button', { name: '检查说明' }));
    await waitFor(() =>
      expect(
        screen.getByRole('region', { name: '角色差异说明' }),
      ).toBeVisible(),
    );
    expect(
      screen.queryByRole('textbox', { name: '修改后的角色提示词' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('长窄脸、细直眉')).toBeVisible();
    expect(screen.getByText('调整下颌与眉眼结构')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '修改稿' }));
    expect(
      screen.getByRole('textbox', { name: '修改后的角色提示词' }),
    ).toHaveValue(edited);
    expect(screen.getByRole('button', { name: '应用到马强' })).toBeEnabled();
    expect(applyCharacterSimilarityPrompt).not.toHaveBeenCalled();
  });
});
