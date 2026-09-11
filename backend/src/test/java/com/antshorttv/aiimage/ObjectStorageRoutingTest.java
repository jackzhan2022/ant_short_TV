package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.storage.ObjectStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class ObjectStorageRoutingTest {

    @Test
    void generatedImageDetectsOriginalFormatAndWritesPngThumbnailLocally() throws Exception {
        Path directory = Files.createTempDirectory("ai-image-local");
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(false);
        AiImageStorageService storageService = new AiImageStorageService(directory.toString(), objectStorageService);
        AiImageTaskEntity task = imageTask();
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream original = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", original);

        StoredImage stored = storageService.storeGenerated(task, 44L, 1,
            "data:image/png;base64," + Base64.getEncoder().encodeToString(original.toByteArray()));

        assertThat(stored.mimeType()).isEqualTo("image/jpeg");
        assertThat(stored.storagePath()).endsWith("/original.jpg");
        BufferedImage thumbnail = ImageIO.read(directory.resolve(stored.thumbnailPath()).toFile());
        assertThat(thumbnail.getWidth()).isEqualTo(512);
        assertThat(thumbnail.getHeight()).isEqualTo(256);
        assertThat(Files.readAllBytes(directory.resolve(stored.thumbnailPath()))).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }

    @Test
    void imagePlaceholderUploadsToObjectStorageWhenEnabled() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(true);
        AiImageStorageService storageService = new AiImageStorageService(
            Files.createTempDirectory("ai-image-local").toString(),
            objectStorageService
        );
        AiImageTaskEntity task = imageTask();

        StoredImage stored = storageService.createPlaceholder(task, 44L, 1);

        String expectedPath = "materials/11/22/images/%s/33-1-44/original.png".formatted(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(eq(expectedPath), bytes.capture(), eq("image/png"));
        assertThat(bytes.getValue()).isNotEmpty();
        assertThat(stored).isNotNull();
    }

    @Test
    void generatedImageUploadsOriginalAndThumbnailToObjectStorage() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(true);
        AiImageStorageService storageService = new AiImageStorageService("target/unused-image-storage", objectStorageService);
        BufferedImage source = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", image);

        StoredImage stored = storageService.storeGenerated(imageTask(), 44L, 1,
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.toByteArray()));

        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> contentTypes = ArgumentCaptor.forClass(String.class);
        verify(objectStorageService, times(2)).upload(paths.capture(), bytes.capture(), contentTypes.capture());
        assertThat(paths.getAllValues()).containsExactly(stored.storagePath(), stored.thumbnailPath());
        assertThat(contentTypes.getAllValues()).containsExactly("image/jpeg", "image/png");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(bytes.getAllValues().get(1))).getWidth()).isEqualTo(512);
    }

    @Test
    void imageResourceReadsFromObjectStorageWhenEnabled() {
        Resource resource = new ByteArrayResource("image".getBytes());
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(true);
        when(objectStorageService.resource("materials/1/2/images/a.png")).thenReturn(resource);
        AiImageStorageService storageService = new AiImageStorageService("target/unused-image-storage", objectStorageService);
        AiImageResultEntity result = new AiImageResultEntity();
        result.setStoragePath("materials/1/2/images/a.png");

        assertThat(storageService.resource(result)).isSameAs(resource);
    }

    @Test
    void materialResourceReadsFromObjectStorageWhenEnabled() {
        Resource resource = new ByteArrayResource("video".getBytes());
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(true);
        when(objectStorageService.resource("/materials/1/2/videos/a.mp4")).thenReturn(resource);
        MaterialFileAccessService accessService = new MaterialFileAccessService(
            "target/unused-material-storage",
            "test-secret",
            objectStorageService
        );

        assertThat(accessService.resource("/materials/1/2/videos/a.mp4")).isSameAs(resource);
        verify(objectStorageService).resource("/materials/1/2/videos/a.mp4");
    }

    private AiImageTaskEntity imageTask() {
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(11L);
        task.setProjectId(22L);
        task.setId(33L);
        task.setTaskType("STORYBOARD_FIRST_FRAME");
        task.setAspectRatio("1:1");
        return task;
    }
}
