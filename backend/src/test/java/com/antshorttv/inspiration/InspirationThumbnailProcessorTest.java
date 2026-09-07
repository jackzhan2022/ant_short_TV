package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.jcodec.api.awt.AWTSequenceEncoder;

class InspirationThumbnailProcessorTest {

    @Test
    void createsBoundedJpegThumbnailWithoutUpscaling() throws Exception {
        InspirationThumbnailProcessor processor = new InspirationThumbnailProcessor(80, 0.82f);

        InspirationThumbnail thumbnail = processor.fromImage(png(240, 120), "image/png");
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(thumbnail.bytes()));

        assertThat(thumbnail.mimeType()).isEqualTo("image/jpeg");
        assertThat(image.getWidth()).isEqualTo(80);
        assertThat(image.getHeight()).isEqualTo(40);

        InspirationThumbnail small = processor.fromImage(png(40, 20), "image/png");
        BufferedImage smallImage = ImageIO.read(new java.io.ByteArrayInputStream(small.bytes()));
        assertThat(smallImage.getWidth()).isEqualTo(40);
        assertThat(smallImage.getHeight()).isEqualTo(20);
    }

    @Test
    void rejectsUnreadableImage() {
        InspirationThumbnailProcessor processor = new InspirationThumbnailProcessor(80, 0.82f);

        assertThatThrownBy(() -> processor.fromImage("not-an-image".getBytes(), "image/png"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("图片");
    }

    @Test
    void rejectsOutOfRangeJpegQuality() {
        assertThatThrownBy(() -> new InspirationThumbnailProcessor(80, 1.1f))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("质量");
    }

    @Test
    void rejectsUndecodableVideoFrame() throws Exception {
        InspirationThumbnailProcessor processor = new InspirationThumbnailProcessor(80, 0.82f);
        Path video = Files.createTempFile("inspiration-thumbnail-", ".mp4");
        Files.writeString(video, "not-a-video");

        try {
            assertThatThrownBy(() -> processor.fromVideo(video))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("视频");
        } finally {
            Files.deleteIfExists(video);
        }
    }

    @Test
    void createsBoundedJpegThumbnailFromVideoFirstFrame() throws Exception {
        InspirationThumbnailProcessor processor = new InspirationThumbnailProcessor(80, 0.82f);
        Path video = Files.createTempFile("inspiration-thumbnail-", ".mp4");
        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(video.toFile(), 1);
        encoder.encodeImage(coloredImage(240, 120));
        encoder.finish();

        try {
            InspirationThumbnail thumbnail = processor.fromVideo(video);
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(thumbnail.bytes()));

            assertThat(thumbnail.mimeType()).isEqualTo("image/jpeg");
            assertThat(image.getWidth()).isEqualTo(80);
            assertThat(image.getHeight()).isEqualTo(40);
        } finally {
            Files.deleteIfExists(video);
        }
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = coloredImage(width, height);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private BufferedImage coloredImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        return image;
    }
}
