import { Image, type ImageProps } from 'antd';
import { type CSSProperties, type ReactNode, useCallback, useRef, useState } from 'react';
import { useMediaVisibility } from './useMediaVisibility';

export type LazyMediaImageProps = Omit<ImageProps, 'fallback' | 'srcSet'> & {
  native?: boolean;
  active?: boolean;
  inView?: boolean;
  placeholderFallback?: ReactNode;
  imageStyle?: CSSProperties;
};

const LazyMediaImage = ({
  src,
  alt = '',
  width = '100%',
  height,
  native = false,
  active = true,
  inView,
  placeholderFallback,
  imageStyle,
  style,
  styles,
  preview = true,
  onLoad,
  onError,
  ...props
}: LazyMediaImageProps) => {
  const { ref, shouldLoad } = useMediaVisibility(src, active, inView);
  const [readySrc, setReadySrc] = useState<string>();
  const [failedSrc, setFailedSrc] = useState<string>();
  const currentSrc = useRef(src);
  currentSrc.current = src;
  const nativeImage = useRef<HTMLImageElement | null>(null);
  const nativeSource = useRef({ source: src, imageSrc: undefined as string | undefined });
  const attachNativeImage = useCallback((image: HTMLImageElement | null) => {
    if (!image && nativeImage.current) {
      nativeImage.current.removeAttribute('src');
      currentSrc.current = undefined;
    }
    nativeImage.current = image;
    if (image) {
      currentSrc.current = nativeSource.current.source;
      const desired = nativeSource.current.imageSrc;
      if (desired && image.getAttribute('src') !== desired) image.setAttribute('src', desired);
    }
  }, []);
  const failed = Boolean(src && failedSrc === src);
  const ready = Boolean(src && readySrc === src);
  nativeSource.current = { source: src, imageSrc: shouldLoad && !failed ? src : undefined };
  const sharedImageStyle: CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    ...imageStyle,
    opacity: ready ? 1 : 0,
  };
  const handleLoad: NonNullable<ImageProps['onLoad']> = (event) => {
    const image = event.currentTarget;
    const loadedSrc = src;
    const decoded =
      typeof image.decode === 'function'
        ? image.decode().catch(() => undefined)
        : Promise.resolve();
    void decoded.then(() => {
      if (loadedSrc && currentSrc.current === loadedSrc) setReadySrc(loadedSrc);
    });
    onLoad?.(event);
  };
  const handleError: NonNullable<ImageProps['onError']> = (event) => {
    setFailedSrc(src);
    onError?.(event);
  };

  return (
    <span
      ref={ref}
      style={{
        position: 'relative',
        display: 'block',
        overflow: 'hidden',
        width,
        height,
        aspectRatio: height ? undefined : '16 / 9',
        background: 'var(--app-color-fill-secondary, #f3f3f3)',
        ...style,
      }}
    >
      {!src || failed ? placeholderFallback : null}
      {src && !ready && !failed ? (
        <span role="status" aria-label={`${alt}加载中`} />
      ) : null}
      {native ? (
        src ? (
          <img
            {...props}
            ref={attachNativeImage}
            alt={alt}
            src={shouldLoad && !failed ? src : undefined}
            width={width}
            height={height}
            loading="lazy"
            decoding="async"
            onLoad={handleLoad}
            onError={handleError}
            style={sharedImageStyle}
          />
        ) : null
      ) : (
        <Image
          {...props}
          alt={alt}
          src={shouldLoad && !failed ? src : undefined}
          width="100%"
          height="100%"
          loading="lazy"
          decoding="async"
          preview={shouldLoad && !failed ? preview : false}
          onLoad={handleLoad}
          onError={handleError}
          styles={
            typeof styles === 'function'
              ? styles
              : { ...styles, image: { ...sharedImageStyle, ...styles?.image } }
          }
        />
      )}
    </span>
  );
};

export default LazyMediaImage;
