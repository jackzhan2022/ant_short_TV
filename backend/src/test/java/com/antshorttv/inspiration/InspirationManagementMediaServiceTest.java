package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.antshorttv.storage.CloudInfiniteVideoCoverService;
import com.antshorttv.storage.VerifiedMediaUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspirationManagementMediaServiceTest {
    private InspirationCreationMediaStorage storage;
    private CloudInfiniteVideoCoverService covers;
    private InspirationManagementMediaService service;

    @BeforeEach
    void setUp() {
        storage = mock(InspirationCreationMediaStorage.class);
        covers = mock(CloudInfiniteVideoCoverService.class);
        service = new InspirationManagementMediaService(storage, covers);
    }

    @Test
    void storesValidatedImageAndThumbnail() {
        VerifiedMediaUpload upload = upload("image/jpeg", "source.jpg", 1024L);
        ManagedInspirationMedia media = service.store("manual-1", upload);

        assertThat(media.creationType()).isEqualTo("IMAGE");
        assertThat(media.storagePath()).endsWith("original.jpg");
        assertThat(media.thumbnailPath()).endsWith("derived/display.webp");
        verify(storage).copyVerifiedUpload(
            upload.objectKey(), media.storagePath(), upload.size(), upload.contentType()
        );
        verify(storage).delete(upload.objectKey());
    }

    @Test
    void acceptsImageWithoutApplicationSizeCeiling() {
        ManagedInspirationMedia media = service.store(
            "manual-large", upload("image/jpeg", "source.jpg", 10_000_000_000L)
        );

        assertThat(media.fileSize()).isEqualTo(10_000_000_000L);
    }

    @Test
    void storesVerifiedCosImageWithoutBrowserBytesPassingThroughSpring() {
        VerifiedMediaUpload upload = new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/source.png", "image/png", 2048L, "etag-1"
        );

        ManagedInspirationMedia media = service.store("manual-cos", upload);

        assertThat(media.storagePath()).isEqualTo("inspiration/creations/manual-cos/original.png");
        assertThat(media.thumbnailPath()).isEqualTo(
            "inspiration/creations/manual-cos/derived/display.webp"
        );
        verify(storage).copyVerifiedUpload(
            "uploads/11/session-1/source.png",
            "inspiration/creations/manual-cos/original.png",
            2048L,
            "image/png"
        );
    }

    @Test
    void storesVideoAndCleansUpWhenThumbnailFails() {
        VerifiedMediaUpload video = upload("video/mp4", "source.mp4", 2048L);
        doThrow(new IllegalArgumentException("无法生成视频封面"))
            .when(covers).create(any(), any());

        assertThatThrownBy(() -> service.store("manual-video", video))
            .hasMessageContaining("视频缩略图生成失败");

        verify(storage).delete("inspiration/creations/manual-video/original.mp4");
        verify(storage).delete("inspiration/creations/manual-video/cover/original.jpg");
        verify(storage, never()).delete(video.objectKey());
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> service.store(
            "manual-text", upload("text/plain", "source.txt", 5L)
        )).hasMessageContaining("仅支持");
    }

    private VerifiedMediaUpload upload(String contentType, String fileName, long size) {
        return new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/" + fileName, contentType, size, "etag-1"
        );
    }
}
