export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type UserProfile = {
  id: number;
  mobile: string;
  email?: string | null;
  nickname: string;
  avatar?: string | null;
  status: 'ACTIVE' | 'DISABLED';
};

export type LayoutCurrentUser = {
  name?: string;
  avatar?: string;
  userid?: string;
  email?: string;
  phone?: string;
  title?: string;
  group?: string;
  access?: string;
};

export type TenantType = 'COMPANY' | 'STUDIO' | 'PERSONAL' | 'OTHER';
export type TenantStatus = 'ACTIVE' | 'DISABLED';
export type MemberType = 'OWNER' | 'MEMBER';
export type MemberStatus = 'ACTIVE' | 'REMOVED';
export type RoleType = 'SYSTEM' | 'CUSTOM';
export type RoleStatus = 'ACTIVE' | 'DISABLED';
export type PermissionType = 'MENU' | 'PAGE' | 'BUTTON' | 'API';
export type InvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED';

export type TenantSummary = {
  id: number;
  code: string;
  name: string;
  type: TenantType;
  logo?: string | null;
  description?: string | null;
  status: TenantStatus;
  memberType: MemberType;
  memberId: number;
};

export type AuthSession = {
  user: UserProfile;
  tenants: TenantSummary[];
  nextAction: 'CREATE_OR_JOIN_TEAM' | 'ENTER_WORKSPACE' | 'SELECT_TENANT';
  expiresAt: string;
};

export type BootstrapSession = {
  sessionId: string;
  expiresAt: string;
};

export type PlatformAccess = {
  roles: string[];
  permissions: string[];
};

export type BootstrapTenantMembership = {
  id: number;
  memberType: MemberType;
  status: MemberStatus;
};

export type SelectedTenantAccess = {
  tenant: TenantSummary;
  membership: BootstrapTenantMembership;
  roles: string[];
  permissions: string[];
};

export type AuthBootstrap = {
  user: UserProfile;
  session: BootstrapSession;
  platform: PlatformAccess;
  tenants: TenantSummary[];
  selectedTenant?: SelectedTenantAccess | null;
  unavailableSelectionReason?: string | null;
  nextAction: 'CREATE_OR_JOIN_TEAM' | 'ENTER_WORKSPACE' | 'SELECT_TENANT';
};

export type TenantMember = {
  id: number;
  tenantId: number;
  userId: number;
  mobile?: string | null;
  nickname?: string | null;
  avatar?: string | null;
  memberType: MemberType;
  status: MemberStatus;
  joinedAt: string;
};

export type TenantInvitation = {
  id: number;
  tenantId: number;
  tenantName?: string | null;
  inviteMobile: string;
  inviteUserId?: number | null;
  invitedBy: number;
  token: string;
  status: InvitationStatus;
  expiredAt: string;
  acceptedAt?: string | null;
  createdAt: string;
};

export type TeamPointAccount = {
  tenantId: number;
  balance: number;
  totalGranted: number;
  totalConsumed: number;
  updatedAt?: string | null;
};

export type TeamPointTransaction = {
  id: number;
  tenantId: number;
  userId: number;
  transactionType: string;
  changeAmount: number;
  balanceAfter: number;
  businessScene?: string | null;
  businessId?: number | null;
  description?: string | null;
  createdAt: string;
};

export type TeamPointTransactionPage = {
  records: TeamPointTransaction[];
  total: number;
  current: number;
  pageSize: number;
};

export type Role = {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  description?: string | null;
  roleType: RoleType;
  status: RoleStatus;
  isDefault: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
};

export type Permission = {
  id: number;
  code: string;
  name: string;
  type: PermissionType;
  resource: string;
  action: string;
};

export type PermissionTreeNode = {
  key: string;
  title: string;
  resource: string;
  permissionCode?: string | null;
  children?: PermissionTreeNode[];
};

export type ProjectStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'COMPLETED'
  | 'ARCHIVED';

export type ProjectMemberStatus = 'ACTIVE' | 'REMOVED';
export type ProjectRoleStatus = 'ACTIVE' | 'DISABLED';

export type Project = {
  id: number;
  tenantId: number;
  name: string;
  code: string;
  description?: string | null;
  coverUrl?: string | null;
  coverSource?: string | null;
  ownerId: number;
  ownerName?: string | null;
  status: ProjectStatus;
  startDate?: string | null;
  endDate?: string | null;
  aspectRatio?: string | null;
  fileFormat?: string | null;
  scriptType?: string | null;
  breakdownStrength?: string | null;
  visualStyle?: string | null;
  initialScriptContent?: string | null;
  memberCount: number;
  accessSource: 'TENANT_WIDE' | 'PROJECT_MEMBERSHIP';
  projectRoleCode?: string | null;
  projectRoleName?: string | null;
  effectivePermissions: string[];
  capabilities: {
    canView: boolean;
    canEdit: boolean;
    canDelete: boolean;
    canManageMembers: boolean;
    canManageRoles: boolean;
  };
  createdAt: string;
  updatedAt: string;
};

export type ProjectMember = {
  id: number;
  tenantId: number;
  projectId: number;
  userId: number;
  nickname?: string | null;
  mobile?: string | null;
  roleId: number;
  roleName?: string | null;
  roleCode?: string | null;
  status: ProjectMemberStatus;
  joinedAt: string;
};

export type ProjectRole = {
  id: number;
  tenantId: number;
  projectId: number;
  name: string;
  code: string;
  description?: string | null;
  isSystem: boolean;
  status: ProjectRoleStatus;
  createdAt: string;
  updatedAt: string;
};
