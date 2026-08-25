import { DownOutlined, TeamOutlined } from '@ant-design/icons';
import { App, Select } from 'antd';
import { useMemo, useState } from 'react';
import type { TenantSummary } from '@/services/account-team/types';

type TeamSwitcherProps = {
  currentTenantId?: number;
  tenants?: TenantSummary[];
  onChange?: (tenantId: number) => void | Promise<void>;
};

const TeamSwitcher = ({
  currentTenantId,
  tenants = [],
  onChange,
}: TeamSwitcherProps) => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  const options = useMemo(
    () => tenants.map((tenant) => ({ label: tenant.name, value: tenant.id })),
    [tenants],
  );

  const handleChange = async (value: number | string) => {
    const tenantId = Number(value);
    const tenant = tenants.find((item) => item.id === tenantId);
    setLoading(true);
    try {
      await onChange?.(tenantId);
      message.success(`已切换至 ${tenant?.name ?? '当前团队'}`);
    } catch {
      // The request layer reports the authorization failure; keep the old selection.
    } finally {
      setLoading(false);
    }
  };

  return (
    <Select
      className="ant-short-team-switcher"
      aria-label="当前团队"
      prefix={<TeamOutlined />}
      loading={loading}
      options={options}
      placeholder="选择团队"
      size="middle"
      showSearch={{ optionFilterProp: 'label' }}
      suffixIcon={<DownOutlined />}
      value={currentTenantId}
      onChange={handleChange}
    />
  );
};

export default TeamSwitcher;
