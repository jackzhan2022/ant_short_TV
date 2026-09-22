package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InspirationManagementMediaService {
    static final long MAX_IMAGE_BYTES = 1_572_864L;
    static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    static final int MAX_IMAGE_DIMENSION = 1920;

    private final InspirationCreationMediaStorage storage;
    private final InspirationThumbnailProcessor thumbnails;

    public InspirationManagementMediaService(
        InspirationCreationMediaStorage storage,
        InspirationThumbnailProcessor thumbnails
    ) {
        this.storage = storage;
        this.thumbnails = thumbnails;
    }

    public ManagedInspirationMedia store(String externalId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validation("请选择要上传的图片或视频。");
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (mimeType.equals("image/jpeg") || mimeType.equals("image/png")) {
            return storeImage(externalId, file, mimeType);
        }
        if (mimeType.equals("video/mp4")) {
            return storeVideo(externalId, file, mimeType);
        }
        throw validation("仅支持 JPEG、PNG 图片或 MP4 视频。");
    }

    private ManagedInspirationMedia storeImage(String externalId, MultipartFile file, String mimeType) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw validation("压缩后的图片不能超过 1.5MB。");
        }
        String extension = "image/png".equals(mimeType) ? "png" : "jpg";
        String originalPath = base(externalId) + "/original." + extension;
        String thumbnailPath = InspirationCreationMediaStorage.thumbnailPath(externalId);
        try {
            byte[] bytes = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw validation("无法读取图片内容。");
            }
            if (Math.max(image.getWidth(), image.getHeight()) > MAX_IMAGE_DIMENSION) {
                throw validation("图片最长边不能超过 1920px。");
            }
            InspirationThumbnail thumbnail = thumbnails.fromImage(bytes, mimeType);
            storage.uploadOriginal(originalPath, bytes, mimeType);
            storage.uploadThumbnail(thumbnailPath, thumbnail);
            return new ManagedInspirationMedia(
                "IMAGE", mimeType, file.getSize(), originalPath,
                thumbnailPath, thumbnail.mimeType(), (long) thumbnail.bytes().length
            );
        } catch (BusinessException exception) {
            cleanup(originalPath, thumbnailPath);
            throw exception;
        } catch (Exception exception) {
            cleanup(originalPath, thumbnailPath);
            throw validation("图片上传失败：" + exception.getMessage());
        }
    }

    private ManagedInspirationMedia storeVideo(String externalId, MultipartFile file, String mimeType) {
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw validation("视频不能超过 200MB。");
        }
        String originalPath = base(externalId) + "/original.mp4";
        String thumbnailPath = InspirationCreationMediaStorage.thumbnailPath(externalId);
        Path temporary = null;
        try {
            temporary = Files.createTempFile("inspiration-upload-", ".mp4");
            file.transferTo(temporary);
            storage.uploadOriginalFile(originalPath, temporary, mimeType);
            InspirationThumbnail thumbnail = thumbnails.fromVideo(temporary);
            storage.uploadThumbnail(thumbnailPath, thumbnail);
            return new ManagedInspirationMedia(
                "VIDEO", mimeType, file.getSize(), originalPath,
                thumbnailPath, thumbnail.mimeType(), (long) thumbnail.bytes().length
            );
        } catch (Exception exception) {
            cleanup(originalPath, thumbnailPath);
            throw validation("视频缩略图生成失败，请检查视频文件后重试。");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // Temporary files are also removed by the operating system eventually.
                }
            }
        }
    }

    private void cleanup(String originalPath, String thumbnailPath) {
        storage.delete(originalPath);
        storage.delete(thumbnailPath);
    }

    private String base(String externalId) {
        return "inspiration/creations/" + externalId;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}

record ManagedInspirationMedia(
    String creationType,
    String mimeType,
    long fileSize,
    String storagePath,
    String thumbnailPath,
    String thumbnailMimeType,
    long thumbnailFileSize
) {
}
