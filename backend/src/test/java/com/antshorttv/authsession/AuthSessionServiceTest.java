package com.antshorttv.authsession;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthSessionServiceTest {

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AuthSessionMapper authSessionMapper;

    @Autowired
    private UserMapper userMapper;

    private UserEntity user;

    @BeforeEach
    void createUser() {
        LocalDateTime now = LocalDateTime.now();
        user = new UserEntity();
        user.setMobile("139" + System.nanoTime());
        user.setPasswordHash("{noop}Password123");
        user.setNickname("Session User");
        user.setStatus(UserStatus.ACTIVE.name());
        user.setTokenVersion(0L);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }

    @Test
    void issuesOpaqueCredentialAndAuthenticatesFromStoredHash() {
        IssuedSession issued = authSessionService.issue(user, "127.0.0.1", "test-agent");

        AuthSessionEntity stored = authSessionMapper.selectBySessionId(issued.sessionId());
        assertThat(issued.credential()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(stored.getTokenHash()).doesNotContain(issued.credential());
        assertThat(stored.getCreatedIp()).isEqualTo("127.0.0.1");
        assertThat(authSessionService.authenticate(issued.credential()))
            .get()
            .extracting(AuthenticatedUser::userId, AuthenticatedUser::sessionId)
            .containsExactly(user.getId(), issued.sessionId());
    }

    @Test
    void rejectsInvalidExpiredRevokedAndVersionMismatchedCredentials() {
        assertThat(authSessionService.authenticate("not-a-session")).isEmpty();

        IssuedSession expired = authSessionService.issue(user, null, null);
        AuthSessionEntity expiredEntity = authSessionMapper.selectBySessionId(expired.sessionId());
        expiredEntity.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        authSessionMapper.updateById(expiredEntity);
        assertThat(authSessionService.authenticate(expired.credential())).isEmpty();

        IssuedSession revoked = authSessionService.issue(user, null, null);
        authSessionService.revokeCurrent(revoked.sessionId(), "LOGOUT");
        assertThat(authSessionService.authenticate(revoked.credential())).isEmpty();

        IssuedSession oldVersion = authSessionService.issue(user, null, null);
        user.setTokenVersion(1L);
        userMapper.updateById(user);
        assertThat(authSessionService.authenticate(oldVersion.credential())).isEmpty();
    }

    @Test
    void rejectsDisabledAndDeletedUsersOnEveryRequest() {
        IssuedSession disabled = authSessionService.issue(user, null, null);
        user.setStatus(UserStatus.DISABLED.name());
        userMapper.updateById(user);
        assertThat(authSessionService.authenticate(disabled.credential())).isEmpty();

        user.setStatus(UserStatus.ACTIVE.name());
        user.setDeletedAt(LocalDateTime.now());
        userMapper.updateById(user);
        assertThat(authSessionService.authenticate(disabled.credential())).isEmpty();
    }

    @Test
    void invalidatesEverySessionByRotatingUserTokenVersion() {
        IssuedSession first = authSessionService.issue(user, null, null);
        IssuedSession second = authSessionService.issue(user, null, null);

        authSessionService.invalidateAll(user.getId(), "LOGOUT_ALL");

        assertThat(authSessionService.authenticate(first.credential())).isEmpty();
        assertThat(authSessionService.authenticate(second.credential())).isEmpty();
        assertThat(userMapper.selectById(user.getId()).getTokenVersion()).isEqualTo(1L);
    }

    @Test
    void cleanupDeletesExpiredAndOldRevokedSessions() {
        IssuedSession expired = authSessionService.issue(user, null, null);
        AuthSessionEntity expiredEntity = authSessionMapper.selectBySessionId(expired.sessionId());
        expiredEntity.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        authSessionMapper.updateById(expiredEntity);

        IssuedSession revoked = authSessionService.issue(user, null, null);
        authSessionService.revokeCurrent(revoked.sessionId(), "LOGOUT");
        AuthSessionEntity revokedEntity = authSessionMapper.selectBySessionId(revoked.sessionId());
        revokedEntity.setRevokedAt(LocalDateTime.now().minusDays(31));
        authSessionMapper.updateById(revokedEntity);

        IssuedSession recentlyRevoked = authSessionService.issue(user, null, null);
        authSessionService.revokeCurrent(recentlyRevoked.sessionId(), "LOGOUT");

        int deleted = authSessionService.cleanupExpiredAndRevoked();

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(authSessionMapper.selectBySessionId(expired.sessionId())).isNull();
        assertThat(authSessionMapper.selectBySessionId(revoked.sessionId())).isNull();
        assertThat(authSessionMapper.selectBySessionId(recentlyRevoked.sessionId())).isNotNull();
    }

    @Test
    void authenticationDoesNotWriteActivityInsideTheThrottleWindow() {
        IssuedSession issued = authSessionService.issue(user, null, null);
        LocalDateTime originalLastSeen = authSessionMapper
            .selectBySessionId(issued.sessionId())
            .getLastSeenAt();

        assertThat(authSessionService.authenticate(issued.credential())).isPresent();

        assertThat(authSessionMapper.selectBySessionId(issued.sessionId()).getLastSeenAt())
            .isEqualTo(originalLastSeen);
    }
}
