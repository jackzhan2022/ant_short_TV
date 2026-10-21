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
  playbackKey?: string | number;
  previewDuration?: number;
  playing?: boolean;
  onPlayingChange?: (playing: boolean) => void;
  onEnded?: () => void;
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
  const [localPlaying, setLocalPlaying] = useState(false);
  const playing = props.playing ?? localPlaying;
  const setPlaying = (value: boolean) => {
    setLocalPlaying(value);
    props.onPlayingChange?.(value);
  };
  const [time, setTime] = useState(0);
  const timeRef = useRef(0);
  const [mediaDuration, setMediaDuration] = useState(0);
  const previewDuration = Number.isFinite(props.previewDuration)
    ? Math.max(0, props.previewDuration || 0) : 0;
  const duration = props.src ? mediaDuration : previewDuration;
  const canPlay = Boolean(props.src || previewDuration);
  const [volume, setVolume] = useState(1);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (props.playing === undefined) setLocalPlaying(false);
    timeRef.current = 0;
    setTime(0);
    setMediaDuration(0);
    setError('');
    const video = videoRef.current;
    if (video) video.volume = volume;
    const pauseWhenHidden = () => {
      if (document.hidden) {
        video?.pause();
        setPlaying(false);
      }
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
  }, [props.src, props.playbackKey, retry]);

  useEffect(() => {
    if (props.active === false) {
      videoRef.current?.pause();
      setPlaying(false);
    }
  }, [props.active]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !props.src) return;
    if (!playing || props.active === false) {
      if (!video.paused) video.pause();
      return;
    }
    let active = true;
    void video.play().catch(() => {
      if (active && video === videoRef.current) {
        setPlaying(false);
        setError('视频暂时无法播放，请重试或刷新素材。');
      }
    });
    return () => { active = false; };
  }, [playing, props.src, props.playbackKey, props.active, retry]);

  const finishPlayback = () => {
    if (props.onEnded) props.onEnded();
    else setPlaying(false);
  };

  useEffect(() => {
    if (props.src || !playing || props.active === false || !previewDuration) return;
    let previousTick = performance.now();
    const timer = window.setInterval(() => {
      const now = performance.now();
      const next = Math.min(previewDuration, timeRef.current + (now - previousTick) / 1000);
      previousTick = now;
      timeRef.current = next;
      setTime(next);
      if (next >= previewDuration) {
        window.clearInterval(timer);
        finishPlayback();
      }
    }, 100);
    return () => window.clearInterval(timer);
  }, [playing, props.src, props.playbackKey, props.active, previewDuration, props.onEnded]);

  const togglePlay = () => {
    if (!canPlay || props.active === false) return;
    if (!playing && duration && timeRef.current >= duration) {
      timeRef.current = 0;
      setTime(0);
      if (videoRef.current) videoRef.current.currentTime = 0;
    }
    setPlaying(!playing);
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
            key={`${props.playbackKey}-${props.src}-${retry}`}
            ref={videoRef}
            aria-label="当前视频"
            src={props.src}
            poster={props.poster}
            preload="metadata"
            playsInline
            onPlay={() => setPlaying(true)}
            onPause={(event) => {
              if (event.currentTarget === videoRef.current && !event.currentTarget.ended) setPlaying(false);
            }}
            onEnded={finishPlayback}
            onTimeUpdate={(event) => {
              timeRef.current = event.currentTarget.currentTime;
              setTime(event.currentTarget.currentTime);
            }}
            onLoadedMetadata={(event) =>
              setMediaDuration(event.currentTarget.duration)
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
        {canPlay && (
          <span className="video-preview-source">{props.src ? props.sourceLabel : '分镜预演'}</span>
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
          disabled={!canPlay || !duration || !!error}
          onChange={(event) => {
            const value = Number(event.target.value);
            if (videoRef.current) videoRef.current.currentTime = value;
            timeRef.current = value;
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
            disabled={!canPlay || !!error}
            onClick={togglePlay}
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
