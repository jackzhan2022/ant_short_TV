package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.net.URI;
import java.net.URLConnection;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryImageStorage {
    private final ObjectStorageService objectStorageService;

    public StyleLibraryImageStorage(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public Resource resource(StyleLibraryEntity style) {
        return objectStorageService.resource(style.getStoragePath());
    }

    public StoredStyleImage transfer(String externalId, String sourceImageUrl) {
        String storagePath = storagePath(externalId, sourceImageUrl);
        try {
            byte[] bytes = URI.create(sourceImageUrl).toURL().openStream().readAllBytes();
            objectStorageService.upload(storagePath, bytes, contentType(storagePath));
            return new StoredStyleImage(storagePath, bytes.length);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "风格参考图转存失败：" + exception.getMessage());
        }
    }

    static String storagePath(String externalId, String sourceImageUrl) {
        return "style-library/public/%s/cover.%s".formatted(externalId, extension(sourceImageUrl));
    }

    private static String extension(String sourceImageUrl) {
        String path = URI.create(sourceImageUrl).getPath().toLowerCase();
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "jpg";
        }
        if (path.endsWith(".webp")) {
            return "webp";
        }
        return "png";
    }

    private static String contentType(String storagePath) {
        return URLConnection.guessContentTypeFromName(storagePath) == null
            ? "application/octet-stream"
            : URLConnection.guessContentTypeFromName(storagePath);
    }
}

record StoredStyleImage(String storagePath, long fileSize) {
}
