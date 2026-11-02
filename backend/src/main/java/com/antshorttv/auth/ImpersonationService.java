package com.antshorttv.auth;

import com.antshorttv.adminauth.AdminAuthenticatorService;
import com.antshorttv.authsession.AuthSessionCookieService;
import com.antshorttv.authsession.AuthSessionEntity;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.authsession.IssuedSession;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImpersonationService {
    private final CurrentPrincipal principal;
    private final PlatformPermissionGuard guard;
    private final PlatformAuthorizationService authorization;
    private final AdminAuthenticatorService authenticator;
    private final TenantMapper tenants;
    private final TenantMemberMapper members;
    private final UserMapper users;
    private final AuthSessionService sessions;
    private final AuthSessionCookieService cookies;
    private final OperationLogService logs;

    public ImpersonationService(CurrentPrincipal principal, PlatformPermissionGuard guard,
        PlatformAuthorizationService authorization, AdminAuthenticatorService authenticator,
        TenantMapper tenants, TenantMemberMapper members, UserMapper users,
        AuthSessionService sessions, AuthSessionCookieService cookies, OperationLogService logs) {
        this.principal = principal;
        this.guard = guard;
        this.authorization = authorization;
        this.authenticator = authenticator;
        this.tenants = tenants;
        this.members = members;
        this.users = users;
        this.sessions = sessions;
        this.cookies = cookies;
        this.logs = logs;
    }

    @Transactional
    public IssuedSession start(Long tenantId, Long memberId, String code, String reason, HttpServletRequest request) {
        guard.requireAdministrator();
        if (principal.require().impersonation() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先返回管理员，再切换登录。");
        }
        var tenant = tenants.selectById(tenantId);
        var member = members.selectById(memberId);
        if (tenant == null || tenant.getDeletedAt() != null || member == null || !tenantId.equals(member.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租户或成员不存在。");
        }
        var target = users.selectById(member.getUserId());
        if (!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(member.getStatus())
            || target == null || target.getDeletedAt() != null || !"ACTIVE".equals(target.getStatus())
            || target.getId().equals(principal.require().userId())
            || authorization.roleCodes(target.getId()).contains("PLATFORM_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该成员不可切换登录，请检查租户、成员及账号状态。");
        }
        authenticator.consume(code);
        AuthSessionEntity previous = ownedSession(request);
        IssuedSession issued = sessions.exchange(previous, target, tenantId, request.getRemoteAddr(), request.getHeader("User-Agent"));
        logs.record(previous.getUserId(), tenantId, "IMPERSONATION_START", target.getId(), OperationResult.SUCCESS,
            request, Map.of("administratorId", previous.getUserId(), "targetUserId", target.getId(),
                "memberId", memberId, "reason", reason.trim(), "sessionId", issued.sessionId()));
        return issued;
    }

    @Transactional
    public IssuedSession stop(HttpServletRequest request) {
        AuthSessionEntity previous = ownedSession(request);
        if (previous.getImpersonatedUserId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前不处于代登录状态。");
        }
        IssuedSession issued = sessions.exchange(previous, null, null, request.getRemoteAddr(), request.getHeader("User-Agent"));
        logs.record(previous.getUserId(), previous.getImpersonationTenantId(), "IMPERSONATION_STOP",
            previous.getImpersonatedUserId(), OperationResult.SUCCESS, request,
            Map.of("administratorId", previous.getUserId(), "targetUserId", previous.getImpersonatedUserId(),
                "sessionId", previous.getSessionId()));
        return issued;
    }

    private AuthSessionEntity ownedSession(HttpServletRequest request) {
        return cookies.read(request).flatMap(sessions::ownedSession)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "管理员会话已失效，请重新登录。"));
    }
}
