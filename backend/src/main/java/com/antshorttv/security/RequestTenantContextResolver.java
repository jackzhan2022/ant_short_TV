package com.antshorttv.security;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestTenantContextResolver {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final CurrentPrincipal currentPrincipal;
    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;

    public RequestTenantContextResolver(
        CurrentPrincipal currentPrincipal,
        TenantMapper tenantMapper,
        TenantMemberMapper tenantMemberMapper
    ) {
        this.currentPrincipal = currentPrincipal;
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
    }

    public TenantContext require(HttpServletRequest request) {
        String header = request.getHeader(TENANT_HEADER);
        if (header == null || header.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少团队上下文。");
        }
        Long tenantId = parse(header);
        TenantSelectionResult result = resolve(tenantId);
        if (result.context() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该创作团队。");
        }
        return result.context();
    }

    public TenantSelectionResult resolveHeader(String header) {
        if (header == null || header.isBlank()) {
            return TenantSelectionResult.unavailable("MISSING_SELECTION");
        }
        try {
            return resolve(Long.valueOf(header));
        } catch (NumberFormatException exception) {
            return TenantSelectionResult.unavailable("INVALID_TENANT_ID");
        }
    }

    public TenantSelectionResult resolve(Long tenantId) {
        Long userId = currentPrincipal.require().userId();
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getDeletedAt() != null) {
            return TenantSelectionResult.unavailable("TENANT_UNAVAILABLE");
        }
        if (!TenantStatus.ACTIVE.name().equals(tenant.getStatus())) {
            return TenantSelectionResult.unavailable("TENANT_DISABLED");
        }
        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (member == null) {
            return TenantSelectionResult.unavailable("TENANT_UNAVAILABLE");
        }
        if (!MemberStatus.ACTIVE.name().equals(member.getStatus())) {
            return TenantSelectionResult.unavailable("MEMBERSHIP_REMOVED");
        }
        return TenantSelectionResult.available(new TenantContext(
            userId, tenantId, member.getId(), member.getMemberType()));
    }

    private Long parse(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
        }
    }
}
