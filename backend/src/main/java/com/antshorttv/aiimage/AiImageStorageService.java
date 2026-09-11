package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiImageStorageService {
    private final Path root;
    private final ObjectStorageService objectStorageService;

    public AiImageStorageService(@Value("${ai.image.storage-dir:storage}") String storageDir, ObjectStorageService objectStorageService) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        this.objectStorageService = objectStorageService;
    }

    public StoredImage storeGenerated(AiImageTaskEntity task, Long resultId, int index, String dataUrl) {
        try {
            int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
            if (comma < 0 || !dataUrl.startsWith("data:image/")) throw new IllegalArgumentException("生成图片不是 Base64 图像数据。");
            byte[] original = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            String mimeType = detectedMimeType(original);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) throw new IllegalArgumentException("生成图片格式无法解析。");
            String extension = extension(mimeType);
            String base = basePath(task, resultId, index);
            String originalPath = base + "/original." + extension;
            String thumbnailPath = base + "/thumbnail.png";
            BufferedImage thumbnail = thumbnail(image);
            ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "png", thumbnailBytes);
            write(originalPath, original, mimeType);
            write(thumbnailPath, thumbnailBytes.toByteArray(), "image/png");
            return new StoredImage(originalPath, thumbnailPath, mimeType, image.getWidth(), image.getHeight(), (long) original.length);
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
            graphics.drawString("Result #" + index + " / " + resultId, Math.max(24, width / 16), Math.max(104, height / 2 + 48));
            graphics.dispose();
            ByteArrayOutputStream original = new ByteArrayOutputStream();
            ImageIO.write(image, "png", original);
            ByteArrayOutputStream thumbnail = new ByteArrayOutputStream();
            ImageIO.write(thumbnail(image), "png", thumbnail);
            String base = basePath(task, resultId, index);
            String path = base + "/original.png";
            String thumbnailPath = base + "/thumbnail.png";
            write(path, original.toByteArray(), "image/png");
            write(thumbnailPath, thumbnail.toByteArray(), "image/png");
            return new StoredImage(path, thumbnailPath, "image/png", image.getWidth(), image.getHeight(), (long) original.size());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片保存失败：" + exception.getMessage());
        }
    }

    public Resource resource(AiImageResultEntity result) { return resource(result.getStoragePath()); }
    public Resource thumbnailResource(AiImageResultEntity result) { return resource(result.getThumbnailPath()); }

    private Resource resource(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) throw new BusinessException(ErrorCode.NOT_FOUND, "图片文件不存在。");
        if (objectStorageService.enabled()) return objectStorageService.resource(storagePath);
        Path file = root.resolve(storagePath).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) throw new BusinessException(ErrorCode.NOT_FOUND, "图片文件不存在。");
        return new FileSystemResource(file);
    }

    private void write(String storagePath, byte[] bytes, String contentType) throws Exception {
        if (objectStorageService.enabled()) { objectStorageService.upload(storagePath, bytes, contentType); return; }
        Path file = root.resolve(storagePath).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("图片存储路径不正确。");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private String basePath(AiImageTaskEntity task, Long resultId, int index) {
        return "materials/%d/%d/images/%s/%d-%d-%d".formatted(task.getTenantId(), task.getProjectId(),
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), task.getId(), index, resultId);
    }

    private BufferedImage thumbnail(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= 512) return source;
        double scale = 512.0 / longest;
        BufferedImage target = new BufferedImage((int) Math.round(source.getWidth() * scale), (int) Math.round(source.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
        graphics.dispose();
        return target;
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
        return switch (mimeType) { case "image/jpeg" -> "jpg"; case "image/webp" -> "webp"; case "image/gif" -> "gif"; default -> "png"; };
    }

    private String detectedMimeType(byte[] imageBytes) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("生成图片格式无法识别。");
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

record StoredImage(String storagePath, String thumbnailPath, String mimeType, Integer width, Integer height, Long fileSize) { }
