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

export type TenantType = 'COMPANY' | 'STUDIO' | 'PERSONAL' | 'OTHER';
export type TenantStatus = 'ACTIVE' | 'DISABLED';
export type MemberType = 'OWNER' | 'MEMBER';
export type MemberStatus = 'ACTIVE' | 'REMOVED';
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
  accessToken: string;
  user: UserProfile;
  tenants: TenantSummary[];
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

export type CurrentTenant = {
  userId: number;
  tenantId: number;
  memberId: number;
  memberType: MemberType;
};
