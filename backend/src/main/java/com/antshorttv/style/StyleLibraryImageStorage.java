package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.ImageDisplayRenditionPlanner;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.RegisteredMediaDetails;
import com.antshorttv.storage.StoredObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryImageStorage {
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory keys;
    private final ImageDisplayRenditionService imageRenditions;
    private final StyleLibraryMapper styleLibraryMapper;
    private final CurrentPrincipal currentPrincipal;
    private final MediaDeliveryGrantService deliveryGrants;

    public StyleLibraryImageStorage(
        ObjectStorageService objectStorageService,
        ObjectStorageKeyFactory keys,
        ImageDisplayRenditionService imageRenditions,
        StyleLibraryMapper styleLibraryMapper,
        CurrentPrincipal currentPrincipal,
        MediaDeliveryGrantService deliveryGrants
    ) {
        this.objectStorageService = objectStorageService;
        this.keys = keys;
        this.imageRenditions = imageRenditions;
        this.styleLibraryMapper = styleLibraryMapper;
        this.currentPrincipal = currentPrincipal;
        this.deliveryGrants = deliveryGrants;
    }

    public String deliveryUrl(StyleLibraryEntity style) {
        if (style == null || !Boolean.TRUE.equals(style.getIsPublic())
            || style.getStoragePath() == null || style.getStoragePath().isBlank()) {
            throw unavailable();
        }
        MediaObjectIdentity identity = identity(style);
        if (!historical(style.getStoragePath())) {
            RegisteredMediaDetails display = imageRenditions.displayDetails(identity);
            if (display == null || !"READY".equals(display.status()) || display.fileSize() <= 0
                || display.mimeType() == null
                || !Set.of("image/jpeg", "image/png", "image/gif").contains(display.mimeType())
                || !style.getStoragePath().equals(display.objectKey())) {
                throw unavailable();
            }
        }
        // Subject 0 is reserved for anonymous access to styles already authorized as public.
        Long subjectId = currentPrincipal.get().map(user -> user.userId()).orElse(0L);
        return deliveryGrants.issue(new DeliveryGrantRequest(
            identity.tenantId(), identity.projectId(), subjectId, identity.assetType(),
            identity.assetId(), identity.versionId(), "DISPLAY", style.getStoragePath(), false
        )).url();
    }

    public StoredStyleImage transfer(String externalId, String sourceImageUrl) {
        StyleLibraryEntity style = styleLibraryMapper.selectOne(new LambdaQueryWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getExternalId, externalId).last("limit 1"));
        if (style == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在。");
        }
        MediaObjectIdentity identity = identity(style);
        if (historical(style.getStoragePath())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "历史风格参考图不参与转存。");
        }
        try {
            RegisteredMediaDetails original = imageRenditions.originalDetails(identity);
            if (original != null) {
                return reuseOriginal(style, identity, original);
            }
            byte[] bytes;
            try (var input = URI.create(sourceImageUrl).toURL().openStream()) {
                bytes = input.readAllBytes();
            }
            ImageMetadata metadata = imageMetadata(bytes);
            String originalPath = keys.tenantOriginal(0L, "style_library", style.getId(),
                style.getExternalId(), LocalDate.now(), metadata.extension());
            StoredObject uploaded = objectStorageService.uploadOriginal(originalPath, bytes, metadata.mimeType());
            String displayPath = new ImageDisplayRenditionPlanner(keys)
                .plan(originalPath, metadata.mimeType()).objectKey();
            persistDisplay(style, displayPath, metadata.width(), metadata.height());
            var display = imageRenditions.registerOriginalAndSubmit(identity, uploaded,
                metadata.width(), metadata.height(), correlation(style));
            return new StoredStyleImage(display.displayKey(), uploaded.size());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "风格参考图转存失败。");
        }
    }

    private StoredStyleImage reuseOriginal(
        StyleLibraryEntity style,
        MediaObjectIdentity identity,
        RegisteredMediaDetails original
    ) {
        if (!"READY".equals(original.status()) || original.fileSize() <= 0
            || original.objectKey() == null || original.objectKey().isBlank()
            || original.mimeType() == null || !original.mimeType().startsWith("image/")) {
            throw new IllegalStateException("风格参考图原图尚未就绪。");
        }
        RegisteredMediaDetails display = imageRenditions.displayDetails(identity);
        String displayPath;
        if (display == null || "FAILED".equals(display.status())) {
            displayPath = imageRenditions.retryFailedDisplay(identity, correlation(style)).displayKey();
        } else if ("READY".equals(display.status()) || "PENDING".equals(display.status())) {
            displayPath = display.objectKey();
        } else {
            throw new IllegalStateException("风格参考图展示版本不可用。");
        }
        persistDisplay(style, displayPath, style.getImageWidth(), style.getImageHeight());
        return new StoredStyleImage(displayPath, original.fileSize());
    }

    private void persistDisplay(StyleLibraryEntity style, String path, Integer width, Integer height) {
        int updated = styleLibraryMapper.update(new LambdaUpdateWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getId, style.getId())
            .set(StyleLibraryEntity::getStoragePath, path)
            .set(StyleLibraryEntity::getImageWidth, width)
            .set(StyleLibraryEntity::getImageHeight, height)
            .set(StyleLibraryEntity::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) throw new IllegalStateException("风格参考图记录已不存在。");
    }

    private MediaObjectIdentity identity(StyleLibraryEntity style) {
        if (style.getId() == null || style.getId() <= 0 || style.getExternalId() == null
            || style.getExternalId().isBlank()) {
            throw unavailable();
        }
        return new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", style.getId(), style.getExternalId());
    }

    private boolean historical(String path) {
        return path != null && path.startsWith("style-library/public/");
    }

    private String correlation(StyleLibraryEntity style) {
        return "style-library-" + style.getId() + "-" + style.getExternalId();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.NOT_FOUND, "风格参考图尚未就绪。");
    }

    private ImageMetadata imageMetadata(byte[] bytes) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("风格参考图格式无法识别。");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpg", "jpeg" -> "jpg";
                    case "png" -> "png";
                    case "gif" -> "gif";
                    case "webp" -> "webp";
                    default -> throw new IllegalArgumentException("风格参考图格式不受支持。");
                };
                if (reader.read(0) == null) throw new IllegalArgumentException("风格参考图内容无效。");
                return new ImageMetadata(extension, extension.equals("jpg") ? "image/jpeg" : "image/" + extension,
                    reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private record ImageMetadata(String extension, String mimeType, int width, int height) {
        private ImageMetadata {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("风格参考图尺寸无效。");
        }
    }
}

record StoredStyleImage(String storagePath, long fileSize) {
}
