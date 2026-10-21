package com.antshorttv.script;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetVisualUploadService {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final AssetVisualVariantService variants;
    private final AssetNormalizationGuard normalization;
    private final MediaUploadSessionService uploads;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final ObjectStorageProperties properties;
    private final ImageDisplayRenditionService renditions;
    private final AiImageResultMapper images;

    public AssetVisualUploadService(AssetVisualVariantService variants, AssetNormalizationGuard normalization,
        MediaUploadSessionService uploads, ObjectStorageService storage, ObjectStorageKeyFactory keys,
        ObjectStorageProperties properties, ImageDisplayRenditionService renditions, AiImageResultMapper images) {
        this.variants = variants;
        this.normalization = normalization;
        this.uploads = uploads;
        this.storage = storage;
        this.keys = keys;
        this.properties = properties;
        this.renditions = renditions;
        this.images = images;
    }

    @Transactional
    public AssetVisualVariantService.VariantResponse bind(
        Long tenantId, Long projectId, Long variantId, Long userId, String sessionToken
    ) {
        normalization.requireReady(tenantId, projectId);
        variants.requireUploadTarget(tenantId, projectId, variantId);
        var upload = uploads.requireCompleted(userId, tenantId, projectId, sessionToken);
        String extension = switch (upload.contentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidImage();
        };
        if (upload.size() <= 0 || upload.size() > MAX_BYTES) throw invalidImage();
        String key = keys.projectOriginal(tenantId, projectId, "images", variantId,
            "upload-" + upload.sessionToken(), LocalDate.of(2000, 1, 1), extension);
        // Retrying a completed binding reuses the image rather than creating duplicate results.
        AiImageResultEntity image = images.selectOne(new LambdaQueryWrapper<AiImageResultEntity>()
            .eq(AiImageResultEntity::getTenantId, tenantId).eq(AiImageResultEntity::getProjectId, projectId)
            .eq(AiImageResultEntity::getTargetType, "VISUAL_VARIANT").eq(AiImageResultEntity::getTargetId, variantId)
            .eq(AiImageResultEntity::getStoragePath, key).eq(AiImageResultEntity::getStatus, "ACTIVE").last("limit 1"));
        if (image == null) image = store(tenantId, projectId, variantId, upload, key);
        return variants.bindUploadedImage(tenantId, projectId, variantId, image.getId(), image.getImageUrl());
    }

    private AiImageResultEntity store(Long tenantId, Long projectId, Long variantId, VerifiedMediaUpload upload, String key) {
        try {
            byte[] bytes;
            try (var input = storage.resource(upload.objectKey()).getInputStream()) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length != upload.size() || bytes.length > MAX_BYTES) throw invalidImage();
            int width, height;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                        case "jpg", "jpeg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "webp" -> "image/webp";
                        default -> throw invalidImage();
                    };
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                    if (!mime.equals(upload.contentType()) || width <= 0 || height <= 0
                        || (long) width * height > 40_000_000L || reader.read(0) == null) throw invalidImage();
                } finally {
                    reader.dispose();
                }
            }
            var original = storage.copyCompletedUploadOriginal(new StoredObject(upload.objectKey(), upload.size(),
                upload.contentType(), upload.eTag(), properties.getStorageClass()), key);
            AiImageResultEntity image = new AiImageResultEntity();
            image.setTenantId(tenantId);
            image.setProjectId(projectId);
            image.setTargetType("VISUAL_VARIANT");
            image.setTargetId(variantId);
            image.setImageUrl("");
            image.setStoragePath(key);
            image.setMimeType(upload.contentType());
            image.setWidth(width);
            image.setHeight(height);
            image.setFileSize(upload.size());
            image.setIsSelected(false);
            image.setStatus("ACTIVE");
            image.setCreatedAt(LocalDateTime.now());
            image.setUpdatedAt(LocalDateTime.now());
            images.insert(image);
            var registered = renditions.registerOriginalAndSubmit(
                new MediaObjectIdentity(tenantId, projectId, "AI_IMAGE_RESULT", image.getId(), "result-" + image.getId()),
                original, width, height, "asset-upload:" + image.getId());
            String base = "/api/projects/" + projectId + "/ai-image-results/" + image.getId();
            image.setImageUrl(base + "/display");
            image.setThumbnailUrl(base + "/thumbnail");
            image.setDisplayPath(registered.displayKey());
            image.setThumbnailPath(registered.displayKey());
            images.updateById(image);
            return image;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片上传处理失败，请重新选择有效图片或稍后重试。");
        }
    }

    private BusinessException invalidImage() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择不超过20MB、4000万像素的有效 JPG、PNG 或 WebP 图片。");
    }
}
