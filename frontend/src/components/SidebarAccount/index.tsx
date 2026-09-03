import { WalletOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { useEffect, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount } from '@/services/account-team/points';
import type { LayoutCurrentUser } from '@/services/account-team/types';
import { AvatarDropdown } from '../RightContent/AvatarDropdown';

type SidebarAccountProps = {
  currentUser?: LayoutCurrentUser;
};

const SidebarAccount = ({ currentUser }: SidebarAccountProps) => {
  const [balance, setBalance] = useState<number>();

  useEffect(() => {
    let disposed = false;
    const refreshBalance = async () => {
      const tenantId = getCurrentTenantId();
      if (!tenantId) {
        if (!disposed) setBalance(undefined);
        return;
      }
      try {
        const result = await queryTeamPointAccount(tenantId);
        if (!disposed) setBalance(result.data?.balance);
      } catch {
        if (!disposed) setBalance(undefined);
      }
    };

    void refreshBalance();
    window.addEventListener('focus', refreshBalance);
    return () => {
      disposed = true;
      window.removeEventListener('focus', refreshBalance);
    };
  }, []);

  if (!currentUser) {
    return null;
  }

  const formattedBalance =
    balance === undefined ? '-' : balance.toLocaleString('zh-CN');

  return (
    <div className="ant-short-sidebar-account">
      <button
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
      </button>
      <AvatarDropdown
        overlayClassName="ant-short-sidebar-account-menu"
        placement="topLeft"
        triggerClassName="ant-short-sidebar-profile"
      />
    </div>
  );
};

export default SidebarAccount;
