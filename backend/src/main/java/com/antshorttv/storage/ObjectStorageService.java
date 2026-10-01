package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.qcloud.cos.COS;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.StorageClass;
import com.qcloud.cos.transfer.TransferManager;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {
    private final ObjectStorageProperties properties;
    private final COS cos;
    private final TransferManager transfers;
    private final ObjectStorageKeyFactory keys;
    private final CosStorageMetrics metrics;
    private CdnTypeDSigner cdnSigner;

    public ObjectStorageService(
        ObjectStorageProperties properties,
        COS cos,
        ObjectStorageKeyFactory keys,
        CosStorageMetrics metrics
    ) {
        this(properties, cos, null, keys, metrics);
    }

    @Autowired
    public ObjectStorageService(
        ObjectStorageProperties properties,
        COS cos,
        TransferManager transfers,
        ObjectStorageKeyFactory keys,
        CosStorageMetrics metrics
    ) {
        this.properties = properties;
        this.cos = cos;
        this.transfers = transfers;
        this.keys = keys;
        this.metrics = metrics;
    }

    @PostConstruct
    public void initialize() {
        properties.validate();
        cdnSigner = new CdnTypeDSigner(properties.getCdnDomain(), properties.getCdnTypeDKey());
    }

    public void upload(String storagePath, byte[] bytes, String contentType) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            upload(storagePath, input, bytes.length, contentType);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("对象存储上传失败", exception);
        }
    }

    public StoredObject uploadOriginal(String storagePath, byte[] bytes, String contentType) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return uploadOriginal(storagePath, input, bytes.length, contentType);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("原始对象上传失败", exception);
        }
    }

    public StoredObject uploadOriginal(
        String storagePath,
        InputStream input,
        long size,
        String contentType
    ) {
        uploadObject(storagePath, input, size, contentType);
        return verifyOriginal(storagePath, size, contentType);
    }

    public StoredObject uploadOriginal(String storagePath, Path file, String contentType) {
        if (transfers == null) {
            try (InputStream input = Files.newInputStream(file)) {
                return uploadOriginal(storagePath, input, Files.size(file), contentType);
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw storageFailure("原始对象上传失败", exception);
            }
        }
        try {
            long size = Files.size(file);
            String key = keys.objectKey(storagePath);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType);
            PutObjectRequest request = new PutObjectRequest(
                properties.getBucket(), key, file.toFile()
            );
            request.setMetadata(metadata);
            request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
            metrics.record("MULTIPART_UPLOAD", "upload_original", size, () -> {
                transfers.upload(request).waitForUploadResult();
                return null;
            });
            return verifyOriginal(key, size, metadata.getContentType());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw storageFailure("原始对象上传被中断", exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("原始对象上传失败", exception);
        }
    }

    public StoredObject upload(String storagePath, InputStream input, long size, String contentType) {
        return uploadObject(storagePath, input, size, contentType);
    }

    private StoredObject uploadObject(
        String storagePath,
        InputStream input,
        long size,
        String contentType
    ) {
        try {
            String key = keys.objectKey(storagePath);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType);
            PutObjectRequest request = new PutObjectRequest(properties.getBucket(), key, input, metadata);
            request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
            if (isImmutableOriginalKey(key)) {
                request.putCustomRequestHeader("x-cos-forbid-overwrite", "true");
            }
            PutObjectResult result = metrics.record(
                "PUT_OBJECT", "upload", size, () -> cos.putObject(request)
            );
            return new StoredObject(key, size, metadata.getContentType(), result.getETag(), properties.getStorageClass());
        } catch (Exception exception) {
            throw storageFailure("对象存储上传失败", exception);
        }
    }

    public void uploadFile(String storagePath, Path file, String contentType) {
        if (transfers != null) {
            try {
                String key = keys.objectKey(storagePath);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(Files.size(file));
                metadata.setContentType(contentType == null || contentType.isBlank()
                    ? "application/octet-stream" : contentType);
                PutObjectRequest request = new PutObjectRequest(properties.getBucket(), key, file.toFile());
                request.setMetadata(metadata);
                request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
                metrics.record("MULTIPART_UPLOAD", "upload", Files.size(file), () -> {
                    transfers.upload(request).waitForUploadResult();
                    return null;
                });
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw storageFailure("对象存储上传被中断", exception);
            } catch (Exception exception) {
                throw storageFailure("对象存储上传失败", exception);
            }
        }
        try (InputStream input = Files.newInputStream(file)) {
            upload(storagePath, input, Files.size(file), contentType);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("对象存储上传失败", exception);
        }
    }

    public StoredObject metadata(String storagePath) {
        try {
            String key = keys.objectKey(storagePath);
            ObjectMetadata metadata = metrics.record(
                "HEAD_OBJECT", null, 0L,
                () -> cos.getObjectMetadata(properties.getBucket(), key)
            );
            return new StoredObject(
                key,
                metadata.getContentLength(),
                metadata.getContentType(),
                metadata.getETag(),
                metadata.getStorageClass()
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象文件不存在。");
        }
    }

    public Resource resource(String storagePath) {
        try {
            COSObject object = metrics.record(
                "GET_OBJECT", "download", 0L,
                () -> cos.getObject(properties.getBucket(), keys.objectKey(storagePath))
            );
            return new InputStreamResource(object.getObjectContent());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象文件不存在。");
        }
    }

    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            metrics.record("DELETE_OBJECT", null, 0L, () -> {
                try {
                    cos.deleteObject(properties.getBucket(), keys.objectKey(storagePath));
                } catch (CosServiceException exception) {
                    if (exception.getStatusCode() != 404) throw exception;
                }
                return null;
            });
        } catch (CosServiceException exception) {
            throw storageFailure("对象存储删除失败", exception);
        } catch (Exception exception) {
            throw storageFailure("对象存储删除失败", exception);
        }
    }

    public String publicUrl(String storagePath) {
        return cdnSigner().sign(keys.objectKey(storagePath),
            Instant.now().plusSeconds(properties.getCdnAuthorizationSeconds()));
    }

    public String cdnUrl(String storagePath, Instant expiresAt) {
        return cdnSigner().sign(keys.objectKey(storagePath), expiresAt);
    }

    public String modelAccessUrl(String storagePath, Duration validFor) {
        if (validFor == null || validFor.isZero() || validFor.isNegative()) {
            throw new IllegalArgumentException("模型访问链接有效期必须大于 0。");
        }
        try {
            return metrics.record("PRESIGN_GET", null, 0L, () -> cos.generatePresignedUrl(
                    properties.getBucket(),
                    keys.objectKey(storagePath),
                    Date.from(Instant.now().plus(validFor)),
                    HttpMethodName.GET
                ).toString()
            );
        } catch (Exception exception) {
            throw storageFailure("对象访问链接生成失败", exception);
        }
    }

    private CdnTypeDSigner cdnSigner() {
        if (cdnSigner == null) {
            properties.validate();
            cdnSigner = new CdnTypeDSigner(properties.getCdnDomain(), properties.getCdnTypeDKey());
        }
        return cdnSigner;
    }

    public StoredObject promoteVerifiedUpload(StoredObject source, String targetPath) {
        if (source == null || source.key() == null || source.eTag() == null
            || source.eTag().isBlank()) {
            throw new IllegalArgumentException("待固化上传对象信息不完整。");
        }
        String sourceKey = keys.objectKey(source.key());
        String targetKey = keys.objectKey(targetPath);
        if (sourceKey.equals(targetKey) || !sourceKey.startsWith("uploads/")
            || !targetKey.startsWith("materials/")) {
            throw new IllegalArgumentException("上传对象固化路径不合法。");
        }
        return copyVerifiedObject(source, targetKey, "promote_upload");
    }

    public StoredObject copyCompletedUploadOriginal(StoredObject source, String targetPath) {
        if (source == null || source.key() == null || source.eTag() == null
            || source.eTag().isBlank()) {
            throw new IllegalArgumentException("Completed upload object metadata is incomplete.");
        }
        String sourceKey = keys.objectKey(source.key());
        String targetKey = keys.objectKey(targetPath);
        if (!sourceKey.matches("materials/[0-9]+(?:/[0-9]+)?/uploads/[0-9]{6}/[^/]+/v1/original\\.[^/]+")
            || !targetKey.startsWith("materials/") || !targetKey.matches(".*/original\\.[^/]+")
            || sourceKey.equals(targetKey)) {
            throw new IllegalArgumentException("Completed upload copy paths are invalid.");
        }
        return copyVerifiedObject(source, targetKey, "copy_completed_upload");
    }

    private StoredObject copyVerifiedObject(StoredObject source, String targetKey, String operation) {
        CopyObjectRequest request = new CopyObjectRequest(
            properties.getBucket(), keys.objectKey(source.key()), properties.getBucket(), targetKey
        );
        request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
        request.withMatchingETagConstraint(source.eTag());
        try {
            metrics.record("COPY_OBJECT", operation, source.size(), () -> {
                if (transfers == null) {
                    cos.copyObject(request);
                } else {
                    transfers.copy(request).waitForCopyResult();
                }
                return null;
            });
            StoredObject promoted = metadata(targetKey);
            if (promoted.size() != source.size()
                || !same(promoted.contentType(), source.contentType())
                || !same(promoted.storageClass(), properties.getStorageClass())) {
                throw new IllegalStateException("固化对象元数据校验失败。");
            }
            return promoted;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw storageFailure("上传对象固化被中断", exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("上传对象固化失败", exception);
        }
    }

    private StoredObject verifyOriginal(String storagePath, long size, String contentType) {
        StoredObject verified = metadata(storagePath);
        if (verified.size() != size
            || !same(verified.contentType(), contentType)
            || !same(verified.storageClass(), properties.getStorageClass())) {
            throw new IllegalStateException("原始对象元数据校验失败。");
        }
        return verified;
    }

    static boolean isImmutableOriginalKey(String key) {
        return key != null && key.startsWith("materials/") && !key.contains("/derived/")
            && key.matches(".*/original\\.[^/]+");
    }

    private BusinessException storageFailure(String message, Exception exception) {
        return new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            message + "：" + SensitiveValueRedactor.redact(exception.getMessage())
        );
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
