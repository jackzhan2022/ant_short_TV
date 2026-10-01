package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ImageDisplayRenditionPlan;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.ImageDisplayRenditionPlanner;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.RegisteredImageDisplay;
import com.antshorttv.storage.RegisteredMediaDetails;
import com.antshorttv.storage.StoredObject;
import com.antshorttv.storage.VerifiedMediaUpload;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationMediaStorage {
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory keys;
    private final ImageDisplayRenditionService imageRenditions;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public InspirationCreationMediaStorage(
        ObjectStorageService objectStorageService,
        ObjectStorageKeyFactory keys,
        ImageDisplayRenditionService imageRenditions
    ) {
        this.objectStorageService = objectStorageService;
        this.keys = keys;
        this.imageRenditions = imageRenditions;
    }

    public InspirationCreationMediaTransfer transfer(
        Long assetId,
        String externalId,
        String mediaUrl
    ) {
        InspirationCreationMediaTransfer existing = reuseRegisteredImage(assetId, externalId);
        if (existing != null) return existing;
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体URL不能为空。");
        }
        Path temporary = null;
        try {
            HttpResponse<InputStream> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(mediaUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体下载失败：" + response.statusCode());
            }
            String mimeType = contentType(response, mediaUrl);
            String storagePath = storagePath(externalId, mediaUrl, mimeType);
            temporary = Files.createTempFile("inspiration-import-", ".media");
            try (InputStream input = response.body()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            long fileSize = Files.size(temporary);
            if (mimeType.startsWith("image/")) {
                if (assetId == null) {
                    throw new IllegalArgumentException("灵感图片必须先持久化业务记录。");
                }
                return storeDownloadedImage(assetId, externalId, temporary, mimeType);
            }
            objectStorageService.uploadFile(storagePath, temporary, mimeType);
            return new InspirationCreationMediaTransfer(
                storagePath, mimeType, fileSize, null, null, null
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体下载失败：" + exception.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // The operating system will eventually clean up an orphaned temporary file.
                }
            }
        }
    }

    InspirationCreationMediaTransfer reuseRegisteredImage(Long assetId, String externalId) {
        if (assetId == null) return null;
        MediaObjectIdentity identity = new MediaObjectIdentity(
            0L, null, "INSPIRATION_CREATION", assetId, externalId
        );
        RegisteredMediaDetails original = imageRenditions.originalDetails(identity);
        if (original == null) return null;
        if (!"READY".equals(original.status()) || original.fileSize() <= 0
            || original.objectKey() == null || original.objectKey().isBlank()
            || original.mimeType() == null || !original.mimeType().startsWith("image/")) {
            throw new IllegalStateException("Registered inspiration original is not ready.");
        }
        ImageDisplayRenditionPlan expected = displayPlan(original.objectKey(), original.mimeType());
        RegisteredMediaDetails display = imageRenditions.displayDetails(identity);
        if (display == null || "FAILED".equals(display.status())) {
            RegisteredImageDisplay retried = imageRenditions.retryFailedDisplay(
                identity, "inspiration-creation:" + assetId
            );
            if (!expected.objectKey().equals(retried.displayKey())) {
                throw new IllegalStateException("Registered inspiration display key is inconsistent.");
            }
            display = imageRenditions.displayDetails(identity);
        }
        if (display == null || !expected.objectKey().equals(display.objectKey())) {
            throw new IllegalStateException("Registered inspiration display key is inconsistent.");
        }
        boolean ready = "READY".equals(display.status()) && display.fileSize() > 0
            && expected.mimeType().equals(display.mimeType());
        if (!ready && !"PENDING".equals(display.status()) && !"SUBMITTED".equals(display.status())) {
            throw new IllegalStateException("Registered inspiration display is not available.");
        }
        return new InspirationCreationMediaTransfer(
            original.objectKey(), original.mimeType(), original.fileSize(),
            display.objectKey(), expected.mimeType(), ready ? "READY" : "PENDING",
            ready ? display.fileSize() : null
        );
    }

    public InspirationCreationMediaTransfer storeUploadedImage(
        Long assetId,
        String externalId,
        VerifiedMediaUpload upload
    ) {
        try {
            ImageDimensions dimensions;
            try (InputStream input = objectStorageService.resource(upload.objectKey()).getInputStream()) {
                dimensions = dimensions(input);
            }
            StoredObject source = objectStorageService.metadata(upload.objectKey());
            if (source.size() != upload.size()
                || !same(source.contentType(), upload.contentType())
                || upload.eTag() != null && !upload.eTag().isBlank()
                    && !same(source.eTag(), upload.eTag())) {
                throw new IllegalStateException("COS 暂存图片元数据与上传会话不一致。");
            }
            String originalPath = imageOriginalPath(
                assetId, externalId, upload.contentType()
            );
            StoredObject original = objectStorageService.copyCompletedUploadOriginal(source, originalPath);
            InspirationCreationMediaTransfer transfer = registerImage(
                assetId, externalId, original, dimensions
            );
            return transfer;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "图片上传失败：" + exception.getMessage()
            );
        }
    }

    public Resource resource(InspirationCreationEntity entity) {
        return objectStorageService.resource(entity.getStoragePath());
    }

    public Resource thumbnailResource(InspirationCreationEntity entity) {
        return objectStorageService.resource(entity.getThumbnailPath());
    }

    public void uploadOriginal(String storagePath, byte[] bytes, String mimeType) {
        objectStorageService.upload(storagePath, bytes, mimeType);
    }

    public void uploadOriginalFile(String storagePath, Path file, String mimeType) {
        objectStorageService.uploadFile(storagePath, file, mimeType);
    }

    public void copyVerifiedUpload(
        String sourcePath,
        String targetPath,
        long size,
        String mimeType
    ) {
        try (InputStream input = objectStorageService.resource(sourcePath).getInputStream()) {
            objectStorageService.upload(targetPath, input, size, mimeType);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "COS 暂存素材固化失败：" + exception.getMessage()
            );
        }
    }

    public void delete(String storagePath) {
        try {
            objectStorageService.delete(storagePath);
        } catch (RuntimeException ignored) {
            // Cleanup is best-effort; database visibility remains authoritative.
        }
    }

    static String storagePath(String externalId, String mediaUrl, String mimeType) {
        return "inspiration/creations/%s/original.%s".formatted(externalId, extension(mediaUrl, mimeType));
    }

    static ImageDisplayRenditionPlan displayPlan(String originalPath, String mimeType) {
        return new ImageDisplayRenditionPlanner(new ObjectStorageKeyFactory())
            .plan(originalPath, mimeType);
    }

    static String coverOriginalPath(String externalId) {
        return "inspiration/creations/%s/cover/original.jpg".formatted(externalId);
    }

    private InspirationCreationMediaTransfer storeDownloadedImage(
        Long assetId,
        String externalId,
        Path file,
        String mimeType
    ) throws Exception {
        ImageDimensions dimensions = dimensions(file);
        String originalPath = imageOriginalPath(assetId, externalId, mimeType);
        StoredObject original = objectStorageService.uploadOriginal(originalPath, file, mimeType);
        return registerImage(assetId, externalId, original, dimensions);
    }

    private InspirationCreationMediaTransfer registerImage(
        Long assetId,
        String externalId,
        StoredObject original,
        ImageDimensions dimensions
    ) {
        MediaObjectIdentity identity = new MediaObjectIdentity(
            0L, null, "INSPIRATION_CREATION", assetId, externalId
        );
        try {
            RegisteredImageDisplay display = imageRenditions.registerOriginalAndSubmit(
                identity,
                original,
                dimensions.width(),
                dimensions.height(),
                "inspiration-creation:" + assetId
            );
            ImageDisplayRenditionPlan expected = displayPlan(
                original.key(), original.contentType()
            );
            if (!expected.objectKey().equals(display.displayKey())) {
                throw new IllegalStateException("万象展示图输出路径与预期不一致。");
            }
            return new InspirationCreationMediaTransfer(
                original.key(), original.contentType(), original.size(),
                display.displayKey(), expected.mimeType(), "PENDING"
            );
        } catch (RuntimeException exception) {
            try {
                imageRenditions.retire(identity);
            } catch (RuntimeException retireFailure) {
                exception.addSuppressed(retireFailure);
            }
            throw exception;
        }
    }

    private String imageOriginalPath(
        Long assetId,
        String externalId,
        String mimeType
    ) {
        return keys.tenantOriginal(
            0L,
            "inspiration_creation",
            assetId,
            externalId,
            LocalDate.now(),
            imageExtension(mimeType)
        );
    }

    private String imageExtension(String mimeType) {
        if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("灵感图片 MIME 类型不合法。");
        }
        String subtype = mimeType.substring("image/".length()).toLowerCase();
        if ("jpeg".equals(subtype) || "jpg".equals(subtype)) return "jpg";
        int suffix = subtype.indexOf('+');
        String extension = suffix < 0 ? subtype : subtype.substring(0, suffix);
        if (!extension.matches("[a-z0-9]{2,5}")) {
            throw new IllegalArgumentException("灵感图片 MIME 类型不受支持。");
        }
        return extension;
    }

    private ImageDimensions dimensions(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            return dimensions(input);
        }
    }

    private ImageDimensions dimensions(InputStream input) throws Exception {
        BufferedImage image = ImageIO.read(input);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("图片格式无法解析。");
        }
        return new ImageDimensions(image.getWidth(), image.getHeight());
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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

    private static String contentType(HttpResponse<?> response, String mediaUrl) {
        return response.headers()
            .firstValue("Content-Type")
            .map(value -> value.split(";")[0].trim())
            .filter(value -> !value.isBlank())
            .orElseGet(() -> contentType(mediaUrl, null));
    }

    static String extension(String mediaUrl, String mimeType) {
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
    Long fileSize,
    String displayPath,
    String displayMimeType,
    String displayStatus,
    Long displayFileSize
) {
    InspirationCreationMediaTransfer(
        String storagePath, String mimeType, Long fileSize, String displayPath,
        String displayMimeType, String displayStatus
    ) {
        this(storagePath, mimeType, fileSize, displayPath, displayMimeType, displayStatus, null);
    }
}

record ImageDimensions(int width, int height) {
}
