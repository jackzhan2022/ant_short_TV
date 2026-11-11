import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ReviewReportReader from './ReviewReportReader';

afterEach(cleanup);
it('从实际章节生成目录，保留完整报告，忽略代码中的标题', async () => {
  render(
    <ReviewReportReader
      markdown={
        '# 完整报告\n\n## 人物动机\n\n证据与修改建议\n\n```text\n## 不是章节\n```\n\n## 人物动机\n\n第二处证据'
      }
    />,
  );
  const nav = screen.getByRole('navigation', { name: '报告目录' });
  await waitFor(() =>
    expect(within(nav).getAllByRole('button')).toHaveLength(2),
  );
  expect(within(nav).queryByText('不是章节')).not.toBeInTheDocument();
  expect(screen.getByText('证据与修改建议')).toBeInTheDocument();
  const headings = screen.getAllByRole('heading', { name: '人物动机' });
  const scroll = vi.fn();
  const reader = screen.getByRole('region', { name: '审核报告正文' });
  reader.scrollTo = scroll;
  fireEvent.click(within(nav).getAllByRole('button')[1]);
  expect(scroll).toHaveBeenCalled();
  expect(headings).toHaveLength(2);
});

it('更换报告后更新目录，无章节时仍展示完整正文', async () => {
  const view = render(<ReviewReportReader markdown={'## 原报告\n\n原内容'} />);
  await screen.findByRole('button', { name: /原报告/ });
  view.rerender(<ReviewReportReader markdown="只有正文，没有二级标题" />);
  await waitFor(() =>
    expect(
      screen.queryByRole('button', { name: /原报告/ }),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByText('只有正文，没有二级标题')).toBeInTheDocument();
});
