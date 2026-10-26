import { Button, Modal } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { type MediaUploadHandle, startMediaUpload } from '@/services/mediaUpload';
import { captureVideoFrame, formatFrameTime, generateVideoThumbnails, type CapturedVideoFrame, type VideoThumbnail } from './video-frame-capture';
import { saveVideoFrame, videoFrameSource } from './video-frame-service';
import './VideoFrameCaptureModal.css';

export function FrameCameraIcon() {
  return <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M3 6h5l2-2h4l2 2h5v14H3z" /><circle cx="12" cy="12" r="4" /></svg>;
}

type PendingFrame = CapturedVideoFrame & { sessionToken?: string; upload?: MediaUploadHandle };
export default function VideoFrameCaptureModal({ projectId, resultId, storyboardNo, onClose }: {
  projectId: number; resultId: number; storyboardNo: number; onClose: () => void;
}) {
  const source = videoFrameSource(projectId, resultId);
  const video = useRef<HTMLVideoElement>(null);
  const active = useRef(false);
  const abort = useRef<AbortController | undefined>(undefined);
  const pending = useRef<PendingFrame | undefined>(undefined);
  const busy = useRef(false);
  const [ready, setReady] = useState(false);
  const [seeking, setSeeking] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [thumbnails, setThumbnails] = useState<VideoThumbnail[]>([]);
  const [thumbnailError, setThumbnailError] = useState(false);
  const [mediaError, setMediaError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState(0);
  const [saved, setSaved] = useState(0);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    active.current = true;
    const controller = new AbortController(); abort.current = controller;
    const player = video.current;
    pending.current = undefined; setSaveError('');
    if (player && !player.getAttribute('src')) { player.src = source; player.load(); }
    setReady(false); setMediaError(''); setThumbnails([]); setThumbnailError(false);
    setTime(0); setDuration(0); setPlaying(false); setSeeking(false);
    void generateVideoThumbnails(source, controller.signal).then((frames) => {
      if (!controller.signal.aborted) setThumbnails(frames);
    }).catch(() => { if (!controller.signal.aborted) setThumbnailError(true); });
    const pauseWhenHidden = () => { if (document.hidden) player?.pause(); };
    document.addEventListener('visibilitychange', pauseWhenHidden);
    return () => {
      active.current = false; controller.abort(); pending.current?.upload?.cancel();
      player?.pause(); player?.removeAttribute('src'); player?.load();
      document.removeEventListener('visibilitychange', pauseWhenHidden);
    };
  }, [source, retry]);

  const seek = (value: number) => {
    const player = video.current;
    if (!player || busy.current || !duration || mediaError) return;
    player.pause(); pending.current = undefined; setSaveError('');
    const next = Math.min(Math.max(0, value), Math.max(0, duration - 0.001));
    if (Math.abs(next - player.currentTime) < 0.001 && !player.seeking) return;
    setSeeking(true); setTime(next); player.currentTime = next;
  };
  const togglePlay = async () => {
    const player = video.current;
    if (!player || busy.current || mediaError) return;
    if (!player.paused) { player.pause(); return; }
    pending.current = undefined; setSaveError('');
    try { await player.play(); } catch { setMediaError('视频暂时无法播放，请重新加载'); }
  };
  const capture = async () => {
    const player = video.current;
    const signal = abort.current?.signal;
    if (!player || !signal || signal.aborted || busy.current || !ready || seeking || mediaError) return;
    busy.current = true; setSaving(true); setSaveError(''); setProgress(0);
    try {
      const frame: PendingFrame = pending.current || await captureVideoFrame(player);
      if (signal.aborted) return;
      pending.current = frame;
      setTime(frame.timeSeconds);
      if (!frame.sessionToken) {
        if (!frame.upload) {
          frame.upload = await startMediaUpload(new File([frame.blob], `分镜${storyboardNo}-${resultId}-${Math.round(frame.timeSeconds * 1000)}ms.png`, { type: 'image/png' }), {
            projectId, onProgress: ({ percent }) => { if (!signal.aborted) setProgress(Math.round(percent * 100)); },
          });
          if (signal.aborted) { frame.upload.cancel(); return; }
          const verified = await frame.upload.attempt;
          frame.sessionToken = verified.sessionToken;
        } else {
          // Resume/retry the same upload session; server binding uses its token as the idempotency key.
          const verified = await frame.upload.retry();
          frame.sessionToken = verified.sessionToken;
        }
      }
      if (signal.aborted) return;
      setProgress(100);
      await saveVideoFrame(projectId, resultId, frame.sessionToken, frame.timeSeconds, signal);
      if (!signal.aborted) { pending.current = undefined; setSaved(value => value + 1); }
    } catch (error) {
      if (!signal.aborted) setSaveError(error instanceof Error ? error.message : '截图保存失败，请重试');
    } finally {
      busy.current = false;
      if (active.current && !signal.aborted) setSaving(false);
    }
  };
  const controlsDisabled = !ready || saving || Boolean(mediaError);
  return (
    <Modal open title="视频截帧" width={720} footer={null} centered className="video-frame-modal"
      closable={!saving} keyboard={!saving} mask={{ closable: false }}
      onCancel={() => { if (!busy.current) onClose(); }}>
      <div className="video-frame-stage">
        <video key={`${source}:${retry}`} ref={video} src={source} preload="auto" playsInline aria-label="截帧视频预览"
          onLoadedMetadata={event => { const value = event.currentTarget.duration; setDuration(Number.isFinite(value) ? value : 0); }}
          onLoadedData={() => setReady(true)} onSeeking={() => setSeeking(true)}
          onSeeked={event => { setSeeking(false); setReady(event.currentTarget.readyState >= 2); setTime(event.currentTarget.currentTime); }}
          onTimeUpdate={event => setTime(event.currentTarget.currentTime)}
          onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)}
          onError={() => { setReady(false); setPlaying(false); setMediaError('视频加载失败或无权访问，请重新加载'); }}>
          <track kind="captions" />
        </video>
        {!ready && !mediaError ? <div className="video-frame-loading" role="status">正在加载视频…</div> : null}
        {mediaError ? <div className="video-frame-error" role="alert"><span>{mediaError}</span><button type="button" disabled={saving} onClick={() => { if (!busy.current) setRetry(value => value + 1); }}>重新加载</button></div> : null}
        <div className="video-frame-playback">
          <button type="button" aria-label="后退0.04秒" title="后退0.04秒" disabled={controlsDisabled || seeking || time <= 0} onClick={() => seek((video.current?.currentTime || 0) - 0.04)}>Ⅰ◀</button>
          <button className="video-frame-play" type="button" aria-label={playing ? '暂停' : '播放'} disabled={controlsDisabled || seeking} onClick={() => void togglePlay()}>{playing ? 'Ⅱ' : '▶'}</button>
          <button type="button" aria-label="前进0.04秒" title="前进0.04秒" disabled={controlsDisabled || seeking || time >= duration - 0.001} onClick={() => seek((video.current?.currentTime || 0) + 0.04)}>▶Ⅰ</button>
        </div>
      </div>
      <div className="video-frame-timeline">
        <div className="video-frame-ticks">{[0, 1, 2, 3, 4].map(tick => <span key={tick}>{formatFrameTime(duration * tick / 4).slice(0, duration < 4 ? 7 : 5)}</span>)}</div>
        <div className="video-frame-strip">
          {thumbnails.length ? thumbnails.map(frame => <img key={frame.time} src={frame.url} alt={`${formatFrameTime(frame.time)}缩略图`} />) : <span>{thumbnailError ? '缩略图加载失败，仍可拖动定位' : '正在生成时间轴缩略图…'}</span>}
          <div className="video-frame-cursor" style={{ left: `${duration ? Math.min(100, time / duration * 100) : 0}%` }} />
          <input type="range" aria-label="定位视频帧" min={0} max={duration || 0} step={0.001} value={time} disabled={controlsDisabled || !duration} onChange={event => seek(Number(event.target.value))} />
        </div>
        <div className="video-frame-time"><span>分镜 {storyboardNo} · 视频版本 {resultId}</span><span>{formatFrameTime(time)} / {formatFrameTime(duration)}</span></div>
      </div>
      <div className="video-frame-save">
        <p>截图当前帧并保存至「项目素材库-视频帧」，支持多次截图</p>
        <Button type="primary" icon={<FrameCameraIcon />} loading={saving} disabled={controlsDisabled || seeking}
          aria-label={saveError && pending.current ? '重试保存当前截图' : '截取当前帧并保存'}
          title={saveError && pending.current ? '重试保存当前截图' : '截取当前帧并保存'} onClick={() => void capture()} />
        <div className="video-frame-feedback" aria-live="polite">
          {saving ? <span>{progress >= 100 ? '正在保存截图…' : `正在上传截图 ${progress}%…`}</span>
            : saveError ? <span role="alert">{saveError}，可点击相机按钮重试</span>
              : saved ? <span className="video-frame-success">已保存至项目素材库，本次已保存 {saved} 张</span> : seeking ? <span>正在定位画面…</span> : null}
        </div>
      </div>
    </Modal>
  );
}
