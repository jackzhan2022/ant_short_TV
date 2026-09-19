import { Spin } from 'antd';
import {
  type CSSProperties,
  type ReactNode,
  useState,
} from 'react';

type StableImageProps = {
  src?: string | null;
  previewSrc?: string | null;
  alt: string;
  fallback?: ReactNode;
  loading?: 'eager' | 'lazy';
  style?: CSSProperties;
  imageStyle?: CSSProperties;
};

const revealAfterDecode = (
  image: HTMLImageElement,
  src: string,
  reveal: (loadedSrc: string) => void,
) => {
  const decoded =
    typeof image.decode === 'function'
      ? image.decode().catch(() => undefined)
      : Promise.resolve();
  void decoded.then(() => reveal(src));
};

const StableImage = ({
  src,
  previewSrc,
  alt,
  fallback,
  loading = 'lazy',
  style,
  imageStyle,
}: StableImageProps) => {
  const [readySrc, setReadySrc] = useState<string>();
  const [readyPreviewSrc, setReadyPreviewSrc] = useState<string>();
  const [failedSrc, setFailedSrc] = useState<string>();
  const [failedPreviewSrc, setFailedPreviewSrc] = useState<string>();
  const targetSrc = src || undefined;
  const lowResolutionSrc =
    previewSrc && previewSrc !== targetSrc ? previewSrc : undefined;
  const targetReady = Boolean(targetSrc && readySrc === targetSrc);
  const targetFailed = Boolean(targetSrc && failedSrc === targetSrc);
  const previewReady = Boolean(
    lowResolutionSrc && readyPreviewSrc === lowResolutionSrc,
  );
  const previewFailed = Boolean(
    lowResolutionSrc && failedPreviewSrc === lowResolutionSrc,
  );
  const showFallback =
    !targetSrc || (targetFailed && (!lowResolutionSrc || previewFailed));
  const showLoading =
    Boolean(targetSrc) && !targetReady && !previewReady && !showFallback;
  const sharedImageStyle: CSSProperties = {
    position: 'absolute',
    inset: 0,
    width: '100%',
    height: '100%',
    ...imageStyle,
  };

  return (
    <span
      style={{
        position: 'relative',
        display: 'grid',
        width: '100%',
        height: '100%',
        placeItems: 'center',
        overflow: 'hidden',
        ...style,
      }}
    >
      {showFallback ? fallback : null}
      {showLoading ? (
        <span role="status" aria-label={`${alt}加载中`}>
          <Spin size="small" />
        </span>
      ) : null}
      {lowResolutionSrc ? (
        <img
          src={lowResolutionSrc}
          alt=""
          aria-hidden="true"
          loading={loading}
          decoding="async"
          onLoad={(event) =>
            revealAfterDecode(
              event.currentTarget,
              lowResolutionSrc,
              setReadyPreviewSrc,
            )
          }
          onError={() => setFailedPreviewSrc(lowResolutionSrc)}
          style={{
            ...sharedImageStyle,
            opacity: previewReady && !targetReady ? 1 : 0,
          }}
        />
      ) : null}
      {targetSrc ? (
        <img
          src={targetSrc}
          alt={alt}
          loading={loading}
          decoding="async"
          onLoad={(event) =>
            revealAfterDecode(event.currentTarget, targetSrc, setReadySrc)
          }
          onError={() => setFailedSrc(targetSrc)}
          style={{ ...sharedImageStyle, opacity: targetReady ? 1 : 0 }}
        />
      ) : null}
    </span>
  );
};

export default StableImage;
