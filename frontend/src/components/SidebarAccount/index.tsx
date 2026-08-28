import { WalletOutlined } from '@ant-design/icons';
import type { LayoutCurrentUser } from '@/services/account-team/types';
import { AvatarDropdown } from '../RightContent/AvatarDropdown';

type SidebarAccountProps = {
  currentUser?: LayoutCurrentUser;
};

const SidebarAccount = ({ currentUser }: SidebarAccountProps) => {
  if (!currentUser) {
    return null;
  }

  return (
    <div className="ant-short-sidebar-account">
      <div className="ant-short-sidebar-points">
        <span className="ant-short-sidebar-points-icon">
          <WalletOutlined />
        </span>
        <span>团队积分</span>
      </div>
      <AvatarDropdown
        overlayClassName="ant-short-sidebar-account-menu"
        placement="topLeft"
        triggerClassName="ant-short-sidebar-profile"
      />
    </div>
  );
};

export default SidebarAccount;
