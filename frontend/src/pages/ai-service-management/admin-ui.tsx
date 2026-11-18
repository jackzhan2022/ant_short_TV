import { createStyles } from 'antd-style';
import type { ReactNode } from 'react';

export const useAdminStyles = createStyles(({ token }) => ({
  page: {
    color: token.colorText,
    '& .ant-pro-table-search, & .ant-pro-card': {
      background: token.colorBgContainer,
      border: `1px solid ${token.colorBorderSecondary}`,
      borderRadius: 10,
      boxShadow: 'none',
    },
    '& .ant-pro-table-search': { padding: '16px 20px 0', marginBottom: 12 },
    '& .ant-pro-table-list-toolbar-container': {
      padding: '16px 20px',
      gap: 12,
    },
    '& .ant-pro-table-list-toolbar-title': { fontSize: token.fontSize, fontWeight: 500 },
    '& .ant-table': { fontSize: token.fontSize },
    '& .ant-table-thead > tr > th': {
      background: token.colorBgLayout,
      color: token.colorTextSecondary,
      fontSize: token.fontSize,
      fontWeight: 400,
      padding: '12px 18px',
    },
    '& .ant-table-tbody > tr > td': {
      padding: '16px 18px',
      borderColor: token.colorBorderSecondary,
      verticalAlign: 'middle',
    },
    '& .ant-table-tbody > tr:hover > td': { background: token.colorPrimaryBg },
    '& .ant-table .ant-btn-link': {
      padding: '0 5px',
      height: 28,
      fontSize: token.fontSize,
    },
    '& .ant-table .ant-typography-secondary': {
      fontSize: token.fontSize,
      color: token.colorTextSecondary,
    },
    '& .ant-tag': {
      fontSize: token.fontSize,
      lineHeight: '20px',
      borderRadius: 5,
      marginInlineEnd: 4,
    },
    '& .ant-pagination': { padding: '0 18px', fontSize: token.fontSize },
    '& .ant-empty': { padding: '38px 16px' },
    '& .ant-empty-description': {
      fontSize: token.fontSize,
      color: token.colorTextSecondary,
    },
    '& .ant-input, & .ant-select, & .ant-btn': { fontSize: token.fontSize },
  },
  switchLabel: { fontSize: token.fontSize },
  tagGroup: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: 4,
    maxWidth: '100%',
    '& .ant-tag': {
      maxWidth: '100%',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
    },
    '& small': { fontSize: token.fontSize, color: token.colorTextSecondary },
  },
  compactTable: {
    '& .ant-table-thead > tr > th, & .ant-table-tbody > tr > td': {
      paddingInline: 10,
    },
    '& .ant-table-thead > tr > th': { background: token.colorBgLayout },
    '& .ant-table-tbody > tr > .ant-table-cell-fix-right': {
      background: token.colorBgContainer,
    },
    '& .ant-table-thead > tr > .ant-table-cell-fix-right': {
      background: token.colorBgLayout,
    },
  },
  heading: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
    margin: '1px 0 19px',
    '& h1': {
      margin: 0,
      fontSize: 22,
      fontWeight: 600,
      letterSpacing: '-.4px',
    },
    '& p': {
      margin: '7px 0 0',
      color: token.colorTextSecondary,
      fontSize: token.fontSize,
      lineHeight: 1.8,
    },
  },
  surface: {
    background: token.colorBgContainer,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: 10,
    overflow: 'hidden',
  },
  filters: {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 12,
    padding: '16px 20px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
    '& > .ant-space': { width: '100%' },
    '& .ant-space-item:last-child:has(.ant-btn-primary)': {
      marginInlineStart: 'auto',
    },
    '@media (max-width: 700px)': {
      padding: 13,
      '& .ant-input-search': { maxWidth: '100%' },
    },
  },
  name: { display: 'flex', alignItems: 'center', gap: 11, minWidth: 0 },
  mark: {
    width: 35,
    height: 37,
    flexShrink: 0,
    display: 'grid',
    placeItems: 'center',
    borderRadius: 8,
    background: token.colorPrimaryBg,
    color: token.colorPrimary,
    fontSize: 14,
    border: `1px solid ${token.colorPrimaryBorder}`,
  },
  muted: { fontSize: token.fontSize, color: token.colorTextSecondary, lineHeight: 1.8 },
  empty: {
    padding: '56px 20px',
    textAlign: 'center',
    '& strong': {
      display: 'block',
      fontSize: 14,
      marginBottom: 8,
      fontWeight: 500,
    },
    '& p': { margin: 0, fontSize: token.fontSize, color: token.colorTextSecondary },
  },
  secondary: {
    fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace',
    fontSize: token.fontSize,
    color: token.colorTextSecondary,
    marginTop: 5,
    overflowWrap: 'anywhere',
  },
  summary: {
    display: 'flex',
    flexDirection: 'column',
    gap: 5,
    fontSize: token.fontSize,
    '& small': { fontSize: token.fontSize, color: token.colorTextSecondary },
    '& strong': { fontWeight: 500 },
  },
  code: {
    padding: '16px 18px',
    background: token.colorFillQuaternary,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: 8,
    fontSize: token.fontSize,
    lineHeight: 1.9,
    whiteSpace: 'pre-wrap',
    overflowWrap: 'anywhere',
    margin: '12px 0 24px',
  },
  overlay: {
    '& .ant-drawer-header': {
      padding: '24px 27px',
      borderColor: token.colorBorderSecondary,
    },
    '& .ant-drawer-title, & .ant-modal-title': {
      fontSize: 20,
      fontWeight: 600,
    },
    '& .ant-drawer-body': { padding: '24px 27px' },
    '& .ant-descriptions-item-label': {
      color: token.colorTextSecondary,
      fontSize: token.fontSize,
    },
    '& .ant-descriptions-item-content': {
      fontSize: token.fontSize,
      overflowWrap: 'anywhere',
    },
    '& .ant-form-item-label > label': { fontSize: token.fontSize },
    '& .ant-alert': { borderRadius: 8, fontSize: token.fontSize },
    '& .ant-table': { fontSize: token.fontSize },
    '& .ant-table-thead > tr > th': {
      color: token.colorTextSecondary,
      fontWeight: 400,
      background: token.colorFillQuaternary,
    },
    '& .ant-tabs-tab': { fontSize: token.fontSize },
    '& .ant-modal-content': { padding: 27, borderRadius: 12 },
  },
}));

export const AdminHeading = ({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) => {
  const { styles } = useAdminStyles();
  return (
    <header className={styles.heading}>
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </header>
  );
};

export const AdminEmpty = ({
  title,
  description,
}: {
  title: string;
  description: string;
}) => {
  const { styles } = useAdminStyles();
  return (
    <div className={styles.empty}>
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
};
