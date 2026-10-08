import {
  ExpandOutlined,
  PauseOutlined,
  PlayCircleOutlined,
  SoundOutlined,
} from '@ant-design/icons';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { formatVideoTime } from './video-workbench-state';

type Props = {
  active?: boolean;
  src?: string;
  poster?: string;
  label: string;
  sourceLabel: string;
  empty: ReactNode;
  leadingControls: ReactNode;
  trailingControls: ReactNode;
  previous: ReactNode;
  next: ReactNode;
};

export default function VideoPreviewPlayer(props: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const stageRef = useRef<HTMLElement>(null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    setPlaying(false);
    setTime(0);
    setDuration(0);
    setError('');
    const video = videoRef.current;
    if (video) video.volume = volume;
    const pauseWhenHidden = () => {
      if (document.hidden) video?.pause();
    };
    document.addEventListener('visibilitychange', pauseWhenHidden);
    return () => {
      document.removeEventListener('visibilitychange', pauseWhenHidden);
      if (video) {
        video.pause();
        video.removeAttribute('src');
        video.load();
      }
    };
  }, [props.src, retry]);

  useEffect(() => {
    if (props.active === false) videoRef.current?.pause();
  }, [props.active]);

  const togglePlay = async () => {
    const video = videoRef.current;
    if (!video || !props.src) return;
    if (!video.paused) {
      video.pause();
      return;
    }
    try {
      await video.play();
    } catch {
      if (video === videoRef.current)
        setError('视频暂时无法播放，请重试或刷新素材。');
    }
  };

  return (
    <section
      className="video-preview-player"
      aria-label="视频预览区"
      ref={stageRef}
    >
      <div className="video-preview-stage">
        {props.src ? (
          <video
            key={`${props.src}-${retry}`}
            ref={videoRef}
            aria-label="当前视频"
            src={props.src}
            poster={props.poster}
            preload="metadata"
            playsInline
            onPlay={() => setPlaying(true)}
            onPause={() => setPlaying(false)}
            onEnded={() => setPlaying(false)}
            onTimeUpdate={(event) => setTime(event.currentTarget.currentTime)}
            onLoadedMetadata={(event) =>
              setDuration(event.currentTarget.duration)
            }
            onError={() => {
              setPlaying(false);
              setError('视频加载失败，请重试或刷新素材。');
            }}
          >
            <track
              kind="captions"
              src="data:text/vtt,WEBVTT"
              srcLang="zh"
              label="字幕"
            />
          </video>
        ) : (
          props.empty
        )}
        <span className="video-preview-label">{props.label}</span>
        {props.src && (
          <span className="video-preview-source">{props.sourceLabel}</span>
        )}
        {error && (
          <div className="video-preview-error" role="alert">
            <p>{error}</p>
            <button
              type="button"
              onClick={() => setRetry((value) => value + 1)}
            >
              重新加载视频
            </button>
          </div>
        )}
      </div>
      <div className="video-preview-seek">
        <input
          aria-label="播放进度"
          type="range"
          min={0}
          max={Number.isFinite(duration) ? duration : 0}
          step={0.1}
          value={time}
          disabled={!props.src || !duration || !!error}
          onChange={(event) => {
            const value = Number(event.target.value);
            if (videoRef.current) videoRef.current.currentTime = value;
            setTime(value);
          }}
        />
      </div>
      <div className="video-preview-controls">
        <div className="video-preview-mode">{props.leadingControls}</div>
        <div className="video-playback-controls">
          <span className="video-preview-time">
            {formatVideoTime(time)} <span>/ {formatVideoTime(duration)}</span>
          </span>
          {props.previous}
          <button
            className="video-play-button"
            type="button"
            aria-label={playing ? '暂停' : '播放'}
            disabled={!props.src || !!error}
            onClick={() => void togglePlay()}
          >
            {playing ? <PauseOutlined /> : <PlayCircleOutlined />}
          </button>
          {props.next}
        </div>
        <div className="video-preview-tools">
          <SoundOutlined />
          <input
            aria-label="音量"
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={volume}
            onChange={(event) => {
              const value = Number(event.target.value);
              setVolume(value);
              if (videoRef.current) videoRef.current.volume = value;
            }}
          />
          <button
            type="button"
            aria-label="全屏预览"
            onClick={() =>
              void stageRef.current
                ?.requestFullscreen?.()
                .catch(() => setError('当前浏览器不支持全屏预览。'))
            }
          >
            <ExpandOutlined />
          </button>
          {props.trailingControls}
        </div>
      </div>
    </section>
  );
}
