package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.snapshot.CosSnapshotRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudInfiniteVideoCoverServiceTest {
    private static final String VIDEO = "materials/0/inspiration_creation/202610/44/video-44/original.mp4";
    private static final String ORIGINAL = "materials/0/inspiration_creation/202610/44/video-44/cover/original.jpg";
    private static final String DISPLAY = ORIGINAL.replace("/original.jpg", "/derived/display.jpg");
    private final MediaObjectIdentity identity = new MediaObjectIdentity(0L, null, "INSPIRATION_CREATION", 44L, "video-44");
    private COS cos;
    private ObjectStorageService storage;
    private ImageDisplayRenditionService renditions;
    private CloudInfiniteVideoCoverService service;
    private byte[] jpeg;
    private StoredObject stored;

    @BeforeEach
    void setUp() throws Exception {
        cos = mock(COS.class);
        storage = mock(ObjectStorageService.class);
        renditions = mock(ImageDisplayRenditionService.class);
        service = new CloudInfiniteVideoCoverService(cos, storage, new ObjectStorageKeyFactory(), "antv-1418200553", renditions);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "jpg", output);
        jpeg = output.toByteArray();
        stored = new StoredObject(ORIGINAL, jpeg.length, "image/jpeg", "cover-etag", "INTELLIGENT_TIERING");
        when(cos.getSnapshot(any())).thenAnswer(call -> new ByteArrayInputStream(jpeg));
        when(storage.uploadOriginal(ORIGINAL, jpeg, "image/jpeg")).thenReturn(stored);
        when(renditions.registerOriginalAndSubmit(identity, stored, 12, 8, "inspiration-creation:44"))
            .thenReturn(new RegisteredImageDisplay(ORIGINAL, DISPLAY, "SUBMITTED"));
    }

    @Test
    void persistsVerifiedSnapshotWithDecodedDimensionsAndSubmitsAsyncDisplay() {
        RegisteredImageDisplay display = service.create(identity, VIDEO, ORIGINAL);

        ArgumentCaptor<CosSnapshotRequest> request = ArgumentCaptor.forClass(CosSnapshotRequest.class);
        verify(cos).getSnapshot(request.capture());
        assertThat(request.getValue().getTime()).isEqualTo("1");
        assertThat(request.getValue().getFormat()).isEqualTo("jpg");
        verify(storage).uploadOriginal(ORIGINAL, jpeg, "image/jpeg");
        verify(renditions).registerOriginalAndSubmit(identity, stored, 12, 8, "inspiration-creation:44");
        verify(storage, never()).upload(any(String.class), any(byte[].class), any(String.class));
        assertThat(display.displayKey()).isEqualTo(DISPLAY);
        assertThat(display.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void fallsBackOnceToZeroSecondsWhenOneSecondSnapshotCannotBeDecoded() {
        when(cos.getSnapshot(any())).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}))
            .thenReturn(new ByteArrayInputStream(jpeg));

        service.create(identity, VIDEO, ORIGINAL);

        ArgumentCaptor<CosSnapshotRequest> requests = ArgumentCaptor.forClass(CosSnapshotRequest.class);
        verify(cos, times(2)).getSnapshot(requests.capture());
        assertThat(requests.getAllValues()).extracting(CosSnapshotRequest::getTime).containsExactly("1", "0");
        verify(storage, times(1)).uploadOriginal(ORIGINAL, jpeg, "image/jpeg");
    }

    @Test
    void repeatedCreationReusesRegisteredOriginalAndSubmittedDisplay() {
        registered("SUBMITTED", 0L);

        assertThat(service.create(identity, VIDEO, ORIGINAL).displayKey()).isEqualTo(DISPLAY);
        assertThat(service.create(identity, VIDEO, ORIGINAL).displayKey()).isEqualTo(DISPLAY);

        verify(cos, never()).getSnapshot(any());
        verify(storage, never()).uploadOriginal(any(String.class), any(byte[].class), any(String.class));
        verify(renditions, never()).registerOriginalAndSubmit(any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(renditions, never()).retryFailedDisplay(any(), any());
    }

    @Test
    void failedDisplayRetriesFromAcceptedCoverWithoutAnotherSnapshot() {
        registered("FAILED", 0L);
        when(renditions.retryFailedDisplay(identity, "inspiration-creation:44"))
            .thenReturn(new RegisteredImageDisplay(ORIGINAL, DISPLAY, "SUBMITTED"));

        assertThat(service.create(identity, VIDEO, ORIGINAL).status()).isEqualTo("SUBMITTED");

        verify(renditions).retryFailedDisplay(identity, "inspiration-creation:44");
        verify(cos, never()).getSnapshot(any());
        verify(storage, never()).uploadOriginal(any(String.class), any(byte[].class), any(String.class));
    }

    @Test
    void invalidSnapshotsNeverRegisterOrPublishGuessedCover() {
        when(cos.getSnapshot(any())).thenAnswer(call -> new ByteArrayInputStream(new byte[] {1, 2}));

        assertThatThrownBy(() -> service.create(identity, VIDEO, ORIGINAL)).hasMessageContaining("无法解析");

        verify(cos, times(2)).getSnapshot(any());
        verify(storage, never()).uploadOriginal(any(String.class), any(byte[].class), any(String.class));
    }

    private void registered(String status, long displaySize) {
        when(renditions.originalDetails(identity)).thenReturn(new RegisteredMediaDetails(
            1L, identity, "ORIGINAL", ORIGINAL, "image/jpeg", jpeg.length, "READY", null));
        when(renditions.displayDetails(identity)).thenReturn(new RegisteredMediaDetails(
            2L, identity, "DISPLAY_IMAGE_SLIM", DISPLAY, "image/jpeg", displaySize, status, null));
    }
}
