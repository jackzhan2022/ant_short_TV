package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ImageDisplayRenditionPlanner;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiImageStorageService {
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory keys;
    private final ImageDisplayRenditionPlanner imageRenditions;

    public AiImageStorageService(ObjectStorageService objectStorageService, ObjectStorageKeyFactory keys) {
        this.objectStorageService = objectStorageService;
        this.keys = keys;
        this.imageRenditions = new ImageDisplayRenditionPlanner(keys);
    }

    public StoredImage storeGenerated(AiImageTaskEntity task, Long resultId, int index, String dataUrl) {
        try {
            int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
            if (comma < 0 || !dataUrl.startsWith("data:image/")) {
                throw new IllegalArgumentException("生成图片不是 Base64 图像数据。");
            }
            byte[] original = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            String mimeType = detectedMimeType(original);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) {
                throw new IllegalArgumentException("生成图片格式无法解析。");
            }
            return store(task, resultId, index, original, mimeType, image.getWidth(), image.getHeight());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片保存失败：" + exception.getMessage());
        }
    }

    public StoredImage createPlaceholder(AiImageTaskEntity task, Long resultId, int index) {
        try {
            int width = width(task.getAspectRatio());
            int height = height(task.getAspectRatio());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(237, 242, 247));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(47, 54, 64));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, width / 18)));
            graphics.drawString(task.getTaskType(), Math.max(24, width / 16), Math.max(64, height / 2));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(18, width / 30)));
            graphics.drawString("Result #" + index + " / " + resultId,
                Math.max(24, width / 16), Math.max(104, height / 2 + 48));
            graphics.dispose();
            ByteArrayOutputStream original = new ByteArrayOutputStream();
            ImageIO.write(image, "png", original);
            return store(task, resultId, index, original.toByteArray(), "image/png", width, height);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片保存失败：" + exception.getMessage());
        }
    }

    public Resource resource(AiImageResultEntity result) {
        return objectStorageService.resource(result.getStoragePath());
    }

    public Resource displayResource(AiImageResultEntity result) {
        return objectStorageService.resource(result.getDisplayPath());
    }

    public Resource thumbnailResource(AiImageResultEntity result) {
        return objectStorageService.resource(result.getThumbnailPath());
    }

    private StoredImage store(
        AiImageTaskEntity task,
        Long resultId,
        int index,
        byte[] original,
        String mimeType,
        int width,
        int height
    ) {
        String originalPath = keys.projectOriginal(
            task.getTenantId(),
            task.getProjectId(),
            "images",
            resultId,
            "task-%d-%d".formatted(task.getId(), index),
            LocalDate.now(),
            extension(mimeType)
        );
        String displayPath = imageRenditions.plan(originalPath, mimeType).objectKey();
        String thumbnailPath = displayPath;
        objectStorageService.upload(originalPath, original, mimeType);
        return new StoredImage(
            originalPath, displayPath, thumbnailPath, mimeType, width, height, (long) original.length
        );
    }

    private int width(String aspectRatio) {
        return switch (aspectRatio) {
            case "1:1" -> 1024;
            case "3:4" -> 768;
            case "4:3" -> 1024;
            case "16:9" -> 1280;
            default -> 720;
        };
    }

    private int height(String aspectRatio) {
        return switch (aspectRatio) {
            case "1:1" -> 1024;
            case "3:4" -> 1024;
            case "4:3" -> 768;
            case "16:9" -> 720;
            default -> 1280;
        };
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String detectedMimeType(byte[] imageBytes) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("生成图片格式无法识别。");
            }
            ImageReader reader = readers.next();
            try {
                return switch (reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    case "gif" -> "image/gif";
                    default -> throw new IllegalArgumentException("生成图片格式不受支持。");
                };
            } finally {
                reader.dispose();
            }
        }
    }
}

record StoredImage(
    String storagePath,
    String displayPath,
    String thumbnailPath,
    String mimeType,
    Integer width,
    Integer height,
    Long fileSize
) {
}
