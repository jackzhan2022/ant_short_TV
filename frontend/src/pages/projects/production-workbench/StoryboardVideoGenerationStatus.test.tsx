import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import StoryboardVideoGenerationStatus from './StoryboardVideoGenerationStatus';
import type { AiVideoTask } from './service';

afterEach(cleanup);
it('shows intelligent avatar preparation as ongoing without an error or cancel action', () => {
  render(
    <StoryboardVideoGenerationStatus
      storyboardNo={1}
      task={
        {
          id: 62,
          status: 'GENERATING',
          executionPhase: 'AVATAR_PREPARING',
        } as AiVideoTask
      }
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByText('智能虚拟化中')).toBeInTheDocument();
  expect(
    screen.getByText('正在准备虚拟人像素材，完成后自动继续生成'),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: /取消/ }),
  ).not.toBeInTheDocument();
});
it('returns to generation after preparation', () => {
  render(
    <StoryboardVideoGenerationStatus
      storyboardNo={1}
      task={
        {
          id: 62,
          status: 'GENERATING',
          executionPhase: 'VIDEO_QUERY',
        } as AiVideoTask
      }
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByText('正在生成')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /取消/ })).toBeInTheDocument();
});
