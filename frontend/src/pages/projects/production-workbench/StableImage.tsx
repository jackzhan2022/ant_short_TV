import type { ImageProps } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import ImageLoadingPlaceholder from '@/components/LazyMediaImage/ImageLoadingPlaceholder';

type StableImageProps = {
  src?: string | null;
  previewSrc?: string | null;
  preview?: ImageProps['preview'];
  alt: string;
  fallback?: ReactNode;
  loading?: 'eager' | 'lazy';
  style?: CSSProperties;
  imageStyle?: CSSProperties;
};

const StableImage = ({
  src,
  previewSrc,
  preview = false,
  alt,
  fallback,
  style,
  imageStyle,
}: StableImageProps) => (
  <LazyMediaImage
    native={!preview}
    preview={preview}
    src={previewSrc || src || undefined}
    alt={alt}
    height="100%"
    loadingPlaceholder={<ImageLoadingPlaceholder />}
    placeholderFallback={fallback}
    style={{ display: 'grid', placeItems: 'center', ...style }}
    imageStyle={{ position: 'absolute', inset: 0, ...imageStyle }}
  />
);

export default StableImage;
