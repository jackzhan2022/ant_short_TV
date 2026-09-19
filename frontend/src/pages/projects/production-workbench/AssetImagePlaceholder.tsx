import { PictureOutlined } from '@ant-design/icons';

const AssetImagePlaceholder = ({ compact = false }: { compact?: boolean }) => (
  <span
    role="img"
    aria-label="暂无图片"
    style={{
      display: 'flex',
      width: '100%',
      height: '100%',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: compact ? 0 : 8,
      color: 'var(--app-color-text-tertiary)',
      fontSize: compact ? 22 : 32,
      fontWeight: 400,
    }}
  >
    <PictureOutlined aria-hidden="true" />
    {compact ? null : <span style={{ fontSize: 12 }}>暂无图片</span>}
  </span>
);

export default AssetImagePlaceholder;
