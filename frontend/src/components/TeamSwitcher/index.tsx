import { DownOutlined, TeamOutlined } from '@ant-design/icons';
import { App, Select } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { queryMyTenants, switchTenant } from '@/services/account-team/tenant';
import type { TenantSummary } from '@/services/account-team/types';

type TeamSwitcherProps = {
  currentTenantId?: number;
  onChange?: (tenantId: number) => void | Promise<void>;
};

const TeamSwitcher = ({ currentTenantId, onChange }: TeamSwitcherProps) => {
  const { message } = App.useApp();
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    const loadTenants = async () => {
      setLoading(true);
      try {
        const response = await queryMyTenants();
        if (active) {
          setTenants(response.data);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadTenants();

    return () => {
      active = false;
    };
  }, []);

  const options = useMemo(
    () => tenants.map((tenant) => ({ label: tenant.name, value: tenant.id })),
    [tenants],
  );

  const handleChange = async (value: number | string) => {
    const tenantId = Number(value);
    const tenant = tenants.find((item) => item.id === tenantId);
    await switchTenant(tenantId);
    message.success(`已切换至 ${tenant?.name ?? '当前团队'}`);
    await onChange?.(tenantId);
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
