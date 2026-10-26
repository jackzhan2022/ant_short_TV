import { FrameCameraIcon } from './VideoFrameCaptureModal';

export default function StoryboardVideoTools({ enabled, resultId, onCapture }: {
  enabled: boolean; resultId?: number; onCapture: () => void;
}) {
  return <div className="storyboard-video-tools" role="toolbar" aria-label="视频操作">
    <button type="button" aria-label="取视频帧" title={!enabled ? '暂无取视频帧权限' : !resultId ? '此视频暂不支持截帧' : '取视频帧'}
      disabled={!enabled || !resultId} onClick={event => { event.stopPropagation(); onCapture(); }}><FrameCameraIcon /></button>
    <button type="button" aria-label="擦除字幕（暂未开放）" title="擦除字幕（暂未开放）" disabled>
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><rect x="3" y="4" width="18" height="14" rx="2" /><path d="M6 9h5m-2.5 0v5m6-5h3m-3 3h3M3 22L22 3" /></svg>
    </button>
  </div>;
}
