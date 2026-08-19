import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as service from '../service';
import BaseView from './base';

const mocks = vi.hoisted(() => ({
  form: {
    setFieldValue: vi.fn(),
  },
  initialValues: undefined as Record<string, any> | undefined,
}));

vi.mock('@ant-design/pro-components', () => {
  const ProForm = ({
    children,
    formRef,
    initialValues,
  }: any) => {
    if (formRef) {
      formRef.current = mocks.form;
    }
    mocks.initialValues = initialValues;
    return <form>{children}</form>;
  };

  return {
    ProForm,
    ProFormFieldSet: ({ children }: any) => <div>{children}</div>,
    ProFormSelect: () => <div />,
    ProFormText: () => <div />,
    ProFormTextArea: () => <div />,
  };
});

vi.mock('antd', () => ({
  Button: ({ children }: any) => <button type="button">{children}</button>,
  Input: (props: any) => <input {...props} />,
  Upload: ({ children }: any) => <div>{children}</div>,
  message: {
    success: vi.fn(),
  },
}));

vi.mock('@ant-design/icons', () => ({
  UploadOutlined: () => <span />,
}));

vi.mock('./index.style', () => ({
  default: () => ({
    styles: {
      area_code: 'area-code',
      avatar: 'avatar',
      avatar_title: 'avatar-title',
      baseView: 'base-view',
      button_view: 'button-view',
      left: 'left',
      phone_number: 'phone-number',
      right: 'right',
    },
  }),
}));

vi.mock('../service', () => ({
  queryCurrent: vi.fn(),
}));

describe('BaseView personal info form', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    mocks.initialValues = undefined;
    vi.clearAllMocks();

    vi.mocked(service.queryCurrent).mockResolvedValue({
      data: {
        address: '西湖区工专路 77 号',
        avatar: '',
        country: 'China',
        email: 'antdesign@alipay.com',
        geographic: {
          province: { label: '浙江省', key: '330000' },
          city: { label: '杭州市', key: '330100' },
        },
        group: '',
        name: 'Ant Design',
        notice: [],
        notifyCount: 0,
        phone: '0752-268888888',
        signature: '',
        tags: [],
        title: '',
        unreadCount: 0,
        userid: '00000001',
      },
    });
  });

  it('does not render geographic fields or request geographic data', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <BaseView />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(mocks.initialValues?.province).toBeUndefined();
      expect(mocks.initialValues?.city).toBeUndefined();
    });
  });
});
