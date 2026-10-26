import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { captureVideoFrame, seekVideoFrame } from './video-frame-capture';

const drawImage = vi.fn();
let canvas: HTMLCanvasElement;
let encodedSize: number[];
let video: HTMLVideoElement;
beforeEach(() => {
  video = document.createElement('video');
  canvas = document.createElement('canvas');
  Object.defineProperties(video, { videoWidth: { value: 1080 }, videoHeight: { value: 1920 },
    readyState: { value: 2, configurable: true }, seeking: { value: false, configurable: true }, duration: { value: 5 } });
  video.currentTime = 1.24;
  vi.spyOn(video, 'pause').mockImplementation(() => {});
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as any);
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (this: HTMLCanvasElement, callback) {
    canvas = this;
    encodedSize = [this.width, this.height];
    callback(new Blob(['png'], { type: 'image/png' }));
  });
});
afterEach(() => { vi.restoreAllMocks(); drawImage.mockClear(); });
it('captures only the decoded video at original resolution and records the frozen time', async () => {
  const result = await captureVideoFrame(video);
  expect(video.pause).toHaveBeenCalled();
  expect(encodedSize).toEqual([1080, 1920]);
  expect(canvas.width).toBe(0); // The temporary bitmap is released after encoding.
  expect(drawImage).toHaveBeenCalledWith(video, 0, 0, 1080, 1920);
  expect(result).toMatchObject({ timeSeconds: 1.24, width: 1080, height: 1920 });
  expect(result.blob.type).toBe('image/png');
});
it('refuses capture during seek or before the first video frame is decoded', async () => {
  Object.defineProperty(video, 'seeking', { value: true });
  await expect(captureVideoFrame(video)).rejects.toThrow('定位');
  Object.defineProperty(video, 'seeking', { value: false });
  Object.defineProperty(video, 'readyState', { value: 1 });
  await expect(captureVideoFrame(video)).rejects.toThrow('加载');
  expect(drawImage).not.toHaveBeenCalled();
});
it('reports canvas security failure without producing an image', async () => {
  drawImage.mockImplementationOnce(() => { throw new DOMException('Tainted', 'SecurityError'); });
  await expect(captureVideoFrame(video)).rejects.toThrow('截帧');
});
it('waits for seek completion and can cancel a pending thumbnail seek', async () => {
  const abort = new AbortController();
  const result = seekVideoFrame(video, 2, abort.signal);
  expect(video.currentTime).toBe(2);
  video.dispatchEvent(new Event('seeked'));
  await result;
  const pending = seekVideoFrame(video, 3, abort.signal);
  abort.abort();
  await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
});
