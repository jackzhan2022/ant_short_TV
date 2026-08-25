import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamSwitcher from './index';

const mocks = vi.hoisted(() => ({
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

describe('TeamSwitcher', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses bootstrap tenants and confirms only after the validated change completes', async () => {
    const onChange = vi.fn();

    render(
      <TeamSwitcher
        currentTenantId={10}
        tenants={[
          { id: 10, name: '新禾文化' } as any,
          { id: 11, name: '星计科技' } as any,
        ]}
        onChange={onChange}
      />,
    );

    expect(screen.getByRole('combobox')).toHaveClass('ant-short-team-switcher');
    await waitFor(() => {
      expect(screen.getByDisplayValue('新禾文化')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11' } });

    await waitFor(() => {
      expect(mocks.messageSuccess).toHaveBeenCalledWith('已切换至 星计科技');
      expect(onChange).toHaveBeenCalledWith(11);
    });
  });

  it('does not show success when tenant validation fails', async () => {
    const onChange = vi.fn().mockRejectedValue(new Error('unavailable'));
    render(
      <TeamSwitcher
        currentTenantId={10}
        tenants={[
          { id: 10, name: '新禾文化' } as any,
          { id: 11, name: '星计科技' } as any,
        ]}
        onChange={onChange}
      />,
    );

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11' } });

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(11));
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
  });
});
