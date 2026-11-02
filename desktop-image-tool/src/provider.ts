import { randomUUID } from 'node:crypto';
import { validateOutputName } from './output';
import { BASE_URL, type Draft, type Format, SIZES } from './shared';

export function imageFormat(bytes: Buffer): Format | undefined {
  if (
    bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
  )
    return 'png';
  if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'jpeg';
  if (
    bytes.toString('ascii', 0, 4) === 'RIFF' &&
    bytes.toString('ascii', 8, 12) === 'WEBP'
  )
    return 'webp';
  return undefined;
}

export function decodeImage(
  value: string,
  maxBytes: number,
): { bytes: Buffer; format: Format } {
  if (
    typeof value !== 'string' ||
    value.length > Math.ceil(maxBytes / 3) * 4 + 100
  )
    throw new Error('图片文件过大。');
  const match =
    /^data:image\/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$/.exec(value);
  if (!match) throw new Error('仅支持 PNG、JPEG、WEBP 图片。');
  const bytes = Buffer.from(match[2], 'base64');
  const format = imageFormat(bytes);
  if (
    !format ||
    format !== match[1] ||
    bytes.length > maxBytes ||
    bytes.toString('base64') !== match[2]
  )
    throw new Error('图片内容无效或超出大小限制。');
  return { bytes, format };
}

export function validateDraft(value: Draft): Draft {
  if (
    !value ||
    typeof value.prompt !== 'string' ||
    !value.prompt.trim() ||
    value.prompt.length > 2000
  )
    throw new Error('请输入 1～2000 字的画面描述。');
  if (typeof value.model !== 'string' || !/^[\w.:-]{1,100}$/.test(value.model))
    throw new Error('图片模型名称无效。');
  if (
    !(SIZES as readonly string[]).includes(value.size) ||
    !['auto', 'low', 'medium', 'high'].includes(value.quality) ||
    !['png', 'jpeg', 'webp'].includes(value.format)
  )
    throw new Error('图片参数无效。');
  if (!Array.isArray(value.references) || value.references.length > 8)
    throw new Error('最多添加 8 张参考图。');
  let total = 0;
  const references = value.references.map((ref) => {
    if (!ref || typeof ref.name !== 'string') throw new Error('参考图无效。');
    total += decodeImage(ref.dataUrl, 10 * 1024 * 1024).bytes.length;
    return { name: ref.name.slice(0, 200), dataUrl: ref.dataUrl };
  });
  if (total > 30 * 1024 * 1024) throw new Error('参考图总大小不能超过 30 MB。');
  return {
    outputName: validateOutputName(value.outputName),
    prompt: value.prompt.trim(),
    model: value.model,
    size: value.size,
    quality: value.quality,
    format: value.format,
    references,
  };
}

export function buildRequest(draft: Draft, responseModel: string) {
  return {
    model: responseModel,
    input: [
      {
        role: 'user',
        content: [
          { type: 'input_text', text: draft.prompt },
          ...draft.references.map((ref) => ({
            type: 'input_image',
            image_url: ref.dataUrl,
          })),
        ],
      },
    ],
    tools: [
      {
        type: 'image_generation',
        model: draft.model,
        size: draft.size,
        quality: draft.quality,
        output_format: draft.format,
        action: draft.references.length ? 'edit' : 'generate',
      },
    ],
  };
}

export async function generateImage(
  draft: Draft,
  key: string,
  responseModel: string,
  signal: AbortSignal,
  fetcher: typeof fetch = fetch,
) {
  const response = await fetcher(`${BASE_URL}/responses`, {
    method: 'POST',
    redirect: 'error',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${key}`,
      'Idempotency-Key': randomUUID(),
    },
    body: JSON.stringify(buildRequest(draft, responseModel)),
  });
  if (!response.ok) {
    await response.body?.cancel();
    const errors: Record<number, string> = {
      401: 'Key 无效或已过期，请更新个人 Key。',
      403: '当前 Key 无权使用所选模型。',
      402: '账户额度不足，请检查服务商账户。',
      404: '服务接口或模型不存在，请核对服务商配置。',
      429: '服务商限流或额度不足，请稍后手动重试。',
    };
    throw new Error(
      errors[response.status] ||
        `图片服务返回 HTTP ${response.status}，请检查模型和参数后重试。`,
    );
  }
  const reader = response.body?.getReader();
  if (!reader) throw new Error('图片服务返回了空响应。');
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > 100 * 1024 * 1024)
        throw new Error('服务响应超过 100 MB，已停止接收。');
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  }
  let data: {
    id?: string;
    output?: { type: string; result?: string; output_format?: string }[];
  };
  try {
    data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new Error('服务返回的内容不是有效 JSON。');
  }
  if (!data || !Array.isArray(data.output))
    throw new Error('服务未返回图片结果。');
  const images = data.output
    .filter((item) => item?.type === 'image_generation_call' && item.result)
    .map((item) =>
      decodeImage(
        `data:image/${item.output_format || draft.format};base64,${item.result}`,
        50 * 1024 * 1024,
      ),
    );
  if (!images.length)
    throw new Error('服务未返回图片结果，请检查模型是否支持图片工具。');
  return {
    images,
    responseId: typeof data.id === 'string' ? data.id : undefined,
  };
}
