package com.antshorttv.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class CdnTypeDSigner {
    private final String domain;
    private final String key;
    private final long authenticationSeconds;

    public CdnTypeDSigner(String domain, String key) {
        this(domain, key, 604800);
    }

    public CdnTypeDSigner(String domain, String key, long authenticationSeconds) {
        if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("CDN 域名和 Type D 密钥不能为空。");
        }
        String normalized = domain.trim();
        this.domain = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        this.key = key;
        if (authenticationSeconds < 1) throw new IllegalArgumentException("CDN 鉴权有效时长必须大于零。");
        this.authenticationSeconds = authenticationSeconds;
    }

    public String sign(String objectKey, Instant expiresAt) {
        String normalized = new ObjectStorageKeyFactory().objectKey(objectKey);
        if (expiresAt == null) {
            throw new IllegalArgumentException("CDN 链接过期时间不能为空。");
        }
        String path = "/" + normalized;
        // Tencent adds its configured lifetime to t; derive t from the persisted expiry.
        long generatedAt = Math.subtractExact(expiresAt.getEpochSecond(), authenticationSeconds);
        if (generatedAt < 0) throw new IllegalArgumentException("CDN 鉴权时间戳不能小于零。");
        String timestamp = Long.toHexString(generatedAt);
        String signature = sha256(key + path + timestamp);
        return domain + path + "?sign=" + signature + "&t=" + timestamp;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 CDN Type D 签名。", exception);
        }
    }
}
