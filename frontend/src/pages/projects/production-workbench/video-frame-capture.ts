export type CapturedVideoFrame = { blob: Blob; timeSeconds: number; width: number; height: number };

export const formatFrameTime = (seconds: number) => {
  const milliseconds = Math.max(0, Math.round((Number.isFinite(seconds) ? seconds : 0) * 1000));
  return `${String(Math.floor(milliseconds / 60000)).padStart(2, '0')}:${String(Math.floor(milliseconds / 1000) % 60).padStart(2, '0')}.${String(milliseconds % 1000).padStart(3, '0')}`;
};

export async function captureVideoFrame(video: HTMLVideoElement): Promise<CapturedVideoFrame> {
  video.pause();
  if (video.seeking) throw new Error('正在定位画面，请稍后截取');
  const width = video.videoWidth, height = video.videoHeight;
  if (video.readyState < 2 || !width || !height) throw new Error('视频画面尚未加载完成');
  if (width * height > 40_000_000) throw new Error('视频画面超过4000万像素，暂不支持截取');
  const timeSeconds = video.currentTime;
  const canvas = document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  try {
    const context = canvas.getContext('2d');
    if (!context) throw new Error('无法创建截帧画布，请更换浏览器后重试');
    context.drawImage(video, 0, 0, width, height);
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(
      (value) => value ? resolve(value) : reject(new Error('截帧失败，请重试')), 'image/png',
    ));
    if (blob.size > 20 * 1024 * 1024) throw new Error('截图超过20MB，暂无法保存');
    return { blob, width, height, timeSeconds };
  } catch (error) {
    if (error instanceof DOMException && error.name === 'SecurityError') {
      throw new Error('视频不允许截帧，请重新加载视频后重试');
    }
    throw error;
  } finally {
    canvas.width = 0; canvas.height = 0;
  }
}

const aborted = () => new DOMException('操作已取消', 'AbortError');
function waitForMedia(video: HTMLVideoElement, event: 'loadeddata' | 'seeked', signal: AbortSignal, start: () => void) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) { reject(aborted()); return; }
    const cleanup = () => {
      window.clearTimeout(timer);
      video.removeEventListener(event, done);
      video.removeEventListener('error', failed);
      signal.removeEventListener('abort', cancel);
    };
    const done = () => { cleanup(); resolve(); };
    const failed = () => { cleanup(); reject(new Error('视频画面加载失败')); };
    const cancel = () => { cleanup(); reject(aborted()); };
    const timer = window.setTimeout(() => { cleanup(); reject(new Error('视频画面加载超时，请重试')); }, 20000);
    video.addEventListener(event, done, { once: true });
    video.addEventListener('error', failed, { once: true });
    signal.addEventListener('abort', cancel, { once: true });
    try { start(); } catch (error) { cleanup(); reject(error); }
  });
}

export async function seekVideoFrame(video: HTMLVideoElement, time: number, signal: AbortSignal) {
  if (signal.aborted) throw aborted();
  if (!video.seeking && Math.abs(video.currentTime - time) < 0.001 && video.readyState >= 2) return;
  await waitForMedia(video, 'seeked', signal, () => { video.currentTime = time; });
  if (video.readyState < 2) throw new Error('视频画面尚未加载完成');
}

export type VideoThumbnail = { time: number; url: string };
export async function generateVideoThumbnails(src: string, signal: AbortSignal): Promise<VideoThumbnail[]> {
  const video = document.createElement('video');
  const canvas = document.createElement('canvas');
  video.muted = true; video.playsInline = true; video.preload = 'auto';
  try {
    await waitForMedia(video, 'loadeddata', signal, () => { video.src = src; video.load(); });
    if (!Number.isFinite(video.duration) || video.duration <= 0) return [];
    const context = canvas.getContext('2d');
    if (!context || !video.videoWidth || !video.videoHeight) return [];
    const scale = Math.min(160 / video.videoWidth, 320 / video.videoHeight);
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    // Only sample six low-resolution frames when the modal is opened.
    const frames: VideoThumbnail[] = [];
    for (let index = 0; index < 6; index++) {
      const time = (video.duration * index) / 6;
      await seekVideoFrame(video, time, signal);
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      frames.push({ time, url: canvas.toDataURL('image/jpeg', 0.65) });
    }
    return frames;
  } finally {
    video.pause(); video.removeAttribute('src'); video.load();
    canvas.width = 0; canvas.height = 0;
  }
}
