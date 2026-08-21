package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationMediaStorage {
    private final ObjectStorageService objectStorageService;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public InspirationCreationMediaStorage(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public InspirationCreationMediaTransfer transfer(String externalId, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体URL不能为空。");
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(URI.create(mediaUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体下载失败：" + response.statusCode());
            }
            byte[] bytes = response.body();
            String mimeType = contentType(response, mediaUrl);
            String storagePath = storagePath(externalId, mediaUrl, mimeType);
            objectStorageService.upload(storagePath, bytes, mimeType);
            return new InspirationCreationMediaTransfer(storagePath, mimeType, (long) bytes.length);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体下载失败：" + exception.getMessage());
        }
    }

    public Resource resource(InspirationCreationEntity entity) {
        return objectStorageService.resource(entity.getStoragePath());
    }

    static String storagePath(String externalId, String mediaUrl, String mimeType) {
        return "inspiration/creations/%s/original.%s".formatted(externalId, extension(mediaUrl, mimeType));
    }

    static String contentType(String storagePath, String storedMimeType) {
        if (storedMimeType != null && !storedMimeType.isBlank()) {
            return storedMimeType;
        }
        String value = storagePath == null ? "" : storagePath.toLowerCase();
        if (value.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (value.endsWith(".png")) {
            return "image/png";
        }
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (value.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private static String contentType(HttpResponse<byte[]> response, String mediaUrl) {
        return response.headers()
            .firstValue("Content-Type")
            .map(value -> value.split(";")[0].trim())
            .filter(value -> !value.isBlank())
            .orElseGet(() -> contentType(mediaUrl, null));
    }

    private static String extension(String mediaUrl, String mimeType) {
        String path = URI.create(mediaUrl).getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String extension = path.substring(dot + 1).toLowerCase();
            if (extension.matches("[a-z0-9]{2,5}")) {
                return extension;
            }
        }
        if ("image/png".equals(mimeType)) {
            return "png";
        }
        if ("image/jpeg".equals(mimeType)) {
            return "jpg";
        }
        if ("image/webp".equals(mimeType)) {
            return "webp";
        }
        if ("video/mp4".equals(mimeType)) {
            return "mp4";
        }
        return "bin";
    }
}

record InspirationCreationMediaTransfer(
    String storagePath,
    String mimeType,
    Long fileSize
) {
}
