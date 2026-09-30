package com.antshorttv.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class CdnTypeDSigner {
    private final String domain;
    private final String key;

    public CdnTypeDSigner(String domain, String key) {
        if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("CDN 域名和 Type D 密钥不能为空。");
        }
        String normalized = domain.trim();
        this.domain = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        this.key = key;
    }

    public String sign(String objectKey, Instant expiresAt) {
        String normalized = new ObjectStorageKeyFactory().objectKey(objectKey);
        if (expiresAt == null) {
            throw new IllegalArgumentException("CDN 链接过期时间不能为空。");
        }
        String path = "/" + normalized;
        String timestamp = Long.toHexString(expiresAt.getEpochSecond());
        String signature = md5(path + timestamp + key);
        return domain + path + "?sign=" + signature + "&t=" + timestamp;
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 CDN Type D 签名。", exception);
        }
    }
}
