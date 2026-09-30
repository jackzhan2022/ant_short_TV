package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.ImageDisplayRenditionPlanner;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.RegisteredImageDisplay;
import com.antshorttv.storage.StoredObject;
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
    void generatedImageRegistersVerifiedOriginalAndSubmitsDisplayRendition() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        AiImageStorageService storageService = new AiImageStorageService(
            objectStorageService, new com.antshorttv.storage.ObjectStorageKeyFactory(), renditions
        );
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream original = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", original);
        StoredObject verified = new StoredObject(
            "materials/11/22/images/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "/44/task-33-1/original.jpg",
            original.size(),
            "image/jpeg",
            "etag-original",
            "INTELLIGENT_TIERING"
        );
        when(objectStorageService.uploadOriginal(
            eq(verified.key()), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/jpeg")
        )).thenReturn(verified);
        when(renditions.registerOriginalAndSubmit(
            org.mockito.ArgumentMatchers.any(), eq(verified), eq(800), eq(400), eq("ai-image-result:44")
        )).thenReturn(new RegisteredImageDisplay(
            verified.key(),
            verified.key().replace("/original.jpg", "/derived/display.jpg"),
            "SUBMITTED"
        ));

        StoredImage stored = storageService.storeGenerated(
            imageTask(), 44L, 1,
            "data:image/png;base64," + Base64.getEncoder().encodeToString(original.toByteArray())
        );

        assertThat(stored.storagePath()).isEqualTo(verified.key());
        assertThat(stored.displayPath()).endsWith("/derived/display.jpg");
        verify(objectStorageService).uploadOriginal(
            eq(verified.key()), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/jpeg")
        );
        ArgumentCaptor<MediaObjectIdentity> identity = ArgumentCaptor.forClass(MediaObjectIdentity.class);
        verify(renditions).registerOriginalAndSubmit(
            identity.capture(), eq(verified), eq(800), eq(400), eq("ai-image-result:44")
        );
        assertThat(identity.getValue()).isEqualTo(
            new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44")
        );
    }

    @Test
    void generatedImageDetectsOriginalFormatAndUsesPersistentImageSlimRendition() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = storageService(objectStorageService);
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
        verify(objectStorageService).uploadOriginal(
            eq(stored.storagePath()), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/jpeg")
        );
    }

    @Test
    void imagePlaceholderUploadsToObjectStorage() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = storageService(objectStorageService);
        AiImageTaskEntity task = imageTask();

        StoredImage stored = storageService.createPlaceholder(task, 44L, 1);

        String expectedPath = "materials/11/22/images/%s/44/task-33-1/original.png"
            .formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).uploadOriginal(eq(expectedPath), bytes.capture(), eq("image/png"));
        assertThat(bytes.getValue()).isNotEmpty();
        assertThat(stored).isNotNull();
    }

    @Test
    void generatedImageUploadsOnlyOriginalToObjectStorage() throws Exception {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AiImageStorageService storageService = storageService(objectStorageService);
        BufferedImage source = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", image);

        StoredImage stored = storageService.storeGenerated(imageTask(), 44L, 1,
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.toByteArray()));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> contentTypes = ArgumentCaptor.forClass(String.class);
        verify(objectStorageService).uploadOriginal(path.capture(), bytes.capture(), contentTypes.capture());
        assertThat(path.getValue()).isEqualTo(stored.storagePath());
        assertThat(contentTypes.getValue()).isEqualTo("image/jpeg");
        assertThat(bytes.getValue()).isEqualTo(image.toByteArray());
    }

    @Test
    void imageResourceReadsFromObjectStorage() {
        Resource resource = new ByteArrayResource("image".getBytes());
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.resource("materials/1/2/images/a.png")).thenReturn(resource);
        AiImageStorageService storageService = storageService(objectStorageService);
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

    private AiImageStorageService storageService(ObjectStorageService objectStorageService) {
        com.antshorttv.storage.ObjectStorageKeyFactory keys =
            new com.antshorttv.storage.ObjectStorageKeyFactory();
        ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        when(objectStorageService.uploadOriginal(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(byte[].class),
            org.mockito.ArgumentMatchers.anyString()
        )).thenAnswer(invocation -> new StoredObject(
            invocation.getArgument(0),
            ((byte[]) invocation.getArgument(1)).length,
            invocation.getArgument(2),
            "etag-original",
            "INTELLIGENT_TIERING"
        ));
        when(renditions.registerOriginalAndSubmit(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString()
        )).thenAnswer(invocation -> {
            StoredObject original = invocation.getArgument(1);
            String display = new ImageDisplayRenditionPlanner(keys)
                .plan(original.key(), original.contentType()).objectKey();
            return new RegisteredImageDisplay(original.key(), display, "SUBMITTED");
        });
        return new AiImageStorageService(objectStorageService, keys, renditions);
    }
}
