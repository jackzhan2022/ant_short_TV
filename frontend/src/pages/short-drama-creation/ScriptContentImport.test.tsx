import { App } from 'antd';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ScriptContentImport from './ScriptContentImport';

const mocks = vi.hoisted(() => ({
  parseScriptFile: vi.fn(),
  queryReviewProject: vi.fn(),
  queryReviewProjectSummaries: vi.fn(),
  queryReviewProjectMetrics: vi.fn(),
}));

vi.mock('./service', () => ({
  parseScriptFile: mocks.parseScriptFile,
  queryReviewProject: mocks.queryReviewProject,
  queryReviewProjectSummaries: mocks.queryReviewProjectSummaries,
  queryReviewProjectMetrics: mocks.queryReviewProjectMetrics,
}));

const renderImport = (currentContent = '', onImport = vi.fn()) => {
  const result = render(
    <App>
      <ScriptContentImport currentContent={currentContent} onImport={onImport} />
    </App>,
  );
  return { ...result, onImport };
};

const chooseLocalFile = async (file: File) => {
  fireEvent.click(screen.getByRole('button', { name: '导入剧本内容' }));
  fireEvent.click(await screen.findByText('本地上传'));
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
};

describe('ScriptContentImport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryReviewProjectMetrics.mockResolvedValue({ data: [] });
  });

  it('reads txt and markdown locally without calling the parse API', async () => {
    const { onImport } = renderImport();
    const file = new File(['第一集\n开场'], 'story.txt', { type: 'text/plain' });
    Object.defineProperty(file, 'text', { value: vi.fn().mockResolvedValue('第一集\n开场') });

    await chooseLocalFile(file);

    await waitFor(() => expect(onImport).toHaveBeenCalledWith('第一集\n开场', '已导入：story.txt'));
    expect(mocks.parseScriptFile).not.toHaveBeenCalled();
    expect(document.querySelector('input[type="file"]')).toHaveAttribute('accept', '.txt,.md,.docx');
  });

  it('sends docx to the parse-only API', async () => {
    mocks.parseScriptFile.mockResolvedValue({
      data: { fileName: 'story.docx', content: '解析后的正文' },
    });
    const { onImport } = renderImport();
    const file = new File(['binary'], 'story.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });

    await chooseLocalFile(file);

    await waitFor(() => expect(mocks.parseScriptFile).toHaveBeenCalledWith(file));
    expect(onImport).toHaveBeenCalledWith('解析后的正文', '已导入：story.docx');
  });

  it('loads all versions for an expanded review project and imports a selected version', async () => {
    mocks.queryReviewProjectSummaries.mockResolvedValue({
      data: [{ id: 7, name: '审核剧本 A', sourceType: 'TEXT', status: 'ACTIVE' }],
    });
    mocks.queryReviewProject.mockResolvedValue({
      data: {
        project: { id: 7, name: '审核剧本 A' },
        versions: [
          { id: 71, projectId: 7, versionNo: 1, sourceType: 'TEXT', content: '初版' },
          { id: 72, projectId: 7, versionNo: 2, sourceType: 'MANUAL_EDIT', fileName: 'A.md', content: '修订版' },
        ],
        tasks: [],
      },
    });
    const { onImport } = renderImport();

    fireEvent.click(screen.getByRole('button', { name: '导入剧本内容' }));
    fireEvent.click(await screen.findByText('从审核剧本引用'));
    fireEvent.click(await screen.findByText('审核剧本 A'));
    fireEvent.click(await screen.findByRole('radio', { name: /版本 2/ }));
    fireEvent.click(screen.getByRole('button', { name: '引用所选版本' }));

    expect(mocks.queryReviewProject).toHaveBeenCalledWith(7);
    expect(onImport).toHaveBeenCalledWith('修订版', '已引用：审核剧本 A · 版本 2');
  });

  it('shows selectable project summaries before version-count metrics resolve', async () => {
    let resolveMetrics: (value: unknown) => void = () => undefined;
    mocks.queryReviewProjectSummaries.mockResolvedValue({
      data: [{ id: 7, name: '审核剧本 A', sourceType: 'TEXT', status: 'ACTIVE' }],
    });
    mocks.queryReviewProjectMetrics.mockReturnValueOnce(new Promise((resolve) => {
      resolveMetrics = resolve;
    }));
    renderImport();

    fireEvent.click(screen.getByRole('button', { name: '导入剧本内容' }));
    fireEvent.click(await screen.findByText('从审核剧本引用'));
    expect(await screen.findByText('审核剧本 A')).toBeInTheDocument();
    expect(screen.getByText('版本数加载中')).toBeInTheDocument();

    resolveMetrics({ data: [{ projectId: 7, versionCount: 2, latestRoundNo: 0 }] });
    expect(await screen.findByText('2 个版本')).toBeInTheDocument();
  });

  it('preserves an existing draft when replacement is canceled', async () => {
    const { onImport } = renderImport('正在编辑');
    const file = new File(['替换内容'], 'story.md', { type: 'text/markdown' });
    Object.defineProperty(file, 'text', { value: vi.fn().mockResolvedValue('替换内容') });

    await chooseLocalFile(file);
    expect(await screen.findByText('当前剧本内容将被覆盖，是否继续？')).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
      await new Promise((resolve) => setTimeout(resolve, 500));
    });
    expect(onImport).not.toHaveBeenCalled();
  });

  it('retries loading review projects after a request failure', async () => {
    mocks.queryReviewProjectSummaries
      .mockRejectedValueOnce(new Error('load failed'))
      .mockResolvedValueOnce({
        data: [{ id: 7, name: '审核剧本 A', sourceType: 'TEXT', status: 'ACTIVE' }],
      });
    renderImport();

    fireEvent.click(screen.getByRole('button', { name: '导入剧本内容' }));
    fireEvent.click(await screen.findByText('从审核剧本引用'));
    fireEvent.click(await screen.findByRole('button', { name: '重新加载' }));

    expect(await screen.findByText('审核剧本 A')).toBeInTheDocument();
    expect(mocks.queryReviewProjectSummaries).toHaveBeenCalledTimes(2);
  });

  it('does not replace content when a file is empty or parsing fails', async () => {
    const { onImport } = renderImport('保留内容');
    const empty = new File([], 'empty.txt', { type: 'text/plain' });
    Object.defineProperty(empty, 'text', { value: vi.fn().mockResolvedValue('') });
    await chooseLocalFile(empty);
    await waitFor(() => expect(onImport).not.toHaveBeenCalled());

    mocks.parseScriptFile.mockRejectedValue(new Error('parse failed'));
    await chooseLocalFile(new File(['broken'], 'broken.docx'));
    await waitFor(() => expect(mocks.parseScriptFile).toHaveBeenCalled());
    expect(onImport).not.toHaveBeenCalled();
  });
});
