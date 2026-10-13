import { DownOutlined, TeamOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Select } from 'antd';
import { useStyles } from './index.style';
import { useMemo, useState } from 'react';
import type { TenantSummary } from '@/services/account-team/types';
import { TeamIcon } from '../TeamIconPicker/teamIcons';

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
  const { styles } = useStyles();
  const [loading, setLoading] = useState(false);

  const options = useMemo(
    () => tenants.map((tenant) => ({ label: tenant.name, value: tenant.id, logo: tenant.logo })),
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

  if (tenants.length === 0) {
    return (
      <div className={styles.empty}>
        <span className={styles.label}><TeamOutlined /> 尚未加入团队</span>
        <Button type="link" size="small" onClick={() => history.push('/team/my')}>
          创建或加入团队
        </Button>
      </div>
    );
  }

  return (
    <Select
      className="ant-short-team-switcher"
      aria-label="当前团队"
      prefix={<TeamIcon value={tenants.find((tenant) => tenant.id === currentTenantId)?.logo} size={14} />}
      loading={loading}
      options={options}
      optionRender={(option) => <span className={styles.option}><TeamIcon value={option.data.logo} size={16} /><span>{option.data.label}</span></span>}
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
