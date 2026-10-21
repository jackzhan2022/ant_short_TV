package com.antshorttv.adminauth;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.user.UserMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminAuthenticatorService {
    private final JdbcTemplate jdbc;
    private final AiSecretCodec codec;
    private final PasswordEncoder passwords;
    private final UserMapper users;
    private final PlatformPermissionGuard guard;
    private final CurrentPrincipal principal;
    private final OperationLogService logs;
    private final TransactionTemplate attempts;
    private Clock clock = Clock.systemUTC();

    public AdminAuthenticatorService(JdbcTemplate jdbc, AiSecretCodec codec, PasswordEncoder passwords,
        UserMapper users, PlatformPermissionGuard guard, CurrentPrincipal principal,
        OperationLogService logs, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; this.codec = codec; this.passwords = passwords; this.users = users;
        this.guard = guard; this.principal = principal; this.logs = logs;
        attempts = new TransactionTemplate(transactions);
        attempts.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Status status() {
        guard.requireAdministrator();
        List<String> secrets = jdbc.query("select secret_cipher from admin_authenticator where user_id=?",
            (rs, n) -> rs.getString(1), principal.require().userId());
        return new Status(!secrets.isEmpty() && secrets.get(0) != null);
    }

    public Enrollment enroll(String password) {
        guard.requireAdministrator();
        Long userId = principal.require().userId();
        Attempt<Enrollment> result = attempts.execute(tx -> {
            State state = lock(userId);
            LocalDateTime now = now();
            if (locked(state, now)) return failure("验证失败次数过多，请十五分钟后重试。");
            if (state.secret() != null) return failure("已绑定验证器，不可重复绑定。");
            var user = users.selectById(userId);
            if (user == null || user.getDeletedAt() != null || !"ACTIVE".equals(user.getStatus())
                || password == null || !passwords.matches(password, user.getPasswordHash())) {
                failed(userId, state, now);
                return failure("登录密码不正确。");
            }
            String secret = AuthenticatorTotp.newSecret();
            LocalDateTime expiresAt = now.plusMinutes(10);
            // Starting a new enrollment must not reset the verification-attempt counter.
            jdbc.update("update admin_authenticator set pending_secret_cipher=?, pending_expires_at=?, updated_at=? where user_id=?",
                codec.encrypt(secret), expiresAt, now, userId);
            String label = URLEncoder.encode("AntShortTV:" + user.getMobile(), StandardCharsets.UTF_8);
            String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=AntShortTV&algorithm=SHA1&digits=6&period=30";
            return new Attempt<>(new Enrollment(secret, uri, expiresAt.toInstant(ZoneOffset.UTC).toString()), null);
        });
        return unwrap(result);
    }

    public void confirm(String code) { verify(code, true); }
    public void consume(String code) { verify(code, false); }

    private void verify(String code, boolean binding) {
        guard.requireAdministrator();
        Long userId = principal.require().userId();
        Attempt<Boolean> result = attempts.execute(tx -> {
            State state = lock(userId);
            LocalDateTime now = now();
            if (locked(state, now)) return failure("验证失败次数过多，请十五分钟后重试。");
            if (binding && state.secret() != null) return failure("已绑定验证器，不可重复绑定。");
            String cipher = binding ? state.pendingSecret() : state.secret();
            if (cipher == null || binding && (state.pendingExpiresAt() == null || !state.pendingExpiresAt().isAfter(now))) {
                return failure(binding ? "绑定信息已失效，请重新验证密码并绑定。" : "请先绑定本人验证器。");
            }
            long step = AuthenticatorTotp.match(codec.decrypt(cipher), code, clock.instant(), state.lastUsedStep());
            if (step < 0) {
                failed(userId, state, now);
                return failure("验证码错误、已过期或已使用，请输入新的验证码。");
            }
            if (binding) {
                jdbc.update("update admin_authenticator set secret_cipher=pending_secret_cipher, pending_secret_cipher=null, pending_expires_at=null where user_id=?", userId);
                logs.record(userId, null, "PLATFORM_BIND_AUTHENTICATOR", userId, OperationResult.SUCCESS, null);
            }
            jdbc.update("update admin_authenticator set last_used_step=?, failed_attempts=0, locked_until=null, updated_at=? where user_id=?",
                step, now, userId);
            return new Attempt<>(true, null);
        });
        unwrap(result);
    }

    private State lock(Long userId) {
        // Serialize initialization as well as checks across application instances.
        jdbc.queryForObject("select id from app_user where id=? for update", Long.class, userId);
        jdbc.update("insert into admin_authenticator (user_id, updated_at) select ?, ? where not exists (select 1 from admin_authenticator where user_id=?)", userId, now(), userId);
        return jdbc.queryForObject("select * from admin_authenticator where user_id=? for update", (rs, n) ->
            new State(rs.getString("secret_cipher"), rs.getString("pending_secret_cipher"),
                rs.getObject("pending_expires_at", LocalDateTime.class), rs.getLong("last_used_step"),
                rs.getInt("failed_attempts"), rs.getObject("locked_until", LocalDateTime.class)), userId);
    }

    private void failed(Long userId, State state, LocalDateTime now) {
        int count = state.lockedUntil() != null && !state.lockedUntil().isAfter(now) ? 1 : state.failures() + 1;
        jdbc.update("update admin_authenticator set failed_attempts=?, locked_until=?, updated_at=? where user_id=?",
            count, count >= 5 ? now.plusMinutes(15) : null, now, userId);
        logs.record(userId, null, "PLATFORM_AUTHENTICATOR_VERIFICATION", userId, OperationResult.FAILURE, null);
    }

    private boolean locked(State state, LocalDateTime now) { return state.lockedUntil() != null && state.lockedUntil().isAfter(now); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private <T> Attempt<T> failure(String error) { return new Attempt<>(null, error); }
    private <T> T unwrap(Attempt<T> result) {
        if (result == null) throw new IllegalStateException("Authenticator transaction returned no result");
        if (result.error() != null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, result.error());
        return result.value();
    }
    private record State(String secret, String pendingSecret, LocalDateTime pendingExpiresAt, long lastUsedStep, int failures, LocalDateTime lockedUntil) {}
    private record Attempt<T>(T value, String error) {}
    public record Status(boolean bound) {}
    public record Enrollment(String secret, String uri, String expiresAt) {}
}
