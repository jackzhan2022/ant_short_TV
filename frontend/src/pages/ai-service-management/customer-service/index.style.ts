import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ token }) => ({
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: 16,
    padding: 24,
    background: token.colorBgContainer,
    borderRadius: token.borderRadiusLG,
  },
  title: { margin: 0, fontSize: 16, fontWeight: 500 },
  description: {
    margin: 0,
    color: token.colorTextSecondary,
    overflowWrap: 'anywhere',
  },
  preview: {
    display: 'grid',
    placeItems: 'center',
    width: 240,
    maxWidth: '100%',
    minHeight: 240,
    border: `1px dashed ${token.colorBorder}`,
    borderRadius: token.borderRadius,
    padding: 16,
    color: token.colorTextSecondary,
  },
  image: {
    display: 'block',
    width: '100%',
    height: 208,
    objectFit: 'contain',
    background: '#fff',
  },
  actions: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 },
}));
