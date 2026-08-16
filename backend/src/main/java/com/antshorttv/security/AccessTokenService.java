package com.antshorttv.security;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final String SECRET = "ant-short-tv-local-access-token-secret";

    public String issue(UserEntity user) {
        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = "%d:%s:%d:%s".formatted(user.getId(), user.getMobile(), expiresAt, UUID.randomUUID());
        String encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public CurrentUser parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录。");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split(":", 4);
        if (values.length != 4) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录。");
        }
        long expiresAt = Long.parseLong(values[2]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录。");
        }
        return new CurrentUser(Long.parseLong(values[0]), values[1]);
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign access token", exception);
        }
    }
}
