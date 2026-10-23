import type { ReferenceConstraint } from './service';
export type ReferenceMetadata = {
  width?: number;
  height?: number;
  durationSeconds?: number;
};
export function referenceMetadataError(
  metadata: ReferenceMetadata,
  rule?: ReferenceConstraint,
) {
  if (!rule) return undefined;
  if (
    rule.minDimension !== undefined ||
    rule.maxDimension !== undefined ||
    rule.minAspectRatio !== undefined ||
    rule.maxAspectRatio !== undefined
  ) {
    if (!metadata.width || !metadata.height) return '无法读取参考素材尺寸';
    if (
      (rule.minDimension !== undefined &&
        Math.min(metadata.width, metadata.height) < rule.minDimension) ||
      (rule.maxDimension !== undefined &&
        Math.max(metadata.width, metadata.height) > rule.maxDimension)
    )
      return '参考素材尺寸不符合当前模型要求';
    const ratio = metadata.width / metadata.height;
    if (
      (rule.minAspectRatio !== undefined && ratio < rule.minAspectRatio) ||
      (rule.maxAspectRatio !== undefined && ratio > rule.maxAspectRatio)
    )
      return '参考素材宽高比不符合当前模型要求';
  }
  if (
    rule.minDurationSeconds !== undefined ||
    rule.maxDurationSeconds !== undefined ||
    rule.maxTotalDurationSeconds !== undefined
  ) {
    if (!metadata.durationSeconds || !Number.isFinite(metadata.durationSeconds))
      return '无法读取参考素材时长';
    if (
      (rule.minDurationSeconds !== undefined &&
        metadata.durationSeconds < rule.minDurationSeconds) ||
      (rule.maxDurationSeconds !== undefined &&
        metadata.durationSeconds > rule.maxDurationSeconds)
    )
      return '参考素材时长不符合当前模型要求';
  }
  return undefined;
}
export function readReferenceMetadata(
  file: File,
  kind: 'image' | 'video' | 'audio',
  rule: ReferenceConstraint | undefined,
  signal: AbortSignal,
): Promise<ReferenceMetadata> {
  if (
    !rule ||
    ![
      'minDimension',
      'maxDimension',
      'minAspectRatio',
      'maxAspectRatio',
      'minDurationSeconds',
      'maxDurationSeconds',
      'maxTotalDurationSeconds',
    ].some((key) => rule[key as keyof ReferenceConstraint] !== undefined)
  )
    return Promise.resolve({});
  return new Promise((resolve, reject) => {
    const element =
      kind === 'image' ? new Image() : document.createElement(kind);
    const url = URL.createObjectURL(file);
    const cleanup = () => {
      clearTimeout(timer);
      signal.removeEventListener('abort', abort);
      element.removeEventListener(
        kind === 'image' ? 'load' : 'loadedmetadata',
        loaded,
      );
      element.removeEventListener('error', failed);
      element.removeAttribute('src');
      URL.revokeObjectURL(url);
    };
    const fail = (message: string) => {
      cleanup();
      reject(new Error(message));
    };
    const abort = () => fail('素材读取已取消');
    const failed = () => fail('无法读取素材信息，请检查文件后重试');
    const loaded = () => {
      const metadata: ReferenceMetadata =
        element instanceof HTMLImageElement
          ? { width: element.naturalWidth, height: element.naturalHeight }
          : {
              durationSeconds: element.duration,
              ...(element instanceof HTMLVideoElement
                ? { width: element.videoWidth, height: element.videoHeight }
                : {}),
            };
      const invalid = referenceMetadataError(metadata, rule);
      cleanup();
      if (invalid) reject(new Error(invalid));
      else resolve(metadata);
    };
    const timer = setTimeout(() => fail('读取素材信息超时，请重试'), 15000);
    signal.addEventListener('abort', abort, { once: true });
    element.addEventListener(
      kind === 'image' ? 'load' : 'loadedmetadata',
      loaded,
      { once: true },
    );
    element.addEventListener('error', failed, { once: true });
    if (signal.aborted) abort();
    else element.src = url;
  });
}
