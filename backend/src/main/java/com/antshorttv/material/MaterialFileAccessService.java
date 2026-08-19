package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class MaterialFileAccessService {
    private final Path storageRoot;
    private final byte[] secret;

    public MaterialFileAccessService(
        @Value("${ai.video.storage-root:storage}") String storageRoot,
        @Value("${material.access-secret:ant-short-tv-material-access}") String secret
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String publicUrl(String storagePath) {
        if (storagePath == null || !storagePath.startsWith("/materials/")) {
            return storagePath;
        }
        String path = pathOnly(storagePath);
        return path + "?token=" + token(path);
    }

    public boolean isValidToken(String storagePath, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(token(pathOnly(storagePath)).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    public Resource resource(String storagePath) {
        Path file = resolve(storagePath);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材文件不存在。");
        }
        return new FileSystemResource(file);
    }

    public String contentType(String storagePath) {
        String value = storagePath == null ? "" : pathOnly(storagePath).toLowerCase();
        if (value.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (value.endsWith(".png")) {
            return "image/png";
        }
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (value.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (value.endsWith(".srt")) {
            return "application/x-subrip";
        }
        return "application/octet-stream";
    }

    private Path resolve(String storagePath) {
        String path = pathOnly(storagePath);
        if (path == null || !path.startsWith("/materials/") || path.contains("..")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材文件不存在。");
        }
        Path file = storageRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材文件不存在。");
        }
        return file;
    }

    private String token(String storagePath) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(storagePath.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("素材访问签名生成失败", exception);
        }
    }

    private String pathOnly(String storagePath) {
        if (storagePath == null) {
            return null;
        }
        int queryStart = storagePath.indexOf('?');
        return queryStart < 0 ? storagePath : storagePath.substring(0, queryStart);
    }
}
