package com.antshorttv.bootstrap;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.rbac.RbacService;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.security.RequestTenantContextResolver;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantSelectionResult;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.tenant.TenantStatus;
import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserProfileResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthBootstrapService {

    private final CurrentPrincipal currentPrincipal;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final RequestTenantContextResolver requestTenantContextResolver;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final RbacService rbacService;
    private final RbacPermissionService rbacPermissionService;

    public AuthBootstrapService(
        CurrentPrincipal currentPrincipal,
        UserMapper userMapper,
        TenantMapper tenantMapper,
        TenantMemberMapper tenantMemberMapper,
        RequestTenantContextResolver requestTenantContextResolver,
        PlatformAuthorizationService platformAuthorizationService,
        RbacService rbacService,
        RbacPermissionService rbacPermissionService
    ) {
        this.currentPrincipal = currentPrincipal;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.requestTenantContextResolver = requestTenantContextResolver;
        this.platformAuthorizationService = platformAuthorizationService;
        this.rbacService = rbacService;
        this.rbacPermissionService = rbacPermissionService;
    }

    @Transactional
    public AuthBootstrapResponse bootstrap(String tenantHeader) {
        AuthenticatedUser principal = currentPrincipal.require();
        UserEntity user = userMapper.selectById(principal.userId());
        List<TenantSummaryResponse> tenants = activeTenants(principal.userId());

        TenantSelectionResult selection = selectTenant(tenantHeader, tenants);
        SelectedTenantResponse selectedTenant = selection.context() == null
            ? null
            : selectedTenant(selection.context());

        return new AuthBootstrapResponse(
            UserProfileResponse.from(user),
            new BootstrapSessionResponse(principal.sessionId(), principal.expiresAt()),
            new PlatformAccessResponse(
                platformAuthorizationService.roleCodes(principal.userId()),
                platformAuthorizationService.permissionCodes(principal.userId())),
            tenants,
            selectedTenant,
            "MISSING_SELECTION".equals(selection.unavailableReason()) ? null : selection.unavailableReason(),
            nextAction(tenants, selectedTenant)
        );
    }

    private List<TenantSummaryResponse> activeTenants(Long userId) {
        List<TenantSummaryResponse> responses = new ArrayList<>();
        for (TenantMemberEntity member : tenantMemberMapper.selectActiveByUserId(userId)) {
            TenantEntity tenant = tenantMapper.selectById(member.getTenantId());
            if (tenant != null
                && tenant.getDeletedAt() == null
                && TenantStatus.ACTIVE.name().equals(tenant.getStatus())) {
                responses.add(TenantSummaryResponse.from(tenant, member.getMemberType(), member.getId()));
            }
        }
        return responses;
    }

    private TenantSelectionResult selectTenant(String tenantHeader, List<TenantSummaryResponse> tenants) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return requestTenantContextResolver.resolveHeader(tenantHeader);
        }
        if (tenants.size() == 1) {
            return requestTenantContextResolver.resolve(tenants.get(0).id());
        }
        return TenantSelectionResult.unavailable("MISSING_SELECTION");
    }

    private SelectedTenantResponse selectedTenant(TenantContext context) {
        rbacService.initializeTenant(context.tenantId());
        TenantEntity tenant = tenantMapper.selectById(context.tenantId());
        TenantMemberEntity member = tenantMemberMapper.selectById(context.memberId());
        return new SelectedTenantResponse(
            TenantSummaryResponse.from(tenant, member.getMemberType(), member.getId()),
            new TenantMembershipResponse(member.getId(), member.getMemberType(), member.getStatus()),
            rbacPermissionService.roleCodes(context),
            rbacPermissionService.permissionCodes(context)
        );
    }

    private String nextAction(List<TenantSummaryResponse> tenants, SelectedTenantResponse selectedTenant) {
        if (selectedTenant != null) {
            return "ENTER_WORKSPACE";
        }
        return tenants.isEmpty() ? "CREATE_OR_JOIN_TEAM" : "SELECT_TENANT";
    }
}
