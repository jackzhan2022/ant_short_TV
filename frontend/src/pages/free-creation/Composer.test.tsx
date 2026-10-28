import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useState } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import Composer from './Composer';
import { newDraft } from './draft';
import type { CreationMode, CreationModel } from './service';

const mocks = vi.hoisted(() => ({ polish: vi.fn() }));
vi.mock('./prompt-polish', () => ({ polishPrompt: mocks.polish }));
vi.mock('./ReferenceFiles', () => ({ default: () => null }));
const models: CreationModel[] = [1, 2].map((id) => ({
  id,
  name: `视频${id}`,
  serviceType: 'VIDEO',
  isDefault: id === 1,
  configStatus: 'configured',
  references: {},
  resolutions: [],
}));
function Editor({ mode = 'VIDEO' }: { mode?: CreationMode }) {
  const [draft, setDraft] = useState({
    ...newDraft(models[0]),
    prompt: '雨夜街景',
  });
  return (
    <Composer
      mode={mode}
      models={models}
      loading={false}
      draft={draft}
      onChange={setDraft}
      example="新的示例"
    />
  );
}
beforeEach(() => vi.clearAllMocks());
it('shows the default checked video option and preserves it when changing models', () => {
  render(<Editor />);
  const checkbox = screen.getByRole('checkbox', { name: '字幕显示反向约束' });
  expect(checkbox).toBeChecked();
  fireEvent.click(checkbox);
  fireEvent.change(screen.getByLabelText('生成模型'), {
    target: { value: '2' },
  });
  expect(checkbox).not.toBeChecked();
});
it('does not show video constraints in image mode', () => {
  render(<Editor mode="IMAGE" />);
  expect(
    screen.queryByRole('checkbox', { name: '字幕显示反向约束' }),
  ).not.toBeInTheDocument();
});
it('polishes the original prompt and fills the editor with the result', async () => {
  mocks.polish.mockResolvedValue('镜头缓慢推进雨夜街景');
  render(<Editor />);
  fireEvent.click(screen.getByRole('button', { name: 'AI润色' }));
  await waitFor(() =>
    expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue(
      '镜头缓慢推进雨夜街景',
    ),
  );
  expect(mocks.polish).toHaveBeenCalledWith(
    '雨夜街景',
    'VIDEO',
    expect.any(AbortSignal),
  );
});
it('keeps the original text and reports errors when polishing fails', async () => {
  mocks.polish.mockRejectedValue(new Error('暂无可用的文本模型'));
  render(<Editor />);
  fireEvent.click(screen.getByRole('button', { name: 'AI润色' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    '暂无可用的文本模型',
  );
  expect(screen.getByRole('textbox', { name: '创意描述' })).toHaveValue(
    '雨夜街景',
  );
});
it('disables empty prompts and ignores results after the text changes', async () => {
  let finish!: (value: string) => void;
  mocks.polish.mockReturnValue(
    new Promise<string>((resolve) => {
      finish = resolve;
    }),
  );
  render(<Editor />);
  const textbox = screen.getByRole('textbox', { name: '创意描述' });
  fireEvent.change(textbox, { target: { value: '  ' } });
  expect(screen.getByRole('button', { name: 'AI润色' })).toBeDisabled();
  fireEvent.change(textbox, { target: { value: '雨夜街景' } });
  fireEvent.click(screen.getByRole('button', { name: 'AI润色' }));
  expect(screen.getByRole('button', { name: '润色中…' })).toBeDisabled();
  fireEvent.change(textbox, { target: { value: '用户的新描述' } });
  await act(async () => finish('旧请求的润色结果'));
  expect(textbox).toHaveValue('用户的新描述');
});
