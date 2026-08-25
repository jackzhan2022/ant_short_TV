package com.antshorttv.auth;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.platform.PlatformAdminBootstrap;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.authsession.IssuedSession;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserProfileResponse;
import com.antshorttv.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final AuthSessionService authSessionService;
    private final CurrentPrincipal currentPrincipal;
    private final OperationLogService operationLogService;
    private final PlatformAdminBootstrap platformAdminBootstrap;

    public AuthService(
        UserMapper userMapper,
        TenantMapper tenantMapper,
        TenantMemberMapper tenantMemberMapper,
        PasswordEncoder passwordEncoder,
        VerificationCodeService verificationCodeService,
        AuthSessionService authSessionService,
        CurrentPrincipal currentPrincipal,
        OperationLogService operationLogService,
        PlatformAdminBootstrap platformAdminBootstrap
    ) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.authSessionService = authSessionService;
        this.currentPrincipal = currentPrincipal;
        this.operationLogService = operationLogService;
        this.platformAdminBootstrap = platformAdminBootstrap;
    }

    @Transactional
    public AuthResult register(RegisterRequest request, HttpServletRequest servletRequest) {
        verificationCodeService.verify(request.mobile(), request.verificationCode());
        if (userMapper.selectByMobile(request.mobile()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_MOBILE, "该手机号已注册。");
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setMobile(request.mobile());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        platformAdminBootstrap.assignIfConfigured(user);

        operationLogService.record(user.getId(), null, "REGISTER", user.getId(), OperationResult.SUCCESS, servletRequest);
        return sessionFor(user, "CREATE_OR_JOIN_TEAM", servletRequest);
    }

    @Transactional
    public AuthResult login(LoginByMobileRequest request, HttpServletRequest servletRequest) {
        UserEntity user = userMapper.selectByMobile(request.mobile());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "手机号或密码错误。");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用。");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        platformAdminBootstrap.assignIfConfigured(user);
        operationLogService.record(user.getId(), null, "LOGIN", user.getId(), OperationResult.SUCCESS, servletRequest);
        return sessionFor(user, "CREATE_OR_JOIN_TEAM", servletRequest);
    }

    public void logout(HttpServletRequest servletRequest) {
        currentPrincipal.get().ifPresent(user -> {
            authSessionService.revokeCurrent(user.sessionId(), "LOGOUT");
            operationLogService.record(user.userId(), null, "LOGOUT", user.userId(), OperationResult.SUCCESS, servletRequest);
        });
    }

    private AuthResult sessionFor(UserEntity user, String nextAction, HttpServletRequest servletRequest) {
        List<TenantSummaryResponse> tenants = tenantMemberMapper.selectActiveByUserId(user.getId())
            .stream()
            .map(this::tenantSummary)
            .toList();
        IssuedSession issuedSession = authSessionService.issue(
            user,
            servletRequest == null ? null : servletRequest.getRemoteAddr(),
            servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
        AuthSessionResponse response = new AuthSessionResponse(
            UserProfileResponse.from(user),
            tenants,
            nextActionFor(tenants, nextAction),
            issuedSession.expiresAt()
        );
        return new AuthResult(response, issuedSession);
    }

    private TenantSummaryResponse tenantSummary(TenantMemberEntity member) {
        TenantEntity tenant = tenantMapper.selectById(member.getTenantId());
        return TenantSummaryResponse.from(tenant, member.getMemberType(), member.getId());
    }

    private String nextActionFor(List<TenantSummaryResponse> tenants, String fallback) {
        if (tenants.isEmpty()) {
            return fallback;
        }
        if (tenants.size() == 1) {
            return "ENTER_WORKSPACE";
        }
        return "SELECT_TENANT";
    }
}
