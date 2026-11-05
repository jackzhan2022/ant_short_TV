import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Histogram, Trend, time } from './presentation';

describe('operations metric presentation', () => {
  it('keeps zero and missing latency distinct and breaks trend lines at coverage gaps', () => {
    const { container } = render(
      <Trend
        trend={[
          { at: '2026-11-05T00:00:00Z', requests: 0, errors: 0, p95Ms: null },
          {
            at: '2026-11-05T00:01:00Z',
            requests: null,
            errors: null,
            p95Ms: null,
          },
          { at: '2026-11-05T00:02:00Z', requests: 0, errors: 0, p95Ms: null },
        ]}
      />,
    );
    expect(screen.getByText('近似P95（ms） · 最大 无数据')).toBeInTheDocument();
    expect(screen.getByText('请求数 · 最大 0')).toBeInTheDocument();
    expect(container.querySelectorAll('polyline')).toHaveLength(4);
  });
  it('renders histogram overflow explicitly and formats UTC as Beijing time', () => {
    render(
      <Histogram
        bins={[
          { upperBoundMs: 1000, count: 2 },
          { upperBoundMs: null, count: 1 },
        ]}
      />,
    );
    expect(screen.getByText('>1800000')).toBeInTheDocument();
    expect(time('2026-11-05T00:00:00Z')).toContain('08:00:00');
  });
});
