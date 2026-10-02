import { VideoCameraOutlined } from '@ant-design/icons';
import LazyMediaImage from '@/components/LazyMediaImage';

type Props = { alt: string; placeholderClassName?: string; src?: string };

const LazyInspirationThumbnail = ({
  alt,
  placeholderClassName,
  src,
}: Props) => {
  if (!src)
    return (
      <span
        aria-label={`${alt} 缩略图未就绪`}
        className={placeholderClassName}
        role="img"
      >
        <VideoCameraOutlined />
      </span>
    );
  return <LazyMediaImage native alt={alt} src={src} height="100%" />;
};

export default LazyInspirationThumbnail;
