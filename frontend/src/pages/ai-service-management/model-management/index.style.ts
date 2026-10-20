import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ token }) => ({
  modules: {
    '& > .ant-tabs-nav': { marginBottom: 16 },
    '& > .ant-tabs-nav .ant-tabs-tab': {
      padding: '0 0 14px',
      fontSize: 18,
      fontWeight: 600,
    },
    '& > .ant-tabs-nav .ant-tabs-tab-active .ant-tabs-tab-btn': {
      color: token.colorText,
      textShadow: 'none',
    },
  },
  functions: {
    '& > .ant-tabs-nav': { marginBottom: 16 },
    '& > .ant-tabs-nav::before': { borderBottom: 0 },
    '& > .ant-tabs-nav .ant-tabs-tab': {
      padding: '8px 15px',
      borderRadius: token.borderRadius,
      color: token.colorTextSecondary,
    },
    '& > .ant-tabs-nav .ant-tabs-tab-active': {
      background: token.colorPrimaryBg,
    },
    '& > .ant-tabs-nav .ant-tabs-tab-active .ant-tabs-tab-btn': {
      fontWeight: 500,
      textShadow: 'none',
    },
    '& > .ant-tabs-nav .ant-tabs-ink-bar': { display: 'none' },
  },
}));
