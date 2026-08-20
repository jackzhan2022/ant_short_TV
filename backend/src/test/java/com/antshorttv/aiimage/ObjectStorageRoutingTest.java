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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class ObjectStorageRoutingTest {

    @Test
    void imagePlaceholderUploadsToObjectStorageWhenEnabled() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.enabled()).thenReturn(true);
        AiImageStorageService storageService = new AiImageStorageService(
            Files.createTempDirectory("ai-image-local").toString(),
            objectStorageService
        );
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(11L);
        task.setProjectId(22L);
        task.setId(33L);
        task.setTaskType("STORYBOARD_FIRST_FRAME");
        task.setAspectRatio("1:1");

        StoredImage stored = storageService.createPlaceholder(task, 44L, 1);

        String expectedPath = "materials/11/22/images/%s/33-1.png".formatted(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(eq(expectedPath), bytes.capture(), eq("image/png"));
        assertThat(bytes.getValue()).isNotEmpty();
        assertThat(stored).isNotNull();
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
}
