import { PlayCircleOutlined } from '@ant-design/icons';
import {
  type CSSProperties,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import LazyMediaImage from '../LazyMediaImage';

type Props = {
  src?: string | null;
  poster?: string | null;
  alt: string;
  active?: boolean;
  className?: string;
  style?: CSSProperties;
  resolveSrc?: (signal: AbortSignal) => Promise<string>;
};

let activePlayer: (() => void) | undefined;
export const stopActiveVideo = () => activePlayer?.();

const releaseVideo = (video: HTMLVideoElement) => {
  video.pause();
  video.removeAttribute('src');
  video.querySelectorAll('source').forEach((item) => {
    item.removeAttribute('src');
  });
  video.load();
};

const ClickToPlayVideo = ({
  src,
  poster,
  alt,
  active = true,
  className,
  style,
  resolveSrc,
}: Props) => {
  const [playingSrc, setPlayingSrc] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const controller = useRef<AbortController | undefined>(undefined);
  const attachVideo = useCallback((video: HTMLVideoElement | null) => {
    if (!video && videoRef.current) releaseVideo(videoRef.current);
    videoRef.current = video;
    if (video) void video.play().catch(() => undefined);
  }, []);
  const stop = useCallback(() => {
    controller.current?.abort();
    controller.current = undefined;
    const video = videoRef.current;
    if (video) {
      releaseVideo(video);
    }
    setPlayingSrc(undefined);
    setLoading(false);
    setError(false);
    if (activePlayer === stop) activePlayer = undefined;
  }, []);

  useEffect(() => {
    if (!active) stop();
    return stop;
  }, [active, src, resolveSrc, stop]);

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.hidden) stop();
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () =>
      document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [stop]);

  const play = async () => {
    if (!active || (!src && !resolveSrc)) return;
    stopActiveVideo();
    activePlayer = stop;
    const request = new AbortController();
    controller.current = request;
    setError(false);
    setLoading(true);
    try {
      const url = resolveSrc ? await resolveSrc(request.signal) : src;
      if (!request.signal.aborted && activePlayer === stop && url)
        setPlayingSrc(url);
    } catch {
      if (!request.signal.aborted) setError(true);
    } finally {
      if (!request.signal.aborted) setLoading(false);
    }
  };

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        width: '100%',
        aspectRatio: '16 / 9',
        background: '#111',
        overflow: 'hidden',
        ...style,
      }}
    >
      {playingSrc && active ? (
        <video
          ref={attachVideo}
          src={playingSrc}
          controls
          preload="none"
          aria-label={alt}
          onError={() => setError(true)}
          style={{
            width: '100%',
            height: '100%',
            display: 'block',
            objectFit: 'contain',
          }}
        >
          <track kind="captions" />
        </video>
      ) : (
        <>
          {poster ? (
            <LazyMediaImage
              native
              src={poster}
              alt={alt}
              height="100%"
              active={active}
              imageStyle={{ objectFit: 'contain' }}
            />
          ) : null}
          <button
            type="button"
            aria-label={`播放${alt}`}
            title={`播放${alt}`}
            disabled={loading || !active || (!src && !resolveSrc)}
            onClick={() => void play()}
            style={{
              position: 'absolute',
              inset: 0,
              display: 'grid',
              placeItems: 'center',
              border: 0,
              padding: 0,
              background: 'transparent',
              color: '#fff',
              fontSize: 44,
              cursor: 'pointer',
            }}
          >
            <PlayCircleOutlined />
          </button>
        </>
      )}
      {error ? (
        <span
          role="status"
          style={{
            position: 'absolute',
            top: 8,
            left: 8,
            color: '#fff',
            fontSize: 12,
            background: '#222',
            padding: 4,
          }}
        >
          视频暂时无法播放
        </span>
      ) : null}
    </div>
  );
};

export default ClickToPlayVideo;
