package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.qcloud.cos.COS;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
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
    private CdnTypeDSigner cdnSigner;

    public ObjectStorageService(ObjectStorageProperties properties, COS cos, ObjectStorageKeyFactory keys) {
        this(properties, cos, null, keys);
    }

    @Autowired
    public ObjectStorageService(
        ObjectStorageProperties properties,
        COS cos,
        TransferManager transfers,
        ObjectStorageKeyFactory keys
    ) {
        this.properties = properties;
        this.cos = cos;
        this.transfers = transfers;
        this.keys = keys;
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
            if (isImageOriginal(key, metadata.getContentType())) {
                request.setPicOperations(imageRenditions(key));
            }
            PutObjectResult result = cos.putObject(request);
            if (request.getPicOperations() != null) {
                waitForRendition(keys.rendition(key, "display", "webp"));
            }
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
                if (isImageOriginal(key, metadata.getContentType())) {
                    request.setPicOperations(imageRenditions(key));
                }
                transfers.upload(request).waitForUploadResult();
                if (request.getPicOperations() != null) {
                    waitForRendition(keys.rendition(key, "display", "webp"));
                }
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
            ObjectMetadata metadata = cos.getObjectMetadata(properties.getBucket(), key);
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
            COSObject object = cos.getObject(properties.getBucket(), keys.objectKey(storagePath));
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
            cos.deleteObject(properties.getBucket(), keys.objectKey(storagePath));
        } catch (CosServiceException exception) {
            if (exception.getStatusCode() != 404) {
                throw storageFailure("对象存储删除失败", exception);
            }
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
            return cos.generatePresignedUrl(
                properties.getBucket(),
                keys.objectKey(storagePath),
                Date.from(Instant.now().plus(validFor)),
                HttpMethodName.GET
            ).toString();
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

    private PicOperations imageRenditions(String originalKey) {
        PicOperations operations = new PicOperations();
        operations.setIsPicInfo(1);
        PicOperations.Rule display = new PicOperations.Rule();
        display.setBucket(properties.getBucket());
        display.setFileId(keys.rendition(originalKey, "display", "webp"));
        display.setRule("imageMogr2/format/webp/quality/" + properties.getImageWebpQuality());
        operations.setRules(List.of(display));
        return operations;
    }

    private void waitForRendition(String renditionKey) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                cos.getObjectMetadata(properties.getBucket(), renditionKey);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                if (attempt < 29) Thread.sleep(200);
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
}
