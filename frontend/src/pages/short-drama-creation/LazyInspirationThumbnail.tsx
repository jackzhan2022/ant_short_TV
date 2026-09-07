import { VideoCameraOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';

type LazyInspirationThumbnailProps = {
  alt: string;
  placeholderClassName?: string;
  src?: string;
};

const LazyInspirationThumbnail = ({
  alt,
  placeholderClassName,
  src,
}: LazyInspirationThumbnailProps) => {
  const imageRef = useRef<HTMLImageElement>(null);
  const [shouldLoad, setShouldLoad] = useState(() => !src || typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    if (!src || shouldLoad) {
      return;
    }
    const image = imageRef.current;
    if (!image) {
      return;
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setShouldLoad(true);
        observer.disconnect();
      }
    }, { rootMargin: '240px 0px' });
    observer.observe(image);
    return () => observer.disconnect();
  }, [shouldLoad, src]);

  if (!src) {
    return (
      <span aria-label={`${alt} 缩略图未就绪`} className={placeholderClassName} role="img">
        <VideoCameraOutlined />
      </span>
    );
  }

  return <img alt={alt} decoding="async" loading="lazy" ref={imageRef} src={shouldLoad ? src : undefined} />;
};

export default LazyInspirationThumbnail;
