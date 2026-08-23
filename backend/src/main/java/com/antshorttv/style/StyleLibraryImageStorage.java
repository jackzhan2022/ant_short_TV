package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryImageStorage {
    private static final int MAX_SIDE = 1280;
    private static final float JPEG_QUALITY = 0.82f;
    private final ObjectStorageService objectStorageService;

    public StyleLibraryImageStorage(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public Resource resource(StyleLibraryEntity style) {
        return objectStorageService.resource(style.getStoragePath());
    }

    public StoredStyleImage transfer(String externalId, String sourceImageUrl) {
        String storagePath = storagePath(externalId);
        try {
            byte[] bytes = URI.create(sourceImageUrl).toURL().openStream().readAllBytes();
            byte[] compressed = compress(bytes);
            objectStorageService.upload(storagePath, compressed, "image/jpeg");
            return new StoredStyleImage(storagePath, compressed.length);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "风格参考图转存失败：" + exception.getMessage());
        }
    }

    static String storagePath(String externalId, String sourceImageUrl) {
        return storagePath(externalId);
    }

    private static String storagePath(String externalId) {
        return "style-library/public/%s/cover-compressed.jpg".formatted(externalId);
    }

    private static byte[] compress(byte[] source) throws Exception {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(source));
        if (image == null) {
            throw new IllegalArgumentException("图片格式不支持。");
        }
        BufferedImage rgb = toRgb(image);
        BufferedImage resized = resize(rgb);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(resized, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage toRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private static BufferedImage resize(BufferedImage image) {
        int max = Math.max(image.getWidth(), image.getHeight());
        if (max <= MAX_SIDE) {
            return image;
        }
        double scale = (double) MAX_SIDE / max;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }
}

record StoredStyleImage(String storagePath, long fileSize) {
}
