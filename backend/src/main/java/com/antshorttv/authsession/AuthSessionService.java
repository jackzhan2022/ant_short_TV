package com.antshorttv.authsession;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserStatus;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthSessionMapper authSessionMapper;
    private final UserMapper userMapper;
    private final AuthSessionProperties properties;
    private final PlatformAuthorizationService platformAuthorization;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
        AuthSessionMapper authSessionMapper,
        UserMapper userMapper,
        AuthSessionProperties properties,
        PlatformAuthorizationService platformAuthorization
    ) {
        this.authSessionMapper = authSessionMapper;
        this.userMapper = userMapper;
        this.properties = properties;
        this.platformAuthorization = platformAuthorization;
    }

    @Transactional
    public IssuedSession issue(UserEntity user, String ip, String userAgent) {
        return issue(user, null, null, LocalDateTime.now().plus(properties.getTtl()), ip, userAgent);
    }

    private IssuedSession issue(UserEntity user, UserEntity target, Long tenantId,
        LocalDateTime expiresAt, String ip, String userAgent) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime now = LocalDateTime.now();

        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        if (target != null) {
            session.setImpersonatedUserId(target.getId());
            session.setImpersonatedTokenVersion(tokenVersion(target));
            session.setImpersonationTenantId(tenantId);
        }
        session.setTokenHash(hash(credential));
        session.setTokenVersion(tokenVersion(user));
        session.setStatus(AuthSessionStatus.ACTIVE.name());
        session.setExpiresAt(expiresAt);
        session.setLastSeenAt(now);
        session.setCreatedIp(ip);
        session.setUserAgent(userAgent);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        authSessionMapper.insert(session);
        return new IssuedSession(credential, session.getSessionId(), expiresAt);
    }

    /** The owner remains the administrator, while business requests run as the target. */
    @Transactional
    public IssuedSession exchange(AuthSessionEntity previous, UserEntity target, Long tenantId,
        String ip, String userAgent) {
        UserEntity owner = userMapper.selectById(previous.getUserId());
        if (!validUser(owner, previous.getTokenVersion())
            || !platformAuthorization.roleCodes(owner.getId()).contains("PLATFORM_ADMIN")
            || authSessionMapper.revokeForExchange(previous.getSessionId(),
                target == null ? "IMPERSONATION_STOP" : "IMPERSONATION_START", LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "管理员会话已失效，请重新登录。");
        }
        return issue(owner, target, tenantId, previous.getExpiresAt(), ip, userAgent);
    }

    /** Validate the real owner independently so disabled targets can still be exited. */
    public Optional<AuthSessionEntity> ownedSession(String credential) {
        if (credential == null || credential.isBlank()) return Optional.empty();
        AuthSessionEntity session = authSessionMapper.selectByTokenHash(hash(credential));
        if (session == null || !AuthSessionStatus.ACTIVE.name().equals(session.getStatus())
            || !session.getExpiresAt().isAfter(LocalDateTime.now())
            || !validUser(userMapper.selectById(session.getUserId()), session.getTokenVersion())) {
            return Optional.empty();
        }
        if (session.getImpersonatedUserId() != null
            && !platformAuthorization.roleCodes(session.getUserId()).contains("PLATFORM_ADMIN")) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private boolean validUser(UserEntity user, Long version) {
        return user != null && user.getDeletedAt() == null
            && UserStatus.ACTIVE.name().equals(user.getStatus())
            && version != null && version == tokenVersion(user);
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String credential) {
        AuthSessionEntity session = ownedSession(credential).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (session == null) return Optional.empty();
        UserEntity user = userMapper.selectById(session.getUserId());
        AuthenticatedUser.Impersonation impersonation = null;
        if (session.getImpersonatedUserId() != null) {
            UserEntity target = userMapper.selectById(session.getImpersonatedUserId());
            if (!validUser(target, session.getImpersonatedTokenVersion())
                || platformAuthorization.roleCodes(target.getId()).contains("PLATFORM_ADMIN")) return Optional.empty();
            impersonation = new AuthenticatedUser.Impersonation(user.getId(), user.getNickname(), session.getImpersonationTenantId());
            user = target;
        }

        if (session.getLastSeenAt().plus(properties.getActivityUpdateInterval()).isBefore(now)) {
            // Never write a stale ACTIVE status back after a concurrent logout or identity switch.
            if (authSessionMapper.updateActivity(session.getSessionId(), now) != 1) return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(
            user.getId(), user.getMobile(), session.getSessionId(), session.getExpiresAt(), impersonation));
    }

    @Transactional
    public void revokeCurrent(String sessionId, String reason) {
        AuthSessionEntity session = authSessionMapper.selectBySessionId(sessionId);
        if (session == null || !AuthSessionStatus.ACTIVE.name().equals(session.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(AuthSessionStatus.REVOKED.name());
        session.setRevokedAt(now);
        session.setRevokedReason(reason);
        session.setUpdatedAt(now);
        authSessionMapper.updateById(session);
    }

    @Transactional
    public void invalidateAll(Long userId, String reason) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        authSessionMapper.revokeActiveByUserId(userId, reason, LocalDateTime.now());
    }

    @Scheduled(fixedDelayString = "${auth.session.cleanup-interval:PT1H}")
    @Transactional
    public int cleanupExpiredAndRevoked() {
        LocalDateTime now = LocalDateTime.now();
        return authSessionMapper.deleteExpiredAndRevokedBefore(
            now,
            now.minus(properties.getCleanupRetention())
        );
    }

    private long tokenVersion(UserEntity user) {
        return user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    }

    private String hash(String credential) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                properties.getTokenPepper().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(credential.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot protect session credential", exception);
        }
    }
}
