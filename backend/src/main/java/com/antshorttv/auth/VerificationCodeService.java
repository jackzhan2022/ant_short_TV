package com.antshorttv.auth;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class VerificationCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final SmsSender smsSender;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();

    public VerificationCodeService(SmsSender smsSender, PasswordEncoder passwordEncoder) {
        this.smsSender = smsSender;
        this.passwordEncoder = passwordEncoder;
    }

    public synchronized void sendRegistrationCode(String mobile) {
        Instant now = Instant.now();
        VerificationCode current = verificationCodes.get(mobile);
        if (current != null && now.isBefore(current.sentAt().plus(RESEND_COOLDOWN))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码发送过于频繁，请稍后重试。");
        }

        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        smsSender.sendRegistrationVerificationCode(mobile, code);
        verificationCodes.put(mobile, new VerificationCode(passwordEncoder.encode(code), now, now.plus(CODE_TTL), 0));
    }

    public synchronized void verify(String mobile, String verificationCode) {
        VerificationCode current = verificationCodes.get(mobile);
        Instant now = Instant.now();
        if (current == null || !now.isBefore(current.expiresAt())) {
            verificationCodes.remove(mobile);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码错误。");
        }
        if (!passwordEncoder.matches(verificationCode, current.codeHash())) {
            if (current.attempts() + 1 >= MAX_VERIFY_ATTEMPTS) {
                verificationCodes.remove(mobile);
            } else {
                verificationCodes.put(mobile, current.withAttempts(current.attempts() + 1));
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码错误。");
        }
        verificationCodes.remove(mobile);
    }

    private record VerificationCode(String codeHash, Instant sentAt, Instant expiresAt, int attempts) {
        private VerificationCode withAttempts(int newAttempts) {
            return new VerificationCode(codeHash, sentAt, expiresAt, newAttempts);
        }
    }
}
