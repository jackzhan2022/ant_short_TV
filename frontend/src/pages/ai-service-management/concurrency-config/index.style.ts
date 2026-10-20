import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ token }) => ({
  page: {
    display: 'grid',
    gap: 16,
  },
  form: {
    display: 'grid',
    gap: 16,
  },
  description: {
    margin: '0 0 16px',
    color: token.colorTextSecondary,
    lineHeight: 1.6,
  },
  fields: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
    gap: '0 24px',
    '& .ant-input-number': { width: '100%' },
    '& .ant-form-item': { marginBottom: 16 },
  },
  database: {
    margin: 0,
    color: token.colorTextSecondary,
    '& output': { color: token.colorText, fontWeight: 600, marginLeft: 8 },
  },
  actions: {
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 16,
    color: token.colorTextSecondary,
    '& p': { margin: 0 },
  },
}));
