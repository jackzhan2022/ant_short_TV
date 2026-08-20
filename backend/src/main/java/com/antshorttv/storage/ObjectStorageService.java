package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {
    private final ObjectStorageProperties properties;
    private MinioClient client;
    private boolean bucketReady;

    public ObjectStorageService(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    @PostConstruct
    public void initialize() {
        if (enabled()) {
            try {
                ensureBucket();
            } catch (Exception exception) {
                throw new IllegalStateException("对象存储初始化失败：" + exception.getMessage(), exception);
            }
        }
    }

    public void upload(String storagePath, byte[] bytes, String contentType) {
        if (!enabled()) {
            return;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            put(storagePath, input, bytes.length, contentType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "对象存储上传失败：" + exception.getMessage());
        }
    }

    public void uploadFile(String storagePath, Path file, String contentType) {
        if (!enabled()) {
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            put(storagePath, input, Files.size(file), contentType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "对象存储上传失败：" + exception.getMessage());
        }
    }

    public Resource resource(String storagePath) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象存储未启用。");
        }
        try {
            ensureBucket();
            InputStream object = client().getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key(storagePath))
                .build());
            return new InputStreamResource(object);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象文件不存在。");
        }
    }

    private void put(String storagePath, InputStream input, long size, String contentType) throws Exception {
        ensureBucket();
        client().putObject(PutObjectArgs.builder()
            .bucket(properties.getBucket())
            .object(key(storagePath))
            .stream(input, size, -1)
            .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
            .build());
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        boolean exists = client().bucketExists(BucketExistsArgs.builder()
            .bucket(properties.getBucket())
            .build());
        if (!exists && properties.isAutoCreateBucket()) {
            MakeBucketArgs.Builder builder = MakeBucketArgs.builder().bucket(properties.getBucket());
            if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
                builder.region(properties.getRegion());
            }
            client().makeBucket(builder.build());
            exists = true;
        }
        if (!exists) {
            throw new IllegalStateException("对象桶不存在：" + properties.getBucket());
        }
        bucketReady = true;
    }

    private MinioClient client() {
        if (client == null) {
            if (blank(properties.getEndpoint()) || blank(properties.getAccessKey()) || blank(properties.getSecretKey())) {
                throw new IllegalStateException("对象存储 endpoint/accessKey/secretKey 未配置。");
            }
            MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());
            if (!blank(properties.getRegion())) {
                builder.region(properties.getRegion());
            }
            client = builder.build();
        }
        return client;
    }

    private String key(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("对象路径不能为空。");
        }
        String normalized = storagePath.startsWith("/") ? storagePath.substring(1) : storagePath;
        if (normalized.contains("..") || normalized.startsWith("/") || normalized.isBlank()) {
            throw new IllegalArgumentException("对象路径不合法。");
        }
        return normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
