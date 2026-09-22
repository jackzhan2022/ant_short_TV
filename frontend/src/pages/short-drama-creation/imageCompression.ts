export const MAX_IMAGE_BYTES = 1.5 * 1024 * 1024;
export const MAX_IMAGE_EDGE = 1920;

export type CompressedImage = {
  file: File;
  originalBytes: number;
  compressedBytes: number;
  width: number;
  height: number;
};

export const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

export const fitWithin = (
  width: number,
  height: number,
  maxEdge = MAX_IMAGE_EDGE,
) => {
  const scale = Math.min(1, maxEdge / Math.max(width, height));
  return {
    width: Math.round(width * scale),
    height: Math.round(height * scale),
  };
};

const canvasBlob = (canvas: HTMLCanvasElement, quality: number) =>
  new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', quality),
  );

export const compressImage = async (file: File): Promise<CompressedImage> => {
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    throw new Error('仅支持 JPG 或 PNG 图片');
  }
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error('图片无法读取，请重新选择');
  }
  try {
    const size = fitWithin(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = size.width;
    canvas.height = size.height;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('当前浏览器不支持图片压缩');
    context.drawImage(bitmap, 0, 0, size.width, size.height);
    for (const quality of [0.9, 0.8, 0.7, 0.6, 0.5, 0.4]) {
      const blob = await canvasBlob(canvas, quality);
      if (blob && blob.size <= MAX_IMAGE_BYTES) {
        return {
          file: new File([blob], file.name.replace(/\.[^.]+$/, '.jpg'), {
            type: 'image/jpeg',
          }),
          originalBytes: file.size,
          compressedBytes: blob.size,
          ...size,
        };
      }
    }
    throw new Error('图片压缩后仍超过 1.5MB，请选择更小的图片');
  } finally {
    bitmap.close();
  }
};
