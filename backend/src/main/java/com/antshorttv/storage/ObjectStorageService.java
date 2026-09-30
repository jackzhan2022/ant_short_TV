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
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.TransferManager;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final ImageDisplayRenditionPlanner imageRenditions;
    private CdnTypeDSigner cdnSigner;

    public ObjectStorageService(
        ObjectStorageProperties properties,
        COS cos,
        ObjectStorageKeyFactory keys,
        CosStorageMetrics metrics,
        ImageDisplayRenditionPlanner imageRenditions
    ) {
        this(properties, cos, null, keys, metrics, imageRenditions);
    }

    @Autowired
    public ObjectStorageService(
        ObjectStorageProperties properties,
        COS cos,
        TransferManager transfers,
        ObjectStorageKeyFactory keys,
        CosStorageMetrics metrics,
        ImageDisplayRenditionPlanner imageRenditions
    ) {
        this.properties = properties;
        this.cos = cos;
        this.transfers = transfers;
        this.keys = keys;
        this.metrics = metrics;
        this.imageRenditions = imageRenditions;
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

    public StoredObject upload(String storagePath, InputStream input, long size, String contentType) {
        try {
            String key = keys.objectKey(storagePath);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType);
            PutObjectRequest request = new PutObjectRequest(properties.getBucket(), key, input, metadata);
            request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
            ImageDisplayRenditionPlan rendition = isImageOriginal(key, metadata.getContentType())
                ? imageRenditions.plan(key, metadata.getContentType()) : null;
            if (rendition != null) request.setPicOperations(imageOperations(rendition));
            PutObjectResult result = metrics.record(
                "PUT_OBJECT", "upload", size, () -> cos.putObject(request)
            );
            if (rendition != null) waitForRendition(rendition.objectKey());
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
                ImageDisplayRenditionPlan rendition = isImageOriginal(key, metadata.getContentType())
                    ? imageRenditions.plan(key, metadata.getContentType()) : null;
                if (rendition != null) request.setPicOperations(imageOperations(rendition));
                metrics.record("MULTIPART_UPLOAD", "upload", Files.size(file), () -> {
                    transfers.upload(request).waitForUploadResult();
                    return null;
                });
                if (rendition != null) waitForRendition(rendition.objectKey());
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

    private boolean isImageOriginal(String key, String contentType) {
        int separator = key.lastIndexOf('/');
        String fileName = separator < 0 ? key : key.substring(separator + 1);
        return contentType.startsWith("image/") && fileName.startsWith("original.")
            && !key.contains("/derived/");
    }

    private PicOperations imageOperations(ImageDisplayRenditionPlan plan) {
        PicOperations operations = new PicOperations();
        operations.setIsPicInfo(1);
        PicOperations.Rule display = new PicOperations.Rule();
        display.setBucket(properties.getBucket());
        display.setFileId(plan.objectKey());
        display.setRule(plan.processRule());
        operations.setRules(List.of(display));
        return operations;
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
        CopyObjectRequest request = new CopyObjectRequest(
            properties.getBucket(), sourceKey, properties.getBucket(), targetKey
        );
        request.setStorageClass(StorageClass.fromValue(properties.getStorageClass()));
        request.withMatchingETagConstraint(source.eTag());
        try {
            metrics.record("COPY_OBJECT", "promote_upload", source.size(), () -> {
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

    private void waitForRendition(String renditionKey) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                metrics.record(
                    "HEAD_OBJECT", null, 0L,
                    () -> cos.getObjectMetadata(properties.getBucket(), renditionKey)
                );
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                if (attempt < 29) {
                    metrics.retry("HEAD_OBJECT");
                    Thread.sleep(200);
                }
            }
        }
        throw new IllegalStateException(
            "万象压缩图在限定时间内未就绪。",
            lastFailure
        );
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
