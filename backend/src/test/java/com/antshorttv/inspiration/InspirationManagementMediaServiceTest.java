package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class InspirationManagementMediaServiceTest {
    private InspirationCreationMediaStorage storage;
    private InspirationThumbnailProcessor thumbnails;
    private InspirationManagementMediaService service;

    @BeforeEach
    void setUp() {
        storage = mock(InspirationCreationMediaStorage.class);
        thumbnails = mock(InspirationThumbnailProcessor.class);
        service = new InspirationManagementMediaService(storage, thumbnails);
    }

    @Test
    void storesValidatedImageAndThumbnail() throws Exception {
        byte[] image = image(1200, 800);
        when(thumbnails.fromImage(image, "image/jpeg"))
            .thenReturn(new InspirationThumbnail("thumb".getBytes(), "image/jpeg"));

        ManagedInspirationMedia media = service.store("manual-1", new MockMultipartFile(
            "file", "cover.jpg", "image/jpeg", image
        ));

        assertThat(media.creationType()).isEqualTo("IMAGE");
        assertThat(media.storagePath()).endsWith("original.jpg");
        assertThat(media.thumbnailPath()).endsWith("thumbnail.jpg");
        verify(storage).uploadOriginal(media.storagePath(), image, "image/jpeg");
        verify(storage).uploadThumbnail(eq(media.thumbnailPath()), any(InspirationThumbnail.class));
    }

    @Test
    void rejectsOversizedOrOverdimensionedImages() throws Exception {
        assertThatThrownBy(() -> service.store("manual-large", new MockMultipartFile(
            "file", "large.jpg", "image/jpeg", new byte[1_572_865]
        ))).hasMessageContaining("1.5MB");

        byte[] wide = image(1921, 20);
        assertThatThrownBy(() -> service.store("manual-wide", new MockMultipartFile(
            "file", "wide.jpg", "image/jpeg", wide
        ))).hasMessageContaining("1920");
    }

    @Test
    void storesVideoAndCleansUpWhenThumbnailFails() {
        MockMultipartFile video = new MockMultipartFile(
            "file", "clip.mp4", "video/mp4", "fake-video".getBytes()
        );
        doThrow(new IllegalArgumentException("无法提取视频首帧"))
            .when(thumbnails).fromVideo(any());

        assertThatThrownBy(() -> service.store("manual-video", video))
            .hasMessageContaining("视频缩略图生成失败");

        verify(storage).delete("inspiration/creations/manual-video/original.mp4");
        verify(storage).delete("inspiration/creations/manual-video/thumbnail.jpg");
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> service.store("manual-text", new MockMultipartFile(
            "file", "notes.txt", "text/plain", "hello".getBytes()
        ))).hasMessageContaining("仅支持");
    }

    private byte[] image(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
