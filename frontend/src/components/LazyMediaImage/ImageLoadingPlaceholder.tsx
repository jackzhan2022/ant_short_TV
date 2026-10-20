import { Spin } from 'antd';

const ImageLoadingPlaceholder = () => (
  <span
    style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 8,
      color: 'var(--app-color-text-secondary)',
      fontSize: 12,
      fontWeight: 400,
      lineHeight: 1.5,
    }}
  >
    <Spin size="small" />
    <span>加载中</span>
  </span>
);

export default ImageLoadingPlaceholder;
