import { WalletOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import { queryTeamPointAccount } from '@/services/account-team/points';
import type { LayoutCurrentUser } from '@/services/account-team/types';
import { AvatarDropdown } from '../RightContent/AvatarDropdown';

type SidebarAccountProps = {
  currentUser?: LayoutCurrentUser;
  currentTenantId?: number;
};

const SidebarAccount = ({ currentUser, currentTenantId }: SidebarAccountProps) => {
  const [pointAccount, setPointAccount] = useState<{ tenantId: number; balance: number }>();

  useEffect(() => {
    let disposed = false;
    const refreshBalance = async () => {
      const tenantId = currentTenantId;
      if (!tenantId) {
        if (!disposed) setPointAccount(undefined);
        return;
      }
      try {
        const result = await queryTeamPointAccount(tenantId);
        if (!disposed) setPointAccount({ tenantId, balance: result.data.balance });
      } catch {
        if (!disposed) setPointAccount(undefined);
      }
    };

    void refreshBalance();
    window.addEventListener('focus', refreshBalance);
    return () => {
      disposed = true;
      window.removeEventListener('focus', refreshBalance);
    };
  }, [currentTenantId]);

  if (!currentUser) {
    return null;
  }

  const balance = pointAccount?.tenantId === currentTenantId ? pointAccount?.balance : undefined;
  const formattedBalance =
    balance === undefined ? '-' : balance.toLocaleString('zh-CN');

  return (
    <div className="ant-short-sidebar-account">
      {currentTenantId && <button
        aria-label={`团队积分 ${formattedBalance}，前往充值中心`}
        className="ant-short-sidebar-points"
        onClick={() => history.push('/recharge')}
        type="button"
      >
        <span className="ant-short-sidebar-points-icon">
          <WalletOutlined />
        </span>
        <span>团队积分</span>
        <span className="ant-short-sidebar-points-separator">·</span>
        <span className="ant-short-sidebar-points-value">
          {formattedBalance}
        </span>
      </button>}
      <AvatarDropdown
        overlayClassName="ant-short-sidebar-account-menu"
        placement="topLeft"
        triggerClassName="ant-short-sidebar-profile"
      />
    </div>
  );
};

export default SidebarAccount;
