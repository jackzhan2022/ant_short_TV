import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchScript from './script';

const mocks = vi.hoisted(() => ({
  queryScriptWorkspace: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ id: '1' }),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Flex: ({ children }: any) => <div>{children}</div>,
  Input: Object.assign(
    ({ value, ...props }: any) => <textarea value={value} {...props} />,
    {
      TextArea: ({ value, ...props }: any) => <textarea value={value} {...props} />,
    },
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h3>{children}</h3>,
  },
}));

vi.mock('../detail/components/service', () => ({
  queryScriptWorkspace: mocks.queryScriptWorkspace,
}));

describe('ProductionWorkbenchScript', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: {
          id: 11,
          projectId: 1,
          title: '最危险的捉迷藏',
          sourceType: 'AI_GENERATE',
          content:
            '第1集 致命捉迷藏\n斌斌独自下楼玩耍。\n第2集 夜色警报\n家人开始寻找失踪的孩子。',
          status: 'DRAFT',
          currentVersionId: 3,
          updatedAt: '2026-08-19 18:21:00',
        },
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [
          {
            id: 101,
            shotNo: 1,
            episodeNo: 1,
            shotType: '远景',
            visualDescription: '斌斌独自下楼玩耍',
            characters: '斌斌',
            scene: '小区楼下',
            dialogue: '今天我一定要赢。',
            durationSeconds: 5,
            imagePrompt: '',
            videoPrompt: '',
          },
          {
            id: 201,
            shotNo: 1,
            episodeNo: 2,
            shotType: '中景',
            visualDescription: '家人开始在楼道里寻找',
            characters: '刘凤英',
            scene: '楼道',
            dialogue: '斌斌你在哪儿？',
            durationSeconds: 5,
            imagePrompt: '',
            videoPrompt: '',
          },
        ],
      },
    });
  });

  it('renders the restored script page without a character list', async () => {
    render(<ProductionWorkbenchScript />);

    expect(await screen.findByText('线上剧本内容')).toBeInTheDocument();
    expect(screen.getByText('剧本类型')).toBeInTheDocument();
    expect(screen.getByText('大纲')).toBeInTheDocument();
    expect(screen.getByText('分集剧情')).toBeInTheDocument();
    expect(screen.getByText('当前集剧情正文')).toBeInTheDocument();
    expect(screen.getByText('第1集')).toBeInTheDocument();
    expect(screen.getByText('第2集')).toBeInTheDocument();
    expect(screen.queryByText('人物列表')).not.toBeInTheDocument();
    expect(screen.queryByText('人物小传')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
  });
});
