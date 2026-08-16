package com.antshorttv.auth;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.AccessTokenService;
import com.antshorttv.security.CurrentUserHolder;
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
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final AccessTokenService accessTokenService;
    private final OperationLogService operationLogService;

    public AuthService(
        UserMapper userMapper,
        PasswordEncoder passwordEncoder,
        VerificationCodeService verificationCodeService,
        AccessTokenService accessTokenService,
        OperationLogService operationLogService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.accessTokenService = accessTokenService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public AuthSessionResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
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

        operationLogService.record(user.getId(), null, "REGISTER", user.getId(), OperationResult.SUCCESS, servletRequest);
        return sessionFor(user, "CREATE_OR_JOIN_TEAM");
    }

    @Transactional
    public AuthSessionResponse login(LoginByMobileRequest request, HttpServletRequest servletRequest) {
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
        operationLogService.record(user.getId(), null, "LOGIN", user.getId(), OperationResult.SUCCESS, servletRequest);
        return sessionFor(user, "CREATE_OR_JOIN_TEAM");
    }

    public UserProfileResponse currentUser() {
        Long userId = CurrentUserHolder.require().userId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录。");
        }
        return UserProfileResponse.from(user);
    }

    public void logout(HttpServletRequest servletRequest) {
        CurrentUserHolder.get().ifPresent(user ->
            operationLogService.record(user.userId(), null, "LOGOUT", user.userId(), OperationResult.SUCCESS, servletRequest));
    }

    private AuthSessionResponse sessionFor(UserEntity user, String nextAction) {
        return new AuthSessionResponse(
            accessTokenService.issue(user),
            UserProfileResponse.from(user),
            List.<TenantSummaryResponse>of(),
            nextAction
        );
    }
}
