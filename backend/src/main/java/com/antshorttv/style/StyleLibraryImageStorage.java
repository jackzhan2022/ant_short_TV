package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ImageDisplayRenditionPlanner;
import java.net.URI;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryImageStorage {
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory keys;

    public StyleLibraryImageStorage(ObjectStorageService objectStorageService, ObjectStorageKeyFactory keys) {
        this.objectStorageService = objectStorageService;
        this.keys = keys;
    }

    public String deliveryUrl(StyleLibraryEntity style) {
        return objectStorageService.publicUrl(style.getStoragePath());
    }

    public StoredStyleImage transfer(String externalId, String sourceImageUrl) {
        String originalPath = originalPath(externalId, sourceImageUrl);
        try (var input = URI.create(sourceImageUrl).toURL().openStream()) {
            byte[] bytes = input.readAllBytes();
            objectStorageService.upload(originalPath, bytes, contentType(originalPath));
            return new StoredStyleImage(
                new ImageDisplayRenditionPlanner(keys)
                    .plan(originalPath, contentType(originalPath)).objectKey(),
                bytes.length
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "风格参考图转存失败：" + exception.getMessage());
        }
    }

    static String storagePath(String externalId, String sourceImageUrl) {
        ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();
        String originalPath = originalPath(externalId, sourceImageUrl);
        return new ImageDisplayRenditionPlanner(keys)
            .plan(originalPath, contentType(originalPath)).objectKey();
    }

    private static String originalPath(String externalId, String sourceImageUrl) {
        String extension = "jpg";
        try {
            String path = URI.create(sourceImageUrl).getPath();
            int dot = path == null ? -1 : path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String candidate = path.substring(dot + 1).toLowerCase();
                if (candidate.matches("png|jpe?g|webp|gif")) {
                    extension = candidate.equals("jpeg") ? "jpg" : candidate;
                }
            }
        } catch (Exception ignored) {
            // Provider URLs without a useful suffix use a JPEG-compatible extension.
        }
        return "platform/style-library/%s/source/original.%s".formatted(externalId, extension);
    }

    private static String contentType(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        if (key.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}

record StoredStyleImage(String storagePath, long fileSize) {
}
