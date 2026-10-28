import { request } from '@umijs/max';
import { beforeEach, expect, it, vi } from 'vitest';
import { polishPrompt } from './prompt-polish';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
beforeEach(() => vi.clearAllMocks());
it('sends only the original text and media type to the tenant-scoped endpoint', async () => {
  vi.mocked(request).mockResolvedValue({
    success: true,
    data: { prompt: '润色后的描述' },
  });
  const signal = new AbortController().signal;
  await expect(polishPrompt(' 原始描述 ', 'VIDEO', signal)).resolves.toBe(
    '润色后的描述',
  );
  expect(request).toHaveBeenCalledWith(
    '/api/free-creations/polish-prompt',
    expect.objectContaining({
      method: 'POST',
      data: { prompt: '原始描述', mediaType: 'VIDEO' },
      signal,
    }),
  );
});
it('reports model errors without returning fabricated text', async () => {
  vi.mocked(request).mockResolvedValue({
    success: false,
    errorMessage: '暂无可用的文本模型。',
  });
  await expect(polishPrompt('描述', 'IMAGE')).rejects.toThrow(
    '暂无可用的文本模型',
  );
});
it.each([
  '',
  ' ',
  '字'.repeat(2001),
  undefined,
])('rejects unusable model output', async (prompt) => {
  vi.mocked(request).mockResolvedValue({ success: true, data: { prompt } });
  await expect(polishPrompt('描述', 'VIDEO')).rejects.toThrow('润色结果无效');
});
