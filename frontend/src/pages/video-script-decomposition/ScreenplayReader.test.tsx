import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ScreenplayReader, { screenplayBlocks } from './ScreenplayReader';

vi.mock('@ant-design/x-markdown', () => ({
  default: ({ children }: { children: string }) => <div>{children}</div>,
}));

describe('screenplay reading format', () => {
  it('keeps unfamiliar Markdown, code blocks and blank lines in one unchanged document', () => {
    const content = '# 第1集\n\n## 场景\n\n```text\n字幕\n\n画外音\n```\n';
    expect(screenplayBlocks(content)).toEqual([
      { kind: 'markdown', text: content, offset: 0 },
    ]);
  });
  it('styles only explicit speakers and preserves OS, emotions, line order and ending', () => {
    const text =
      '# 第1集：来信\n\n## 1-1 夜 内 客厅\n\n出场人物：林晚、陈默\n\n窗外雷声响起。\n\n林晚（OS）\n（低声）\n这是谁的信？\n\n## 1-2 日 外 街头\n\n陈默\n\n——本集完';
    const blocks = screenplayBlocks(text);
    expect(
      blocks.filter((b) => b.kind === 'dialogue').map((b) => b.text),
    ).toEqual(['林晚（OS）\n（低声）\n这是谁的信？']);
    expect(blocks.map((b) => b.text).join('\n\n')).toBe(text);
    render(
      <ScreenplayReader content={text} episodeNo={1} fileName="one.mp4" />,
    );
    expect(screen.getByText('林晚（OS）')).toBeInTheDocument();
    expect(screen.getByText('——本集完')).toBeInTheDocument();
    expect(screen.getByText('陈默', { exact: true })).toBeInTheDocument();
  });
});
