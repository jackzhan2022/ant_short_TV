import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { PointEstimate } from '@/services/point-estimate';
import PointEstimateContent from '.';

const line = (label: string, points: number, trialEligible = true) => ({
  label,
  points,
  trialEligible,
  estimated: true,
});
const quote: PointEstimate = {
  estimatedPoints: 100.1,
  availablePoints: 10115.5,
  paidAvailablePoints: 80,
  sufficient: true,
  lines: [
    line('剧本全局理解', 10.2),
    line('剧集智能拆分', 9.2),
    line('第1集概要（按集标题估算）', 4.1),
    line('第1集资产识别', 20.1),
    line('第13集概要（按集标题估算）', 0),
    line('生成分镜（含自动分镜）', 10.1),
    line('生成分镜（含自动分镜）', 10.1),
    line('全剧资产自动归一', 36.3),
  ],
  notice: '实际按模型用量结算，未使用的预占积分会释放。',
};

describe('compact point estimate content', () => {
  it('uses the server total and groups raw amounts before rounding for display', () => {
    render(<PointEstimateContent estimate={quote} />);
    expect(screen.getByLabelText('本次消耗积分')).toHaveTextContent('101');
    expect(screen.getByLabelText('当前可用积分')).toHaveTextContent('10,116');
    const summary = screen.getByRole('list', { name: '费用构成' });
    expect(
      within(summary).getByText('剧本分析').closest('li'),
    ).toHaveTextContent('19');
    expect(
      within(summary).getByText('剧集处理').closest('li'),
    ).toHaveTextContent('24');
    expect(
      within(summary).getByText('分镜生成').closest('li'),
    ).toHaveTextContent('20');
    expect(
      within(summary).getByText('全剧资产自动归一').closest('li'),
    ).toHaveTextContent('36');
    expect(screen.queryByText(quote.notice)).not.toBeInTheDocument();
  });

  it('reveals complete episode labels and zero amounts without submitting anything', () => {
    render(<PointEstimateContent estimate={quote} />);
    const toggle = screen.getByRole('button', { name: '查看费用明细' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(
      screen.queryByText('第13集概要（按集标题估算）'),
    ).not.toBeInTheDocument();
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    const details = screen.getByRole('region', { name: '费用明细' });
    expect(
      within(details).getByText('第1集概要（按集标题估算）'),
    ).toBeVisible();
    expect(
      within(details).getByText('第13集概要（按集标题估算）').closest('li'),
    ).toHaveTextContent('0');
    expect(within(details).queryByText(/免费/)).not.toBeInTheDocument();
    expect(
      within(details).getByText('生成分镜（含自动分镜） × 2'),
    ).toBeVisible();
    expect(within(details).getByText(quote.notice)).toBeVisible();
    expect(
      within(details).getByText('明细按项取整，与总额可能存在尾差。'),
    ).toBeVisible();
    fireEvent.click(toggle);
    expect(
      screen.queryByRole('region', { name: '费用明细' }),
    ).not.toBeInTheDocument();
  });

  it('retains paid video restrictions and relies on server sufficiency', () => {
    render(
      <PointEstimateContent
        estimate={{
          ...quote,
          sufficient: false,
          lines: [line('生成视频', 100.1, false)],
        }}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      '可用积分不足，请充值后重试。',
    );
    expect(screen.getByText('试用积分不可用于视频生成')).toBeVisible();
    expect(screen.getByLabelText('视频生成可用积分')).toHaveTextContent('80');
    expect(screen.queryByText('余额充足')).not.toBeInTheDocument();
  });
});
