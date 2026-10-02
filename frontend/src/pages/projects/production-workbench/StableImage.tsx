import type { CSSProperties, ReactNode } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';

type StableImageProps = {
  src?: string | null;
  previewSrc?: string | null;
  alt: string;
  fallback?: ReactNode;
  loading?: 'eager' | 'lazy';
  style?: CSSProperties;
  imageStyle?: CSSProperties;
};

const StableImage = ({
  src,
  previewSrc,
  alt,
  fallback,
  style,
  imageStyle,
}: StableImageProps) => (
  <LazyMediaImage
    native
    src={previewSrc || src || undefined}
    alt={alt}
    height="100%"
    placeholderFallback={fallback}
    style={{ display: 'grid', placeItems: 'center', ...style }}
    imageStyle={{ position: 'absolute', inset: 0, ...imageStyle }}
  />
);

export default StableImage;
