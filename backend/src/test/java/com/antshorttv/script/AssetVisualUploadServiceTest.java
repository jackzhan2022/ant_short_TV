package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class AssetVisualUploadServiceTest {
    private final AssetVisualVariantService variants = mock(AssetVisualVariantService.class);
    private final AssetNormalizationGuard normalization = mock(AssetNormalizationGuard.class);
    private final MediaUploadSessionService uploads = mock(MediaUploadSessionService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
    private final AiImageResultMapper images = mock(AiImageResultMapper.class);
    private final AssetVisualUploadService service = new AssetVisualUploadService(variants, normalization, new LocalImageUploadService(uploads,
        storage, new ObjectStorageKeyFactory(), new ObjectStorageProperties(), renditions, images));
    private byte[] png;

    @BeforeEach
    void setup() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", buffer);
        png = buffer.toByteArray();
        verified(png, "image/png");
        when(storage.copyCompletedUploadOriginal(any(), anyString())).thenAnswer(call ->
            new StoredObject(call.getArgument(1), png.length, "image/png", "etag", "STANDARD"));
        when(images.insert(any(AiImageResultEntity.class))).thenAnswer(call -> {
            AiImageResultEntity image = call.getArgument(0);
            image.setId(99L);
            assertThat(image.getTaskId()).isNull();
            assertThat(image.getExecutionId()).isNull();
            return 1;
        });
        when(renditions.registerOriginalAndSubmit(any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new RegisteredImageDisplay("original", "display", "PENDING"));
    }

    private void verified(byte[] bytes, String mime) {
        when(uploads.requireCompleted(4L, 1L, 2L, "token"))
            .thenReturn(new VerifiedMediaUpload("token", "verified-original", mime, bytes.length, "etag"));
        when(storage.resource("verified-original")).thenReturn(new ByteArrayResource(bytes));
    }

    @Test
    void persistsLocalImageWithoutAiExecutionAndRegistersMediaDelivery() {
        service.bind(1L, 2L, 3L, 4L, "token");
        verify(uploads).requireCompleted(4L, 1L, 2L, "token");
        verify(renditions).registerOriginalAndSubmit(eq(new MediaObjectIdentity(1L, 2L, "AI_IMAGE_RESULT", 99L, "result-99")),
            any(), eq(20), eq(30), anyString());
        verify(variants).bindUploadedImage(1L, 2L, 3L, 99L, "/api/projects/2/ai-image-results/99/display");
    }

    @Test
    void retriesBindingWithoutDuplicatingAnExistingResult() {
        AiImageResultEntity existing = new AiImageResultEntity();
        existing.setId(99L);
        existing.setImageUrl("image");
        when(images.selectOne(any())).thenReturn(existing);
        service.bind(1L, 2L, 3L, 4L, "token");
        verify(images, never()).insert(any(AiImageResultEntity.class));
        verifyNoInteractions(renditions);
        verify(variants).bindUploadedImage(1L, 2L, 3L, 99L, "image");
    }

    @Test
    void rejectsCorruptAndMislabeledImagesWithoutChangingTheVariant() {
        verified("not an image".getBytes(), "image/png");
        assertThatThrownBy(() -> service.bind(1L, 2L, 3L, 4L, "token")).isInstanceOf(BusinessException.class);
        verified(png, "image/jpeg");
        assertThatThrownBy(() -> service.bind(1L, 2L, 3L, 4L, "token")).isInstanceOf(BusinessException.class);
        verify(variants, never()).bindUploadedImage(any(), any(), any(), any(), any());
        verifyNoInteractions(renditions);
    }

    @Test
    void rejectsUploadsOutsideTheUsersProjectBeforeReadingStorage() {
        when(uploads.requireCompleted(4L, 1L, 2L, "token")).thenThrow(new IllegalArgumentException("scope"));
        assertThatThrownBy(() -> service.bind(1L, 2L, 3L, 4L, "token")).hasMessage("scope");
        verify(storage, never()).resource(anyString());
        verify(variants, never()).bindUploadedImage(any(), any(), any(), any(), any());
    }

    @Test
    void blocksBindingDuringNormalizationOrGeneration() {
        doThrow(new IllegalArgumentException("normalizing")).when(normalization).requireReady(1L, 2L);
        assertThatThrownBy(() -> service.bind(1L, 2L, 3L, 4L, "token")).hasMessage("normalizing");
        verify(uploads, never()).requireCompleted(any(), any(), any(), any());
        reset(normalization);
        doThrow(new IllegalArgumentException("generating")).when(variants).requireUploadTarget(1L, 2L, 3L);
        assertThatThrownBy(() -> service.bind(1L, 2L, 3L, 4L, "token")).hasMessage("generating");
        verify(variants, never()).bindUploadedImage(any(), any(), any(), any(), any());
    }
}
