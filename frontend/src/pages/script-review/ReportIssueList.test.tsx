import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ReportIssueList from './ReportIssueList';

vi.mock('@ant-design/x-markdown', () => ({
  default: ({ children }: { children: string }) => <div>{children}</div>,
}));

describe('ReportIssueList', () => {
  it('shows major findings with evidence and suggestions, excluding dimension conclusions', () => {
    render(
      <ReportIssueList
        markdown={
          '# 综合报告\n\n## 一、审核范围与限制\n部分正文为空\n\n## 二、合并后的主要问题\n\n### 1. 人物立场突变\n- **严重程度：** MEDIUM\n证据原文\n**建议：** 补充转折\n\n---\n\n### 2. 字幕译名不一致\n修改字幕\n\n## 三、各维度审核结论\n### 1. 台词合理性\n维度总结'
        }
      />,
    );
    const list = screen.getByRole('list', { name: '审核问题列表' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(
      within(list).getByRole('heading', { name: '1. 人物立场突变' }),
    ).toBeVisible();
    expect(list).toHaveTextContent('补充转折');
    expect(list).toHaveTextContent('证据原文');
    expect(list).not.toHaveTextContent('维度总结');
    expect(screen.getByText('共 2 项问题')).toBeVisible();
    expect(screen.getByText('查看完整报告（含审核范围与限制）')).toBeVisible();
  });

  it('preserves nonstandard reports instead of claiming zero findings', () => {
    render(
      <ReportIssueList
        markdown={'# 自定义审核\n\n|位置|建议|\n|---|---|\n|第1集|保留|'}
      />,
    );
    expect(screen.getByTestId('markdown-report-reader')).toHaveTextContent(
      '第1集|保留',
    );
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(screen.queryByText('共 0 项问题')).not.toBeInTheDocument();
  });

  it('does not treat numbered examples in code fences as findings', () => {
    render(
      <ReportIssueList
        markdown={
          '## 主要问题\n### 1. 台词问题\n```markdown\n### 2. 示例\n```\n建议修改'
        }
      />,
    );
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
  });
});
