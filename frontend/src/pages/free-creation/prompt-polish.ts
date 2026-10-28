import { request } from '@umijs/max';
import type { CreationMode } from './service';

export async function polishPrompt(
  prompt: string,
  mediaType: CreationMode,
  signal?: AbortSignal,
): Promise<string> {
  const response = await request<{
    success: boolean;
    data?: { prompt: string };
    errorMessage?: string;
  }>('/api/free-creations/polish-prompt', {
    method: 'POST',
    data: { prompt: prompt.trim(), mediaType },
    signal,
    timeout: 90000,
    skipErrorHandler: true,
  });
  const polished = response?.data?.prompt;
  if (
    !response?.success ||
    typeof polished !== 'string' ||
    !polished.trim() ||
    polished.length > 2000
  ) {
    throw new Error(response?.errorMessage || '润色结果无效，请重试');
  }
  return polished.trim();
}
