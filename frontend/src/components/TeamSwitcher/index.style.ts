import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ token }) => ({
  option: { display: 'flex', alignItems: 'center', gap: 8 },
  empty: {
    display: 'flex',
    alignItems: 'flex-start',
    flexDirection: 'column',
    gap: 4,
    padding: '10px 12px',
    background: token.colorFillAlter,
    borderRadius: token.borderRadius,
  },
  label: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    color: token.colorTextSecondary,
    fontSize: 13,
  },
}));
