package com.antshorttv.security;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.tenant.TenantStatus;
import org.springframework.stereotype.Service;

@Service
public class TenantContextResolver {

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;

    public TenantContextResolver(TenantMapper tenantMapper, TenantMemberMapper tenantMemberMapper) {
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
    }

    public TenantContext requireActiveMember(Long tenantId) {
        CurrentUser currentUser = CurrentUserHolder.require();
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该创作团队。");
        }
        if (TenantStatus.DISABLED.name().equals(tenant.getStatus())) {
            throw new BusinessException(ErrorCode.TENANT_DISABLED, "当前创作团队已被停用，暂时无法进入。");
        }

        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, currentUser.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该创作团队。");
        }
        if (!MemberStatus.ACTIVE.name().equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.MEMBER_REMOVED, "你已不再是该创作团队成员。");
        }
        return new TenantContext(currentUser.userId(), tenantId, member.getId(), member.getMemberType());
    }

    public TenantContext requireOwner(Long tenantId) {
        TenantContext context = requireActiveMember(tenantId);
        if (!MemberType.OWNER.name().equals(context.memberType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅团队所有者可执行该操作。");
        }
        return context;
    }
}
