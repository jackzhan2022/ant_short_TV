import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import StoryboardVideoFailure from './StoryboardVideoFailure';

afterEach(cleanup);

it('keeps technical errors in a details dialog and never retries while inspecting them', async () => {
  const onClose = vi.fn();
  render(
    <StoryboardVideoFailure
      storyboardNo={1}
      message="HTTP 400 Request id: example-123"
      onClose={onClose}
    >
      <span>图片1：角色参考图</span>
    </StoryboardVideoFailure>,
  );
  const region = screen.getByRole('region', { name: '分镜1视频生成错误' });
  expect(within(region).getByRole('alert')).toHaveTextContent('视频生成失败');
  expect(within(region).queryByText(/HTTP 400/)).not.toBeInTheDocument();
  fireEvent.click(
    within(region).getByRole('button', { name: '查看分镜1错误详情' }),
  );
  const details = await screen.findByRole('dialog');
  expect(details).toHaveTextContent('HTTP 400 Request id: example-123');
  expect(details).toHaveTextContent('图片1：角色参考图');
  expect(onClose).not.toHaveBeenCalled();
});
