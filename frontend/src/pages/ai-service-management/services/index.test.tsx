import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiServiceManagement from './index';

const mocks = vi.hoisted(() => ({
  createAiServiceConfig: vi.fn(),
  deleteAiServiceConfig: vi.fn(),
  getCurrentTenantId: vi.fn(),
  lastProTableProps: undefined as any,
  providerSelectProps: undefined as any,
  queryAiProviders: vi.fn(),
  queryAiServiceConfigs: vi.fn(),
  setDefaultAiServiceConfig: vi.fn(),
  success: vi.fn(),
  testAiServiceConfig: vi.fn(),
  updateAiServiceConfig: vi.fn(),
  updateAiServiceConfigStatus: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useAccess: () => ({
    canCreateAiServices: true,
    canEditAiServices: true,
    canDeleteAiServices: true,
    canTestAiServices: true,
  }),
}));

vi.mock('@ant-design/icons', () => ({
  ApiOutlined: () => <span data-testid="api-icon" />,
  CheckCircleOutlined: () => <span data-testid="check-icon" />,
  DeleteOutlined: () => <span data-testid="delete-icon" />,
  DownOutlined: () => <span data-testid="down-icon" />,
  EditOutlined: () => <span data-testid="edit-icon" />,
  PlusOutlined: () => <span data-testid="plus-icon" />,
  ThunderboltOutlined: () => <span data-testid="test-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.success, warning: vi.fn() } }),
  },
  Button: ({ children, icon, ...props }: any) => (
    <button type="button" {...props}>
      {icon}
      {children}
    </button>
  ),
  Dropdown: ({ children, menu }: any) => (
    <div>
      {children}
      <div>
        {menu.items.map((item: any) => (
          <button
            key={item.key}
            type="button"
            onClick={() => menu.onClick({ key: item.key })}
          >
            {item.label}
          </button>
        ))}
      </div>
    </div>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Form: {
    useForm: () => [
      {
        resetFields: vi.fn(),
        getFieldValue: vi.fn(),
        setFieldValue: vi.fn(),
        setFieldsValue: vi.fn(),
      },
    ],
  },
  Popconfirm: ({ children }: any) => <div>{children}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Switch: ({ checked, onChange }: any) => (
    <button
      type="button"
      aria-label={checked ? '开关已开启' : '开关已关闭'}
      onClick={() => onChange?.(!checked)}
    >
      {checked ? '开' : '关'}
    </button>
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, trigger }: any) => (
    <div>
      {trigger}
      {children}
    </div>
  ),
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProFormDigit: ({ label }: any) => <span>{label}</span>,
  ProFormSelect: ({ label, name, options }: any) => {
    if (name === 'provider') {
      mocks.providerSelectProps = { options };
    }
    return <span>{label}</span>;
  },
  ProFormSwitch: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ columns, scroll, headerTitle, request, toolBarRender }: any) => {
    mocks.lastProTableProps = { columns, scroll };
    request?.({}, {}, {});
    return (
      <section>
        <h1>{headerTitle}</h1>
        <div>{toolBarRender?.()}</div>
        <div>
          {columns.map((column: any) => (
            <span key={column.dataIndex || column.title}>{column.title}</span>
          ))}
        </div>
      </section>
    );
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('./service', () => ({
  createAiServiceConfig: mocks.createAiServiceConfig,
  deleteAiServiceConfig: mocks.deleteAiServiceConfig,
  queryAiProviders: mocks.queryAiProviders,
  queryAiServiceConfigs: mocks.queryAiServiceConfigs,
  setDefaultAiServiceConfig: mocks.setDefaultAiServiceConfig,
  testAiServiceConfig: mocks.testAiServiceConfig,
  updateAiServiceConfig: mocks.updateAiServiceConfig,
  updateAiServiceConfigStatus: mocks.updateAiServiceConfigStatus,
}));

describe('AiServiceManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.lastProTableProps = undefined;
    mocks.providerSelectProps = undefined;
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryAiProviders.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          name: 'OpenAI',
          code: 'OpenAI',
          supportedTypes: 'TEXT,IMAGE,VOICE',
          defaultBaseUrl: 'https://api.openai.com/v1',
          status: 'ENABLED',
        },
      ],
    });
    mocks.queryAiServiceConfigs.mockResolvedValue({ success: true, data: [] });
  });

  it('renders AI service configuration table for the current team', async () => {
    render(<AiServiceManagement />);

    expect(screen.getByText('AI服务配置')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '新增服务' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '新增文本服务' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '新增图片服务' }),
    ).toBeInTheDocument();
    expect(screen.queryAllByText('服务类型')).toHaveLength(1);
    expect(screen.queryByText('调用端点')).not.toBeInTheDocument();
    expect(screen.queryByText('查询端点')).not.toBeInTheDocument();
    expect(screen.queryByText('设为默认')).not.toBeInTheDocument();
    expect(screen.queryByText('启用状态')).not.toBeInTheDocument();
    expect(screen.getAllByText('服务名称').length).toBeGreaterThan(0);
    expect(screen.getAllByText('服务商').length).toBeGreaterThan(0);
    expect(screen.getAllByText('模型').length).toBeGreaterThan(0);

    await waitFor(() => {
      expect(mocks.queryAiServiceConfigs).toHaveBeenCalledWith(10);
    });
    await waitFor(() => {
      expect(mocks.queryAiProviders).toHaveBeenCalled();
    });
    expect(mocks.lastProTableProps.columns[0].responsive).toEqual([
      'lg',
      'xl',
      'xxl',
    ]);
    expect(mocks.lastProTableProps.columns[1].responsive).toEqual([
      'md',
      'lg',
      'xl',
      'xxl',
    ]);
    expect(mocks.lastProTableProps.scroll).toEqual({ x: 1400 });
    expect(mocks.lastProTableProps.columns.at(-1).width).toBe(180);
    expect(mocks.lastProTableProps.columns.at(-1).fixed).toBe('right');
    expect(mocks.lastProTableProps.columns.at(-1).align).toBe('center');
  });

  it('prompts users to select a team when there is no current tenant', () => {
    mocks.getCurrentTenantId.mockReturnValue(undefined);

    render(<AiServiceManagement />);

    expect(
      screen.getByText('请先在我的团队中选择当前创作团队'),
    ).toBeInTheDocument();
  });
});
