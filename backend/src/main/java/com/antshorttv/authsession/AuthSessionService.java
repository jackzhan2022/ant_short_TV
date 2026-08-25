package com.antshorttv.authsession;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserStatus;
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
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
        AuthSessionMapper authSessionMapper,
        UserMapper userMapper,
        AuthSessionProperties properties
    ) {
        this.authSessionMapper = authSessionMapper;
        this.userMapper = userMapper;
        this.properties = properties;
    }

    @Transactional
    public IssuedSession issue(UserEntity user, String ip, String userAgent) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(properties.getTtl());

        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
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

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String credential) {
        if (credential == null || credential.isBlank()) {
            return Optional.empty();
        }
        AuthSessionEntity session = authSessionMapper.selectByTokenHash(hash(credential));
        LocalDateTime now = LocalDateTime.now();
        if (session == null
            || !AuthSessionStatus.ACTIVE.name().equals(session.getStatus())
            || !session.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null
            || user.getDeletedAt() != null
            || !UserStatus.ACTIVE.name().equals(user.getStatus())
            || session.getTokenVersion() != tokenVersion(user)) {
            return Optional.empty();
        }

        if (session.getLastSeenAt().plus(properties.getActivityUpdateInterval()).isBefore(now)) {
            session.setLastSeenAt(now);
            session.setUpdatedAt(now);
            authSessionMapper.updateById(session);
        }
        return Optional.of(new AuthenticatedUser(
            user.getId(), user.getMobile(), session.getSessionId(), session.getExpiresAt()));
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
