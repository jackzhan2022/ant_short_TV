package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.storage.ObjectStorageService;
import java.nio.file.Files;
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
    void generatedImageDetectsOriginalFormatAndUsesPersistentImageSlimRendition() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = new AiImageStorageService(objectStorageService, new com.antshorttv.storage.ObjectStorageKeyFactory());
        AiImageTaskEntity task = imageTask();
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream original = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", original);

        StoredImage stored = storageService.storeGenerated(task, 44L, 1,
            "data:image/png;base64," + Base64.getEncoder().encodeToString(original.toByteArray()));

        assertThat(stored.mimeType()).isEqualTo("image/jpeg");
        assertThat(stored.storagePath()).endsWith("/original.jpg");
        assertThat(stored.displayPath()).endsWith("/derived/display.jpg");
        assertThat(stored.thumbnailPath()).isEqualTo(stored.displayPath());
        verify(objectStorageService).upload(eq(stored.storagePath()), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void imagePlaceholderUploadsToObjectStorage() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = new AiImageStorageService(
            objectStorageService, new com.antshorttv.storage.ObjectStorageKeyFactory()
        );
        AiImageTaskEntity task = imageTask();

        StoredImage stored = storageService.createPlaceholder(task, 44L, 1);

        String expectedPath = "materials/11/22/images/%s/44/task-33-1/original.png"
            .formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(eq(expectedPath), bytes.capture(), eq("image/png"));
        assertThat(bytes.getValue()).isNotEmpty();
        assertThat(stored).isNotNull();
    }

    @Test
    void generatedImageUploadsOnlyOriginalToObjectStorage() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = new AiImageStorageService(
            objectStorageService, new com.antshorttv.storage.ObjectStorageKeyFactory()
        );
        BufferedImage source = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", image);

        StoredImage stored = storageService.storeGenerated(imageTask(), 44L, 1,
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.toByteArray()));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> contentTypes = ArgumentCaptor.forClass(String.class);
        verify(objectStorageService).upload(path.capture(), bytes.capture(), contentTypes.capture());
        assertThat(path.getValue()).isEqualTo(stored.storagePath());
        assertThat(contentTypes.getValue()).isEqualTo("image/jpeg");
        assertThat(bytes.getValue()).isEqualTo(image.toByteArray());
    }

    @Test
    void imageResourceReadsFromObjectStorage() {
        Resource resource = new ByteArrayResource("image".getBytes());
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.resource("materials/1/2/images/a.png")).thenReturn(resource);
        AiImageStorageService storageService = new AiImageStorageService(
            objectStorageService, new com.antshorttv.storage.ObjectStorageKeyFactory()
        );
        AiImageResultEntity result = new AiImageResultEntity();
        result.setStoragePath("materials/1/2/images/a.png");

        assertThat(storageService.resource(result)).isSameAs(resource);
    }

    @Test
    void materialResourceReadsFromObjectStorage() {
        Resource resource = new ByteArrayResource("video".getBytes());
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.resource("materials/1/2/videos/a.mp4")).thenReturn(resource);
        MaterialFileAccessService accessService = new MaterialFileAccessService(
            objectStorageService,
            new com.antshorttv.storage.ObjectStorageKeyFactory()
        );

        assertThat(accessService.resource("/materials/1/2/videos/a.mp4")).isSameAs(resource);
        verify(objectStorageService).resource("materials/1/2/videos/a.mp4");
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
