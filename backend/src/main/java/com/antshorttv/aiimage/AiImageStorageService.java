package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiImageStorageService {
    private final Path root;

    public AiImageStorageService(@Value("${ai.image.storage-dir:storage}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredImage createPlaceholder(AiImageTaskEntity task, Long resultId, int index) {
        int width = width(task.getAspectRatio());
        int height = height(task.getAspectRatio());
        String relativePath = "materials/%d/%d/images/%s/%d-%d.png".formatted(
            task.getTenantId(),
            task.getProjectId(),
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            task.getId(),
            index
        );
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片存储路径不正确。");
        }
        try {
            Files.createDirectories(file.getParent());
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
            ImageIO.write(image, "png", file.toFile());
            return new StoredImage(relativePath, width, height, Files.size(file));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片保存失败：" + exception.getMessage());
        }
    }

    public Resource resource(AiImageResultEntity result) {
        if (result.getStoragePath() == null || result.getStoragePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片文件不存在。");
        }
        Path file = root.resolve(result.getStoragePath()).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片文件不存在。");
        }
        return new FileSystemResource(file);
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
}

record StoredImage(String storagePath, Integer width, Integer height, Long fileSize) {
}
