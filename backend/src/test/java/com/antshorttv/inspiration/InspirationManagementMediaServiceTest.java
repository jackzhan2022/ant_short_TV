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
        org.mockito.Mockito.when(storage.storeUploadedImage(44L, "manual-1", upload))
            .thenReturn(new InspirationCreationMediaTransfer(
                "materials/0/inspiration_creation/202610/44/manual-1/original.jpg",
                "image/jpeg", 1024L,
                "materials/0/inspiration_creation/202610/44/manual-1/derived/display.jpg",
                "image/jpeg", "PENDING"
            ));
        ManagedInspirationMedia media = service.storeImage(44L, "manual-1", upload);

        assertThat(media.creationType()).isEqualTo("IMAGE");
        assertThat(media.storagePath()).endsWith("original.jpg");
        assertThat(media.thumbnailPath()).endsWith("derived/display.jpg");
        assertThat(media.thumbnailStatus()).isEqualTo("PENDING");
        assertThat(media.thumbnailFileSize()).isNull();
        verify(storage).storeUploadedImage(44L, "manual-1", upload);
    }

    @Test
    void acceptsImageWithoutApplicationSizeCeiling() {
        VerifiedMediaUpload upload = upload("image/jpeg", "source.jpg", 10_000_000_000L);
        org.mockito.Mockito.when(storage.storeUploadedImage(45L, "manual-large", upload))
            .thenReturn(new InspirationCreationMediaTransfer(
                "materials/0/inspiration_creation/202610/45/manual-large/original.jpg",
                "image/jpeg", upload.size(), "display.jpg", "image/jpeg", "PENDING"
            ));
        ManagedInspirationMedia media = service.storeImage(45L, "manual-large", upload);

        assertThat(media.fileSize()).isEqualTo(10_000_000_000L);
    }

    @Test
    void storesVerifiedCosImageWithoutBrowserBytesPassingThroughSpring() {
        VerifiedMediaUpload upload = new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/source.png", "image/png", 2048L, "etag-1"
        );

        org.mockito.Mockito.when(storage.storeUploadedImage(46L, "manual-cos", upload))
            .thenReturn(new InspirationCreationMediaTransfer(
                "materials/0/inspiration_creation/202610/46/manual-cos/original.png",
                "image/png", 2048L,
                "materials/0/inspiration_creation/202610/46/manual-cos/derived/display.png",
                "image/png", "PENDING"
            ));
        ManagedInspirationMedia media = service.storeImage(46L, "manual-cos", upload);

        assertThat(media.storagePath()).endsWith("/46/manual-cos/original.png");
        assertThat(media.thumbnailPath()).isEqualTo(
            "materials/0/inspiration_creation/202610/46/manual-cos/derived/display.png"
        );
        verify(storage).storeUploadedImage(46L, "manual-cos", upload);
    }

    @Test
    void preservesAcceptedVideoWhenAsyncCoverSubmissionFails() {
        VerifiedMediaUpload video = upload("video/mp4", "source.mp4", 2048L);
        org.mockito.Mockito.when(storage.storeUploadedVideo(44L, "manual-video", video))
            .thenReturn(new InspirationCreationMediaTransfer("materials/0/inspiration_creation/202610/44/manual-video/original.mp4",
                "video/mp4", 2048L, null, null, "PENDING"));
        ManagedInspirationMedia original = service.storeVideoOriginal(44L, "manual-video", video);
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setId(44L);
        entity.setExternalId("manual-video");
        entity.setStoragePath(original.storagePath());
        entity.setMimeType("video/mp4");
        entity.setFileSize(2048L);
        doThrow(new IllegalArgumentException("Cover unavailable")).when(covers).create(any(), any(), any());

        assertThatThrownBy(() -> service.storeVideoCover(entity)).hasMessageContaining("Cover unavailable");

        assertThat(original.thumbnailStatus()).isEqualTo("PENDING");
        assertThat(original.thumbnailFileSize()).isNull();
        verify(storage, never()).delete(any());
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> service.storeVideoOriginal(
            44L, "manual-text", upload("text/plain", "source.txt", 5L)
        )).hasMessageContaining("仅支持");
    }

    private VerifiedMediaUpload upload(String contentType, String fileName, long size) {
        return new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/" + fileName, contentType, size, "etag-1"
        );
    }
}
