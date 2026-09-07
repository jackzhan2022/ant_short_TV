package com.antshorttv.inspiration;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.jcodec.api.awt.AWTFrameGrab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InspirationThumbnailProcessor {
    private final int maxDimension;
    private final float jpegQuality;

    @Autowired
    public InspirationThumbnailProcessor(InspirationThumbnailProperties properties) {
        this(properties.getMaxDimension(), properties.getJpegQuality());
    }

    public InspirationThumbnailProcessor(int maxDimension, float jpegQuality) {
        if (maxDimension < 1) {
            throw new IllegalArgumentException("缩略图最大尺寸必须大于 0。");
        }
        if (jpegQuality <= 0 || jpegQuality > 1) {
            throw new IllegalArgumentException("JPEG 质量必须介于 0 和 1 之间。");
        }
        this.maxDimension = maxDimension;
        this.jpegQuality = jpegQuality;
    }

    public InspirationThumbnail fromImage(byte[] source, String mimeType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new IllegalArgumentException("无法读取图片内容。");
            }
            return encode(image);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取图片内容。", exception);
        }
    }

    public InspirationThumbnail fromVideo(Path video) {
        try {
            return encode(AWTFrameGrab.getFrame(video.toFile(), 0));
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法提取视频首帧。", exception);
        }
    }

    private InspirationThumbnail encode(BufferedImage image) throws IOException {
        BufferedImage resized = resize(image);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalArgumentException("当前运行环境不支持 JPEG 图片编码。");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(jpegQuality);
            }
            writer.write(null, new javax.imageio.IIOImage(resized, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return new InspirationThumbnail(output.toByteArray(), "image/jpeg");
    }

    private BufferedImage resize(BufferedImage source) {
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxDimension) {
            return toRgb(source, source.getWidth(), source.getHeight());
        }
        double ratio = (double) maxDimension / largest;
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        return toRgb(source, width, height);
    }

    private BufferedImage toRgb(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}

record InspirationThumbnail(byte[] bytes, String mimeType) {
}
