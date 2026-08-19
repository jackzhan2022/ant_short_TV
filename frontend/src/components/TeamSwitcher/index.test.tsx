import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamSwitcher from './index';

const mocks = vi.hoisted(() => ({
  queryMyTenants: vi.fn(),
  switchTenant: vi.fn(),
  messageSuccess: vi.fn(),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.messageSuccess } }),
  },
  Select: ({
    options = [],
    value,
    onChange,
    placeholder,
    prefix,
    suffixIcon,
    className,
  }: any) => (
    <select
      aria-label={placeholder}
      value={value ?? ''}
      className={className}
      onChange={(event) => onChange?.(event.target.value)}
    >
      {prefix}
      <option value="">{placeholder}</option>
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
      {suffixIcon}
    </select>
  ),
}));

vi.mock('@/services/account-team/tenant', () => ({
  queryMyTenants: mocks.queryMyTenants,
  switchTenant: mocks.switchTenant,
}));

describe('TeamSwitcher', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryMyTenants.mockResolvedValue({
      success: true,
      data: [
        { id: 10, name: '新禾文化' },
        { id: 11, name: '星计科技' },
      ],
    });
    mocks.switchTenant.mockResolvedValue({ success: true, data: {} });
  });

  it('shows the current team and switches tenant from the sidebar header', async () => {
    const onChange = vi.fn();

    render(<TeamSwitcher currentTenantId={10} onChange={onChange} />);

    expect(screen.getByRole('combobox')).toHaveClass('ant-short-team-switcher');
    await waitFor(() => {
      expect(screen.getByDisplayValue('新禾文化')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11' } });

    await waitFor(() => {
      expect(mocks.switchTenant).toHaveBeenCalledWith(11);
      expect(mocks.messageSuccess).toHaveBeenCalledWith('已切换至 星计科技');
      expect(onChange).toHaveBeenCalledWith(11);
    });
  });
});
